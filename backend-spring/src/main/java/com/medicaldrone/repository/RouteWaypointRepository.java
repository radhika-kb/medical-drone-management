package com.medicaldrone.repository;

import com.medicaldrone.model.RouteWaypoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteWaypointRepository extends JpaRepository<RouteWaypoint, Long> {
    List<RouteWaypoint> findByRouteIdOrderBySequenceNoAsc(Long routeId);
}
