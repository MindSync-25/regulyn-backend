package com.regulyn.consent.api;

import com.regulyn.consent.model.ConsentRequest;
import com.regulyn.consent.model.ConsentResponse;
import com.regulyn.consent.service.ConsentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/consents")
public class ConsentController {
  
  private final ConsentService consentService;
  
  public ConsentController(ConsentService consentService) {
    this.consentService = consentService;
  }
  
  @PostMapping
  public ConsentResponse createConsent(@RequestBody ConsentRequest request) {
    return consentService.createConsent(request);
  }
}
