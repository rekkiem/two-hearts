-- Call sessions for WebRTC video tracking
CREATE TABLE call_sessions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    caller_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    callee_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status          TEXT NOT NULL DEFAULT 'ringing'
                    CHECK (status IN ('ringing','active','ended','rejected','missed')),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    answered_at     TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,
    duration_seconds INT GENERATED ALWAYS AS (
        CASE WHEN answered_at IS NOT NULL AND ended_at IS NOT NULL
        THEN EXTRACT(EPOCH FROM (ended_at - answered_at))::INT
        ELSE NULL END
    ) STORED
);

CREATE INDEX idx_call_sessions_conversation ON call_sessions(conversation_id, started_at DESC);
CREATE INDEX idx_call_sessions_caller       ON call_sessions(caller_id, started_at DESC);
