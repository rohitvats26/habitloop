package com.habitloop.activity.config;

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
 * Enables the Hibernate {@code tenantFilter} on the current session for every service call, using the
 * tenant id from {@link TenantContext} (populated from the JWT). This is the multi-tenancy backstop:
 * even a repository query that forgets its tenant predicate is still scoped to the caller's tenant.
 * Runs at order 1, inside the transaction opened by the order-0 transaction advisor.
 */
@Aspect
@Component
@Order(1)
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Around("execution(* com.habitloop.activity.service..*(..))")
    public Object enableTenantFilter(ProceedingJoinPoint pjp) throws Throwable {
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("tenantId", UUID.fromString(tenantId));
        }
        return pjp.proceed();
    }
}
