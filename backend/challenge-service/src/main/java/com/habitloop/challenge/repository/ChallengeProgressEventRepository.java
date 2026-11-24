package com.habitloop.challenge.repository;

import com.habitloop.challenge.entity.ChallengeProgressEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChallengeProgressEventRepository extends JpaRepository<ChallengeProgressEvent, UUID> {

    boolean existsByParticipantIdAndActivityLogId(UUID participantId, UUID activityLogId);
}
