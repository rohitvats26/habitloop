package com.habitloop.integration.config;

import com.habitloop.common.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Enables the Hibernate tenant filter for authenticated (JWT) service calls. Webhook ingestion has no
 * tenant context — it looks up a connection by id and derives the tenant from it — so the filter is
 * simply not enabled there.
 */
@Aspect
@Component
@Order(1)
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Around("execution(* com.habitloop.integration.service..*(..))")
    public Object enableTenantFilter(ProceedingJoinPoint pjp) throws Throwable {
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("tenantId", UUID.fromString(tenantId));
        }
        return pjp.proceed();
    }
}
