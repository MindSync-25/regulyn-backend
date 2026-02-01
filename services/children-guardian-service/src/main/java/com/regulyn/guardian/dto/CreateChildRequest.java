package com.regulyn.guardian.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;
import java.util.Map;

public record CreateChildRequest(
    @NotBlank(message = "childRef is required")
    String childRef,
    
    @NotBlank(message = "fullName is required")
    String fullName,
    
    @NotNull(message = "dateOfBirth is required")
    @Past(message = "dateOfBirth must be in the past")
    LocalDate dateOfBirth,
    
    String country,
    
    @NotBlank(message = "status is required")
    String status,
    
    Map<String, Object> metadata
) {}
