package com.trackflow.server.adapter;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
@Component
public class CarrierAdapters {
  private final Map<String, CarrierAdapter> adapters;
  public CarrierAdapters(List<CarrierAdapter> list) { adapters = list.stream().collect(Collectors.toMap(CarrierAdapter::carrierCode, Function.identity())); }
  public CarrierAdapter get(String code) {
    CarrierAdapter a = adapters.get(code);
    if (a == null) throw new IllegalArgumentException("Unsupported carrier: " + code);
    return a;
  }
}
