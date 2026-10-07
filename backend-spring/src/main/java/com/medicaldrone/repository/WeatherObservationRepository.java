package com.medicaldrone.repository;

import com.medicaldrone.model.WeatherObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WeatherObservationRepository extends JpaRepository<WeatherObservation, Long> {
    Optional<WeatherObservation> findTopByOrderByObservedAtDesc();
}
