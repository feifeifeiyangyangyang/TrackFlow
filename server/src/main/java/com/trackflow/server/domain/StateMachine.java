package com.trackflow.server.domain;
import com.trackflow.server.domain.Enums.NormalizedStatus;
import java.util.*;
public class StateMachine {
  private static final Map<NormalizedStatus, Set<NormalizedStatus>> ALLOWED = new EnumMap<>(NormalizedStatus.class);
  static {
    allow(NormalizedStatus.CREATED, NormalizedStatus.PICKED_UP, NormalizedStatus.CANCELLED);
    allow(NormalizedStatus.PICKED_UP, NormalizedStatus.IN_TRANSIT, NormalizedStatus.CANCELLED);
    allow(NormalizedStatus.IN_TRANSIT, NormalizedStatus.IN_TRANSIT, NormalizedStatus.ARRIVED_AT_STATION, NormalizedStatus.OUT_FOR_DELIVERY, NormalizedStatus.RETURNING);
    allow(NormalizedStatus.ARRIVED_AT_STATION, NormalizedStatus.IN_TRANSIT, NormalizedStatus.ARRIVED_AT_STATION, NormalizedStatus.OUT_FOR_DELIVERY, NormalizedStatus.RETURNING);
    allow(NormalizedStatus.OUT_FOR_DELIVERY, NormalizedStatus.OUT_FOR_DELIVERY, NormalizedStatus.DELIVERY_FAILED, NormalizedStatus.DELIVERED, NormalizedStatus.RETURNING);
    allow(NormalizedStatus.DELIVERY_FAILED, NormalizedStatus.OUT_FOR_DELIVERY, NormalizedStatus.DELIVERY_FAILED, NormalizedStatus.RETURNING);
    allow(NormalizedStatus.RETURNING, NormalizedStatus.RETURNING, NormalizedStatus.RETURNED);
    allow(NormalizedStatus.DELIVERED); allow(NormalizedStatus.RETURNED); allow(NormalizedStatus.CANCELLED); allow(NormalizedStatus.UNKNOWN);
  }
  private static void allow(NormalizedStatus from, NormalizedStatus... to) { ALLOWED.put(from, to.length == 0 ? EnumSet.noneOf(NormalizedStatus.class) : EnumSet.copyOf(Arrays.asList(to))); }
  public boolean canApply(NormalizedStatus current, NormalizedStatus next) {
    if (next == NormalizedStatus.UNKNOWN) return false;
    if (current == null) return next == NormalizedStatus.CREATED;
    if (current == next) return true;
    return ALLOWED.getOrDefault(current, Set.of()).contains(next);
  }
}
