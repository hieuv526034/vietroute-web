-- Lịch chạy hằng ngày dùng một chuyến đã công bố làm snapshot nguồn.
ALTER TABLE carrier_trips ADD COLUMN daily_service BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE carrier_trips ADD COLUMN service_source_id BIGINT;
ALTER TABLE carrier_trips ADD CONSTRAINT carrier_trips_service_source_fk
    FOREIGN KEY (service_source_id, carrier_id, scope_id) REFERENCES carrier_trips(id, carrier_id, scope_id);
ALTER TABLE carrier_trips ADD CONSTRAINT carrier_trips_daily_service_check
    CHECK (NOT daily_service OR (service_source_id IS NULL AND end_at - departure_at < INTERVAL '1 day'));
CREATE UNIQUE INDEX idx_trips_service_departure ON carrier_trips(service_source_id, departure_at);
CREATE INDEX idx_trips_daily_service ON carrier_trips(id) WHERE daily_service;

-- Chuyển đúng bốn khung giờ giả lập Duy Long đã có sang lịch lặp.
WITH slots(route_code, slot, hour_at) AS (
    VALUES ('DLGHPHNSANG', '25', TIME '05:00'), ('DLGHNHPSANG', '26', TIME '09:30'),
           ('DLGHPHNCHIEU', '27', TIME '12:00'), ('DLGHNHPCHIEU', '28', TIME '15:30')
), sources AS (
    SELECT DISTINCT ON (r.id) t.id
    FROM slots s JOIN carrier_routes r ON r.code = s.route_code
    JOIN carriers c ON c.id = r.carrier_id AND c.code = 'DUYLONG'
    JOIN carrier_trips t ON t.route_id = r.id AND t.carrier_id = c.id
    WHERE t.code LIKE 'DLG1R' || s.slot || 'D________'
        AND (t.departure_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::TIME = s.hour_at
        AND t.status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED') AND t.end_at - t.departure_at < INTERVAL '1 day'
        AND EXISTS (SELECT 1 FROM trip_events e WHERE e.trip_id = t.id AND e.event_type = 'OPENED')
    ORDER BY r.id, t.departure_at, t.id
)
UPDATE carrier_trips t
SET daily_service = TRUE, updated_by = 0, updated_at = CURRENT_TIMESTAMP, version = version + 1
FROM sources s WHERE t.id = s.id;
