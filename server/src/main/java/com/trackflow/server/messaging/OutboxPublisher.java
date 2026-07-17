package com.trackflow.server.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OutboxPublisher {
  private final JdbcTemplate jdbc;
  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper mapper;
  private final String workerId = "outbox-" + UUID.randomUUID();

  public OutboxPublisher(JdbcTemplate jdbc, RabbitTemplate rabbitTemplate, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.rabbitTemplate = rabbitTemplate;
    this.mapper = mapper;
  }

  @Scheduled(fixedDelayString = "${trackflow.outboxPublishInterval:2000}")
  public void publishPending() {
    List<Map<String,Object>> rows = jdbc.queryForList("""
        select * from outbox_event
         where status='PENDING'
            or (status='RETRY_WAIT' and available_at <= CURRENT_TIMESTAMP)
         order by available_at, id
         limit 20
        """);
    for (Map<String,Object> row : rows) publishOne(((Number) row.get("id")).longValue());
  }

  @Transactional
  public boolean claim(long outboxId) {
    return jdbc.update("""
        update outbox_event
           set status='SENDING', locked_at=CURRENT_TIMESTAMP, locked_by=?, updated_at=CURRENT_TIMESTAMP
         where id=?
           and (status='PENDING' or (status='RETRY_WAIT' and available_at <= CURRENT_TIMESTAMP))
        """, workerId, outboxId) == 1;
  }

  public void publishOne(long outboxId) {
    if (!claim(outboxId)) return;
    Map<String,Object> row = jdbc.queryForMap("select * from outbox_event where id=?", outboxId);
    try {
      String routingKey = (String) row.get("routing_key");
      Object payload = payload(row);
      rabbitTemplate.invoke(operations -> {
        operations.convertAndSend(RabbitNames.EVENT_EXCHANGE, routingKey, payload, message -> {
          message.getMessageProperties().setMessageId((String) row.get("event_key"));
          return message;
        });
        operations.waitForConfirmsOrDie(5000);
        return true;
      });
      markSent(outboxId);
    } catch (Exception e) {
      markFailedOrRetry(outboxId, e.getMessage());
    }
  }

  private Object payload(Map<String,Object> row) throws Exception {
    String eventType = (String) row.get("event_type");
    String body = (String) row.get("payload");
    return switch (eventType) {
      case "RAW_EVENT_RECEIVED" -> mapper.readValue(body, EventProcessMessage.class);
      case "RECONCILIATION_REQUESTED" -> mapper.readValue(body, ReconciliationMessage.class);
      default -> throw new IllegalArgumentException("Unsupported outbox event type: " + eventType);
    };
  }

  @Transactional
  public void markSent(long outboxId) {
    jdbc.update("update outbox_event set status='SENT', sent_at=CURRENT_TIMESTAMP, last_error=null, updated_at=CURRENT_TIMESTAMP where id=?", outboxId);
  }

  @Transactional
  public void markFailedOrRetry(long outboxId, String error) {
    Map<String,Object> row = jdbc.queryForMap("select retry_count, max_retry_count from outbox_event where id=?", outboxId);
    int retry = ((Number) row.get("retry_count")).intValue() + 1;
    int max = ((Number) row.get("max_retry_count")).intValue();
    if (retry >= max) {
      jdbc.update("update outbox_event set status='FAILED', retry_count=?, last_error=?, updated_at=CURRENT_TIMESTAMP where id=?", retry, error, outboxId);
      return;
    }
    Instant availableAt = Instant.now().plusSeconds(Math.min(300, (long) Math.pow(2, retry - 1) * 5L));
    jdbc.update("update outbox_event set status='RETRY_WAIT', retry_count=?, available_at=?, locked_at=null, locked_by=null, last_error=?, updated_at=CURRENT_TIMESTAMP where id=?", retry, Timestamp.from(availableAt), error, outboxId);
  }
}
