package com.regulyn.employee;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
    "com.regulyn.employee",
  "com.regulyn.common.audit",
    "com.regulyn.auth",
    "com.regulyn.events",
    "com.regulyn.observability"
})
@EnableScheduling
public class EmployeeDataServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(EmployeeDataServiceApplication.class, args);
  }
}
