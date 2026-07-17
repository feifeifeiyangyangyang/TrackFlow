package com.trackflow.server.messaging;

public final class RabbitNames {
  private RabbitNames() {}

  public static final String EVENT_EXCHANGE = "trackflow.event.exchange";
  public static final String EVENT_PROCESS_QUEUE = "trackflow.event.process.queue";
  public static final String EVENT_PROCESS_ROUTING_KEY = "trackflow.event.process";
  public static final String RECONCILIATION_QUEUE = "trackflow.reconciliation.queue";
  public static final String RECONCILIATION_ROUTING_KEY = "trackflow.reconciliation.process";
  public static final String DLX = "trackflow.event.dlx";
  public static final String DLQ = "trackflow.event.dlq";
  public static final String DLQ_ROUTING_KEY = "trackflow.event.dead";
}
