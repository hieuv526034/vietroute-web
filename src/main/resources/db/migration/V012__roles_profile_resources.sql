-- Tombstone giữ mã role đã xóa để bootstrap không tạo lại hoặc tự cấp quyền trở lại.
ALTER TABLE auth_roles ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE auth_roles ADD CONSTRAINT ck_auth_roles_deleted CHECK (NOT deleted OR NOT active);
ALTER TABLE auth_roles ADD CONSTRAINT ck_auth_roles_admin CHECK (code <> 'ADMIN' OR (active AND NOT deleted));

-- Chỉ liên kết ảnh công khai do MediaService quản lý, không nhận URL/publicId từ trình duyệt.
ALTER TABLE auth_users ADD COLUMN avatar_media_id BIGINT REFERENCES media_assets(id);
CREATE INDEX idx_auth_users_avatar ON auth_users(avatar_media_id);

-- Metadata từ annotation/mapping, tuyệt đối không dùng để nới guard hoặc permitAll.
ALTER TABLE auth_access_resources ADD COLUMN resource_group VARCHAR(40) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE auth_access_resources ADD COLUMN declared_permissions VARCHAR(1024) NOT NULL DEFAULT '';
