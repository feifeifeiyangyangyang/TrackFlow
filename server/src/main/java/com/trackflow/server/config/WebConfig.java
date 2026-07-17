package com.trackflow.server.config;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.context.annotation.*;
import org.springframework.web.cors.*;
import org.springframework.web.filter.*;
import java.io.IOException;
import java.util.*;
@Configuration
public class WebConfig {
  @Bean public CorsFilter corsFilter() {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOriginPatterns(List.of("*"));
    c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
    c.setAllowedHeaders(List.of("*"));
    UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
    s.registerCorsConfiguration("/**", c);
    return new CorsFilter(s);
  }
  @Bean public OncePerRequestFilter traceIdFilter() {
    return new OncePerRequestFilter() {
      protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String traceId = Optional.ofNullable(req.getHeader("X-Trace-Id")).filter(v -> !v.isBlank()).orElse(UUID.randomUUID().toString());
        MDC.put("traceId", traceId); res.setHeader("X-Trace-Id", traceId);
        try { chain.doFilter(req, res); } finally { MDC.remove("traceId"); }
      }
    };
  }
}
