package com.medicaldrone.repository;

import com.medicaldrone.model.CentralHub;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CentralHubRepository extends JpaRepository<CentralHub, Long> {
    Optional<CentralHub> findByCode(String code);
}
