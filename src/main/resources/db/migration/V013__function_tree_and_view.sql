-- VIEW tách quyền vào trang khỏi READ/ghi API. Chỉ chuyển tiếp mapping cũ một lần trong migration.
INSERT INTO auth_permissions(code,name_vi,name_en) VALUES
 ('CATALOG_VIEW','Vào màn hình danh mục','Open catalog workspace'),
 ('MEDIA_VIEW','Vào thư viện ảnh','Open media library'),
 ('ACCESS_VIEW','Vào quản trị phân quyền','Open access workspace'),
 ('JOB_VIEW','Vào quản lý công việc','Open jobs workspace'),
 ('SYSTEM_VIEW','Vào giám sát hệ thống','Open monitoring workspace'),
 ('CONFIGURATION_VIEW','Vào cấu hình hệ thống','Open configuration workspace'),
 ('JOURNAL_VIEW','Vào nhật ký API','Open API journal'),
 ('CONFIGURATION_READ','Đọc cấu hình hệ thống','Read system configuration')
ON CONFLICT(code) DO NOTHING;

INSERT INTO auth_role_permissions(role_code,permission_code,created_by,updated_by)
SELECT DISTINCT role_code,
 CASE
  WHEN left(permission_code,8)='CATALOG_' THEN 'CATALOG_VIEW'
  WHEN left(permission_code,6)='MEDIA_' THEN 'MEDIA_VIEW'
  WHEN left(permission_code,7)='ACCESS_' THEN 'ACCESS_VIEW'
  WHEN permission_code='JOB_READ' THEN 'JOB_VIEW'
  WHEN permission_code='SYSTEM_READ' THEN 'SYSTEM_VIEW'
 END,0,0
FROM auth_role_permissions
WHERE left(permission_code,8)='CATALOG_' OR left(permission_code,6)='MEDIA_' OR left(permission_code,7)='ACCESS_'
 OR permission_code IN ('JOB_READ','SYSTEM_READ')
ON CONFLICT DO NOTHING;

INSERT INTO auth_role_permissions(role_code,permission_code,created_by,updated_by)
SELECT role_code,view_code,0,0 FROM auth_role_permissions
CROSS JOIN (VALUES ('CONFIGURATION_VIEW'),('JOURNAL_VIEW')) AS views(view_code)
WHERE permission_code='SYSTEM_READ'
ON CONFLICT DO NOTHING;

-- Chuyển tiếp quyền đọc cấu hình cũ, sau nâng cấp SYSTEM_READ chỉ còn giám sát và các luồng nhạy cảm có ADMIN.
INSERT INTO auth_role_permissions(role_code,permission_code,created_by,updated_by)
SELECT role_code,'CONFIGURATION_READ',0,0 FROM auth_role_permissions WHERE permission_code='SYSTEM_READ'
ON CONFLICT DO NOTHING;

-- Đổi mapping là một thay đổi thật: stale form của client cũ phải đọc lại version trước khi lưu.
UPDATE auth_roles SET permission_version=permission_version+1,updated_at=CURRENT_TIMESTAMP,updated_by=0
WHERE EXISTS (SELECT 1 FROM auth_role_permissions rp WHERE rp.role_code=auth_roles.code AND rp.permission_code LIKE '%_VIEW');

CREATE TABLE auth_access_functions (
 code VARCHAR(40) PRIMARY KEY,
 parent_code VARCHAR(40) REFERENCES auth_access_functions(code),
 name_key VARCHAR(120) NOT NULL,
 menu_path VARCHAR(300),
 view_permission VARCHAR(40) REFERENCES auth_permissions(code),
 sort_order INTEGER NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 created_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
 updated_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
 version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_access_functions_parent ON auth_access_functions(parent_code);
CREATE INDEX idx_access_functions_created_by ON auth_access_functions(created_by);
CREATE INDEX idx_access_functions_updated_by ON auth_access_functions(updated_by);
CREATE INDEX idx_access_functions_view ON auth_access_functions(view_permission);

ALTER TABLE auth_access_resources ADD COLUMN function_code VARCHAR(40) REFERENCES auth_access_functions(code);
ALTER TABLE auth_access_resources ADD COLUMN permission_match VARCHAR(3) NOT NULL DEFAULT 'ANY' CHECK(permission_match IN ('ANY','ALL'));
ALTER TABLE auth_access_resources ADD COLUMN admin_only BOOLEAN NOT NULL DEFAULT false;
CREATE INDEX idx_access_resources_function ON auth_access_resources(function_code);
