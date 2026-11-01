-- AUTHDB02: User -> Role -> Function -> Menu/API, sáu bảng lõi và hai bảng hỗ trợ.
-- Dừng mọi node cũ trước nâng cấp; không chạy song song code V023 và V024.
ALTER TABLE auth_users ADD COLUMN previous_password_hash_1 VARCHAR(100);
ALTER TABLE auth_users ADD COLUMN previous_password_hash_2 VARCHAR(100);
ALTER TABLE auth_users ADD COLUMN bootstrap_scope_pending BOOLEAN NOT NULL DEFAULT FALSE;
-- Chỉ giữ mốc CAS (tối đa 32 phạm vi/tài khoản), không chứa role/quyền. Gỡ hết role vẫn không tái dùng version 0.
ALTER TABLE auth_users ADD COLUMN scope_membership_versions JSONB NOT NULL DEFAULT '{}';
UPDATE auth_users u SET scope_membership_versions=v.versions FROM (
    SELECT user_id,jsonb_object_agg(scope_id::text,version) AS versions
    FROM auth_scope_memberships GROUP BY user_id
) v WHERE v.user_id=u.id;
WITH history AS (
    SELECT user_id,array_agg(password_hash ORDER BY id DESC) AS hashes
    FROM auth_password_history GROUP BY user_id
)
UPDATE auth_users u SET previous_password_hash_1=h.hashes[1],previous_password_hash_2=h.hashes[2]
FROM history h WHERE h.user_id=u.id;

-- Phạm vi bảo mật thuộc nhà xe, độc lập trạng thái nhận đặt chỗ.
ALTER TABLE carriers ADD COLUMN scope_code VARCHAR(50);
ALTER TABLE carriers ADD COLUMN scope_name_vi VARCHAR(120);
ALTER TABLE carriers ADD COLUMN scope_name_en VARCHAR(120);
ALTER TABLE carriers ADD COLUMN scope_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE carriers ADD COLUMN scope_version BIGINT NOT NULL DEFAULT 0;
UPDATE carriers c SET scope_code=s.code,scope_name_vi=s.name_vi,scope_name_en=s.name_en,
    scope_active=s.active,scope_version=s.version FROM auth_data_scopes s WHERE s.id=c.scope_id;
ALTER TABLE carriers ALTER COLUMN scope_code SET NOT NULL;
ALTER TABLE carriers ALTER COLUMN scope_name_vi SET NOT NULL;
ALTER TABLE carriers ALTER COLUMN scope_name_en SET NOT NULL;
ALTER TABLE carriers ADD CONSTRAINT uq_carrier_scope_code UNIQUE(scope_code);
CREATE SEQUENCE carrier_scope_ids;
SELECT setval('carrier_scope_ids',GREATEST(COALESCE((SELECT max(id) FROM auth_data_scopes),0),1),
    EXISTS(SELECT 1 FROM auth_data_scopes));
ALTER TABLE auth_roles ADD COLUMN functions_initialized BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE auth_roles SET functions_initialized=TRUE;

CREATE TABLE auth_functions (
    code VARCHAR(80) PRIMARY KEY,
    name_vi VARCHAR(500) NOT NULL,
    name_en VARCHAR(500) NOT NULL,
    operation_codes TEXT[] NOT NULL DEFAULT '{}',
    source_managed BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    registry_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    updated_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    CHECK(NOT deleted OR NOT active)
);
CREATE TABLE auth_role_functions (
    role_code VARCHAR(24) NOT NULL REFERENCES auth_roles(code),
    function_code VARCHAR(80) NOT NULL REFERENCES auth_functions(code),
    operation_codes TEXT[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    updated_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    PRIMARY KEY(role_code,function_code)
);
CREATE INDEX idx_role_functions_function ON auth_role_functions(function_code,role_code);
CREATE TABLE auth_function_resources (
    function_code VARCHAR(80) NOT NULL REFERENCES auth_functions(code) ON DELETE CASCADE,
    kind VARCHAR(12) NOT NULL CHECK(kind IN('MENU','ENDPOINT')),
    target VARCHAR(240) NOT NULL,
    handler VARCHAR(240) NOT NULL,
    description VARCHAR(500) NOT NULL,
    resource_group VARCHAR(40) NOT NULL DEFAULT 'GENERAL',
    feature_code VARCHAR(50) NOT NULL DEFAULT '',
    declared_permissions VARCHAR(1024) NOT NULL DEFAULT '',
    permission_match VARCHAR(3) NOT NULL DEFAULT 'ANY' CHECK(permission_match IN('ANY','ALL')),
    admin_only BOOLEAN NOT NULL DEFAULT FALSE,
    public_route BOOLEAN NOT NULL DEFAULT FALSE,
    configurable BOOLEAN NOT NULL DEFAULT FALSE,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    updated_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY(function_code,kind,target)
);
CREATE INDEX idx_function_resources_target ON auth_function_resources(kind,target);

-- Giữ projection thao tác cũ trong function; UI chỉ cấu hình function và target một lần.
INSERT INTO auth_functions(code,name_vi,name_en,operation_codes,active,registry_version,source_managed,
    created_at,updated_at,created_by,updated_by)
SELECT code,name_vi,name_en,ARRAY[code],active,registry_version,TRUE,
    created_at,updated_at,created_by,updated_by FROM auth_permissions;
INSERT INTO auth_role_functions(role_code,function_code,operation_codes,created_at,updated_at,created_by,updated_by)
SELECT role_code,permission_code,ARRAY[permission_code],created_at,updated_at,created_by,updated_by
FROM auth_role_permissions;
INSERT INTO auth_functions(code,name_vi,name_en,operation_codes,source_managed)
SELECT 'HTTP_'||md5(kind||':'||target),description,description,
    array_remove(string_to_array(declared_permissions,','),''),TRUE FROM auth_access_resources;
INSERT INTO auth_function_resources(function_code,kind,target,handler,description,resource_group,feature_code,
    declared_permissions,permission_match,admin_only,public_route,configurable,first_seen_at,last_seen_at,
    created_at,updated_at,created_by,updated_by,version)
SELECT 'HTTP_'||md5(kind||':'||target),kind,target,handler,description,resource_group,COALESCE(function_code,''),
    declared_permissions,permission_match,admin_only,public_route,configurable,first_seen_at,last_seen_at,
    created_at,updated_at,created_by,updated_by,version FROM auth_access_resources;
INSERT INTO auth_role_functions(role_code,function_code,operation_codes,created_at,updated_at,created_by,updated_by)
SELECT rr.role_code,'HTTP_'||md5(rr.kind||':'||rr.target),
    ARRAY(SELECT rp.permission_code FROM auth_role_permissions rp WHERE rp.role_code=rr.role_code
        AND rp.permission_code=ANY(string_to_array(ar.declared_permissions,','))),
    rr.created_at,rr.updated_at,rr.created_by,rr.updated_by
FROM auth_role_resources rr JOIN auth_access_resources ar USING(kind,target) ON CONFLICT DO NOTHING;
-- Thu hồi target từng có policy để bỏ tầng policy không vô tình mở rộng quyền của role thường.
DELETE FROM auth_role_functions rf USING auth_function_resources fr,auth_access_policies p
WHERE rf.function_code=fr.function_code AND rf.role_code<>'ADMIN' AND fr.kind=p.kind AND fr.target=p.target;

-- Gom quyền hiện có của mỗi role thường thành một Function có tên rõ ràng để quản trị tiếp tục quản lý.
-- Tập thao tác và target giữ nguyên theo role, kể cả ALL đang thiếu một phần hoặc tập target rỗng.
INSERT INTO auth_functions(code,name_vi,name_en,operation_codes)
SELECT 'MIGRATED_'||r.code,'Chức năng hiện có — '||r.name_vi,'Existing access — '||r.name_en,
    ARRAY(SELECT rp.permission_code FROM auth_role_permissions rp WHERE rp.role_code=r.code ORDER BY rp.permission_code)
FROM auth_roles r WHERE r.code<>'ADMIN' AND NOT r.deleted;
INSERT INTO auth_function_resources(function_code,kind,target,handler,description,resource_group,feature_code,
    declared_permissions,permission_match,admin_only,public_route,configurable)
SELECT DISTINCT 'MIGRATED_'||rf.role_code,fr.kind,fr.target,fr.handler,fr.description,fr.resource_group,fr.feature_code,
    fr.declared_permissions,fr.permission_match,fr.admin_only,fr.public_route,fr.configurable
FROM auth_role_functions rf JOIN auth_function_resources fr ON fr.function_code=rf.function_code
JOIN auth_roles r ON r.code=rf.role_code AND NOT r.deleted WHERE rf.role_code<>'ADMIN';
DELETE FROM auth_role_functions WHERE role_code<>'ADMIN';
INSERT INTO auth_role_functions(role_code,function_code,operation_codes)
SELECT r.code,f.code,f.operation_codes FROM auth_roles r JOIN auth_functions f ON f.code='MIGRATED_'||r.code
WHERE r.code<>'ADMIN' AND NOT r.deleted;
UPDATE auth_roles SET permission_version=permission_version+1 WHERE code<>'ADMIN' AND NOT deleted;

ALTER TABLE auth_user_roles RENAME TO auth_user_roles_v023;
ALTER INDEX auth_user_roles_pkey RENAME TO auth_user_roles_v023_pkey;
CREATE TABLE auth_user_roles (
    user_id BIGINT NOT NULL REFERENCES auth_users(id) CHECK(user_id<>0),
    role_code VARCHAR(24) NOT NULL REFERENCES auth_roles(code),
    scope_id BIGINT NOT NULL DEFAULT 0 CHECK(scope_id>=0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    updated_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    global_scope_id BIGINT GENERATED ALWAYS AS (CASE WHEN scope_id>0 THEN 0 ELSE NULL END) STORED,
    carrier_scope_id BIGINT GENERATED ALWAYS AS (NULLIF(scope_id,0)) STORED,
    PRIMARY KEY(user_id,role_code,scope_id),
    FOREIGN KEY(user_id,role_code,global_scope_id) REFERENCES auth_user_roles(user_id,role_code,scope_id) ON DELETE CASCADE,
    FOREIGN KEY(carrier_scope_id) REFERENCES carriers(scope_id),
    CHECK(scope_id=0 OR role_code<>'ADMIN')
);
INSERT INTO auth_user_roles(user_id,role_code,created_at,updated_at,created_by,updated_by)
SELECT user_id,role_code,created_at,updated_at,created_by,updated_by FROM auth_user_roles_v023;
INSERT INTO auth_user_roles(user_id,role_code,scope_id,active,version,created_at,updated_at,created_by,updated_by)
SELECT sr.user_id,sr.role_code,sr.scope_id,m.active,m.version,sr.created_at,sr.updated_at,sr.created_by,sr.updated_by
FROM auth_scope_roles sr JOIN auth_scope_memberships m USING(scope_id,user_id)
JOIN carriers c ON c.scope_id=sr.scope_id;
CREATE INDEX idx_user_roles_role ON auth_user_roles(role_code,user_id);
CREATE INDEX idx_user_roles_scope ON auth_user_roles(scope_id,user_id,role_code) WHERE scope_id>0;
DO $$
DECLARE item RECORD;
BEGIN
    FOR item IN SELECT conrelid::regclass AS relation,conname,pg_get_constraintdef(oid) AS definition
        FROM pg_constraint WHERE contype='f' AND confrelid='auth_data_scopes'::regclass
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I',item.relation,item.conname);
        IF item.relation::text NOT IN('auth_scope_memberships','carriers') THEN
            EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s',item.relation,item.conname,
                replace(item.definition,'REFERENCES auth_data_scopes(id)','REFERENCES carriers(scope_id)'));
        END IF;
    END LOOP;
END $$;
DROP TABLE auth_scope_roles;
DROP TABLE auth_scope_memberships;
DROP TABLE auth_data_scopes;
DROP TABLE auth_user_roles_v023;
DROP TABLE auth_role_resources;
DROP TABLE auth_role_resource_sets;
DROP TABLE auth_role_permissions;
DROP TABLE auth_access_resources;
DROP TABLE auth_access_functions;
DROP TABLE auth_permissions;
DROP TABLE auth_access_policies;
DROP TABLE auth_password_history;
