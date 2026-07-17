package com.trackflow.server.messaging;

import java.time.Instant;

public record ReconciliationMessage(long taskId, long batchId, long shipmentId, String operator, String requestId, Instant requestedAt) {
}
