package com.medicaldrone.repository;

import com.medicaldrone.model.NoFlyZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoFlyZoneRepository extends JpaRepository<NoFlyZone, Long> {
    List<NoFlyZone> findAllByActiveTrue();
}
