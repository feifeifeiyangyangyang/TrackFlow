package com.trackflow.server.adapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.server.domain.Enums.NormalizedStatus;
import java.time.Instant;
import java.util.List;
public interface CarrierAdapter {
  String carrierCode();
  ParsedEvent parse(JsonNode body, NormalizedStatus mappedStatus);
  List<ParsedEvent> parseTrackQuery(JsonNode body);
  record ParsedEvent(String externalEventId, String trackingNo, String rawStatus, NormalizedStatus normalizedStatus, Instant eventTime, String location, String description) {}
}
