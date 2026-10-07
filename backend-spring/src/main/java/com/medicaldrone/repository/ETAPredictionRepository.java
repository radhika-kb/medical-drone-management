package com.medicaldrone.repository;

import com.medicaldrone.model.ETAPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ETAPredictionRepository extends JpaRepository<ETAPrediction, Long> {
    List<ETAPrediction> findByMissionId(Long missionId);
    Optional<ETAPrediction> findTopByMissionIdOrderByCreatedAtDesc(Long missionId);
}
