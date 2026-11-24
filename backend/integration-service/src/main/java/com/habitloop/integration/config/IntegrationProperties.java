package com.habitloop.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "habitloop.integration")
public class IntegrationProperties {

    /** Public base URL used to build webhook URLs returned to partners. */
    private String webhookBaseUrl = "http://localhost:8084";

    public String getWebhookBaseUrl() {
        return webhookBaseUrl;
    }

    public void setWebhookBaseUrl(String webhookBaseUrl) {
        this.webhookBaseUrl = webhookBaseUrl;
    }
}
