package com.habitloop.user.service;

import com.habitloop.common.exception.ResourceNotFoundException;
import com.habitloop.common.security.SecurityUtils;
import com.habitloop.user.dto.TenantResponse;
import com.habitloop.user.entity.Tenant;
import com.habitloop.user.mapper.UserMapper;
import com.habitloop.user.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenants;
    private final UserMapper mapper;

    public TenantService(TenantRepository tenants, UserMapper mapper) {
        this.tenants = tenants;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public TenantResponse getCurrentTenant() {
        UUID tenantId = UUID.fromString(SecurityUtils.currentTenantId());
        Tenant tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
        return mapper.toTenantResponse(tenant);
    }
}
