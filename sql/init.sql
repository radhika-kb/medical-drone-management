-- ==============================================================================
-- Intelligent Medical Drone Delivery System: Bengaluru 4-Zone + Central Hub Architecture
-- PostgreSQL 17 Database Schema & Initial Seed Data
-- ==============================================================================

-- 1. Roles & Users
CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(30) DEFAULT 'ACTIVE' NOT NULL,
    role_id BIGINT REFERENCES roles(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Central Hub & 4 Bengaluru Zones
CREATE TABLE IF NOT EXISTS central_hubs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    status VARCHAR(50) DEFAULT 'OPERATIONAL' NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS drone_zones (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    code VARCHAR(50) NOT NULL UNIQUE,
    hub_id BIGINT REFERENCES central_hubs(id),
    center_lat DOUBLE PRECISION NOT NULL,
    center_lng DOUBLE PRECISION NOT NULL,
    radius_km DOUBLE PRECISION NOT NULL,
    description TEXT,
    active BOOLEAN DEFAULT TRUE NOT NULL
);

-- 3. Medical Centers & Requests
CREATE TABLE IF NOT EXISTS medical_centers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    type VARCHAR(50) DEFAULT 'HOSPITAL' NOT NULL,
    address TEXT NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    zone_id BIGINT REFERENCES drone_zones(id),
    contact_number VARCHAR(50),
    status VARCHAR(50) DEFAULT 'ACTIVE' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Drone Fleet (with Optimistic Lock Version)
CREATE TABLE IF NOT EXISTS drones (
    id BIGSERIAL PRIMARY KEY,
    drone_code VARCHAR(50) NOT NULL UNIQUE,
    model VARCHAR(100) NOT NULL,
    zone_id BIGINT REFERENCES drone_zones(id),
    battery_pct DOUBLE PRECISION NOT NULL DEFAULT 100.0,
    max_payload_kg DOUBLE PRECISION NOT NULL DEFAULT 5.0,
    max_range_km DOUBLE PRECISION NOT NULL DEFAULT 45.0,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    version BIGINT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS medical_requests (
    id BIGSERIAL PRIMARY KEY,
    request_code VARCHAR(50) NOT NULL UNIQUE,
    medical_type VARCHAR(50) NOT NULL,
    urgency VARCHAR(30) NOT NULL,
    weight_kg DOUBLE PRECISION NOT NULL,
    quantity INTEGER DEFAULT 1 NOT NULL,
    center_id BIGINT NOT NULL REFERENCES medical_centers(id),
    pickup_lat DOUBLE PRECISION NOT NULL,
    pickup_lng DOUBLE PRECISION NOT NULL,
    dest_lat DOUBLE PRECISION NOT NULL,
    dest_lng DOUBLE PRECISION NOT NULL,
    zone_id BIGINT REFERENCES drone_zones(id),
    status VARCHAR(50) DEFAULT 'PENDING' NOT NULL,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. Missions & Routes
CREATE TABLE IF NOT EXISTS missions (
    id BIGSERIAL PRIMARY KEY,
    mission_code VARCHAR(50) NOT NULL UNIQUE,
    request_id BIGINT NOT NULL UNIQUE REFERENCES medical_requests(id),
    drone_id BIGINT NOT NULL REFERENCES drones(id),
    status VARCHAR(50) DEFAULT 'DISPATCHED' NOT NULL,
    distance_km DOUBLE PRECISION,
    eta_minutes DOUBLE PRECISION,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS routes (
    id BIGSERIAL PRIMARY KEY,
    mission_id BIGINT NOT NULL UNIQUE REFERENCES missions(id) ON DELETE CASCADE,
    algorithm VARCHAR(50) NOT NULL DEFAULT 'A*-Constrained',
    total_distance_km DOUBLE PRECISION NOT NULL,
    eta_minutes DOUBLE PRECISION NOT NULL,
    battery_required_pct DOUBLE PRECISION NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL
);

CREATE TABLE IF NOT EXISTS route_waypoints (
    id BIGSERIAL PRIMARY KEY,
    route_id BIGINT NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    altitude_meters DOUBLE PRECISION DEFAULT 60.0,
    type VARCHAR(50) DEFAULT 'WAYPOINT'
);

CREATE TABLE IF NOT EXISTS no_fly_zones (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    type VARCHAR(50) DEFAULT 'AIRPORT' NOT NULL,
    center_lat DOUBLE PRECISION NOT NULL,
    center_lng DOUBLE PRECISION NOT NULL,
    radius_km DOUBLE PRECISION NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL
);

-- 6. Telemetry Tracking, AI Decisions, Weather, Notifications, Audit
CREATE TABLE IF NOT EXISTS mission_tracking (
    id BIGSERIAL PRIMARY KEY,
    mission_id BIGINT NOT NULL REFERENCES missions(id) ON DELETE CASCADE,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    battery_pct DOUBLE PRECISION NOT NULL,
    eta_minutes DOUBLE PRECISION,
    status VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_decisions (
    id BIGSERIAL PRIMARY KEY,
    mission_id BIGINT REFERENCES missions(id),
    request_id BIGINT REFERENCES medical_requests(id),
    agent_name VARCHAR(50) NOT NULL,
    decision VARCHAR(100) NOT NULL,
    score DOUBLE PRECISION,
    reason TEXT,
    input_snapshot TEXT,
    output_snapshot TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS weather_observations (
    id BIGSERIAL PRIMARY KEY,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    observed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    wind_speed_kmh DOUBLE PRECISION,
    visibility_km DOUBLE PRECISION,
    rain_mm DOUBLE PRECISION,
    temperature_c DOUBLE PRECISION,
    condition VARCHAR(100),
    flight_condition VARCHAR(50) DEFAULT 'SAFE' NOT NULL
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    mission_id BIGINT REFERENCES missions(id),
    type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT REFERENCES users(id),
    actor_email VARCHAR(150),
    action VARCHAR(100) NOT NULL,
    entity_name VARCHAR(100),
    entity_id VARCHAR(100),
    metadata TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_requests_status ON medical_requests(status);
CREATE INDEX IF NOT EXISTS idx_drones_zone_status ON drones(zone_id, status);
CREATE INDEX IF NOT EXISTS idx_missions_status ON missions(status);
CREATE INDEX IF NOT EXISTS idx_tracking_mission ON mission_tracking(mission_id);
CREATE INDEX IF NOT EXISTS idx_agent_decisions_req ON agent_decisions(request_id);
