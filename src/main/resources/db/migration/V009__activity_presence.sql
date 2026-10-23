-- Hiện diện và bộ đếm chia sẻ giữa node, không lưu địa chỉ IP, token hay payload.
CREATE TABLE system_visitor_presence (
    visitor_hash CHAR(64) PRIMARY KEY,
    user_id BIGINT REFERENCES auth_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    updated_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    first_seen TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_presence_last_seen ON system_visitor_presence(last_seen);
CREATE INDEX idx_presence_user_seen ON system_visitor_presence(user_id,last_seen);
CREATE TABLE system_activity_buckets (
    subject_hash CHAR(64) NOT NULL,
    bucket_at TIMESTAMPTZ NOT NULL,
    user_id BIGINT REFERENCES auth_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    updated_by BIGINT NOT NULL DEFAULT 0 REFERENCES auth_users(id),
    requests BIGINT NOT NULL DEFAULT 1,
    denied BIGINT NOT NULL DEFAULT 0,
    limited BIGINT NOT NULL DEFAULT 0,
    failures BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY(subject_hash,bucket_at)
);
CREATE INDEX idx_activity_bucket_at ON system_activity_buckets(bucket_at);
CREATE INDEX idx_activity_user_bucket ON system_activity_buckets(user_id,bucket_at);

CREATE INDEX idx_presence_created_by ON system_visitor_presence(created_by);
CREATE INDEX idx_presence_updated_by ON system_visitor_presence(updated_by);
CREATE INDEX idx_activity_created_by ON system_activity_buckets(created_by);
CREATE INDEX idx_activity_updated_by ON system_activity_buckets(updated_by);

-- Một lần gọi mạng, nhưng hai lệnh DB riêng trong hàm VOLATILE: sau khi lấy khóa,
-- statement ghi đọc snapshot mới ở READ COMMITTED để quota không đua giữa các node.
-- SECURITY INVOKER + search_path connection cấu hình, không mở quyền hoặc ghi sang schema cố định.
CREATE FUNCTION record_system_activity(p_visitor TEXT, p_subject TEXT, p_user BIGINT, p_status INTEGER)
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY INVOKER AS $$
DECLARE recorded BOOLEAN;
BEGIN
    IF NOT pg_try_advisory_xact_lock(781205615) THEN
        RETURN FALSE;
    END IF;
    WITH presence AS (
        INSERT INTO system_visitor_presence(visitor_hash,user_id)
        SELECT p_visitor,p_user WHERE
            EXISTS(SELECT 1 FROM system_visitor_presence WHERE visitor_hash=p_visitor)
            OR (SELECT count(*) FROM system_visitor_presence)<10000
        ON CONFLICT(visitor_hash) DO UPDATE
            SET user_id=EXCLUDED.user_id,last_seen=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
            WHERE system_visitor_presence.last_seen<CURRENT_TIMESTAMP-INTERVAL '30 seconds'
               OR system_visitor_presence.user_id IS DISTINCT FROM EXCLUDED.user_id
        RETURNING 1
    ), bucket AS (
        INSERT INTO system_activity_buckets(subject_hash,bucket_at,user_id,denied,limited,failures)
        SELECT p_subject,date_trunc('minute',CURRENT_TIMESTAMP),p_user,
            CASE WHEN p_status IN (401,403) THEN 1 ELSE 0 END,
            CASE WHEN p_status=429 THEN 1 ELSE 0 END,
            CASE WHEN p_status>=500 THEN 1 ELSE 0 END
        WHERE EXISTS(SELECT 1 FROM system_activity_buckets
                         WHERE subject_hash=p_subject AND bucket_at=date_trunc('minute',CURRENT_TIMESTAMP))
           OR (SELECT count(*) FROM system_activity_buckets)<10000
        ON CONFLICT(subject_hash,bucket_at) DO UPDATE
            SET requests=system_activity_buckets.requests+1,
                denied=system_activity_buckets.denied+EXCLUDED.denied,
                limited=system_activity_buckets.limited+EXCLUDED.limited,
                failures=system_activity_buckets.failures+EXCLUDED.failures,updated_at=CURRENT_TIMESTAMP
        RETURNING 1
    )
    SELECT EXISTS(SELECT 1 FROM bucket)
       AND (EXISTS(SELECT 1 FROM presence) OR EXISTS(SELECT 1 FROM system_visitor_presence WHERE visitor_hash=p_visitor))
    INTO recorded;
    RETURN recorded;
END;
$$;
