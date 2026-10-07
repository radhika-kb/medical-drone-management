package com.medicaldrone.repository;

import com.medicaldrone.model.DroneZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DroneZoneRepository extends JpaRepository<DroneZone, Long> {
    Optional<DroneZone> findByName(String name);
    Optional<DroneZone> findByCode(String code);
    List<DroneZone> findAllByActiveTrue();
}
