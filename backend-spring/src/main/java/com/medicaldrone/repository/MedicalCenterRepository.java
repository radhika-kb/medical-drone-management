package com.medicaldrone.repository;

import com.medicaldrone.model.MedicalCenter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicalCenterRepository extends JpaRepository<MedicalCenter, Long> {
    List<MedicalCenter> findByZoneId(Long zoneId);
    List<MedicalCenter> findByStatus(String status);
    List<MedicalCenter> findByVerificationStatusAndOperationalStatusAndStatus(String verificationStatus, String operationalStatus, String status);
}
