package com.trackflow.server.messaging;

import java.time.Instant;

public record EventProcessMessage(
    long taskId,
    long rawEventId,
    String messageId,
    Instant occurredAt
) {}
