package com.habitloop.analytics.controller;

import com.habitloop.analytics.dto.CohortResponse;
import com.habitloop.analytics.dto.DailyStatResponse;
import com.habitloop.analytics.dto.PatternSummaryResponse;
import com.habitloop.analytics.dto.SummaryResponse;
import com.habitloop.analytics.dto.WeeklyStatResponse;
import com.habitloop.analytics.service.AnalyticsQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics")
public class AnalyticsController {

    private final AnalyticsQueryService queryService;

    public AnalyticsController(AnalyticsQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Dashboard summary: today, this week and the weekly trend")
    public SummaryResponse summary() {
        return queryService.summary();
    }

    @GetMapping("/daily")
    @Operation(summary = "Daily stats series (optional date range)")
    public List<DailyStatResponse> daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return queryService.daily(from, to);
    }

    @GetMapping("/weekly")
    @Operation(summary = "Weekly completion trend")
    public List<WeeklyStatResponse> weekly(@RequestParam(defaultValue = "8") int weeks) {
        return queryService.weekly(weeks);
    }

    @GetMapping("/pattern")
    @Operation(summary = "Activity pattern summary (by hour and weekday)")
    public PatternSummaryResponse pattern() {
        return queryService.pattern();
    }

    @GetMapping("/cohort")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Tenant-wide cohort insights for a date")
    public CohortResponse cohort(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queryService.cohort(date);
    }
}
