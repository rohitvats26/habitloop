package com.habitloop.integration.controller;

import com.habitloop.integration.dto.WebhookResponse;
import com.habitloop.integration.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public webhook endpoint for partners. Authenticated by the HMAC signature in {@code X-HL-Signature}
 * over the raw body (not JWT). The raw body is bound as a String so the exact bytes are signed/verified.
 */
@RestController
@RequestMapping("/api/webhooks")
@Tag(name = "Webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/{connectionId}")
    @Operation(summary = "Receive a partner event (HMAC-signed)")
    public ResponseEntity<WebhookResponse> receive(
            @PathVariable UUID connectionId,
            @RequestHeader(value = "X-HL-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        WebhookResponse response = webhookService.ingest(connectionId, signature, rawBody);
        HttpStatus status = response.deduplicated() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(response);
    }
}
