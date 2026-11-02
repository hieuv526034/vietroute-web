-- Actor kỹ thuật không đăng nhập. Không ghi đè tài khoản hoặc hash đã tồn tại.
INSERT INTO auth_users(id, username, password_hash, display_name, active, must_change_password, created_by, updated_by)
VALUES (0, 'system', '!SYSTEM_DISABLED', 'Hệ thống', FALSE, FALSE, 0, 0)
ON CONFLICT (id) DO NOTHING;
