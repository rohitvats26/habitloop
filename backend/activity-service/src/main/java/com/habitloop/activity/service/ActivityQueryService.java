package com.habitloop.activity.service;

import com.habitloop.activity.dto.ActivityLogResponse;
import com.habitloop.activity.mapper.ActivityMapper;
import com.habitloop.activity.repository.ActivityLogRepository;
import com.habitloop.common.security.SecurityUtils;
import com.habitloop.common.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class ActivityQueryService {

    private final ActivityLogRepository activityLogs;
    private final ActivityMapper mapper;

    public ActivityQueryService(ActivityLogRepository activityLogs, ActivityMapper mapper) {
        this.activityLogs = activityLogs;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityLogResponse> search(String type, OffsetDateTime from,
                                                    OffsetDateTime to, Pageable pageable) {
        UUID tenantId = UUID.fromString(SecurityUtils.currentTenantId());
        UUID userId = UUID.fromString(SecurityUtils.currentUserId());
        var page = activityLogs.search(tenantId, userId, type, from, to, pageable)
                .map(mapper::toActivityLogResponse);
        return PageResponse.of(page);
    }
}
