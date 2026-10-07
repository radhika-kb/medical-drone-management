package com.medicaldrone.repository;

import com.medicaldrone.model.AgentDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentDecisionRepository extends JpaRepository<AgentDecision, Long> {
    List<AgentDecision> findByMissionIdOrderByCreatedAtAsc(Long missionId);
    List<AgentDecision> findByRequestIdOrderByCreatedAtAsc(Long requestId);
}
