package com.trackflow.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.server.adapter.CarrierAdapter;
import com.trackflow.server.adapter.CarrierAdapters;
import com.trackflow.server.domain.Enums.NormalizedStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

@Component
public class CarrierTrackQueryClient {
  private final RestClient restClient;
  private final CarrierAdapters adapters;
  private final JdbcTemplate jdbc;

  public CarrierTrackQueryClient(@Value("${trackflow.mockCarrierBaseUrl}") String baseUrl, CarrierAdapters adapters, JdbcTemplate jdbc) {
    this.adapters = adapters;
    this.jdbc = jdbc;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(2));
    requestFactory.setReadTimeout(Duration.ofSeconds(3));
    this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
  }

  public List<CarrierAdapter.ParsedEvent> queryTrack(String carrierCode, String trackingNo) {
    try {
      JsonNode body = restClient.get()
          .uri("/mock/carriers/{carrierCode}/shipments/{trackingNo}/events", carrierCode, trackingNo)
          .retrieve()
          .body(JsonNode.class);
      if (body == null) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Carrier query returned empty body");
      }
      long carrierId = jdbc.queryForObject("select id from carrier where carrier_code=?", Long.class, carrierCode);
      return adapters.get(carrierCode).parseTrackQuery(body).stream()
          .map(event -> new CarrierAdapter.ParsedEvent(
              event.externalEventId(),
              event.trackingNo(),
              event.rawStatus(),
              mapping(carrierId, event.rawStatus()),
              event.eventTime(),
              event.location(),
              event.description()))
          .toList();
    } catch (RestClientResponseException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Carrier query failed: HTTP " + e.getStatusCode().value());
    } catch (ResourceAccessException e) {
      throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Carrier query timed out");
    }
  }

  private NormalizedStatus mapping(long carrierId, String raw) {
    try {
      return NormalizedStatus.valueOf(jdbc.queryForObject("select normalized_status from carrier_status_mapping where carrier_id=? and raw_status=? and enabled=true", String.class, carrierId, raw));
    } catch (Exception e) {
      return NormalizedStatus.UNKNOWN;
    }
  }
}
