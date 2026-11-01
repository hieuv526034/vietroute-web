-- AUTHDB03: bỏ projection action; quyền riêng tư chỉ qua User -> Role -> Function -> endpoint.
-- Dừng toàn bộ node V024 trước nâng cấp. Danh bạ endpoint do Spring MVC thu thập, không cần hàng HTTP_ trong DB.
ALTER TABLE auth_roles ADD COLUMN all_scopes BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE auth_roles ADD COLUMN protected_role BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE auth_roles ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
UPDATE auth_roles SET version=GREATEST(registry_version,permission_version)+1;

ALTER TABLE auth_functions RENAME COLUMN registry_version TO version;
ALTER TABLE auth_functions ADD COLUMN description VARCHAR(1000) NOT NULL DEFAULT '';
ALTER TABLE auth_functions ADD COLUMN parent_code VARCHAR(80) REFERENCES auth_functions(code);
ALTER TABLE auth_functions ADD COLUMN icon VARCHAR(50) NOT NULL DEFAULT 'menu';
ALTER TABLE auth_functions ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE auth_functions ADD COLUMN resources_initialized BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE auth_function_resources ADD COLUMN http_method VARCHAR(10);
ALTER TABLE auth_function_resources ADD COLUMN path_template VARCHAR(240);
ALTER TABLE auth_function_resources ADD COLUMN name_vi VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE auth_function_resources ADD COLUMN name_en VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE auth_function_resources ADD COLUMN icon VARCHAR(50) NOT NULL DEFAULT 'menu';
ALTER TABLE auth_function_resources ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;
UPDATE auth_function_resources SET http_method=split_part(target,' ',1),
    path_template=substring(target FROM position(' ' IN target)+1),name_vi=description,name_en=description;
ALTER TABLE auth_function_resources ALTER COLUMN http_method SET NOT NULL;
ALTER TABLE auth_function_resources ALTER COLUMN path_template SET NOT NULL;

-- Function dùng chung có target trước đây bị guard bổ sung từ chối cho một role:
-- thu hồi target ấy trước khi bỏ guard để chuyển schema không vô tình mở rộng quyền.
WITH role_operations AS (
    SELECT rf.role_code,array_agg(DISTINCT op.code) AS codes
    FROM auth_role_functions rf JOIN auth_functions f ON f.code=rf.function_code AND f.active AND NOT f.deleted
    CROSS JOIN LATERAL unnest(rf.operation_codes) op(code)
    JOIN auth_functions p ON p.code=op.code AND p.active AND NOT p.deleted GROUP BY rf.role_code
)
DELETE FROM auth_function_resources fr USING auth_functions f
WHERE fr.function_code=f.code AND NOT f.source_managed
AND EXISTS(SELECT 1 FROM auth_role_functions rf LEFT JOIN role_operations ro ON ro.role_code=rf.role_code
    WHERE rf.function_code=f.code AND (
        fr.admin_only AND rf.role_code<>'ADMIN'
        OR fr.declared_permissions<>'' AND (
            fr.permission_match='ANY' AND NOT(string_to_array(fr.declared_permissions,',') && COALESCE(ro.codes,'{}'))
            OR fr.permission_match='ALL' AND NOT(string_to_array(fr.declared_permissions,',') <@ COALESCE(ro.codes,'{}'))
        )
    ));

-- Gom những grant HTTP_ đang có thành chức năng dữ liệu theo role; không giữ registry quyền Java nữa.
INSERT INTO auth_functions(code,name_vi,name_en,description,operation_codes,source_managed)
SELECT 'MIGRATED_ENDPOINTS_'||r.code,'Menu/API hiện có — '||r.name_vi,'Existing endpoints — '||r.name_en,
    'Chuyển từ grant HTTP trước AUTHDB03','{}',FALSE
FROM auth_roles r WHERE NOT r.deleted AND EXISTS(
    SELECT 1 FROM auth_role_functions rf JOIN auth_functions f ON f.code=rf.function_code AND f.source_managed
    JOIN auth_function_resources fr ON fr.function_code=f.code WHERE rf.role_code=r.code AND NOT fr.public_route
);
WITH role_operations AS (
    SELECT rf.role_code,array_agg(DISTINCT op.code) AS codes
    FROM auth_role_functions rf JOIN auth_functions f ON f.code=rf.function_code AND f.active AND NOT f.deleted
    CROSS JOIN LATERAL unnest(rf.operation_codes) op(code)
    JOIN auth_functions p ON p.code=op.code AND p.active AND NOT p.deleted GROUP BY rf.role_code
)
INSERT INTO auth_function_resources(function_code,kind,target,handler,description,http_method,path_template,name_vi,name_en,icon,
    created_at,updated_at,created_by,updated_by)
SELECT DISTINCT 'MIGRATED_ENDPOINTS_'||rf.role_code,fr.kind,fr.target,fr.handler,fr.description,
    fr.http_method,fr.path_template,fr.name_vi,fr.name_en,fr.icon,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,0
FROM auth_role_functions rf JOIN auth_functions f ON f.code=rf.function_code AND f.source_managed AND f.active AND NOT f.deleted
JOIN auth_function_resources fr ON fr.function_code=f.code
JOIN auth_functions target_function ON target_function.code='MIGRATED_ENDPOINTS_'||rf.role_code
LEFT JOIN role_operations ro ON ro.role_code=rf.role_code
WHERE NOT fr.public_route AND fr.declared_permissions<>'' AND (NOT fr.admin_only OR rf.role_code='ADMIN')
AND ((fr.permission_match='ANY' AND string_to_array(fr.declared_permissions,',') && COALESCE(ro.codes,'{}'))
    OR (fr.permission_match='ALL' AND string_to_array(fr.declared_permissions,',') <@ COALESCE(ro.codes,'{}')));
INSERT INTO auth_role_functions(role_code,function_code,operation_codes)
SELECT r.code,f.code,'{}' FROM auth_roles r JOIN auth_functions f ON f.code='MIGRATED_ENDPOINTS_'||r.code;

DELETE FROM auth_role_functions rf USING auth_functions f
WHERE rf.function_code=f.code AND (f.source_managed OR f.code=ANY(f.operation_codes));
DELETE FROM auth_functions WHERE source_managed OR code=ANY(operation_codes);
ALTER TABLE auth_role_functions DROP COLUMN operation_codes;
ALTER TABLE auth_role_functions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE auth_functions DROP COLUMN operation_codes;
ALTER TABLE auth_functions DROP COLUMN source_managed;
ALTER TABLE auth_functions ALTER COLUMN resources_initialized SET DEFAULT FALSE;
ALTER TABLE auth_roles DROP COLUMN registry_version;
ALTER TABLE auth_roles DROP COLUMN permission_version;
ALTER TABLE auth_roles DROP COLUMN permissions_initialized;
ALTER TABLE auth_function_resources DROP COLUMN resource_group;
ALTER TABLE auth_function_resources DROP COLUMN feature_code;
ALTER TABLE auth_function_resources DROP COLUMN declared_permissions;
ALTER TABLE auth_function_resources DROP COLUMN permission_match;
ALTER TABLE auth_function_resources DROP COLUMN admin_only;
ALTER TABLE auth_function_resources DROP COLUMN public_route;
ALTER TABLE auth_function_resources DROP COLUMN configurable;
ALTER TABLE auth_function_resources DROP COLUMN first_seen_at;
ALTER TABLE auth_function_resources DROP COLUMN last_seen_at;
CREATE INDEX idx_function_resources_path ON auth_function_resources(http_method,path_template,function_code);
-- Đổi mô hình quyền yêu cầu đăng nhập lại; giữ hash/history/khóa/email, không nhận token theo mô hình cũ.
UPDATE auth_users SET token_version=token_version+1,version=version+1,updated_at=CURRENT_TIMESTAMP,updated_by=0 WHERE id>0;

-- Role API không còn biến kind hoặc registry permission; giữ quyền chính xác khi đổi template URL.
UPDATE auth_function_resources SET target=replace(target,'/registry/{kind}','/registry/roles'),
    path_template=replace(path_template,'/registry/{kind}','/registry/roles')
WHERE target LIKE '%/registry/{kind}%';
