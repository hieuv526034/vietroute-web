-- PERF03: chỉ thêm index, không đổi dữ liệu/quyền. Startup giữ transaction và advisory lock hiện hành.
-- Khoảng ngày báo cáo không có status; index cũ (carrier_id,status,departure_at) không thay hai đường này.
CREATE INDEX idx_trips_report_departure ON carrier_trips(departure_at,carrier_id,id) INCLUDE(scope_id,status);
CREATE INDEX idx_trips_report_carrier_date ON carrier_trips(carrier_id,departure_at,id) INCLUDE(scope_id,status);

-- pg_trgm có thể đã được chủ DB cài ở schema khác. Dùng namespace thực của opclass, không đổi search_path
-- hoặc di chuyển extension dùng chung. DB mới cài vào schema ứng dụng hiện hành; cần quyền CREATE database.
DO $perf03$
DECLARE
    application_schema NAME := current_schema();
    extension_schema NAME;
BEGIN
    SELECT n.nspname INTO extension_schema
    FROM pg_catalog.pg_extension e JOIN pg_catalog.pg_namespace n ON n.oid=e.extnamespace
    WHERE e.extname='pg_trgm';
    IF extension_schema IS NULL THEN
        EXECUTE format('CREATE EXTENSION pg_trgm WITH SCHEMA %I',application_schema);
        extension_schema := application_schema;
    END IF;
    -- Các trường NOT NULL: nối text giữ đúng concat_ws cũ nhưng là biểu thức immutable để đánh index.
    EXECUTE format('CREATE INDEX idx_carriers_search_contact ON %I.carriers USING gin ((lower(code || '' '' || name || '' '' || phone)) %I.gin_trgm_ops)',application_schema,extension_schema);
    EXECUTE format('CREATE INDEX idx_carriers_search_name ON %I.carriers USING gin ((lower(code || '' '' || name)) %I.gin_trgm_ops)',application_schema,extension_schema);
    EXECUTE format('CREATE INDEX idx_catalog_search_text ON %I.catalog_items USING gin ((lower(code || '' '' || name_vi || '' '' || name_en)) %I.gin_trgm_ops)',application_schema,extension_schema);
END
$perf03$;
-- Mẫu mặc định 100 giá trị dễ ước lượng sai từ khóa hiếm và chọn index ORDER BY rồi lọc gần hết bảng.
ALTER INDEX idx_carriers_search_contact ALTER COLUMN 1 SET STATISTICS 1000;
ALTER INDEX idx_carriers_search_name ALTER COLUMN 1 SET STATISTICS 1000;
ALTER INDEX idx_catalog_search_text ALTER COLUMN 1 SET STATISTICS 1000;
-- Index biểu thức cần thống kê ngay trên dữ liệu đang có, tránh chờ chu kỳ auto-analyze sau triển khai.
ANALYZE carriers;
ANALYZE catalog_items;
ANALYZE carrier_trips;
