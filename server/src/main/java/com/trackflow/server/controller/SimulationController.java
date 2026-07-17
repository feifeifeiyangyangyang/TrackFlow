package com.trackflow.server.controller;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import java.util.Map;
@RestController
@RequestMapping("/api/simulation")
public class SimulationController {
  private final RestClient restClient;
  public SimulationController(@Value("${trackflow.mockCarrierBaseUrl}") String baseUrl) { this.restClient = RestClient.builder().baseUrl(baseUrl).build(); }
  @PostMapping("/run") public Map<?,?> run(@Valid @RequestBody SimulationRequest req) {
    return restClient.post().uri("/mock/scenarios/run").contentType(MediaType.APPLICATION_JSON).body(req).retrieve().body(Map.class);
  }
  public record SimulationRequest(@NotBlank String carrierCode, @NotBlank String scenario, @NotBlank String trackingNo, Integer repeatCount, Integer intervalMillis) {}
}
