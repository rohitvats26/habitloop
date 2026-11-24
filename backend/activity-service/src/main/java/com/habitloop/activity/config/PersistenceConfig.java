package com.habitloop.activity.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Pins the transaction advisor to the highest precedence (order 0) so {@code TenantFilterAspect}
 * (order 1) runs <em>inside</em> an active transaction, where the Hibernate session exists and the
 * tenant filter can be enabled on it.
 */
@Configuration
@EnableAspectJAutoProxy
@EnableTransactionManagement(order = 0)
public class PersistenceConfig {
}
