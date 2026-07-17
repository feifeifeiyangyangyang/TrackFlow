package com.trackflow.server.controller;
import com.trackflow.server.service.CoreService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Validated
@RestController
@RequestMapping("/api")
public class ApiController {
  private final CoreService service;
  public ApiController(CoreService service) { this.service = service; }
  @GetMapping("/dashboard") public Map<String,Object> dashboard() { return service.dashboard(); }
  @GetMapping("/carriers") public List<Map<String,Object>> carriers() { return service.carriers(); }
  @GetMapping("/shipments") public List<Map<String,Object>> shipments(@RequestParam(name="q", required=false) String q) { return service.shipments(q); }
  @PostMapping("/shipments") public Map<String,Object> createShipment(@RequestBody CreateShipmentRequest req) { return service.createShipment(req.carrierCode(), req.trackingNo(), req.businessOrderNo()); }
  @GetMapping("/shipments/{id}") public Map<String,Object> shipment(@PathVariable("id") long id) { return service.shipmentDetail(id); }
  @GetMapping("/raw-events") public List<Map<String,Object>> rawEvents() { return service.rawEvents(); }
  @GetMapping("/anomalies") public List<Map<String,Object>> anomalies() { return service.anomalies(); }
  @PatchMapping("/anomalies/{id}") public ResponseEntity<Void> resolve(@PathVariable("id") long id, @RequestBody ResolveRequest req, @RequestHeader(value="X-Operator-Name", defaultValue="demo-operator") String operator) { service.resolveAnomaly(id, req.status(), req.note(), operator); return ResponseEntity.noContent().build(); }
  @PostMapping("/reconciliation/shipments/{id}") public Map<String,Object> reconcile(@PathVariable("id") long id, @RequestHeader(value="X-Operator-Name", defaultValue="demo-operator") String operator) { return service.reconcile(id, operator); }
  @GetMapping("/reconciliation/batches") public List<Map<String,Object>> batches() { return service.batches(); }
  @PostMapping("/webhooks/carrier") public Map<String,Object> webhook(HttpServletRequest request, @RequestHeader("X-Carrier-Code") String carrierCode, @RequestHeader("X-Timestamp") String timestamp, @RequestHeader("X-Signature") String signature) throws IOException {
    String raw = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    Map<String,String> headers = new LinkedHashMap<>();
    Collections.list(request.getHeaderNames()).forEach(n -> { if (!n.equalsIgnoreCase("X-Signature")) headers.put(n, request.getHeader(n)); });
    return service.receiveWebhook(carrierCode, timestamp, signature, raw, headers);
  }
  public record CreateShipmentRequest(@NotBlank String carrierCode, @NotBlank String trackingNo, String businessOrderNo) {}
  public record ResolveRequest(@NotBlank String status, String note) {}
}
