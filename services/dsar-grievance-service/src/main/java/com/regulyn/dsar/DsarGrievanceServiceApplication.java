package com.regulyn.dsar;

import com.regulyn.events.config.EventsAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(EventsAutoConfiguration.class)
public class DsarGrievanceServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(DsarGrievanceServiceApplication.class, args);
  }
}
