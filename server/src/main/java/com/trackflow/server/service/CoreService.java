package com.trackflow.server.service;
import com.fasterxml.jackson.databind.*;
import com.trackflow.server.adapter.*;
import com.trackflow.server.domain.Enums.*;
import com.trackflow.server.domain.StateMachine;
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
  private final StateMachine stateMachine = new StateMachine();
  private final long skewSeconds;
  public CoreService(JdbcTemplate jdbc, ObjectMapper mapper, CarrierAdapters adapters, @Value("${trackflow.webhookClockSkewSeconds:300}") long skewSeconds) {
    this.jdbc = jdbc; this.mapper = mapper; this.adapters = adapters; this.skewSeconds = skewSeconds;
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
      String idempotencyKey = parsed.externalEventId() == null || parsed.externalEventId().isBlank() ? "FP:" + CryptoUtil.sha256Hex(parsed.trackingNo()+"|"+parsed.rawStatus()+"|"+parsed.eventTime()+"|"+parsed.location()+"|"+parsed.description()) : "EXT:" + parsed.externalEventId();
      String fingerprint = CryptoUtil.sha256Hex(parsed.trackingNo()+"|"+parsed.rawStatus()+"|"+parsed.eventTime()+"|"+parsed.location()+"|"+parsed.description());
      long shipmentId = ensureShipment(carrierId, parsed.trackingNo(), "AUTO-" + parsed.trackingNo());
      try {
        jdbc.update("insert into raw_carrier_event(carrier_id, shipment_id, tracking_no, external_event_id, idempotency_key, event_fingerprint, raw_status, raw_body, request_headers, signature_valid, received_time, process_status, created_at, updated_at) values(?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", carrierId, shipmentId, parsed.trackingNo(), parsed.externalEventId(), idempotencyKey, fingerprint, parsed.rawStatus(), rawBody, mapper.writeValueAsString(headers), true, "PENDING");
        long rawId = jdbc.queryForObject("select id from raw_carrier_event where carrier_id=? and idempotency_key=?", Long.class, carrierId, idempotencyKey);
        jdbc.update("insert into event_process_task(raw_event_id, task_key, status, retry_count, max_retry_count, next_retry_time, created_at, updated_at) values(?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", rawId, "RAW:"+rawId, "PENDING", 0, 5);
        processRawEvent(rawId);
        return Map.of("accepted", true, "duplicate", false, "rawEventId", rawId);
      } catch (DuplicateKeyException dup) {
        Long rawId = jdbc.queryForObject("select id from raw_carrier_event where carrier_id=? and idempotency_key=?", Long.class, carrierId, idempotencyKey);
        return Map.of("accepted", true, "duplicate", true, "rawEventId", rawId);
      }
    } catch (ResponseStatusException e) { throw e; }
      catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook body: " + e.getMessage()); }
  }
  @Transactional public void processRawEvent(long rawId) {
    Map<String,Object> raw = jdbc.queryForMap("select * from raw_carrier_event where id=?", rawId);
    Long existing = jdbc.queryForObject("select count(*) from normalized_event where raw_event_id=?", Long.class, rawId);
    if (existing != null && existing > 0) { jdbc.update("update event_process_task set status='SUCCESS', updated_at=CURRENT_TIMESTAMP where raw_event_id=?", rawId); return; }
    long carrierId = ((Number) raw.get("carrier_id")).longValue();
    String carrierCode = jdbc.queryForObject("select carrier_code from carrier where id=?", String.class, carrierId);
    try {
      JsonNode json = mapper.readTree((String) raw.get("raw_body"));
      CarrierAdapter.ParsedEvent parsed = adapters.get(carrierCode).parse(json, mapping(carrierId, (String) raw.get("raw_status")));
      boolean late = isLate(((Number) raw.get("shipment_id")).longValue(), parsed.eventTime());
      jdbc.update("insert into normalized_event(shipment_id, carrier_id, raw_event_id, external_event_id, normalized_status, raw_status, event_time, received_time, location, description, source, late_arrival, applied_to_state, validation_status, event_fingerprint, created_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)", raw.get("shipment_id"), carrierId, rawId, parsed.externalEventId(), parsed.normalizedStatus().name(), parsed.rawStatus(), Timestamp.from(parsed.eventTime()), raw.get("received_time"), parsed.location(), parsed.description(), "WEBHOOK", late, false, parsed.normalizedStatus() == NormalizedStatus.UNKNOWN ? "UNKNOWN_STATUS" : "VALID", raw.get("event_fingerprint"));
      rebuildShipment(((Number) raw.get("shipment_id")).longValue());
      jdbc.update("update raw_carrier_event set process_status='SUCCESS', updated_at=CURRENT_TIMESTAMP where id=?", rawId);
      jdbc.update("update event_process_task set status='SUCCESS', message_sent_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP where raw_event_id=?", rawId);
    } catch (Exception e) {
      jdbc.update("update raw_carrier_event set process_status='FAILED', failure_reason=?, updated_at=CURRENT_TIMESTAMP where id=?", e.getMessage(), rawId);
      jdbc.update("update event_process_task set status='FAILED', last_error=?, updated_at=CURRENT_TIMESTAMP where raw_event_id=?", e.getMessage(), rawId);
    }
  }
  @Transactional public void rebuildShipment(long shipmentId) {
    List<Map<String,Object>> events = jdbc.queryForList("select * from normalized_event where shipment_id=? order by event_time, received_time, id", shipmentId);
    NormalizedStatus current = NormalizedStatus.CREATED; Instant currentTime = null;
    for (Map<String,Object> e : events) {
      NormalizedStatus next = NormalizedStatus.valueOf((String) e.get("normalized_status"));
      boolean applied = stateMachine.canApply(current, next);
      String validation = next == NormalizedStatus.UNKNOWN ? "UNKNOWN_STATUS" : (applied ? "VALID" : "INVALID_TRANSITION");
      jdbc.update("update normalized_event set applied_to_state=?, validation_status=? where id=?", applied, validation, e.get("id"));
      if ("UNKNOWN_STATUS".equals(validation)) anomaly(shipmentId, "UNKNOWN_STATUS", "UNKNOWN:"+e.get("id"), "UNKNOWN_STATUS", "未识别物流商原始状态: " + e.get("raw_status"), e.get("id"));
      if ("INVALID_TRANSITION".equals(validation)) anomaly(shipmentId, "INVALID_TRANSITION", "INVALID:"+e.get("id"), "STATE_MACHINE", "状态机拒绝从 " + current + " 转到 " + next, e.get("id"));
      if (applied) { current = next; currentTime = ((Timestamp)e.get("event_time")).toInstant(); }
    }
    Object maxEvent = jdbc.queryForObject("select max(event_time) from normalized_event where shipment_id=?", Object.class, shipmentId);
    Object lastReceived = jdbc.queryForObject("select max(received_time) from normalized_event where shipment_id=?", Object.class, shipmentId);
    long open = count("select count(*) from shipment_anomaly where shipment_id=" + shipmentId + " and status='OPEN'");
    jdbc.update("update shipment set current_status=?, current_status_event_time=?, last_received_time=?, max_event_time=?, has_open_anomaly=?, version=version+1, updated_at=CURRENT_TIMESTAMP where id=?", current.name(), currentTime == null ? null : Timestamp.from(currentTime), lastReceived, maxEvent, open > 0, shipmentId);
  }
  @Transactional public Map<String,Object> reconcile(long shipmentId, String operator) {
    Map<String,Object> shipment = jdbc.queryForMap("select s.*, c.carrier_code from shipment s join carrier c on c.id=s.carrier_id where s.id=?", shipmentId);
    String batchNo = "RC-" + Instant.now().toEpochMilli();
    jdbc.update("insert into reconciliation_batch(batch_no, trigger_type, status, total_count, success_count, failed_count, difference_count, inserted_event_count, started_at, created_at, updated_at) values(?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", batchNo, "MANUAL", "RUNNING", 1, 0, 0, 0, 0);
    long batchId = jdbc.queryForObject("select id from reconciliation_batch where batch_no=?", Long.class, batchNo);
    int inserted = insertReconciliationTail(shipmentId, (String) shipment.get("carrier_code"), (String) shipment.get("tracking_no"));
    rebuildShipment(shipmentId);
    String after = jdbc.queryForObject("select current_status from shipment where id=?", String.class, shipmentId);
    jdbc.update("insert into reconciliation_task(batch_id, shipment_id, task_key, status, retry_count, max_retry_count, before_status, remote_status, after_status, difference_found, inserted_event_count, started_at, completed_at, created_at, updated_at) values(?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", batchId, shipmentId, "RC:"+batchNo+":"+shipmentId, "SUCCESS", 0, 3, shipment.get("current_status"), "DELIVERED", after, inserted > 0, inserted);
    jdbc.update("update reconciliation_batch set status='SUCCESS', success_count=1, difference_count=?, inserted_event_count=?, completed_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP where id=?", inserted > 0 ? 1 : 0, inserted, batchId);
    jdbc.update("insert into operation_log(operation_type, resource_type, resource_id, operator_name, request_id, summary, created_at) values(?,?,?,?,?,?,CURRENT_TIMESTAMP)", "RECONCILE", "SHIPMENT", shipmentId, operator, UUID.randomUUID().toString(), "手动对账补入事件 " + inserted + " 条");
    return jdbc.queryForMap("select * from reconciliation_batch where id=?", batchId);
  }
  public List<Map<String,Object>> rawEvents() { return jdbc.queryForList("select id, carrier_id, shipment_id, tracking_no, external_event_id, raw_status, signature_valid, received_time, process_status, failure_reason, created_at from raw_carrier_event order by received_time desc limit 100"); }
  public List<Map<String,Object>> anomalies() { return jdbc.queryForList("select a.*, s.tracking_no from shipment_anomaly a join shipment s on s.id=a.shipment_id order by detected_at desc limit 100"); }
  public List<Map<String,Object>> batches() { return jdbc.queryForList("select * from reconciliation_batch order by created_at desc limit 100"); }
  @Transactional public void resolveAnomaly(long id, String status, String note, String operator) {
    jdbc.update("update shipment_anomaly set status=?, resolution_note=?, resolved_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP where id=?", status, note, id);
    jdbc.update("insert into operation_log(operation_type, resource_type, resource_id, operator_name, request_id, summary, created_at) values(?,?,?,?,?,?,CURRENT_TIMESTAMP)", "ANOMALY_"+status, "ANOMALY", id, operator, UUID.randomUUID().toString(), note);
  }
  @Scheduled(fixedDelay = 15000) public void scheduledRecovery() {
    for (Map<String,Object> row : jdbc.queryForList("select raw_event_id from event_process_task where status in ('PENDING','RETRY_WAIT') limit 10")) processRawEvent(((Number)row.get("raw_event_id")).longValue());
  }
  private int insertReconciliationTail(long shipmentId, String carrierCode, String trackingNo) {
    Long carrierId = carrierId(carrierCode);
    Timestamp ts = jdbc.queryForObject("select max(event_time) from normalized_event where shipment_id=?", Timestamp.class, shipmentId);
    Instant base = ts == null ? Instant.now().minusSeconds(3600) : ts.toInstant();
    List<NormalizedStatus> tail = List.of(NormalizedStatus.ARRIVED_AT_STATION, NormalizedStatus.OUT_FOR_DELIVERY, NormalizedStatus.DELIVERED);
    int inserted = 0;
    for (int i=0;i<tail.size();i++) {
      NormalizedStatus status = tail.get(i);
      String fp = CryptoUtil.sha256Hex("RC|"+trackingNo+"|"+status+"|"+base.plusSeconds(600L*(i+1)));
      Long exists = jdbc.queryForObject("select count(*) from normalized_event where event_fingerprint=?", Long.class, fp);
      if (exists != null && exists > 0) continue;
      jdbc.update("insert into normalized_event(shipment_id, carrier_id, raw_event_id, external_event_id, normalized_status, raw_status, event_time, received_time, location, description, source, late_arrival, applied_to_state, validation_status, event_fingerprint, created_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)", shipmentId, carrierId, null, "RC-"+fp.substring(0,12), status.name(), "RC_"+status.name(), Timestamp.from(base.plusSeconds(600L*(i+1))), Timestamp.from(Instant.now()), "对账补偿", "主动对账补入 " + status.name(), "RECONCILIATION", false, false, "VALID", fp);
      inserted++;
    }
    return inserted;
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
  private void anomaly(long shipmentId, String type, String key, String rule, String description, Object eventId) {
    try { jdbc.update("insert into shipment_anomaly(shipment_id, anomaly_type, business_key, severity, status, rule_code, evidence_event_id, description, detected_at, created_at, updated_at) values(?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", shipmentId, type, key, "INVALID_TRANSITION".equals(type) ? "HIGH" : "MEDIUM", "OPEN", rule, eventId, description); } catch (DuplicateKeyException ignored) {}
  }
  private long count(String sql) { Number n = jdbc.queryForObject(sql, Number.class); return n == null ? 0 : n.longValue(); }
  private void validateSignature(String timestamp, String signature, String body, String secret) {
    if (timestamp == null || signature == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing signature headers");
    long ts;
    try { ts = Long.parseLong(timestamp); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid timestamp"); }
    if (Math.abs(Instant.now().getEpochSecond() - ts) > skewSeconds) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Expired timestamp");
    String expected = CryptoUtil.hmacSha256Hex(secret, timestamp + "\n" + body);
    if (!CryptoUtil.constantTimeEquals(expected, signature)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid signature");
  }
}
