package com.regulyn.dsar.api;

import com.regulyn.dsar.model.DsarRequest;
import com.regulyn.dsar.model.DsarResponse;
import com.regulyn.dsar.service.DsarService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dsar")
public class DsarController {
  
  private final DsarService dsarService;
  
  public DsarController(DsarService dsarService) {
    this.dsarService = dsarService;
  }
  
  @PostMapping
  public DsarResponse submitRequest(@RequestBody DsarRequest request) {
    return dsarService.submitRequest(request);
  }
}
