-- Nhật ký đã lọc dùng chung giữa các node; không chứa credential hoặc raw HTTP.
CREATE TABLE api_request_journal (
    id UUID PRIMARY KEY,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ NOT NULL,
    instance_id VARCHAR(36) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    method VARCHAR(12) NOT NULL,
    endpoint VARCHAR(180) NOT NULL,
    controller VARCHAR(200) NOT NULL,
    http_status INTEGER NOT NULL CHECK (http_status BETWEEN 100 AND 599),
    duration_ms BIGINT NOT NULL CHECK (duration_ms >= 0),
    code VARCHAR(16) NOT NULL,
    message_key VARCHAR(120) NOT NULL,
    diagnostic VARCHAR(40) NOT NULL,
    sql_state VARCHAR(5) NOT NULL,
    replay_path VARCHAR(240) NOT NULL,
    query_json JSONB NOT NULL CHECK (jsonb_typeof(query_json) = 'object'),
    request_body TEXT NOT NULL CHECK (length(request_body) <= 4096),
    response_body TEXT NOT NULL CHECK (length(response_body) <= 4096),
    content_kind VARCHAR(12) NOT NULL
);
CREATE INDEX idx_api_journal_recorded ON api_request_journal(recorded_at, id);
CREATE INDEX idx_api_journal_trace ON api_request_journal(trace_id, recorded_at);
CREATE INDEX idx_api_journal_status ON api_request_journal(http_status, recorded_at);
