package com.trackflow.server.adapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.server.domain.Enums.NormalizedStatus;
import org.springframework.stereotype.Component;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
@Component
public class MockCarrierAAdapter implements CarrierAdapter {
  public String carrierCode() { return "MOCK_A"; }
  public ParsedEvent parse(JsonNode body, NormalizedStatus mappedStatus) {
    String raw = body.path("status").asText("UNKNOWN");
    return new ParsedEvent(body.path("eventId").asText(null), body.path("mailNo").asText(), raw, mappedStatus,
      LocalDateTime.parse(body.path("eventTime").asText(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toInstant(ZoneOffset.UTC),
      body.path("city").asText(""), body.path("description").asText(""));
  }
  public List<ParsedEvent> parseTrackQuery(JsonNode body) {
    List<ParsedEvent> events = new ArrayList<>();
    for (JsonNode item : body.path("events")) events.add(parse(item, NormalizedStatus.UNKNOWN));
    return events;
  }
}
