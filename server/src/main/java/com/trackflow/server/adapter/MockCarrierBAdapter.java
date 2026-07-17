package com.trackflow.server.adapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.server.domain.Enums.NormalizedStatus;
import org.springframework.stereotype.Component;
import java.time.Instant;
@Component
public class MockCarrierBAdapter implements CarrierAdapter {
  public String carrierCode() { return "MOCK_B"; }
  public ParsedEvent parse(JsonNode body, NormalizedStatus mappedStatus) {
    String raw = body.path("event_code").asText("UNKNOWN");
    String location = body.path("location").path("province").asText("") + body.path("location").path("city").asText("");
    return new ParsedEvent(body.path("id").asText(null), body.path("tracking_number").asText(), raw, mappedStatus,
      Instant.ofEpochMilli(body.path("occurred_at").asLong()), location, body.path("message").asText(""));
  }
}
