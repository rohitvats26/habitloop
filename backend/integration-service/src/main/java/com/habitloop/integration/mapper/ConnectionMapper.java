package com.habitloop.integration.mapper;

import com.habitloop.integration.dto.ConnectionResponse;
import com.habitloop.integration.entity.PartnerConnection;
import org.springframework.stereotype.Component;

@Component
public class ConnectionMapper {

    public ConnectionResponse toResponse(PartnerConnection c, String apiKey, String hmacSecret,
                                         String webhookBaseUrl) {
        String webhookUrl = webhookBaseUrl + "/api/webhooks/" + c.getId();
        return new ConnectionResponse(
                c.getId().toString(), c.getPartner(), c.getStatus(), apiKey, hmacSecret, webhookUrl,
                c.getConnectedAt(), c.getLastEventAt());
    }
}
