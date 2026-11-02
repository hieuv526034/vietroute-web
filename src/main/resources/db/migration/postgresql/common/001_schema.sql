-- common: bảng, khóa chính, ràng buộc và index hiện hành.
-- Schema lấy từ LOCAL search_path; khóa ngoại được tạo sau toàn bộ cụm.

CREATE TABLE IF NOT EXISTS api_request_journal (
    id uuid NOT NULL,
    recorded_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    started_at timestamp with time zone NOT NULL,
    instance_id character varying(36) NOT NULL,
    trace_id character varying(64) NOT NULL,
    method character varying(12) NOT NULL,
    endpoint character varying(180) NOT NULL,
    controller character varying(200) NOT NULL,
    http_status integer NOT NULL,
    duration_ms bigint NOT NULL,
    code character varying(16) NOT NULL,
    message_key character varying(120) NOT NULL,
    diagnostic character varying(40) NOT NULL,
    sql_state character varying(5) NOT NULL,
    replay_path character varying(240) NOT NULL,
    query_json jsonb NOT NULL,
    request_body text NOT NULL,
    response_body text NOT NULL,
    content_kind character varying(12) NOT NULL,
    created_by bigint DEFAULT 0 NOT NULL,
    updated_by bigint DEFAULT 0 NOT NULL,
    request_user_id bigint
);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'api_request_journal'::regclass AND conname = 'api_request_journal_pkey') THEN
        ALTER TABLE api_request_journal ADD CONSTRAINT api_request_journal_pkey PRIMARY KEY (id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'api_request_journal'::regclass AND conname = 'api_request_journal_duration_ms_check') THEN
        ALTER TABLE api_request_journal ADD CONSTRAINT api_request_journal_duration_ms_check CHECK (duration_ms >= 0);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'api_request_journal'::regclass AND conname = 'api_request_journal_http_status_check') THEN
        ALTER TABLE api_request_journal ADD CONSTRAINT api_request_journal_http_status_check CHECK (http_status >= 100 AND http_status <= 599);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'api_request_journal'::regclass AND conname = 'api_request_journal_query_json_check') THEN
        ALTER TABLE api_request_journal ADD CONSTRAINT api_request_journal_query_json_check CHECK (jsonb_typeof(query_json) = 'object'::text);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'api_request_journal'::regclass AND conname = 'api_request_journal_request_body_check') THEN
        ALTER TABLE api_request_journal ADD CONSTRAINT api_request_journal_request_body_check CHECK (length(request_body) <= 4096);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'api_request_journal'::regclass AND conname = 'api_request_journal_response_body_check') THEN
        ALTER TABLE api_request_journal ADD CONSTRAINT api_request_journal_response_body_check CHECK (length(response_body) <= 4096);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_api_journal_created_by ON api_request_journal USING btree (created_by);

CREATE INDEX IF NOT EXISTS idx_api_journal_recorded ON api_request_journal USING btree (recorded_at, id);

CREATE INDEX IF NOT EXISTS idx_api_journal_request_user ON api_request_journal USING btree (request_user_id, recorded_at DESC);

CREATE INDEX IF NOT EXISTS idx_api_journal_status ON api_request_journal USING btree (http_status, recorded_at);

CREATE INDEX IF NOT EXISTS idx_api_journal_trace ON api_request_journal USING btree (trace_id, recorded_at);

CREATE INDEX IF NOT EXISTS idx_api_journal_updated_by ON api_request_journal USING btree (updated_by);
