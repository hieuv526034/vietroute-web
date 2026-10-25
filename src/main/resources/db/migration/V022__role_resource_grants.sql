-- Quyền được chọn theo tài nguyên HTTP cụ thể. Tập rỗng là từ chối, không kế thừa API mới.
CREATE TABLE auth_role_resource_sets (
    role_code VARCHAR(24) PRIMARY KEY REFERENCES auth_roles(code),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    updated_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_auth_role_resource_sets_created_by ON auth_role_resource_sets(created_by);
CREATE INDEX idx_auth_role_resource_sets_updated_by ON auth_role_resource_sets(updated_by);

CREATE TABLE auth_role_resources (
    role_code VARCHAR(24) NOT NULL REFERENCES auth_role_resource_sets(role_code) ON DELETE CASCADE,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('MENU', 'ENDPOINT')),
    target VARCHAR(240) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    updated_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (role_code, kind, target),
    FOREIGN KEY (kind, target) REFERENCES auth_access_resources(kind, target)
);
CREATE INDEX idx_auth_role_resources_resource ON auth_role_resources(kind, target);
CREATE INDEX idx_auth_role_resources_created_by ON auth_role_resources(created_by);
CREATE INDEX idx_auth_role_resources_updated_by ON auth_role_resources(updated_by);

-- Nâng cấp một lần từ registry đã có; giữ nguyên projection quyền và điều kiện ANY/ALL bắt buộc.
-- DB mới chưa có inventory được chuyển khi ghi mapping qua API tương thích lần đầu.
INSERT INTO auth_role_resource_sets(role_code)
SELECT code FROM auth_roles WHERE NOT deleted
AND EXISTS(SELECT 1 FROM auth_access_resources WHERE NOT public_route AND declared_permissions <> '');
INSERT INTO auth_role_resources(role_code,kind,target)
SELECT DISTINCT rs.role_code,ar.kind,ar.target
FROM auth_role_resource_sets rs CROSS JOIN auth_access_resources ar
WHERE NOT ar.public_route AND ar.declared_permissions <> ''
AND EXISTS(SELECT 1 FROM auth_role_permissions rp WHERE rp.role_code=rs.role_code
    AND rp.permission_code=ANY(string_to_array(ar.declared_permissions,',')));

ALTER TABLE auth_users ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE auth_users ADD CONSTRAINT ck_auth_users_deleted CHECK (NOT deleted OR (id <> 0 AND NOT active));
