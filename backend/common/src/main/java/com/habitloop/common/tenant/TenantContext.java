package com.habitloop.common.tenant;

/**
 * Request-scoped holder for the current tenant id, populated from the verified JWT
 * (or from an event envelope on the consumer side). The tenant is NEVER read from a
 * request body or path — this is the single source of truth used by the persistence
 * layer's tenant filter. See docs/08-security-multitenancy.md.
 */
public final class TenantContext {

    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(String tenantId) {
        TENANT.set(tenantId);
    }

    public static String getTenantId() {
        return TENANT.get();
    }

    public static boolean hasTenant() {
        return TENANT.get() != null;
    }

    public static void clear() {
        TENANT.remove();
    }
}
