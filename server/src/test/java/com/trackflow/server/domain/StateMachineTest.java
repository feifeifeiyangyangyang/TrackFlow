package com.trackflow.server.domain;
import com.trackflow.server.domain.Enums.NormalizedStatus;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
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
  @Test void outOfOrderReplayIsDeterministicWithFixedSeed() {
    List<EventPoint> timeline = List.of(
        new EventPoint(1, NormalizedStatus.CREATED, Instant.parse("2026-07-15T10:00:00Z")),
        new EventPoint(2, NormalizedStatus.PICKED_UP, Instant.parse("2026-07-15T11:00:00Z")),
        new EventPoint(3, NormalizedStatus.IN_TRANSIT, Instant.parse("2026-07-15T12:00:00Z")),
        new EventPoint(4, NormalizedStatus.ARRIVED_AT_STATION, Instant.parse("2026-07-15T13:00:00Z")),
        new EventPoint(5, NormalizedStatus.OUT_FOR_DELIVERY, Instant.parse("2026-07-15T14:00:00Z")),
        new EventPoint(6, NormalizedStatus.DELIVERED, Instant.parse("2026-07-15T15:00:00Z"))
    );
    Random random = new Random(20260717);
    for (int i = 0; i < 100; i++) {
      List<EventPoint> arrivalOrder = new ArrayList<>(timeline);
      java.util.Collections.shuffle(arrivalOrder, random);
      NormalizedStatus rebuilt = replay(arrivalOrder);
      assertThat(rebuilt).isEqualTo(NormalizedStatus.DELIVERED);
    }
  }

  private NormalizedStatus replay(List<EventPoint> events) {
    NormalizedStatus current = NormalizedStatus.CREATED;
    for (EventPoint event : events.stream().sorted(Comparator.comparing(EventPoint::eventTime).thenComparing(EventPoint::id)).toList()) {
      if (sm.canApply(current, event.status())) current = event.status();
    }
    return current;
  }

  private record EventPoint(long id, NormalizedStatus status, Instant eventTime) {}
}
