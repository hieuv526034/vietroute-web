-- Bổ sung quyền chức năng và hàng đợi bền vững; V001/V002 giữ nguyên checksum.
ALTER TABLE vietroute.auth_roles
    ADD COLUMN permission_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN permissions_initialized BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_by BIGINT REFERENCES vietroute.auth_users(id);

CREATE TABLE vietroute.auth_permissions (
    code VARCHAR(40) PRIMARY KEY,
    name_vi VARCHAR(160) NOT NULL,
    name_en VARCHAR(160) NOT NULL
);
CREATE TABLE vietroute.auth_role_permissions (
    role_code VARCHAR(24) NOT NULL REFERENCES vietroute.auth_roles(code),
    permission_code VARCHAR(40) NOT NULL REFERENCES vietroute.auth_permissions(code),
    PRIMARY KEY (role_code, permission_code)
);
CREATE INDEX idx_role_permissions_permission ON vietroute.auth_role_permissions(permission_code);
CREATE INDEX idx_roles_updated_by ON vietroute.auth_roles(updated_by);

CREATE TABLE vietroute.system_jobs (
    id UUID PRIMARY KEY,
    type VARCHAR(40) NOT NULL CHECK (type ~ '^[A-Z][A-Z0-9_]{2,39}$'),
    dedup_key VARCHAR(128) NOT NULL UNIQUE,
    payload JSONB NOT NULL CHECK (octet_length(payload::text) <= 65536),
    status VARCHAR(16) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','DEAD')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    max_attempts INTEGER NOT NULL DEFAULT 5 CHECK (max_attempts BETWEEN 1 AND 10),
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until TIMESTAMPTZ,
    lease_token UUID,
    last_error_code VARCHAR(40),
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES vietroute.auth_users(id),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK ((status = 'RUNNING' AND lease_token IS NOT NULL AND locked_until IS NOT NULL)
        OR (status <> 'RUNNING' AND lease_token IS NULL AND locked_until IS NULL))
);
CREATE INDEX idx_jobs_claim ON vietroute.system_jobs(status, available_at, created_at, id);
CREATE INDEX idx_jobs_expired_lease ON vietroute.system_jobs(locked_until) WHERE status = 'RUNNING';
CREATE INDEX idx_jobs_finished ON vietroute.system_jobs(finished_at) WHERE status = 'SUCCEEDED';
CREATE INDEX idx_jobs_created_by ON vietroute.system_jobs(created_by);
