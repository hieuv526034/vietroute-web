-- Định danh kỹ thuật cố định, không tiêu thụ sequence ID của tài khoản người dùng.
-- Nếu ID/tên đã được sử dụng thì dừng migration; không chiếm hoặc đổi mật khẩu tài khoản cũ.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM auth_users WHERE id = 0 OR username = 'system') THEN
        RAISE EXCEPTION 'Reserved system identity conflicts with an existing account';
    END IF;
END $$;

INSERT INTO auth_users(id, username, password_hash, display_name, active, must_change_password, created_by, updated_by)
VALUES (0, 'system', '!SYSTEM_DISABLED', 'Hệ thống', FALSE, FALSE, 0, 0);

-- system chỉ là actor audit: không có mật khẩu đăng nhập và không thể nhận role.
ALTER TABLE auth_users ADD CONSTRAINT chk_auth_users_system_identity CHECK (
    (id <> 0 AND username <> 'system') OR
    (id = 0 AND username = 'system' AND NOT active AND NOT must_change_password AND password_hash = '!SYSTEM_DISABLED')
);
ALTER TABLE auth_user_roles ADD CONSTRAINT chk_user_roles_no_system CHECK (user_id <> 0);

-- Các registry/mapping trước đây chưa có audit: thời điểm dưới đây là lúc nâng cấp,
-- không suy diễn thành thời điểm tạo dữ liệu lịch sử.
ALTER TABLE auth_roles
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN created_by BIGINT REFERENCES auth_users(id);
ALTER TABLE auth_permissions
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN created_by BIGINT REFERENCES auth_users(id),
    ADD COLUMN updated_by BIGINT REFERENCES auth_users(id);
ALTER TABLE auth_user_roles
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN created_by BIGINT REFERENCES auth_users(id),
    ADD COLUMN updated_by BIGINT REFERENCES auth_users(id);
ALTER TABLE auth_role_permissions
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN created_by BIGINT REFERENCES auth_users(id),
    ADD COLUMN updated_by BIGINT REFERENCES auth_users(id);
ALTER TABLE system_jobs ADD COLUMN updated_by BIGINT REFERENCES auth_users(id);
-- Nhật ký chỉ ghi actor kỹ thuật, không bổ sung danh tính người gửi request vào projection.
ALTER TABLE api_request_journal
    ADD COLUMN created_by BIGINT REFERENCES auth_users(id),
    ADD COLUMN updated_by BIGINT REFERENCES auth_users(id);

-- Chỉ lấp NULL, không ghi đè actor đã biết, timestamps, version hoặc mật khẩu người dùng.
-- Tên bảng là whitelist nội bộ; identifier được quote, schema lấy từ search_path của initializer.
DO $$
DECLARE
    audit_table TEXT;
BEGIN
    FOREACH audit_table IN ARRAY ARRAY[
        'auth_users', 'auth_roles', 'auth_permissions', 'auth_user_roles',
        'auth_role_permissions', 'catalog_items', 'system_jobs', 'media_assets', 'api_request_journal'
    ] LOOP
        EXECUTE format('UPDATE %I SET created_by = COALESCE(created_by, 0), updated_by = COALESCE(updated_by, 0) WHERE created_by IS NULL OR updated_by IS NULL', audit_table);
        EXECUTE format('ALTER TABLE %I ALTER COLUMN created_by SET DEFAULT 0, ALTER COLUMN created_by SET NOT NULL, ALTER COLUMN updated_by SET DEFAULT 0, ALTER COLUMN updated_by SET NOT NULL', audit_table);
    END LOOP;
END $$;

CREATE INDEX idx_roles_created_by ON auth_roles(created_by);
CREATE INDEX idx_permissions_created_by ON auth_permissions(created_by);
CREATE INDEX idx_permissions_updated_by ON auth_permissions(updated_by);
CREATE INDEX idx_user_roles_created_by ON auth_user_roles(created_by);
CREATE INDEX idx_user_roles_updated_by ON auth_user_roles(updated_by);
CREATE INDEX idx_role_permissions_created_by ON auth_role_permissions(created_by);
CREATE INDEX idx_role_permissions_updated_by ON auth_role_permissions(updated_by);
CREATE INDEX idx_jobs_updated_by ON system_jobs(updated_by);
CREATE INDEX idx_api_journal_created_by ON api_request_journal(created_by);
CREATE INDEX idx_api_journal_updated_by ON api_request_journal(updated_by);
