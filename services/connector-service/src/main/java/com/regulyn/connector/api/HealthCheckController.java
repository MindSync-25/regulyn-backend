package com.regulyn.connector.api;

import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/health-check")
public class HealthCheckController {
  
  @GetMapping
  public Map<String, String> healthCheck() {
    Map<String, String> response = new HashMap<>();
    response.put("service", "connector-service");
    response.put("status", "UP");
    return response;
  }
}
