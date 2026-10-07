"""
ai_decision.py
──────────────
Multi-Agent Decision microservice and ML inference pipeline for Intelligent Medical Drone Delivery System.
Follows System Design Specification Section 24 & Section 25.

Endpoints:
• POST /internal/ai/priority
• POST /internal/ai/supply-match
• POST /internal/ai/resource-allocation
• POST /internal/ai/route
• POST /internal/ai/weather
• POST /internal/ai/eta
• POST /internal/ai/safety
• POST /internal/ai/monitor
• POST /internal/ai/demand
• POST /internal/ai/decision
• GET  /internal/ai/health
"""
import os
import math
import logging
import joblib
import pandas as pd
import numpy as np
from typing import Any, Dict, List, Optional
from fastapi import APIRouter
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from app.utils.geo import calculate_distance_km
from app.routing.astar import route_leg

logger = logging.getLogger(__name__)

ALLOCATION_SAFETY_FACTOR = float(os.getenv("ALLOCATION_SAFETY_FACTOR", "1.3"))
ALLOCATION_MIN_RESERVE_PCT = float(os.getenv("ALLOCATION_MIN_RESERVE_PCT", "20.0"))
DRONE_CRUISE_SPEED_KMH = float(os.getenv("DRONE_CRUISE_SPEED_KMH", "60.0"))

router = APIRouter(tags=["ai-decision"])

# Lazy ML Artifact Caches
_ml_models = {}
_ml_model_status = {}

def get_model(name: str):
    global _ml_models
    if name not in _ml_models:
        models_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', 'models'))
        path = os.path.join(models_dir, f"{name}.joblib")
        if os.path.exists(path):
            try:
                _ml_models[name] = joblib.load(path)
                _ml_model_status[name] = "LOADED"
                logger.info(f"Loaded ML model '{name}' from {path}")
            except Exception as e:
                logger.error(f"Failed loading ML model '{name}': {e}")
                _ml_models[name] = None
                _ml_model_status[name] = f"LOAD_FAILED:{type(e).__name__}"
        else:
            _ml_models[name] = None
            _ml_model_status[name] = "MISSING_ARTIFACT"
    return _ml_models.get(name)


# ── Pydantic Request Models ──────────────────────────────────────────────────

class GeoPoint(BaseModel):
    lat: float
    lng: float

class DroneCandidate(BaseModel):
    id: Optional[Any] = None
    drone_code: str
    model: Optional[str] = "Hexacopter-V2"
    zone_name: Optional[str] = None
    battery_pct: float
    max_payload_kg: float
    max_range_km: float
    latitude: float
    longitude: float
    status: str = "AVAILABLE"

class NoFlyZoneModel(BaseModel):
    name: str
    type: Optional[str] = "AIRPORT"
    center_lat: float
    center_lng: float
    radius_km: float
    classification: Optional[str] = None

class PriorityRequest(BaseModel):
    medical_type: str = "BLOOD"
    urgency: str = "CRITICAL"
    weight_kg: float = 2.0
    required_by_minutes: Optional[float] = 60.0

class SupplierCandidate(BaseModel):
    hospital_id: Any
    hospital_name: str
    latitude: float
    longitude: float
    available_quantity: int
    reserved_quantity: Optional[int] = 0
    verification_status: Optional[str] = "APPROVED"

class SupplyMatchRequest(BaseModel):
    requesting_hospital_id: Optional[Any] = None
    destination: GeoPoint
    medical_item_code: Optional[str] = None
    required_quantity: int = 2
    candidates: List[SupplierCandidate] = Field(default_factory=list)

class RouteRequest(BaseModel):
    origin: GeoPoint
    destination: GeoPoint
    drone_lat: Optional[float] = None
    drone_lng: Optional[float] = None
    no_fly_zones: List[NoFlyZoneModel] = Field(default_factory=list)

class WeatherRequest(BaseModel):
    pickup: GeoPoint
    destination: GeoPoint
    current_weather: Optional[Dict[str, Any]] = None

class ETARequest(BaseModel):
    distance_km: float
    cruise_speed_kmh: Optional[float] = 60.0
    altitude_m: Optional[float] = 80.0
    wind_speed_kmh: Optional[float] = 12.0
    temperature_c: Optional[float] = 27.0
    payload_kg: Optional[float] = 2.0

class SafetyRequest(BaseModel):
    priority: Dict[str, Any]
    weather: Dict[str, Any]
    allocation: Dict[str, Any]
    route: Dict[str, Any]
    weight_kg: float = 2.0

class MonitorRequest(BaseModel):
    mission_id: Any
    elapsed_minutes: float
    distance_flown_km: float
    total_distance_km: float
    expected_duration_minutes: float
    battery_used_pct: float
    altitude_variance: Optional[float] = 0.3
    position_drift_meters: Optional[float] = 1.0

class DemandRequest(BaseModel):
    hospital_id: int
    medicine_id: int
    medicine_category: str
    available_stock: int
    day_of_week: Optional[int] = 1
    month: Optional[int] = 10
    is_holiday: Optional[int] = 0
    temperature: Optional[float] = 26.0
    rainfall: Optional[float] = 0.0
    previous_day_demand: Optional[float] = 30.0
    previous_week_demand: Optional[float] = 32.0
    emergency_requests: Optional[int] = 5

class AIDecisionRequest(BaseModel):
    request_id: Optional[Any] = None
    request_code: Optional[str] = "REQ-001"
    medical_type: str = "BLOOD"
    urgency: str = "CRITICAL"
    weight_kg: float = 2.0
    pickup: GeoPoint
    destination: GeoPoint
    preferred_zone: Optional[str] = "NORTH_ZONE"
    drones: List[DroneCandidate] = Field(default_factory=list)
    no_fly_zones: List[NoFlyZoneModel] = Field(default_factory=list)
    current_weather: Optional[Dict[str, Any]] = None
    required_quantity: int = 1
    supplier_accepted: bool = True
    supplier_candidates: List[SupplierCandidate] = Field(default_factory=list)


# ── Agent Functions ──────────────────────────────────────────────────────────

def run_priority_agent(medical_type: str, urgency: str, weight_kg: float) -> Dict[str, Any]:
    med_type_upper = medical_type.upper()
    urgency_upper = urgency.upper()

    score = 50.0
    if med_type_upper in ["BLOOD", "ORGANS", "ORGAN"]:
        score += 35.0
    elif med_type_upper in ["ANTIVENOM", "DEFIBRILLATOR", "EMERGENCY_MEDICINE"]:
        score += 30.0
    elif med_type_upper in ["VACCINES", "VACCINE", "PLASMA", "INSULIN"]:
        score += 15.0
    else:
        score += 5.0

    if urgency_upper == "CRITICAL":
        score += 30.0
    elif urgency_upper == "HIGH":
        score += 20.0
    elif urgency_upper == "MEDIUM":
        score += 10.0

    score = min(100.0, score)
    level = "CRITICAL" if score >= 80 else ("HIGH" if score >= 65 else ("MEDIUM" if score >= 40 else "NORMAL"))
    reason = f"Calculated priority score {score:.1f} ({level}) for {medical_type} with urgency {urgency}."
    return {"priority_level": level, "priority_score": score, "reason": reason}


def run_supply_match_agent(destination: GeoPoint, required_qty: int, candidates: List[SupplierCandidate]) -> Dict[str, Any]:
    ranked = []
    for c in candidates:
        if c.verification_status != "APPROVED":
            continue
        if c.available_quantity < required_qty:
            continue

        dist_km = calculate_distance_km(c.latitude, c.longitude, destination.lat, destination.lng)
        # Score based on distance (closer is better) and stock availability
        dist_score = max(0.0, 1.0 - (dist_km / 40.0))
        stock_score = min(1.0, c.available_quantity / (required_qty * 3.0))
        match_score = (dist_score * 0.7) + (stock_score * 0.3)

        ranked.append({
            "hospital_id": c.hospital_id,
            "hospital_name": c.hospital_name,
            "distance_km": round(dist_km, 2),
            "available_quantity": c.available_quantity,
            "match_score": round(match_score, 3),
            "reason": f"Stock {c.available_quantity} units available at {dist_km:.1f} km distance."
        })

    ranked.sort(key=lambda x: x["match_score"], reverse=True)
    best = ranked[0] if ranked else None
    return {
        "status": "MATCH_FOUND" if best else "NO_ELIGIBLE_SUPPLIER",
        "recommended_supplier": best,
        "ranked_suppliers": ranked,
        "reason": f"Recommended {best['hospital_name']} with {best['distance_km']}km flight distance." if best else "No eligible supplying hospital with sufficient verified inventory found."
    }


def _training_weather_category(weather_code: int) -> str:
    """Map Open-Meteo WMO codes to the weather model's trained categories."""
    if weather_code == 0:
        return "Clear"
    if weather_code in (1, 2):
        return "Cloudy"
    if weather_code == 3:
        return "Overcast"
    if weather_code in (51, 53, 55, 61, 80):
        return "Light Rain"
    if weather_code in (63, 81):
        return "Moderate Rain"
    if weather_code in (65, 82):
        return "Heavy Rain"
    raise ValueError(f"Weather code {weather_code} is not represented by the trained flight-condition model")


def _weather_features(raw_weather: Dict[str, Any]) -> Dict[str, Any]:
    """Build the trained model feature row exclusively from the API observation."""
    from datetime import datetime

    current = raw_weather["current"]
    observed_at = datetime.fromisoformat(current["time"])
    weather_code = int(current["weather_code"])
    precipitation = float(current["precip_mm"])
    return {
        "temperature_c": float(current["temp_c"]),
        "humidity_pct": float(current["humidity"]),
        "precipitation_mm": precipitation,
        "wind_speed_kmh": float(current["wind_kph"]),
        "wind_gust_kmh": float(current["gust_kph"]),
        "wind_direction_deg": int(current["wind_degree"]),
        "visibility_km": float(current["vis_km"]),
        "pressure_hpa": float(current["pressure_mb"]),
        "cloud_cover_pct": float(current["cloud"]),
        "dew_point_c": float(current["dewpoint_c"]),
        "uv_index": float(current["uv"]),
        "weather_condition": _training_weather_category(weather_code),
        "rain_event": int(precipitation > 0),
        "month": observed_at.month,
        "hour": observed_at.hour,
        "day_of_week": observed_at.weekday(),
    }


async def run_weather_agent(pickup: GeoPoint, destination: GeoPoint,
                            custom_weather: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """Fetch live weather at both mission endpoints and apply the trained model."""
    from app.services.weather_client import fetch_current_weather

    # Caller supplied weather is intentionally not used as a substitute for live observations.
    locations = []
    for name, point in (("pickup", pickup), ("destination", destination)):
        if any(item["latitude"] == point.lat and item["longitude"] == point.lng for item in locations):
            continue
        success, raw, error = await fetch_current_weather(point.lat, point.lng)
        if not success:
            reason = f"Live weather unavailable at mission {name}: {error}. Dispatch review is blocked." 
            return {
                "flight_condition": "UNSAFE", "flight_suitability": False,
                "model_prediction": None, "confidence": None, "probability": None,
                "model_name": None, "reason": reason, "error": error,
                "observations": [], "timestamp": None, "condition": None,
                "temperature_c": None, "humidity_pct": None, "precipitation_mm": None,
                "rain_mm": None, "wind_speed_kmh": None, "wind_gust_kmh": None,
                "wind_direction_deg": None, "visibility_km": None, "pressure_hpa": None,
                "cloud_cover_pct": None, "dew_point_c": None, "uv_index": None,
            }
        try:
            features = _weather_features(raw)
            ml_artifact = get_model("weather_flight_condition")
            if not ml_artifact or "pipeline" not in ml_artifact:
                raise RuntimeError("Weather flight condition model is unavailable")
            feature_row = pd.DataFrame([features])
            prediction = ml_artifact["pipeline"].predict(feature_row)[0]
            probabilities = ml_artifact["pipeline"].predict_proba(feature_row)[0]
            if ml_artifact.get("label_encoder") is not None:
                prediction = ml_artifact["label_encoder"].inverse_transform([prediction])[0]
            confidence = float(np.max(probabilities))
        except Exception as exc:
            logger.exception("Live weather model inference failed")
            return {
                "flight_condition": "UNSAFE", "flight_suitability": False,
                "model_prediction": None, "confidence": None, "probability": None,
                "model_name": None,
                "reason": f"Weather model could not evaluate the live observation at mission {name}: {exc}",
                "error": str(exc), "observations": [], "timestamp": raw["current"].get("time"),
            }

        result = str(prediction).upper()
        if result not in {"SAFE", "CAUTION", "UNSAFE"}:
            return {
                "flight_condition": "UNSAFE", "flight_suitability": False,
                "model_prediction": result, "confidence": confidence, "probability": confidence,
                "model_name": ml_artifact.get("model_name"),
                "reason": f"Weather model returned an unsupported flight class at mission {name}.",
                "error": "Unsupported weather-model prediction", "observations": [],
                "timestamp": raw["current"].get("time"),
            }

        current = raw["current"]
        locations.append({
            "location": name,
            "latitude": point.lat,
            "longitude": point.lng,
            "observed_at": current["time"],
            "source": raw.get("source"),
            "condition": current["condition"]["text"],
            "temperature_c": features["temperature_c"],
            "humidity_pct": features["humidity_pct"],
            "precipitation_mm": features["precipitation_mm"],
            "wind_speed_kmh": features["wind_speed_kmh"],
            "wind_gust_kmh": features["wind_gust_kmh"],
            "wind_direction_deg": features["wind_direction_deg"],
            "visibility_km": features["visibility_km"],
            "pressure_hpa": features["pressure_hpa"],
            "cloud_cover_pct": features["cloud_cover_pct"],
            "dew_point_c": features["dew_point_c"],
            "uv_index": features["uv_index"],
            "model_weather_condition": features["weather_condition"],
            "rain_event": features["rain_event"],
            "model_prediction": result,
            "flight_condition": result,
            "confidence": confidence,
            "model_name": ml_artifact.get("model_name", "WeatherFlightConditionModel"),
            "flight_suitability": result == "SAFE",
        })

    severity = {"SAFE": 0, "CAUTION": 1, "UNSAFE": 2}
    worst = max(locations, key=lambda item: severity[item["flight_condition"]])
    conditions = "; ".join(
        f"{item['location']} at ({item['latitude']:.5f}, {item['longitude']:.5f}): "
        f"{item['condition']}; {item['temperature_c']} C, wind {item['wind_speed_kmh']} km/h "
        f"(gust {item['wind_gust_kmh']}), precipitation {item['precipitation_mm']} mm, "
        f"visibility {item['visibility_km']} km; model {item['model_prediction']} "
        f"({item['confidence']:.1%}), observed {item['observed_at']}"
        for item in locations
    )
    return {
        **worst,
        "flight_condition": worst["flight_condition"],
        "flight_suitability": worst["flight_condition"] == "SAFE",
        "probability": worst["confidence"],
        "reason": f"Live Open-Meteo observations evaluated by {worst['model_name']}. {conditions}.",
        "observations": locations,
        "timestamp": worst["observed_at"],
        "error": None,
    }


def run_allocation_agent(drones: List[DroneCandidate], pickup: GeoPoint, destination: GeoPoint, weight_kg: float, preferred_zone: Optional[str]) -> Dict[str, Any]:
    mission_dist = calculate_distance_km(pickup.lat, pickup.lng, destination.lat, destination.lng)
    norm_pref_zone = (preferred_zone or "").replace(" ", "_").upper()

    def is_feasible(drone: DroneCandidate):
        if drone.status != "AVAILABLE":
            return False, f"Status is {drone.status}", 0.0
        if drone.max_payload_kg < weight_kg:
            return False, f"Payload limit exceeded", 0.0
        dist_to_pickup = calculate_distance_km(drone.latitude, drone.longitude, pickup.lat, pickup.lng)
        total_dist = dist_to_pickup + mission_dist
        req_range = total_dist * ALLOCATION_SAFETY_FACTOR
        if drone.max_range_km < req_range:
            return False, "Range insufficient", 0.0
        est_drain = (req_range / drone.max_range_km) * 100.0
        if (drone.battery_pct - est_drain) < ALLOCATION_MIN_RESERVE_PCT:
            return False, "Reserve battery below 20%", 0.0
        return True, "Feasible", total_dist

    tier1, tier2, tier3 = [], [], []
    for d in drones:
        ok, _, tot_d = is_feasible(d)
        if not ok: continue
        d_zone = (d.zone_name or "").replace(" ", "_").upper()
        if "CENTRAL" in d_zone or "HUB" in d_zone:
            tier3.append((d, tot_d))
        elif norm_pref_zone and norm_pref_zone in d_zone:
            tier1.append((d, tot_d))
        else:
            tier2.append((d, tot_d))

    pool, tier_name = (tier1, "LOCAL_ZONE") if tier1 else ((tier2, "CROSS_ZONE_FALLBACK") if tier2 else ((tier3, "CENTRAL_HUB_FALLBACK") if tier3 else ([], None)))
    if not pool:
        return {"decision": "NO_FEASIBLE_DRONE", "drone_id": None, "drone_code": None, "tier": None, "score": 0.0, "reason": "No feasible airborne resource available."}

    best_drone, best_d = max(pool, key=lambda item: (item[0].battery_pct / 100.0 * 0.6) + (max(0.0, 1.0 - item[1] / 50.0) * 0.4))
    score_val = (best_drone.battery_pct / 100.0 * 0.6) + (max(0.0, 1.0 - best_d / 50.0) * 0.4)

    return {
        "decision": "ALLOCATED",
        "drone_id": best_drone.id,
        "drone_code": best_drone.drone_code,
        "drone_model": best_drone.model,
        "drone_zone": best_drone.zone_name,
        "drone_lat": best_drone.latitude,
        "drone_lng": best_drone.longitude,
        "drone_battery": best_drone.battery_pct,
        "tier": tier_name,
        "score": round(score_val, 2),
        "reason": f"Allocated {best_drone.drone_code} via {tier_name}. Battery {best_drone.battery_pct}%, total range {best_d:.1f}km."
    }


def run_route_agent(drone_lat: float, drone_lng: float, pickup: GeoPoint, destination: GeoPoint, no_fly_zones: List[NoFlyZoneModel]) -> Dict[str, Any]:
    # No current DigitalSky UAS-zone feed is integrated. Published ATS data alone
    # cannot establish UAS clearance, so never infer a clear route from empty data.
    if not no_fly_zones:
        return {
            "status": "UNSAFE_AIRSPACE_DATA_UNAVAILABLE",
            "algorithm": "A*-Airspace",
            "waypoints": [],
            "distance_km": 0.0,
            "eta_minutes": 0.0,
            "battery_required_pct": 100.0,
            "reason": "Current DigitalSky UAS airspace classification is unavailable; route clearance cannot be assessed.",
        }
    route_segments = [((drone_lat, drone_lng), (pickup.lat, pickup.lng)),
                      ((pickup.lat, pickup.lng), (destination.lat, destination.lng))]
    for zone in no_fly_zones:
        if (zone.classification or "").upper() not in {"YELLOW", "RED"}:
            continue
        lat0 = sum(point[0] for segment in route_segments for point in segment) / 4
        scale_x = 111.32 * max(0.01, math.cos(math.radians(lat0)))
        center = (zone.center_lng * scale_x, zone.center_lat * 111.32)
        for start, end in route_segments:
            a = (start[1] * scale_x, start[0] * 111.32)
            b = (end[1] * scale_x, end[0] * 111.32)
            dx, dy = b[0] - a[0], b[1] - a[1]
            denom = dx * dx + dy * dy
            t = 0.0 if denom == 0 else max(0.0, min(1.0,
                ((center[0] - a[0]) * dx + (center[1] - a[1]) * dy) / denom))
            closest = (a[0] + t * dx, a[1] + t * dy)
            if math.dist(center, closest) <= zone.radius_km:
                return {
                    "status": "UNSAFE_PERMISSION_REQUIRED",
                    "algorithm": "A*-Airspace",
                    "waypoints": [],
                    "distance_km": 0.0,
                    "eta_minutes": 0.0,
                    "battery_required_pct": 100.0,
                    "reason": f"Planned segment intersects {zone.classification} airspace; no permission is represented by this system.",
                }
    active_nfz = no_fly_zones

    drone_base = {"lat": drone_lat, "lng": drone_lng}
    pickup_point = {"lat": pickup.lat, "lng": pickup.lng}
    destination_point = {"lat": destination.lat, "lng": destination.lng}
    try:
        outbound = route_leg(drone_base, pickup_point, active_nfz)
        delivery = route_leg(pickup_point, destination_point, active_nfz)
    except ValueError as exc:
        return {
            "status": "UNSAFE_NO_ROUTE",
            "algorithm": "A*-Airspace",
            "waypoints": [],
            "distance_km": 0.0,
            "eta_minutes": 0.0,
            "battery_required_pct": 100.0,
            "reason": str(exc),
        }

    route_points = outbound + delivery[1:]
    waypoints = []
    for index, point in enumerate(route_points):
        if index == 0:
            waypoint_type = "DRONE_BASE"
        elif index == len(route_points) - 1:
            waypoint_type = "DESTINATION"
        elif point["lat"] == pickup.lat and point["lng"] == pickup.lng:
            waypoint_type = "PICKUP"
        else:
            waypoint_type = "WAYPOINT"
        waypoints.append({**point, "type": waypoint_type})

    total_dist = sum(calculate_distance_km(waypoints[i]["lat"], waypoints[i]["lng"], waypoints[i+1]["lat"], waypoints[i+1]["lng"]) for i in range(len(waypoints)-1))
    baseline_eta = (total_dist / DRONE_CRUISE_SPEED_KMH) * 60.0
    battery_req = total_dist * 2.0
    used_grid_search = len(outbound) > 2 or len(delivery) > 2

    return {
        "status": "SAFE",
        "algorithm": "A*-Airspace",
        "routing_method": "A_STAR_GRID" if used_grid_search else "DIRECT_CLEARANCE",
        "waypoints": waypoints,
        "distance_km": round(total_dist, 2),
        "eta_minutes": round(baseline_eta, 1),
        "battery_required_pct": round(battery_req, 1),
        "baseline_eta_minutes": round(baseline_eta, 1),
        "reason": f"Airspace route passed no-fly-zone segment checks using {('A* grid search' if used_grid_search else 'a clear direct candidate')}. Distance {total_dist:.2f}km; baseline ETA {baseline_eta:.1f} min."
    }


def run_eta_agent(dist_km: float, speed_kmh: float = 60.0, alt_m: float = 80.0, wind_kmh: float = 12.0, temp_c: float = 27.0, payload_kg: float = 2.0) -> Dict[str, Any]:
    baseline_min = (dist_km / max(10.0, speed_kmh)) * 60.0

    ml_art = get_model('eta_prediction_model')
    ml_predicted_min = baseline_min
    model_name = "PhysicsBaselineFallback"
    if ml_art and 'pipeline' in ml_art:
        try:
            df_in = pd.DataFrame([{
                'distance_km': dist_km,
                'cruise_speed_kmh': speed_kmh,
                'altitude_m': alt_m,
                'wind_speed_kmh': wind_kmh,
                'temperature_c': temp_c,
                'payload_kg': payload_kg
            }])
            ml_predicted_min = float(ml_art['pipeline'].predict(df_in)[0])
            model_name = ml_art.get('model_name', 'ETAPredictionModel')
        except Exception as e:
            logger.warning(f"ETA ML prediction fallback: {e}")

    final_eta = round((baseline_min * 0.3) + (ml_predicted_min * 0.7), 1)
    return {
        "baseline_eta_minutes": round(baseline_min, 1),
        "ml_predicted_eta_minutes": round(ml_predicted_min, 1),
        "final_eta_minutes": final_eta,
        "model_name": model_name,
        "reason": f"ML ETA predicted {ml_predicted_min:.1f} min based on wind {wind_kmh}km/h & payload {payload_kg}kg."
    }


def run_safety_agent(priority: Dict[str, Any], weather: Dict[str, Any], allocation: Dict[str, Any], route: Dict[str, Any], weight_kg: float) -> Dict[str, Any]:
    if allocation.get("decision") == "NO_FEASIBLE_DRONE":
        return {"status": "REJECT", "score": 0.0, "reason": "Safety check failed: No feasible drone available."}

    if weather["flight_condition"] == "UNSAFE":
        return {"status": "REJECT", "score": 0.1, "reason": f"Safety check rejected: Weather is UNSAFE ({weather.get('reason')})."}

    if weather["flight_condition"] == "CAUTION" and priority["priority_level"] not in ["HIGH", "CRITICAL"]:
        return {"status": "DELAY", "score": 0.5, "reason": "Safety check delayed: Marginal weather and non-critical urgency."}

    avail_battery = allocation.get("drone_battery", 100.0)
    required_battery = route.get("battery_required_pct", 20.0)
    remaining_battery = avail_battery - required_battery

    if remaining_battery < 20.0:
        return {"status": "REJECT", "score": 0.3, "reason": f"Safety check failed: Projected reserve battery {remaining_battery:.1f}% < 20%."}

    return {"status": "PASS", "score": 1.0, "reason": f"All safety constraints cleared. Weather {weather['flight_condition']}, battery reserve {remaining_battery:.1f}%."}


def run_monitoring_anomaly_agent(req: MonitorRequest) -> Dict[str, Any]:
    avg_speed = (req.distance_flown_km / max(0.01, req.elapsed_minutes)) * 60.0
    drain_rate = req.battery_used_pct / max(0.01, req.distance_flown_km)
    
    ml_art = get_model('mission_anomaly_model')
    monitoring_available = False
    is_anomaly = False
    anomaly_score = 0.1
    anomaly_type = "NORMAL"
    severity = "LOW"
    explanation = "Mission telemetry nominal."

    if ml_art and 'scaler' in ml_art and 'classifier' in ml_art:
        try:
            df_in = pd.DataFrame([{
                'duration_minutes': req.elapsed_minutes,
                'avg_speed_kmh': avg_speed,
                'battery_drain_rate': drain_rate,
                'alt_variance': req.altitude_variance or 0.3,
                'pos_drift_meters': req.position_drift_meters or 1.0,
                'speed_variance': 1.5
            }])
            X_scaled = ml_art['scaler'].transform(df_in)
            raw_score = -ml_art['isolation_forest'].score_samples(X_scaled)[0]
            min_s, max_s = ml_art['min_score'], ml_art['max_score']
            norm_s = max(0.0, min(1.0, (raw_score - min_s) / max(0.001, max_s - min_s)))
            anomaly_score = round(norm_s, 3)

            pred_type = ml_art['classifier'].predict(df_in)[0]
            monitoring_available = True
            if norm_s > 0.65 or pred_type != "NORMAL":
                is_anomaly = True
                anomaly_type = pred_type if pred_type != "NORMAL" else "DELAY_TIMEOUT"
                severity = "CRITICAL" if norm_s > 0.85 else "HIGH"
                explanation = f"Detected {anomaly_type} anomaly (score {anomaly_score:.2f}). Altitude variance: {req.altitude_variance}, drift: {req.position_drift_meters}m."
        except Exception as e:
            logger.warning(f"Anomaly ML error: {e}")

    if not monitoring_available:
        is_anomaly = True
        anomaly_score = 1.0
        anomaly_type = "MONITORING_UNAVAILABLE"
        severity = "CRITICAL"
        explanation = "Mission anomaly model is unavailable or failed inference; hold for Central Hub review."

    # Deterministic milestone timeout check
    if req.elapsed_minutes > (req.expected_duration_minutes * 1.5):
        is_anomaly = True
        anomaly_type = "TIMEOUT"
        severity = "CRITICAL"
        explanation = f"Mission elapsed time ({req.elapsed_minutes:.1f}m) exceeds 150% of expected ETA ({req.expected_duration_minutes:.1f}m)."

    return {
        "monitoring_available": monitoring_available,
        "is_anomaly": is_anomaly,
        "anomaly_score": anomaly_score,
        "anomaly_type": anomaly_type,
        "severity": severity,
        "explanation": explanation
    }


def run_demand_forecasting_agent(req: DemandRequest) -> Dict[str, Any]:
    ml_art = get_model('demand_forecast_model')
    predicted_qty = max(req.previous_day_demand or 0.0,
                        (req.previous_week_demand or 0.0) / 7.0)
    model_name = "HistoricalMeanFallback"

    if ml_art and 'pipeline' in ml_art:
        try:
            df_in = pd.DataFrame([{
                'hospital_id': req.hospital_id,
                'medicine_id': req.medicine_id,
                'medicine_category': req.medicine_category,
                'day_of_week': req.day_of_week,
                'month': req.month,
                'holiday': req.is_holiday,
                'temperature': req.temperature,
                'rainfall': req.rainfall,
                'previous_day_demand': req.previous_day_demand,
                'previous_week_demand': req.previous_week_demand,
                'emergency_requests': req.emergency_requests
            }])
            pred = ml_art['pipeline'].predict(df_in)[0]
            predicted_qty = max(0.0, float(pred))
            model_name = ml_art.get('model_name', 'DemandForecastModel')
        except Exception as e:
            logger.warning(f"Demand ML error: {e}")

    shortage_risk = max(0, int(round(predicted_qty - req.available_stock)))
    risk_level = "HIGH" if shortage_risk > 15 else ("MEDIUM" if shortage_risk > 0 else "LOW")

    return {
        "predicted_demand_quantity": round(predicted_qty, 1),
        "available_stock": req.available_stock,
        "shortage_risk_units": shortage_risk,
        "inventory_risk_level": risk_level,
        "model_name": model_name,
        "recommendation": f"Replenish {shortage_risk} units immediately via inter-hospital network." if shortage_risk > 0 else "Stock levels sufficient for projected demand."
    }


# ── REST Endpoints ───────────────────────────────────────────────────────────

@router.get("/internal/ai/health")
@router.get("/health")
def ai_health():
    loaded_models = {
        "weather": get_model('weather_flight_condition') is not None,
        "demand": get_model('demand_forecast_model') is not None,
        "eta": get_model('eta_prediction_model') is not None,
        "anomaly": get_model('mission_anomaly_model') is not None
    }
    all_models_loaded = all(loaded_models.values())
    payload = {
        "status": "HEALTHY" if all_models_loaded else "DEGRADED",
        "service": "Intelligent Medical Drone Multi-Agent AI",
        "orchestration": "LANGGRAPH",
        "workflow_endpoint": "/internal/ai/workflow",
        "workflow_nodes": [
            "PriorityAgent", "SupplyMatchingAgent", "WAIT_FOR_SUPPLIER",
            "AllocationAgent", "RouteAgent", "WeatherAgent", "ETAPredictionAgent",
            "SafetyAgent", "AWAITING_CENTRAL_HUB_REVIEW", "MonitoringAgent"
        ],
        "models_loaded": loaded_models,
        "model_load_status": {
            "weather": _ml_model_status.get("weather_flight_condition"),
            "demand": _ml_model_status.get("demand_forecast_model"),
            "eta": _ml_model_status.get("eta_prediction_model"),
            "anomaly": _ml_model_status.get("mission_anomaly_model")
        }
    }
    return JSONResponse(status_code=200 if all_models_loaded else 503, content=payload)

@router.post("/internal/ai/priority")
def api_priority(req: PriorityRequest):
    return run_priority_agent(req.medical_type, req.urgency, req.weight_kg)

@router.post("/internal/ai/supply-match")
def api_supply_match(req: SupplyMatchRequest):
    return run_supply_match_agent(req.destination, req.required_quantity, req.candidates)

@router.post("/internal/ai/resource-allocation")
def api_resource_allocation(req: AIDecisionRequest):
    return run_allocation_agent(req.drones, req.pickup, req.destination, req.weight_kg, req.preferred_zone)

@router.post("/internal/ai/route")
def api_route(req: RouteRequest):
    d_lat = req.drone_lat if req.drone_lat is not None else req.origin.lat
    d_lng = req.drone_lng if req.drone_lng is not None else req.origin.lng
    return run_route_agent(d_lat, d_lng, req.origin, req.destination, req.no_fly_zones)

@router.post("/internal/ai/weather")
async def api_weather(req: WeatherRequest):
    return await run_weather_agent(req.pickup, req.destination, req.current_weather)

@router.post("/internal/ai/eta")
def api_eta(req: ETARequest):
    return run_eta_agent(req.distance_km, req.cruise_speed_kmh, req.altitude_m, req.wind_speed_kmh, req.temperature_c, req.payload_kg)

@router.post("/internal/ai/safety")
def api_safety(req: SafetyRequest):
    return run_safety_agent(req.priority, req.weather, req.allocation, req.route, req.weight_kg)

@router.post("/internal/ai/monitor")
async def api_monitor(req: MonitorRequest):
    from app.orchestration.graph import dispatch_graph

    result = await dispatch_graph.ainvoke({
        "workflow_stage": "MONITORING",
        "monitoring_request": req,
    })
    return result["monitoring_result"]

@router.post("/internal/ai/demand")
def api_demand(req: DemandRequest):
    return run_demand_forecasting_agent(req)

async def _run_workflow_graph(req: AIDecisionRequest) -> Dict[str, Any]:
    from app.orchestration.graph import dispatch_graph

    result = await dispatch_graph.ainvoke({
        "workflow_stage": "DISPATCH_REVIEW" if req.supplier_accepted else "SUPPLIER_MATCHING",
        "request": req,
        "supplier_candidates": req.supplier_candidates,
        "supplier_accepted": req.supplier_accepted,
        "decision_trail": [],
        "final_status": "STARTED",
    })
    priority = result.get("priority_result")
    if priority and "priority_level" in priority:
        priority = {**priority, "priority": priority["priority_level"], "priority_score": priority["priority_score"]}
    safety = result.get("safety_result")
    pipeline_status = "APPROVED" if result.get("final_status") == "AWAITING_CENTRAL_HUB_REVIEW" else result.get("final_status", "REJECTED")
    if result.get("final_status") == "UNSAFE_ROUTE":
        pipeline_status = "REJECTED_ROUTE"
        safety = {"status": "REJECT", "score": 0.0,
                  "reason": result.get("halt_reason", "No safe route exists around the active no-fly zones.")}
    if result.get("weather_result", {}).get("flight_condition") == "UNSAFE":
        pipeline_status = "REJECTED_WEATHER"
    return {
        "status": pipeline_status,
        "workflow_status": result.get("final_status"),
        "request_code": req.request_code,
        "priority": priority,
        "supply_matching": result.get("supply_matching_result"),
        "weather": result.get("weather_result"),
        "allocation": result.get("allocation_result"),
        "route": result.get("route_result"),
        "eta": result.get("eta_result"),
        "safety": safety,
        "decision_trail": result.get("decision_trail", []),
        "halt_reason": result.get("halt_reason"),
    }


@router.post("/internal/ai/workflow")
async def run_langgraph_workflow(req: AIDecisionRequest) -> Dict[str, Any]:
    """Run supplier matching or post-acceptance flight recommendation via LangGraph."""
    return await _run_workflow_graph(req)


@router.post("/internal/ai/decision")
async def process_full_ai_decision(req: AIDecisionRequest) -> Dict[str, Any]:
    """Compatibility alias; the implementation is the same LangGraph workflow."""
    return await _run_workflow_graph(req)
