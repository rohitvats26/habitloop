package com.habitloop.integration.controller;

import com.habitloop.integration.dto.ConnectionResponse;
import com.habitloop.integration.dto.CreateConnectionRequest;
import com.habitloop.integration.service.ConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/integrations/connections")
@Tag(name = "Integrations")
public class ConnectionController {

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @PostMapping
    @Operation(summary = "Create a partner connection (returns the API key once)")
    public ResponseEntity<ConnectionResponse> create(@Valid @RequestBody CreateConnectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(connectionService.create(request));
    }

    @GetMapping
    @Operation(summary = "List the current user's partner connections")
    public List<ConnectionResponse> list() {
        return connectionService.list();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a partner connection")
    public void revoke(@PathVariable UUID id) {
        connectionService.revoke(id);
    }
}
