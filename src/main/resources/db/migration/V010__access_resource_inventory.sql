-- Danh bạ UI/API do server thu thập. Chính sách và mapping role tách riêng, không bị startup ghi đè.
CREATE TABLE auth_access_resources (
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('MENU', 'ENDPOINT')),
    target VARCHAR(240) NOT NULL,
    handler VARCHAR(240) NOT NULL,
    description VARCHAR(500) NOT NULL,
    public_route BOOLEAN NOT NULL,
    configurable BOOLEAN NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    updated_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_auth_access_resources PRIMARY KEY (kind, target),
    CONSTRAINT ck_auth_access_resources_public CHECK (NOT public_route OR NOT configurable)
);
CREATE INDEX idx_auth_access_resources_created_by ON auth_access_resources(created_by);
CREATE INDEX idx_auth_access_resources_updated_by ON auth_access_resources(updated_by);
