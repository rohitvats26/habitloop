package com.habitloop.challenge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Transaction advisor at order 0 so {@code TenantFilterAspect} (order 1) runs inside the transaction,
 * where the Hibernate session exists and the tenant filter can be enabled.
 */
@Configuration
@EnableAspectJAutoProxy
@EnableTransactionManagement(order = 0)
public class PersistenceConfig {
}
