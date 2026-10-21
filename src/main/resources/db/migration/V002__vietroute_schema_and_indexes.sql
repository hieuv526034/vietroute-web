-- Không sửa V001 đã có checksum. Lần nâng cấp này chuyển bảng cũ theo OID, giữ dữ liệu/FK/identity/index.
-- Schema/history đã được chuẩn bị trong cùng transaction có advisory lock trước khi chạy migration.
DO $migration$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['auth_roles', 'auth_users', 'auth_user_roles', 'catalog_items']
    LOOP
        IF to_regclass(format('public.%I', table_name)) IS NOT NULL THEN
            IF to_regclass(format('vietroute.%I', table_name)) IS NOT NULL THEN
                RAISE EXCEPTION 'Duplicate application table in public and vietroute: %', table_name;
            END IF;
            EXECUTE format('ALTER TABLE public.%I SET SCHEMA vietroute', table_name);
        END IF;
    END LOOP;
END
$migration$;

-- PK/UNIQUE và index danh mục của V001 được giữ nguyên, không tạo index trùng cùng cột.
-- Index FK hỗ trợ tra cứu audit và kiểm tra ràng buộc khi cập nhật/xóa tài khoản hoặc vai trò.
CREATE INDEX idx_auth_users_created_by ON vietroute.auth_users(created_by);
CREATE INDEX idx_auth_users_updated_by ON vietroute.auth_users(updated_by);
CREATE INDEX idx_auth_user_roles_role_code ON vietroute.auth_user_roles(role_code);
CREATE INDEX idx_catalog_created_by ON vietroute.catalog_items(created_by);
CREATE INDEX idx_catalog_updated_by ON vietroute.catalog_items(updated_by);
