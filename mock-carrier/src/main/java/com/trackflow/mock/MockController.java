package com.trackflow.mock;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.HexFormat;
@RestController
@RequestMapping("/mock")
public class MockController {
  private final RestClient client = RestClient.create();
  private final String callbackUrl; private final String aSecret; private final String bSecret;
  public MockController(@Value("${trackflow.callbackUrl}") String callbackUrl, @Value("${trackflow.mockASecret}") String aSecret, @Value("${trackflow.mockBSecret}") String bSecret) { this.callbackUrl=callbackUrl; this.aSecret=aSecret; this.bSecret=bSecret; }
  @PostMapping("/scenarios/run")
  public Map<String,Object> run(@RequestBody ScenarioRequest req) throws InterruptedException {
    List<Map<String,Object>> events = req.carrierCode().equals("MOCK_B") ? bEvents(req.trackingNo(), req.scenario()) : aEvents(req.trackingNo(), req.scenario());
    List<Map<String,Object>> sendOrder = new ArrayList<>(events);
    if ("OUT_OF_ORDER".equals(req.scenario())) Collections.shuffle(sendOrder, new Random(7));
    if ("DUPLICATE_PUSH".equals(req.scenario())) for (int i=0;i<Math.max(1, Optional.ofNullable(req.repeatCount()).orElse(2));i++) sendOrder.add(events.get(Math.min(2, events.size()-1)));
    int ok=0, duplicate=0; List<Object> responses = new ArrayList<>();
    for (Map<String,Object> event : sendOrder) {
      Map<?,?> resp = push(req.carrierCode(), event); responses.add(resp);
      if (Boolean.TRUE.equals(resp.get("duplicate"))) duplicate++; else ok++;
      Thread.sleep(Math.max(0, Optional.ofNullable(req.intervalMillis()).orElse(0)));
    }
    return Map.of("carrierCode", req.carrierCode(), "scenario", req.scenario(), "trackingNo", req.trackingNo(), "sentOrder", sendOrder, "businessOrder", events, "rawCount", sendOrder.size(), "normalizedHint", events.size(), "duplicateCount", duplicate, "successCount", ok, "responses", responses);
  }
  private Map<?,?> push(String carrier, Map<String,Object> body) {
    String json = toJson(body); String ts = String.valueOf(Instant.now().getEpochSecond());
    String sig = hmac(carrier.equals("MOCK_A") ? aSecret : bSecret, ts + "\n" + json);
    return client.post().uri(callbackUrl).contentType(MediaType.APPLICATION_JSON).header("X-Carrier-Code", carrier).header("X-Timestamp", ts).header("X-Signature", sig).body(json).retrieve().body(Map.class);
  }
  private List<Map<String,Object>> aEvents(String trackingNo, String scenario) {
    LocalDateTime t = LocalDateTime.now(ZoneOffset.UTC).minusHours(3);
    List<String> statuses = new ArrayList<>(List.of("CREATED_ORDER","COLLECTED","SHIPPING","ARRIVED","DELIVERING","SIGNED"));
    if ("MISSING_EVENT".equals(scenario)) statuses.remove("ARRIVED");
    if ("RETURN_AFTER_DELIVERED".equals(scenario)) statuses.add("RETURNING");
    if ("UNKNOWN_STATUS".equals(scenario)) statuses.add(2, "ALIEN_STATUS");
    List<Map<String,Object>> list = new ArrayList<>(); int i=0;
    for (String s: statuses) { list.add(new LinkedHashMap<>(Map.of("eventId", "A-"+trackingNo+"-"+i, "mailNo", trackingNo, "status", s, "eventTime", t.plusMinutes(20L*i).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), "city", "济南市", "description", "A event " + s))); i++; }
    return list;
  }
  private List<Map<String,Object>> bEvents(String trackingNo, String scenario) {
    Instant t = Instant.now().minusSeconds(10800);
    List<String> statuses = new ArrayList<>(List.of("ORDER_CREATED","PICKUP_SUCCESS","TRANSPORTING","STATION_IN","LAST_MILE","DELIVERED"));
    if ("DELIVERY_FAILED_RECOVERY".equals(scenario)) statuses = new ArrayList<>(List.of("ORDER_CREATED","PICKUP_SUCCESS","TRANSPORTING","LAST_MILE","LAST_MILE_FAILED","LAST_MILE","DELIVERED"));
    List<Map<String,Object>> list = new ArrayList<>(); int i=0;
    for (String s: statuses) { list.add(new LinkedHashMap<>(Map.of("id", "B-"+trackingNo+"-"+i, "tracking_number", trackingNo, "event_code", s, "occurred_at", t.plusSeconds(1200L*i).toEpochMilli(), "location", Map.of("province", "山东省", "city", "济南市"), "message", "B event " + s))); i++; }
    return list;
  }
  private String toJson(Map<String,Object> m) { try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(m); } catch(Exception e) { throw new RuntimeException(e); } }
  private String hmac(String secret, String payload) { try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")); return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))); } catch(Exception e) { throw new RuntimeException(e); } }
  public record ScenarioRequest(@NotBlank String carrierCode, @NotBlank String scenario, @NotBlank String trackingNo, Integer repeatCount, Integer intervalMillis) {}
}
