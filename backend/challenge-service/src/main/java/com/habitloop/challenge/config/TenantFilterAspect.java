package com.habitloop.challenge.config;

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
 * Enables the Hibernate {@code tenantFilter} on the current session for every service call, scoping all
 * queries to the caller's tenant (multi-tenancy backstop, docs/08).
 */
@Aspect
@Component
@Order(1)
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Around("execution(* com.habitloop.challenge.service..*(..))")
    public Object enableTenantFilter(ProceedingJoinPoint pjp) throws Throwable {
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("tenantId", UUID.fromString(tenantId));
        }
        return pjp.proceed();
    }
}
