from typing import Any, Dict
from app.utils.geo import calculate_distance_km
from app.core.config import get_settings

def compute_route(
    pickup_lat: float, pickup_lng: float,
    dest_lat: float, dest_lng: float,
    drone_lat: float, drone_lng: float
) -> Dict[str, Any]:
    """
    Computes a V1 straight-line (Haversine) route for the drone.
    Returns the waypoints, total distance, ETA, and required battery.
    """
    settings = get_settings()
    
    # Calculate distances
    dist_to_pickup = calculate_distance_km(drone_lat, drone_lng, pickup_lat, pickup_lng)
    dist_pickup_to_dest = calculate_distance_km(pickup_lat, pickup_lng, dest_lat, dest_lng)
    total_distance_km = dist_to_pickup + dist_pickup_to_dest
    
    # Compute ETA based on cruise speed
    eta_hours = total_distance_km / settings.drone_cruise_speed_kmh
    eta_minutes = eta_hours * 60.0
    
    # Compute battery consumption
    battery_required_pct = total_distance_km * settings.drone_battery_consumption_per_km_pct
    
    # Simple waypoint list
    route = [
        {"lat": drone_lat, "lng": drone_lng},
        {"lat": pickup_lat, "lng": pickup_lng},
        {"lat": dest_lat, "lng": dest_lng}
    ]
    
    return {
        "route": route,
        "distance_km": total_distance_km,
        "eta_minutes": eta_minutes,
        "battery_required_pct": battery_required_pct
    }
