package com.medicaldrone.repository;

import com.medicaldrone.model.Drone;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DroneRepository extends JpaRepository<Drone, Long> {
    Optional<Drone> findByDroneCode(String droneCode);
    List<Drone> findByZoneId(Long zoneId);
    List<Drone> findByStatus(String status);
    List<Drone> findByZoneIdAndStatus(Long zoneId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Drone d WHERE d.id = :id")
    Optional<Drone> findByIdWithLock(@Param("id") Long id);
}
