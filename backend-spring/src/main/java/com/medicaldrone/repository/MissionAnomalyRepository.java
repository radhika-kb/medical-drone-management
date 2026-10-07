package com.medicaldrone.repository;

import com.medicaldrone.model.MissionAnomaly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface MissionAnomalyRepository extends JpaRepository<MissionAnomaly, Long> {
    List<MissionAnomaly> findByMissionId(Long missionId);
    List<MissionAnomaly> findBySeverity(String severity);
    List<MissionAnomaly> findTop20ByOrderByDetectedAtDesc();
    boolean existsByMissionIdAndAnomalyTypeAndDetectedAtAfter(Long missionId, String anomalyType, LocalDateTime detectedAt);
}
