package com.habitloop.activity.controller;

import com.habitloop.activity.dto.ActivityLogResponse;
import com.habitloop.activity.dto.CompletionRequest;
import com.habitloop.activity.dto.CompletionResponse;
import com.habitloop.activity.dto.CreateHabitRequest;
import com.habitloop.activity.dto.HabitResponse;
import com.habitloop.activity.dto.PauseHabitRequest;
import com.habitloop.activity.dto.UpdateHabitRequest;
import com.habitloop.activity.service.HabitService;
import com.habitloop.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/habits")
@Tag(name = "Habits")
public class HabitController {

    private final HabitService habitService;

    public HabitController(HabitService habitService) {
        this.habitService = habitService;
    }

    @PostMapping
    @Operation(summary = "Create a habit")
    public ResponseEntity<HabitResponse> create(@Valid @RequestBody CreateHabitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(habitService.create(request));
    }

    @GetMapping
    @Operation(summary = "List the current user's habits")
    public PageResponse<HabitResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return habitService.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a habit with its streak")
    public HabitResponse get(@PathVariable UUID id) {
        return habitService.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a habit")
    public HabitResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateHabitRequest request) {
        return habitService.update(id, request);
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "Pause a habit for a number of days")
    public HabitResponse pause(@PathVariable UUID id, @Valid @RequestBody PauseHabitRequest request) {
        return habitService.pause(id, request);
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Resume a paused habit")
    public HabitResponse resume(@PathVariable UUID id) {
        return habitService.resume(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a habit")
    public void delete(@PathVariable UUID id) {
        habitService.delete(id);
    }

    @PostMapping("/{id}/completions")
    @Operation(summary = "Mark a habit complete for a day (defaults to today)")
    public CompletionResponse complete(@PathVariable UUID id, @Valid @RequestBody CompletionRequest request) {
        return habitService.complete(id, request);
    }

    @GetMapping("/{id}/completions")
    @Operation(summary = "List a habit's completion history")
    public PageResponse<ActivityLogResponse> completions(@PathVariable UUID id,
                                                         @PageableDefault(size = 20) Pageable pageable) {
        return habitService.completions(id, pageable);
    }
}
