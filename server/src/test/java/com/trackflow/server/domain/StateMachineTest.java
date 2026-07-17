package com.trackflow.server.domain;
import com.trackflow.server.domain.Enums.NormalizedStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class StateMachineTest {
  private final StateMachine sm = new StateMachine();
  @Test void normalFlow() {
    NormalizedStatus current = NormalizedStatus.CREATED;
    for (NormalizedStatus next : new NormalizedStatus[]{NormalizedStatus.PICKED_UP, NormalizedStatus.IN_TRANSIT, NormalizedStatus.ARRIVED_AT_STATION, NormalizedStatus.OUT_FOR_DELIVERY, NormalizedStatus.DELIVERED}) {
      assertThat(sm.canApply(current, next)).isTrue(); current = next;
    }
  }
  @Test void skipAndSameStatusAreAllowed() { assertThat(sm.canApply(NormalizedStatus.IN_TRANSIT, NormalizedStatus.OUT_FOR_DELIVERY)).isTrue(); assertThat(sm.canApply(NormalizedStatus.IN_TRANSIT, NormalizedStatus.IN_TRANSIT)).isTrue(); }
  @Test void unknownAndTerminalConflictDoNotApply() { assertThat(sm.canApply(NormalizedStatus.IN_TRANSIT, NormalizedStatus.UNKNOWN)).isFalse(); assertThat(sm.canApply(NormalizedStatus.DELIVERED, NormalizedStatus.IN_TRANSIT)).isFalse(); }
  @Test void deliveryFailedCanRetryDelivery() { assertThat(sm.canApply(NormalizedStatus.DELIVERY_FAILED, NormalizedStatus.OUT_FOR_DELIVERY)).isTrue(); }
  @Test void returnFlow() { assertThat(sm.canApply(NormalizedStatus.OUT_FOR_DELIVERY, NormalizedStatus.RETURNING)).isTrue(); assertThat(sm.canApply(NormalizedStatus.RETURNING, NormalizedStatus.RETURNED)).isTrue(); }
}
