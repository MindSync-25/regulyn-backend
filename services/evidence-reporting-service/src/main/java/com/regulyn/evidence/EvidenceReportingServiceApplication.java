package com.regulyn.evidence;

import com.regulyn.events.config.EventsAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(EventsAutoConfiguration.class)
public class EvidenceReportingServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(EvidenceReportingServiceApplication.class, args);
  }
}
