from app.api.endpoints.ai_decision import GeoPoint, NoFlyZoneModel, run_route_agent


def test_missing_current_uas_airspace_data_fails_closed_without_fabricated_defaults():
    result = run_route_agent(12.97, 77.59, GeoPoint(lat=12.97, lng=77.59),
                             GeoPoint(lat=12.98, lng=77.60), [])
    assert result["status"] == "UNSAFE_AIRSPACE_DATA_UNAVAILABLE"
    assert result["waypoints"] == []
    assert "DigitalSky" in result["reason"]


def test_yellow_zone_requires_permission_and_is_never_treated_as_clear():
    pickup, destination = GeoPoint(lat=12.97, lng=77.59), GeoPoint(lat=12.97, lng=77.61)
    zone = NoFlyZoneModel(name="Published UAS yellow zone", type="UAS",
                          center_lat=12.97, center_lng=77.60, radius_km=0.5,
                          classification="YELLOW")
    result = run_route_agent(pickup.lat, pickup.lng, pickup, destination, [zone])
    assert result["status"] == "UNSAFE_PERMISSION_REQUIRED"
    assert result["waypoints"] == []
    assert "intersects YELLOW" in result["reason"]


def test_verified_restricted_geometry_crossing_is_rejected():
    pickup, destination = GeoPoint(lat=12.97, lng=77.59), GeoPoint(lat=12.97, lng=77.61)
    zone = NoFlyZoneModel(name="Published red zone", type="UAS",
                          center_lat=12.97, center_lng=77.60, radius_km=0.5,
                          classification="RED")
    result = run_route_agent(pickup.lat, pickup.lng, pickup, destination, [zone])
    assert result["status"] == "UNSAFE_PERMISSION_REQUIRED"
    assert result["waypoints"] == []


def test_no_legacy_bengaluru_buffer_names_or_coordinates_are_fallbacks():
    import inspect
    from app.api.endpoints import ai_decision
    source = inspect.getsource(ai_decision.run_route_agent)
    for marker in ("Simulation buffer near HAL", "Simulation buffer near Yelahanka",
                   "Simulation buffer near Devanahalli", "Simulation buffer near Jakkur"):
        assert marker not in source
