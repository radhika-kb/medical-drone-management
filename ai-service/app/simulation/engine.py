import asyncio
from datetime import datetime, timezone
from motor.motor_asyncio import AsyncIOMotorDatabase
from app.core.config import get_settings
from app.schemas.mission_tracking import MissionTrackingCreate
from app.repositories.mission_tracking_repository import save_tracking_update, update_mission_status
from app.websocket.manager import manager
from app.repositories.mission_repository import get_mission_by_mission_id
from app.repositories.drone_repository import get_drone_by_drone_id, update_drone_status
from app.repositories.medical_request_repository import update_request_status
from app.utils.geo import calculate_distance_km
from app.core.logging import get_logger

logger = get_logger(__name__)

async def run_mission_simulation(mission_id: str, db: AsyncIOMotorDatabase) -> None:
    """
    Background task to simulate a drone flying its assigned route.
    """
    logger.info(f"Starting simulation for mission {mission_id}")
    settings = get_settings()
    
    # 1. Fetch mission & drone baseline
    mission = await get_mission_by_mission_id(db, mission_id)
    if not mission:
        logger.error(f"Mission {mission_id} not found for simulation.")
        return
        
    drone = await get_drone_by_drone_id(db, mission.drone_id)
    if not drone:
        logger.error(f"Drone {mission.drone_id} not found.")
        return
        
    # State tracking
    current_lat = drone.latitude
    current_lng = drone.longitude
    current_battery = drone.battery_pct
    
    route = mission.route  # List of Coordinate: drone -> pickup -> dest
    if len(route) < 3:
        logger.error("Route has insufficient waypoints.")
        return
        
    pickup_target = {"lat": route[1].lat, "lng": route[1].lng}
    dest_target = {"lat": route[2].lat, "lng": route[2].lng}
    
    # Define phases
    phases = [
        {"target": pickup_target, "active_status": "TRAVELLING_TO_PICKUP", "complete_status": "PACKAGE_COLLECTED"},
        {"target": dest_target, "active_status": "IN_TRANSIT", "complete_status": "DELIVERED"}
    ]
    
    tick_interval = settings.simulation_tick_interval_seconds
    speed_mult = settings.simulation_speed_multiplier
    cruise_speed = settings.drone_cruise_speed_kmh
    battery_drain_per_km = settings.drone_battery_consumption_per_km_pct
    
    # Distance drone covers per tick (in km)
    # km_per_sec = cruise_speed / 3600
    # real_km_per_tick = km_per_sec * tick_interval
    # simulated_km_per_tick = real_km_per_tick * speed_mult
    simulated_km_per_tick = (cruise_speed / 3600.0) * tick_interval * speed_mult
    
    async def update_and_broadcast(status: str):
        # Calculate remaining distance to final destination
        dist_to_dest = calculate_distance_km(current_lat, current_lng, dest_target["lat"], dest_target["lng"])
        
        eta = (dist_to_dest / cruise_speed) * 60.0
        
        tracking = MissionTrackingCreate(
            mission_id=mission_id,
            lat=current_lat,
            lng=current_lng,
            battery_pct=current_battery,
            remaining_distance_km=dist_to_dest,
            eta_minutes=eta,
            status=status
        )
        
        await save_tracking_update(db, tracking)
        await update_mission_status(db, mission_id, status)
        await manager.broadcast_to_mission(mission_id, tracking.model_dump(mode='json'))
        
    for phase in phases:
        await update_and_broadcast(phase["active_status"])
        target_lat = phase["target"]["lat"]
        target_lng = phase["target"]["lng"]
        
        while True:
            dist_to_target = calculate_distance_km(current_lat, current_lng, target_lat, target_lng)
            
            if dist_to_target <= simulated_km_per_tick:
                # Reached target
                current_lat = target_lat
                current_lng = target_lng
                current_battery -= (dist_to_target * battery_drain_per_km)
                await update_and_broadcast(phase["complete_status"])
                break
                
            # Interpolate position
            ratio = simulated_km_per_tick / dist_to_target
            current_lat = current_lat + (target_lat - current_lat) * ratio
            current_lng = current_lng + (target_lng - current_lng) * ratio
            
            # Reduce battery
            current_battery -= (simulated_km_per_tick * battery_drain_per_km)
            
            await update_and_broadcast(phase["active_status"])
            
            # Wait for next tick
            await asyncio.sleep(tick_interval)
            
        # Give a small pause between phases if not the final phase
        if phase != phases[-1]:
            await asyncio.sleep(tick_interval)
            
    # Mark drone as available again once delivered
    await update_drone_status(db, drone.id, "AVAILABLE")
    # Mark the original medical request as delivered
    if mission.request_id:
        await update_request_status(db, mission.request_id, "DELIVERED")
        
    logger.info(f"Mission {mission_id} completed successfully.")
