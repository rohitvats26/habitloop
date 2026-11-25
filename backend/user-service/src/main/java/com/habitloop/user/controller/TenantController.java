package com.habitloop.user.controller;

import com.habitloop.user.dto.TenantResponse;
import com.habitloop.user.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
@Tag(name = "Tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'USER')")
    @Operation(summary = "Get the current tenant details")
    public TenantResponse currentTenant() {
        return tenantService.getCurrentTenant();
    }
}
