-- Ảnh công khai trên Cloudinary; DB chỉ giữ metadata và trạng thái phục hồi, không giữ file nhị phân.
CREATE TABLE media_assets (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(200) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'READY', 'DELETING', 'DELETED')),
    url VARCHAR(600),
    thumbnail_url VARCHAR(700),
    format VARCHAR(8) CHECK (format IN ('jpg', 'png')),
    bytes BIGINT CHECK (bytes > 0 AND bytes <= 5242880),
    width INTEGER CHECK (width BETWEEN 1 AND 4096),
    height INTEGER CHECK (height BETWEEN 1 AND 4096),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL REFERENCES auth_users(id),
    updated_by BIGINT NOT NULL REFERENCES auth_users(id),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (status <> 'READY' OR (url IS NOT NULL AND thumbnail_url IS NOT NULL
        AND format IS NOT NULL AND bytes IS NOT NULL AND width IS NOT NULL AND height IS NOT NULL))
);
CREATE INDEX idx_media_owner_created ON media_assets(created_by, created_at, id);
CREATE INDEX idx_media_status_created ON media_assets(status, created_at, id);
CREATE INDEX idx_media_updated_by ON media_assets(updated_by);
