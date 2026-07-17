package com.trackflow.server.service;

import com.trackflow.server.domain.Enums.NormalizedStatus;
import com.trackflow.server.domain.StateMachine;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ShipmentReplayService {
  private final JdbcTemplate jdbc;
  private final StateMachine stateMachine = new StateMachine();

  public ShipmentReplayService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public void rebuildShipment(long shipmentId) {
    jdbc.queryForObject("select id from shipment where id=? for update", Long.class, shipmentId);
    List<Map<String,Object>> events = jdbc.queryForList("select * from normalized_event where shipment_id=? order by event_time, received_time, id", shipmentId);
    NormalizedStatus current = NormalizedStatus.CREATED;
    Instant currentTime = null;
    for (Map<String,Object> event : events) {
      NormalizedStatus next = NormalizedStatus.valueOf((String) event.get("normalized_status"));
      boolean applied = stateMachine.canApply(current, next);
      String validation = next == NormalizedStatus.UNKNOWN ? "UNKNOWN_STATUS" : (applied ? "VALID" : "INVALID_TRANSITION");
      jdbc.update("update normalized_event set applied_to_state=?, validation_status=? where id=?", applied, validation, event.get("id"));
      if ("UNKNOWN_STATUS".equals(validation)) {
        anomaly(shipmentId, "UNKNOWN_STATUS", "UNKNOWN:"+event.get("id"), "UNKNOWN_STATUS", "未识别物流商原始状态: " + event.get("raw_status"), event.get("id"));
      }
      if ("INVALID_TRANSITION".equals(validation)) {
        anomaly(shipmentId, "INVALID_TRANSITION", "INVALID:"+event.get("id"), "STATE_MACHINE", "状态机拒绝从 " + current + " 转到 " + next, event.get("id"));
      }
      if (applied) {
        current = next;
        currentTime = ((Timestamp) event.get("event_time")).toInstant();
      }
    }
    Object maxEvent = jdbc.queryForObject("select max(event_time) from normalized_event where shipment_id=?", Object.class, shipmentId);
    Object lastReceived = jdbc.queryForObject("select max(received_time) from normalized_event where shipment_id=?", Object.class, shipmentId);
    long open = count("select count(*) from shipment_anomaly where shipment_id=" + shipmentId + " and status='OPEN'");
    jdbc.update("update shipment set current_status=?, current_status_event_time=?, last_received_time=?, max_event_time=?, has_open_anomaly=?, version=version+1, updated_at=CURRENT_TIMESTAMP where id=?", current.name(), currentTime == null ? null : Timestamp.from(currentTime), lastReceived, maxEvent, open > 0, shipmentId);
  }

  private void anomaly(long shipmentId, String type, String key, String rule, String description, Object eventId) {
    try {
      jdbc.update("insert into shipment_anomaly(shipment_id, anomaly_type, business_key, severity, status, rule_code, evidence_event_id, description, detected_at, created_at, updated_at) values(?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", shipmentId, type, key, "INVALID_TRANSITION".equals(type) ? "HIGH" : "MEDIUM", "OPEN", rule, eventId, description);
    } catch (DuplicateKeyException ignored) {
    }
  }

  private long count(String sql) {
    Number n = jdbc.queryForObject(sql, Number.class);
    return n == null ? 0 : n.longValue();
  }
}
