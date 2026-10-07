package com.medicaldrone.repository;

import com.medicaldrone.model.DemandPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DemandPredictionRepository extends JpaRepository<DemandPrediction, Long> {
    List<DemandPrediction> findByZoneId(Long zoneId);
    List<DemandPrediction> findByMedicalItemId(Long medicalItemId);
    List<DemandPrediction> findTop20ByOrderByCreatedAtDesc();
    List<DemandPrediction> findTop500ByOrderByCreatedAtDesc();
}
