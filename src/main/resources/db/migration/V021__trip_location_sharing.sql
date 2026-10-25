CREATE TABLE trip_tracking (
    trip_id BIGINT PRIMARY KEY,
    carrier_id BIGINT NOT NULL,
    scope_id BIGINT NOT NULL,
    driver_user_id BIGINT NOT NULL REFERENCES auth_users(id),
    session_key UUID NOT NULL,
    sharing BOOLEAN NOT NULL DEFAULT FALSE,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    latitude NUMERIC(9,6),
    longitude NUMERIC(9,6),
    accuracy NUMERIC(9,2),
    measured_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    updated_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (trip_id,carrier_id,scope_id) REFERENCES carrier_trips(id,carrier_id,scope_id),
    CHECK (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180 AND accuracy BETWEEN 0 AND 100000),
    CHECK ((latitude IS NULL AND longitude IS NULL AND accuracy IS NULL AND measured_at IS NULL AND received_at IS NULL)
        OR (latitude IS NOT NULL AND longitude IS NOT NULL AND accuracy IS NOT NULL AND measured_at IS NOT NULL AND received_at IS NOT NULL))
);
CREATE INDEX idx_trip_tracking_driver ON trip_tracking(driver_user_id);
CREATE INDEX idx_trip_tracking_created_by ON trip_tracking(created_by);
CREATE INDEX idx_trip_tracking_updated_by ON trip_tracking(updated_by);

CREATE TABLE trip_locations (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    carrier_id BIGINT NOT NULL,
    scope_id BIGINT NOT NULL,
    driver_user_id BIGINT NOT NULL REFERENCES auth_users(id),
    session_key UUID NOT NULL,
    source VARCHAR(20) NOT NULL CHECK (source='BROWSER'),
    latitude NUMERIC(9,6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(9,6) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    accuracy NUMERIC(9,2) NOT NULL CHECK (accuracy BETWEEN 0 AND 100000),
    measured_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    updated_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (trip_id,carrier_id,scope_id) REFERENCES carrier_trips(id,carrier_id,scope_id)
);
CREATE INDEX idx_trip_locations_trip ON trip_locations(trip_id,id);
CREATE INDEX idx_trip_locations_received ON trip_locations(received_at,id);
CREATE INDEX idx_trip_locations_driver ON trip_locations(driver_user_id);
CREATE INDEX idx_trip_locations_created_by ON trip_locations(created_by);
CREATE INDEX idx_trip_locations_updated_by ON trip_locations(updated_by);
