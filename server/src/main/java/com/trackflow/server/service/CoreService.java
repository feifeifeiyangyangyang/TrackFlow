package com.trackflow.server.service;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.trackflow.server.adapter.*;
import com.trackflow.server.domain.Enums.*;
import com.trackflow.server.messaging.EventProcessMessage;
import com.trackflow.server.messaging.RabbitNames;
import com.trackflow.server.messaging.ReconciliationMessage;
import com.trackflow.server.util.CryptoUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class CoreService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final CarrierAdapters adapters;
  private final ShipmentReplayService replayService;
  private final CarrierTrackQueryClient trackQueryClient;
  private final long skewSeconds;
  private final int eventMaxRetryCount;
  public CoreService(JdbcTemplate jdbc, ObjectMapper mapper, CarrierAdapters adapters, ShipmentReplayService replayService, CarrierTrackQueryClient trackQueryClient, @Value("${trackflow.webhookClockSkewSeconds:300}") long skewSeconds, @Value("${trackflow.eventMaxRetryCount:5}") int eventMaxRetryCount) {
    this.jdbc = jdbc; this.mapper = mapper; this.adapters = adapters; this.replayService = replayService; this.trackQueryClient = trackQueryClient; this.skewSeconds = skewSeconds; this.eventMaxRetryCount = eventMaxRetryCount;
  }
  public Map<String,Object> dashboard() {
    Map<String,Object> out = new LinkedHashMap<>();
    out.put("shipmentTotal", count("select count(*) from shipment"));
    out.put("inTransit", count("select count(*) from shipment where current_status in ('PICKED_UP','IN_TRANSIT','ARRIVED_AT_STATION','OUT_FOR_DELIVERY')"));
    out.put("delivered", count("select count(*) from shipment where current_status='DELIVERED'"));
    out.put("openAnomalies", count("select count(*) from shipment_anomaly where status='OPEN'"));
    out.put("todayEvents", count("select count(*) from normalized_event where created_at >= CURRENT_DATE"));
    out.put("todayDifferences", count("select coalesce(sum(difference_count),0) from reconciliation_batch where created_at >= CURRENT_DATE"));
    out.put("statusDistribution", jdbc.queryForList("select current_status as status, count(*) as total from shipment group by current_status"));
    out.put("anomalyDistribution", jdbc.queryForList("select anomaly_type as type, count(*) as total from shipment_anomaly group by anomaly_type"));
    out.put("recentEvents", jdbc.queryForList("select ne.*, s.tracking_no from normalized_event ne join shipment s on s.id=ne.shipment_id order by ne.event_time desc limit 8"));
    return out;
  }
  public List<Map<String,Object>> carriers() { return jdbc.queryForList("select id, carrier_code, carrier_name, query_base_url, enabled, created_at, updated_at from carrier order by id"); }
  public List<Map<String,Object>> shipments(String q) {
    if (q == null || q.isBlank()) return jdbc.queryForList("select s.*, c.carrier_code, c.carrier_name from shipment s join carrier c on c.id=s.carrier_id order by s.updated_at desc limit 100");
    return jdbc.queryForList("select s.*, c.carrier_code, c.carrier_name from shipment s join carrier c on c.id=s.carrier_id where s.tracking_no like ? or s.business_order_no like ? order by s.updated_at desc limit 100", "%"+q+"%", "%"+q+"%");
  }
  public Map<String,Object> shipmentDetail(long id) {
    Map<String,Object> detail = new LinkedHashMap<>();
    detail.put("shipment", jdbc.queryForMap("select s.*, c.carrier_code, c.carrier_name from shipment s join carrier c on c.id=s.carrier_id where s.id=?", id));
    detail.put("events", jdbc.queryForList("select * from normalized_event where shipment_id=? order by event_time, received_time, id", id));
    detail.put("rawEvents", jdbc.queryForList("select id, carrier_id, shipment_id, tracking_no, external_event_id, raw_status, signature_valid, received_time, process_status, created_at from raw_carrier_event where shipment_id=? order by received_time desc", id));
    detail.put("anomalies", jdbc.queryForList("select * from shipment_anomaly where shipment_id=? order by detected_at desc", id));
    detail.put("reconciliationTasks", jdbc.queryForList("select rt.* from reconciliation_task rt where rt.shipment_id=? order by rt.created_at desc", id));
    return detail;
  }
  @Transactional public Map<String,Object> createShipment(String carrierCode, String trackingNo, String businessOrderNo) {
    Long carrierId = carrierId(carrierCode);
    try { jdbc.update("insert into shipment(tracking_no, carrier_id, business_order_no, current_status, has_open_anomaly, version, created_at, updated_at) values(?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", trackingNo, carrierId, businessOrderNo, "CREATED", false, 0); } catch (DuplicateKeyException ignored) {}
    return jdbc.queryForMap("select s.*, c.carrier_code from shipment s join carrier c on c.id=s.carrier_id where s.carrier_id=? and s.tracking_no=?", carrierId, trackingNo);
  }
  @Transactional public Map<String,Object> receiveWebhook(String carrierCode, String timestamp, String signature, String rawBody, Map<String,String> headers) {
    Long carrierId = carrierId(carrierCode);
    String secret = jdbc.queryForObject("select webhook_secret from carrier where id=?", String.class, carrierId);
    validateSignature(timestamp, signature, rawBody, secret);
    try {
      JsonNode json = mapper.readTree(rawBody);
      String rawStatus = carrierCode.equals("MOCK_A") ? json.path("status").asText("UNKNOWN") : json.path("event_code").asText("UNKNOWN");
      CarrierAdapter.ParsedEvent parsed = adapters.get(carrierCode).parse(json, mapping(carrierId, rawStatus));
      String fingerprint = eventFingerprint(carrierCode, parsed);
      String idempotencyKey = parsed.externalEventId() == null || parsed.externalEventId().isBlank() ? "FP:" + fingerprint : "EXT:" + parsed.externalEventId();
      long shipmentId = ensureShipment(carrierId, parsed.trackingNo(), "AUTO-" + parsed.trackingNo());
      try {
        jdbc.update("insert into raw_carrier_event(carrier_id, shipment_id, tracking_no, external_event_id, idempotency_key, event_fingerprint, raw_status, raw_body, request_headers, signature_valid, received_time, process_status, created_at, updated_at) values(?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", carrierId, shipmentId, parsed.trackingNo(), parsed.externalEventId(), idempotencyKey, fingerprint, parsed.rawStatus(), rawBody, mapper.writeValueAsString(headers), true, "PENDING");
        long rawId = jdbc.queryForObject("select id from raw_carrier_event where carrier_id=? and idempotency_key=?", Long.class, carrierId, idempotencyKey);
        jdbc.update("insert into event_process_task(raw_event_id, task_key, status, retry_count, max_retry_count, next_retry_time, created_at, updated_at) values(?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", rawId, "RAW:"+rawId, "PENDING", 0, eventMaxRetryCount);
        Long taskId = jdbc.queryForObject("select id from event_process_task where raw_event_id=?", Long.class, rawId);
        createEventOutbox(rawId, taskId, 0, Instant.now());
        return Map.of("accepted", true, "duplicate", false, "rawEventId", rawId, "taskId", taskId);
      } catch (DuplicateKeyException dup) {
        Long rawId = jdbc.queryForObject("select id from raw_carrier_event where carrier_id=? and idempotency_key=?", Long.class, carrierId, idempotencyKey);
        Long taskId = jdbc.queryForObject("select id from event_process_task where raw_event_id=?", Long.class, rawId);
        return Map.of("accepted", true, "duplicate", true, "rawEventId", rawId, "taskId", taskId);
      }
    } catch (ResponseStatusException e) { throw e; }
      catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook body: " + e.getMessage()); }
  }
  @Transactional public void processRawEvent(long rawId) {
    try {
      processRawEventStrict(rawId);
    } catch (Exception e) {
      jdbc.update("update raw_carrier_event set process_status='FAILED', failure_reason=?, updated_at=CURRENT_TIMESTAMP where id=?", e.getMessage(), rawId);
      jdbc.update("update event_process_task set status='FAILED', last_error=?, updated_at=CURRENT_TIMESTAMP where raw_event_id=?", e.getMessage(), rawId);
    }
  }

  @Transactional public String processEventTask(long taskId, String workerId) {
    Map<String,Object> task = jdbc.queryForMap("select * from event_process_task where id=?", taskId);
    String status = (String) task.get("status");
    if ("SUCCESS".equals(status)) return "SUCCESS_ALREADY";
    int claimed = jdbc.update("""
        update event_process_task
           set status='RUNNING', locked_at=CURRENT_TIMESTAMP, locked_by=?, updated_at=CURRENT_TIMESTAMP
         where id=?
           and (status='PENDING' or (status='RETRY_WAIT' and (next_retry_time is null or next_retry_time <= CURRENT_TIMESTAMP)))
        """, workerId, taskId);
    if (claimed == 0) return "NOT_CLAIMED";
    long rawId = ((Number) task.get("raw_event_id")).longValue();
    int retryCount = ((Number) task.get("retry_count")).intValue();
    int maxRetryCount = ((Number) task.get("max_retry_count")).intValue();
    try {
      processRawEventStrict(rawId);
      jdbc.update("update event_process_task set status='SUCCESS', finished_at=CURRENT_TIMESTAMP, last_error=null, updated_at=CURRENT_TIMESTAMP where id=?", taskId);
      return "SUCCESS";
    } catch (Exception e) {
      int nextRetry = retryCount + 1;
      if (nextRetry >= maxRetryCount || !isRetryable(e)) {
        jdbc.update("update raw_carrier_event set process_status='FAILED', failure_reason=?, updated_at=CURRENT_TIMESTAMP where id=?", e.getMessage(), rawId);
        jdbc.update("update event_process_task set status='FAILED', retry_count=?, last_error=?, finished_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP where id=?", nextRetry, e.getMessage(), taskId);
        return "FAILED";
      }
      Instant nextRetryTime = Instant.now().plusSeconds(backoffSeconds(nextRetry));
      jdbc.update("update raw_carrier_event set process_status='PENDING', failure_reason=?, updated_at=CURRENT_TIMESTAMP where id=?", e.getMessage(), rawId);
      jdbc.update("update event_process_task set status='RETRY_WAIT', retry_count=?, next_retry_time=?, last_error=?, locked_at=null, locked_by=null, updated_at=CURRENT_TIMESTAMP where id=?", nextRetry, Timestamp.from(nextRetryTime), e.getMessage(), taskId);
      createEventOutbox(rawId, taskId, nextRetry, nextRetryTime);
      return "RETRY_WAIT";
    }
  }

  private void processRawEventStrict(long rawId) throws Exception {
    Map<String,Object> raw = jdbc.queryForMap("select * from raw_carrier_event where id=?", rawId);
    Long existing = jdbc.queryForObject("select count(*) from normalized_event where raw_event_id=?", Long.class, rawId);
    if (existing != null && existing > 0) {
      jdbc.update("update raw_carrier_event set process_status='SUCCESS', updated_at=CURRENT_TIMESTAMP where id=?", rawId);
      jdbc.update("update event_process_task set status='SUCCESS', finished_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP where raw_event_id=?", rawId);
      return;
    }
    long carrierId = ((Number) raw.get("carrier_id")).longValue();
    String carrierCode = jdbc.queryForObject("select carrier_code from carrier where id=?", String.class, carrierId);
    JsonNode json = mapper.readTree((String) raw.get("raw_body"));
    CarrierAdapter.ParsedEvent parsed = adapters.get(carrierCode).parse(json, mapping(carrierId, (String) raw.get("raw_status")));
    boolean late = isLate(((Number) raw.get("shipment_id")).longValue(), parsed.eventTime());
    jdbc.update("insert into normalized_event(shipment_id, carrier_id, raw_event_id, external_event_id, normalized_status, raw_status, event_time, received_time, location, description, source, late_arrival, applied_to_state, validation_status, event_fingerprint, created_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)", raw.get("shipment_id"), carrierId, rawId, parsed.externalEventId(), parsed.normalizedStatus().name(), parsed.rawStatus(), Timestamp.from(parsed.eventTime()), raw.get("received_time"), parsed.location(), parsed.description(), "WEBHOOK", late, false, parsed.normalizedStatus() == NormalizedStatus.UNKNOWN ? "UNKNOWN_STATUS" : "VALID", raw.get("event_fingerprint"));
    replayService.rebuildShipment(((Number) raw.get("shipment_id")).longValue());
    jdbc.update("update raw_carrier_event set process_status='SUCCESS', updated_at=CURRENT_TIMESTAMP where id=?", rawId);
  }
  @Transactional public void rebuildShipment(long shipmentId) {
    replayService.rebuildShipment(shipmentId);
  }
  @Transactional public Map<String,Object> reconcile(long shipmentId, String operator) {
    Map<String,Object> shipment = jdbc.queryForMap("select s.*, c.carrier_code from shipment s join carrier c on c.id=s.carrier_id where s.id=?", shipmentId);
    String batchNo = "RC-" + Instant.now().toEpochMilli();
    jdbc.update("insert into reconciliation_batch(batch_no, trigger_type, status, total_count, success_count, failed_count, difference_count, inserted_event_count, started_at, created_at, updated_at) values(?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", batchNo, "MANUAL", "RUNNING", 1, 0, 0, 0, 0);
    long batchId = jdbc.queryForObject("select id from reconciliation_batch where batch_no=?", Long.class, batchNo);
    String taskKey = "RC:"+batchNo+":"+shipmentId;
    jdbc.update("""
        insert into reconciliation_task(batch_id, shipment_id, task_key, status, retry_count, max_retry_count, next_retry_time, before_status, difference_found, inserted_event_count, created_at, updated_at)
        values(?,?,?,?,?,?,CURRENT_TIMESTAMP,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
        """, batchId, shipmentId, taskKey, "PENDING", 0, 3, shipment.get("current_status"), false, 0);
    long taskId = jdbc.queryForObject("select id from reconciliation_task where task_key=?", Long.class, taskKey);
    createReconciliationOutbox(taskId, batchId, shipmentId, operator, 0, Instant.now());
    String requestId = UUID.randomUUID().toString();
    jdbc.update("insert into operation_log(operation_type, resource_type, resource_id, operator_name, request_id, summary, created_at) values(?,?,?,?,?,?,CURRENT_TIMESTAMP)", "RECONCILE_REQUEST", "SHIPMENT", shipmentId, operator, requestId, "Reconciliation task requested: " + taskId);
    Map<String,Object> response = new LinkedHashMap<>(jdbc.queryForMap("select * from reconciliation_batch where id=?", batchId));
    response.put("taskId", taskId);
    return response;
  }
  public List<Map<String,Object>> rawEvents() { return jdbc.queryForList("select id, carrier_id, shipment_id, tracking_no, external_event_id, raw_status, signature_valid, received_time, process_status, failure_reason, created_at from raw_carrier_event order by received_time desc limit 100"); }
  public List<Map<String,Object>> eventTasks(String status) {
    if (status == null || status.isBlank()) return jdbc.queryForList("select * from event_process_task order by updated_at desc limit 100");
    return jdbc.queryForList("select * from event_process_task where status=? order by updated_at desc limit 100", status);
  }
  @Transactional public Map<String,Object> retryFailedTask(long taskId, String operator) {
    Map<String,Object> task = jdbc.queryForMap("select * from event_process_task where id=?", taskId);
    if (!"FAILED".equals(task.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only FAILED tasks can be manually retried");
    long rawId = ((Number) task.get("raw_event_id")).longValue();
    jdbc.update("update event_process_task set status='PENDING', retry_count=0, next_retry_time=CURRENT_TIMESTAMP, locked_at=null, locked_by=null, last_error=null, finished_at=null, updated_at=CURRENT_TIMESTAMP where id=?", taskId);
    jdbc.update("update raw_carrier_event set process_status='PENDING', failure_reason=null, updated_at=CURRENT_TIMESTAMP where id=?", rawId);
    createEventOutbox(rawId, taskId, 0, Instant.now());
    jdbc.update("insert into operation_log(operation_type, resource_type, resource_id, operator_name, request_id, summary, created_at) values(?,?,?,?,?,?,CURRENT_TIMESTAMP)", "TASK_RETRY", "EVENT_PROCESS_TASK", taskId, operator, UUID.randomUUID().toString(), "Manual retry event process task");
    return jdbc.queryForMap("select * from event_process_task where id=?", taskId);
  }
  @Transactional public String processReconciliationTask(long taskId, String workerId) {
    Map<String,Object> task = jdbc.queryForMap("select * from reconciliation_task where id=?", taskId);
    String status = (String) task.get("status");
    if ("SUCCESS".equals(status)) return "SUCCESS_ALREADY";
    int claimed = jdbc.update("""
        update reconciliation_task
           set status='RUNNING', locked_at=CURRENT_TIMESTAMP, locked_by=?, started_at=coalesce(started_at, CURRENT_TIMESTAMP), updated_at=CURRENT_TIMESTAMP
         where id=?
           and (status='PENDING' or (status='RETRY_WAIT' and (next_retry_time is null or next_retry_time <= CURRENT_TIMESTAMP)))
        """, workerId, taskId);
    if (claimed == 0) return "NOT_CLAIMED";
    int retryCount = ((Number) task.get("retry_count")).intValue();
    int maxRetryCount = ((Number) task.get("max_retry_count")).intValue();
    try {
      executeReconciliationTask(taskId);
      return "SUCCESS";
    } catch (Exception e) {
      int nextRetry = retryCount + 1;
      long batchId = ((Number) task.get("batch_id")).longValue();
      if (nextRetry >= maxRetryCount || !isRetryable(e)) {
        jdbc.update("update reconciliation_task set status='FAILED', retry_count=?, last_error=?, completed_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP where id=?", nextRetry, e.getMessage(), taskId);
        jdbc.update("update reconciliation_batch set status='FAILED', failed_count=1, completed_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP where id=?", batchId);
        return "FAILED";
      }
      Instant nextRetryTime = Instant.now().plusSeconds(backoffSeconds(nextRetry));
      jdbc.update("update reconciliation_task set status='RETRY_WAIT', retry_count=?, next_retry_time=?, last_error=?, locked_at=null, locked_by=null, updated_at=CURRENT_TIMESTAMP where id=?", nextRetry, Timestamp.from(nextRetryTime), e.getMessage(), taskId);
      createReconciliationOutbox(taskId, batchId, ((Number) task.get("shipment_id")).longValue(), "system", nextRetry, nextRetryTime);
      return "RETRY_WAIT";
    }
  }
  public List<Map<String,Object>> anomalies() { return jdbc.queryForList("select a.*, s.tracking_no from shipment_anomaly a join shipment s on s.id=a.shipment_id order by detected_at desc limit 100"); }
  public List<Map<String,Object>> batches() { return jdbc.queryForList("select * from reconciliation_batch order by created_at desc limit 100"); }
  public List<Map<String,Object>> reconciliationTasks() {
    return jdbc.queryForList("""
        select rt.*, s.tracking_no, c.carrier_code
          from reconciliation_task rt
          join shipment s on s.id=rt.shipment_id
          join carrier c on c.id=s.carrier_id
         order by rt.updated_at desc
         limit 100
        """);
  }
  @Transactional public void resolveAnomaly(long id, AnomalyStatus target, String note, String operator) {
    Map<String,Object> anomaly = jdbc.queryForMap("select id, status from shipment_anomaly where id=?", id);
    AnomalyStatus current = AnomalyStatus.valueOf((String) anomaly.get("status"));
    if (!canTransition(current, target)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Illegal anomaly status transition: " + current + " -> " + target);
    if ((target == AnomalyStatus.RESOLVED || target == AnomalyStatus.IGNORED) && (note == null || note.isBlank())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resolutionNote is required for " + target);
    }
    jdbc.update("update shipment_anomaly set status=?, resolution_note=?, resolved_at=?, version=version+1, updated_at=CURRENT_TIMESTAMP where id=?", target.name(), note, target == AnomalyStatus.RESOLVED ? Timestamp.from(Instant.now()) : null, id);
    jdbc.update("insert into operation_log(operation_type, resource_type, resource_id, operator_name, request_id, summary, created_at) values(?,?,?,?,?,?,CURRENT_TIMESTAMP)", "ANOMALY_"+target.name(), "ANOMALY", id, operator, UUID.randomUUID().toString(), note);
  }
  @Scheduled(fixedDelay = 15000) public void scheduledRecovery() {
    for (Map<String,Object> row : jdbc.queryForList("select id, raw_event_id, retry_count from event_process_task where status='PENDING' or (status='RETRY_WAIT' and (next_retry_time is null or next_retry_time <= CURRENT_TIMESTAMP)) limit 10")) {
      createEventOutbox(((Number) row.get("raw_event_id")).longValue(), ((Number) row.get("id")).longValue(), ((Number) row.get("retry_count")).intValue(), Instant.now());
    }
    for (Map<String,Object> row : jdbc.queryForList("select id, batch_id, shipment_id, retry_count from reconciliation_task where status='PENDING' or (status='RETRY_WAIT' and (next_retry_time is null or next_retry_time <= CURRENT_TIMESTAMP)) limit 10")) {
      createReconciliationOutbox(((Number) row.get("id")).longValue(), ((Number) row.get("batch_id")).longValue(), ((Number) row.get("shipment_id")).longValue(), "system", ((Number) row.get("retry_count")).intValue(), Instant.now());
    }
  }
  private void executeReconciliationTask(long taskId) {
    Map<String,Object> task = jdbc.queryForMap("select * from reconciliation_task where id=?", taskId);
    long batchId = ((Number) task.get("batch_id")).longValue();
    long shipmentId = ((Number) task.get("shipment_id")).longValue();
    Map<String,Object> shipment = jdbc.queryForMap("select s.*, c.carrier_code from shipment s join carrier c on c.id=s.carrier_id where s.id=?", shipmentId);
    String carrierCode = (String) shipment.get("carrier_code");
    String trackingNo = (String) shipment.get("tracking_no");
    List<CarrierAdapter.ParsedEvent> remoteEvents = trackQueryClient.queryTrack(carrierCode, trackingNo);
    int inserted = insertMissingRemoteEvents(shipmentId, carrierCode, remoteEvents);
    replayService.rebuildShipment(shipmentId);
    String after = jdbc.queryForObject("select current_status from shipment where id=?", String.class, shipmentId);
    String remoteStatus = remoteStatus(remoteEvents);
    jdbc.update("""
        update reconciliation_task
           set status='SUCCESS', remote_status=?, after_status=?, difference_found=?, inserted_event_count=?,
               last_error=null, locked_at=null, locked_by=null, completed_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
         where id=?
        """, remoteStatus, after, inserted > 0, inserted, taskId);
    jdbc.update("update reconciliation_batch set status='SUCCESS', success_count=1, difference_count=?, inserted_event_count=?, completed_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP where id=?", inserted > 0 ? 1 : 0, inserted, batchId);
    jdbc.update("insert into operation_log(operation_type, resource_type, resource_id, operator_name, request_id, summary, created_at) values(?,?,?,?,?,?,CURRENT_TIMESTAMP)", "RECONCILE_COMPLETE", "SHIPMENT", shipmentId, "system", UUID.randomUUID().toString(), "Manual reconciliation inserted events: " + inserted);
  }
  private int insertMissingRemoteEvents(long shipmentId, String carrierCode, List<CarrierAdapter.ParsedEvent> remoteEvents) {
    Long carrierId = carrierId(carrierCode);
    int inserted = 0;
    for (CarrierAdapter.ParsedEvent event : remoteEvents) {
      String fp = eventFingerprint(carrierCode, event);
      Long exists = jdbc.queryForObject("select count(*) from normalized_event where carrier_id=? and event_fingerprint=?", Long.class, carrierId, fp);
      if (exists != null && exists > 0) continue;
      boolean late = isLate(shipmentId, event.eventTime());
      jdbc.update("insert into normalized_event(shipment_id, carrier_id, raw_event_id, external_event_id, normalized_status, raw_status, event_time, received_time, location, description, source, late_arrival, applied_to_state, validation_status, event_fingerprint, created_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)", shipmentId, carrierId, null, event.externalEventId(), event.normalizedStatus().name(), event.rawStatus(), Timestamp.from(event.eventTime()), Timestamp.from(Instant.now()), event.location(), event.description(), "RECONCILIATION", late, false, event.normalizedStatus() == NormalizedStatus.UNKNOWN ? "UNKNOWN_STATUS" : "VALID", fp);
      inserted++;
    }
    return inserted;
  }
  private String remoteStatus(List<CarrierAdapter.ParsedEvent> remoteEvents) {
    return remoteEvents.stream()
        .filter(event -> event.normalizedStatus() != NormalizedStatus.UNKNOWN)
        .sorted(Comparator.comparing(CarrierAdapter.ParsedEvent::eventTime))
        .map(event -> event.normalizedStatus().name())
        .reduce((first, second) -> second)
        .orElse("UNKNOWN");
  }
  private long ensureShipment(long carrierId, String trackingNo, String orderNo) {
    try { jdbc.update("insert into shipment(tracking_no, carrier_id, business_order_no, current_status, has_open_anomaly, version, created_at, updated_at) values(?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", trackingNo, carrierId, orderNo, "CREATED", false, 0); } catch (DuplicateKeyException ignored) {}
    return jdbc.queryForObject("select id from shipment where carrier_id=? and tracking_no=?", Long.class, carrierId, trackingNo);
  }
  private Long carrierId(String code) {
    try { return jdbc.queryForObject("select id from carrier where carrier_code=? and enabled=true", Long.class, code); }
    catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or disabled carrier"); }
  }
  private NormalizedStatus mapping(long carrierId, String raw) {
    try { return NormalizedStatus.valueOf(jdbc.queryForObject("select normalized_status from carrier_status_mapping where carrier_id=? and raw_status=? and enabled=true", String.class, carrierId, raw)); }
    catch (Exception e) { return NormalizedStatus.UNKNOWN; }
  }
  private boolean isLate(long shipmentId, Instant eventTime) {
    Timestamp max = jdbc.queryForObject("select max_event_time from shipment where id=?", Timestamp.class, shipmentId);
    return max != null && eventTime.isBefore(max.toInstant());
  }
  private long count(String sql) { Number n = jdbc.queryForObject(sql, Number.class); return n == null ? 0 : n.longValue(); }
  private void createEventOutbox(long rawId, long taskId, int attempt, Instant availableAt) {
    try {
      EventProcessMessage message = new EventProcessMessage(taskId, rawId, UUID.randomUUID().toString(), Instant.now());
      jdbc.update("""
          insert into outbox_event(event_key, aggregate_type, aggregate_id, event_type, routing_key, payload, status, retry_count, max_retry_count, available_at, created_at, updated_at)
          values(?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
          """,
          "RAW:"+rawId+":ATTEMPT:"+attempt,
          "RAW_CARRIER_EVENT",
          rawId,
          "RAW_EVENT_RECEIVED",
          RabbitNames.EVENT_PROCESS_ROUTING_KEY,
          mapper.writeValueAsString(message),
          "PENDING",
          0,
          eventMaxRetryCount,
          Timestamp.from(availableAt));
    } catch (DuplicateKeyException ignored) {
      // Outbox event keys are deterministic, so duplicate enqueue attempts are harmless.
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create outbox event", e);
    }
  }
  private void createReconciliationOutbox(long taskId, long batchId, long shipmentId, String operator, int attempt, Instant availableAt) {
    try {
      ReconciliationMessage message = new ReconciliationMessage(taskId, batchId, shipmentId, operator, UUID.randomUUID().toString(), Instant.now());
      jdbc.update("""
          insert into outbox_event(event_key, aggregate_type, aggregate_id, event_type, routing_key, payload, status, retry_count, max_retry_count, available_at, created_at, updated_at)
          values(?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
          """,
          "RC_TASK:"+taskId+":ATTEMPT:"+attempt,
          "RECONCILIATION_TASK",
          taskId,
          "RECONCILIATION_REQUESTED",
          RabbitNames.RECONCILIATION_ROUTING_KEY,
          mapper.writeValueAsString(message),
          "PENDING",
          0,
          eventMaxRetryCount,
          Timestamp.from(availableAt));
    } catch (DuplicateKeyException ignored) {
      // Deterministic event keys make scheduled recovery idempotent.
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create reconciliation outbox event", e);
    }
  }
  private boolean isRetryable(Exception e) {
    return !(e instanceof JsonProcessingException || e instanceof IllegalArgumentException);
  }
  private long backoffSeconds(int retryCount) {
    return Math.min(300, (long) Math.pow(2, Math.max(0, retryCount - 1)) * 5L);
  }
  private String eventFingerprint(String carrierCode, CarrierAdapter.ParsedEvent parsed) {
    return CryptoUtil.sha256Hex(carrierCode+"|"+parsed.trackingNo()+"|"+parsed.rawStatus()+"|"+parsed.normalizedStatus()+"|"+parsed.eventTime()+"|"+parsed.location()+"|"+parsed.description());
  }
  private boolean canTransition(AnomalyStatus current, AnomalyStatus target) {
    if (current == target) return true;
    return switch (current) {
      case OPEN -> target == AnomalyStatus.PROCESSING || target == AnomalyStatus.RESOLVED || target == AnomalyStatus.IGNORED;
      case PROCESSING -> target == AnomalyStatus.RESOLVED || target == AnomalyStatus.IGNORED;
      case RESOLVED, IGNORED -> false;
    };
  }
  private void validateSignature(String timestamp, String signature, String body, String secret) {
    if (timestamp == null || signature == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing signature headers");
    long ts;
    try { ts = Long.parseLong(timestamp); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid timestamp"); }
    if (Math.abs(Instant.now().getEpochSecond() - ts) > skewSeconds) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Expired timestamp");
    String expected = CryptoUtil.hmacSha256Hex(secret, timestamp + "\n" + body);
    if (!CryptoUtil.constantTimeEquals(expected, signature)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid signature");
  }
}
