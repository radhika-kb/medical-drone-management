package com.medicaldrone.repository;

import com.medicaldrone.model.MissionTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MissionTrackingRepository extends JpaRepository<MissionTracking, Long> {
    List<MissionTracking> findByMissionIdOrderByTimestampAsc(Long missionId);
}
