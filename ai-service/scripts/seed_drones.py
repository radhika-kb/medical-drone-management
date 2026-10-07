"""
scripts/seed_drones.py
──────────────────────
Legacy, dormant seed script — inserts 10 simulated drone records into MongoDB.

Run from the backend/ directory with the project virtual env active:

    python scripts/seed_drones.py

The script is idempotent: if any drones already exist in the collection
it prints a message and exits without inserting duplicates.
"""
from __future__ import annotations

import asyncio
import os
import sys
from datetime import datetime, timezone

# Make sure `app` package is importable when run from backend/
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dotenv import load_dotenv

load_dotenv(os.path.join(os.path.dirname(__file__), "..", ".env"))

from motor.motor_asyncio import AsyncIOMotorClient

from app.schemas.drone import DroneCreate

# ── Drone definitions ─────────────────────────────────────────────────────────
# 10 software-only simulated units scattered around Bengaluru; these do not describe real aircraft.
# Mix of statuses, payloads, ranges, and zones.

_NOW = datetime.now(tz=timezone.utc)

DRONES: list[dict] = [
    # North Bengaluru
    dict(drone_id="DRN-001", model="Simulated Medical Quadcopter - Class S", battery_pct=92.0, max_payload_kg=1.75, max_range_km=40.0, latitude=13.0358, longitude=77.5970, status="AVAILABLE",   zone="North Bengaluru"),
    dict(drone_id="DRN-002", model="Simulated Medical Quadcopter - Class S", battery_pct=34.0, max_payload_kg=1.75, max_range_km=40.0, latitude=13.0210, longitude=77.6150, status="CHARGING",    zone="North Bengaluru"),
    # South Bengaluru
    dict(drone_id="DRN-003", model="Simulated Medical Quadcopter - Class L", battery_pct=78.5, max_payload_kg=6.00, max_range_km=75.0, latitude=12.9102, longitude=77.5680, status="AVAILABLE",   zone="South Bengaluru"),
    dict(drone_id="DRN-004", model="Simulated Medical Quadcopter - Class L", battery_pct=55.0, max_payload_kg=6.00, max_range_km=75.0, latitude=12.9250, longitude=77.5820, status="BUSY",        zone="South Bengaluru"),
    # East Bengaluru
    dict(drone_id="DRN-005", model="Simulated Medical Quadcopter - Class M", battery_pct=100.0, max_payload_kg=2.50, max_range_km=25.0, latitude=12.9800, longitude=77.6900, status="AVAILABLE",  zone="East Bengaluru"),
    dict(drone_id="DRN-006", model="Simulated Medical Quadcopter - Class M", battery_pct=21.0, max_payload_kg=2.50, max_range_km=25.0, latitude=12.9650, longitude=77.7000, status="MAINTENANCE", zone="East Bengaluru"),
    # West Bengaluru
    dict(drone_id="DRN-007", model="Simulated Medical Quadcopter - Class M", battery_pct=88.0, max_payload_kg=2.70, max_range_km=20.0, latitude=12.9720, longitude=77.5560, status="AVAILABLE",   zone="West Bengaluru"),
    dict(drone_id="DRN-008", model="Simulated Medical Quadcopter - Class M", battery_pct=47.0, max_payload_kg=2.70, max_range_km=20.0, latitude=12.9880, longitude=77.5600, status="CHARGING",    zone="West Bengaluru"),
    # Central Bengaluru
    dict(drone_id="DRN-009", model="Simulated Medical Quadcopter - Class XL", battery_pct=73.0, max_payload_kg=9.00, max_range_km=80.0, latitude=12.9716, longitude=77.5946, status="AVAILABLE",   zone="Central Bengaluru"),
    dict(drone_id="DRN-010", model="Simulated Medical Quadcopter - Class XL", battery_pct=15.0, max_payload_kg=9.00, max_range_km=80.0, latitude=12.9780, longitude=77.5990, status="OFFLINE",     zone="Central Bengaluru"),
]


async def seed() -> None:
    mongo_uri = os.getenv("MONGODB_URI", "mongodb://localhost:27017")
    db_name   = os.getenv("MONGODB_DATABASE", "medical_drone_db")

    client: AsyncIOMotorClient = AsyncIOMotorClient(mongo_uri, serverSelectionTimeoutMS=5_000)  # type: ignore[type-arg]
    db = client[db_name]

    existing = await db["drones"].count_documents({})
    if existing > 0:
        print(f"⚠️  Drones collection already has {existing} document(s). Skipping seed.")
        client.close()
        return

    # Validate each drone through the schema before inserting
    drone_objects = [
        DroneCreate(**{**d, "updated_at": _NOW}) for d in DRONES
    ]
    docs = [d.model_dump() for d in drone_objects]

    result = await db["drones"].insert_many(docs)
    print(f"✅ Seeded {len(result.inserted_ids)} drones into '{db_name}.drones'")

    # Summary table
    print(f"\n{'ID':<10} {'Model':<25} {'Status':<12} {'Battery':>8}  {'Zone'}")
    print("─" * 75)
    for d in DRONES:
        print(
            f"{d['drone_id']:<10} {d['model']:<25} {d['status']:<12} "
            f"{d['battery_pct']:>7.1f}%  {d['zone']}"
        )

    client.close()


if __name__ == "__main__":
    asyncio.run(seed())
