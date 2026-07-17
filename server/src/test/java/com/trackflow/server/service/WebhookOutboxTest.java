package com.trackflow.server.service;

import com.trackflow.server.TrackflowServerApplication;
import com.trackflow.server.util.CryptoUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TrackflowServerApplication.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:webhook_outbox;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.rabbitmq.listener.simple.auto-startup=false",
    "trackflow.outboxPublishInterval=3600000"
})
class WebhookOutboxTest {
  @Autowired CoreService service;
  @Autowired JdbcTemplate jdbc;

  @Test
  void webhookWritesRawTaskAndOutboxWithoutSynchronousProcessing() {
    String body = """
        {"eventId":"A-OUTBOX-1","mailNo":"A-OUTBOX","status":"COLLECTED","eventTime":"2026-07-15 11:00:00","city":"济南市","description":"picked up"}
        """;
    String ts = String.valueOf(Instant.now().getEpochSecond());
    String sig = CryptoUtil.hmacSha256Hex("mock-a-local-secret", ts + "\n" + body);

    Map<String,Object> first = service.receiveWebhook("MOCK_A", ts, sig, body, Map.of("X-Carrier-Code", "MOCK_A"));
    Map<String,Object> duplicate = service.receiveWebhook("MOCK_A", ts, sig, body, Map.of("X-Carrier-Code", "MOCK_A"));

    assertThat(first.get("duplicate")).isEqualTo(false);
    assertThat(duplicate.get("duplicate")).isEqualTo(true);
    assertThat(count("raw_carrier_event")).isEqualTo(1);
    assertThat(count("event_process_task")).isEqualTo(1);
    assertThat(count("outbox_event")).isEqualTo(1);
    assertThat(count("normalized_event")).isZero();
  }

  @Test
  void outOfOrderArrivalReplaysByBusinessEventTime() {
    String trackingNo = "A-OO-" + System.nanoTime();
    List<EventInput> events = List.of(
        new EventInput("5", "SIGNED", "2026-07-15 15:00:00"),
        new EventInput("2", "SHIPPING", "2026-07-15 12:00:00"),
        new EventInput("4", "DELIVERING", "2026-07-15 14:00:00"),
        new EventInput("1", "COLLECTED", "2026-07-15 11:00:00"),
        new EventInput("3", "ARRIVED", "2026-07-15 13:00:00"),
        new EventInput("0", "CREATED_ORDER", "2026-07-15 10:00:00")
    );

    for (EventInput event : events) {
      Map<String,Object> accepted = sendWebhook(trackingNo, event);
      service.processEventTask(((Number) accepted.get("taskId")).longValue(), "test-worker");
    }

    String status = jdbc.queryForObject("select current_status from shipment where tracking_no=?", String.class, trackingNo);
    long normalized = jdbc.queryForObject("select count(*) from normalized_event ne join shipment s on s.id=ne.shipment_id where s.tracking_no=?", Long.class, trackingNo);
    long late = jdbc.queryForObject("select count(*) from normalized_event ne join shipment s on s.id=ne.shipment_id where s.tracking_no=? and late_arrival=true", Long.class, trackingNo);
    String lastReceivedStatus = jdbc.queryForObject("select raw_status from raw_carrier_event where tracking_no=? order by received_time desc, id desc limit 1", String.class, trackingNo);

    assertThat(status).isEqualTo("DELIVERED");
    assertThat(normalized).isEqualTo(6);
    assertThat(late).isEqualTo(5);
    assertThat(lastReceivedStatus).isEqualTo("CREATED_ORDER");
  }

  @Test
  void reconciliationRequestCreatesTaskAndOutboxWithoutSynchronousCarrierQuery() {
    String trackingNo = "A-RC-" + System.nanoTime();
    Map<String,Object> shipment = service.createShipment("MOCK_A", trackingNo, "BO-" + trackingNo);

    Map<String,Object> accepted = service.reconcile(((Number) shipment.get("id")).longValue(), "test-operator");

    long taskId = ((Number) accepted.get("taskId")).longValue();
    Map<String,Object> task = jdbc.queryForMap("select * from reconciliation_task where id=?", taskId);
    Map<String,Object> outbox = jdbc.queryForMap("select * from outbox_event where aggregate_type='RECONCILIATION_TASK' and aggregate_id=?", taskId);
    long normalizedForShipment = jdbc.queryForObject("select count(*) from normalized_event where shipment_id=?", Long.class, shipment.get("id"));

    assertThat(task.get("status")).isEqualTo("PENDING");
    assertThat(outbox.get("event_type")).isEqualTo("RECONCILIATION_REQUESTED");
    assertThat(outbox.get("routing_key")).isEqualTo("trackflow.reconciliation.process");
    assertThat(normalizedForShipment).isZero();
  }

  private Map<String,Object> sendWebhook(String trackingNo, EventInput event) {
    String body = """
        {"eventId":"A-%s-%s","mailNo":"%s","status":"%s","eventTime":"%s","city":"济南市","description":"%s"}
        """.formatted(trackingNo, event.id(), trackingNo, event.status(), event.eventTime(), event.status());
    String ts = String.valueOf(Instant.now().getEpochSecond());
    String sig = CryptoUtil.hmacSha256Hex("mock-a-local-secret", ts + "\n" + body);
    return service.receiveWebhook("MOCK_A", ts, sig, body, Map.of("X-Carrier-Code", "MOCK_A"));
  }

  private long count(String table) {
    return jdbc.queryForObject("select count(*) from " + table, Long.class);
  }

  private record EventInput(String id, String status, String eventTime) {}
}
