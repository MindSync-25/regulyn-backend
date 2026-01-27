package com.regulyn.identity.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/identity")
public class HealthController {

  @GetMapping("/ping")
  public Map<String, Object> ping() {
    return Map.of(
        "service", "identity-tenant-service",
        "status", "ok"
    );
  }
}
