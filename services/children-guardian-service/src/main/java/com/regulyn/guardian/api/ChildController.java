package com.regulyn.guardian.api;

import com.regulyn.guardian.dto.CreateChildRequest;
import com.regulyn.guardian.dto.CreateChildResponse;
import com.regulyn.guardian.service.ChildService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/children")
public class ChildController {
    
    private final ChildService childService;
    
    public ChildController(ChildService childService) {
        this.childService = childService;
    }
    
    @PostMapping
    public ResponseEntity<CreateChildResponse> createChild(@Valid @RequestBody CreateChildRequest request) {
        CreateChildResponse response = childService.createChild(request);
        return ResponseEntity.ok(response);
    }
}
