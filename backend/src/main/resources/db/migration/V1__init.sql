-- Two Hearts Database Schema
-- PostgreSQL 16 + pgvector extension

-- Enable pgvector
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ============================================================
-- USERS
-- ============================================================
CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email       TEXT UNIQUE NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- ============================================================
-- MAGIC LINKS (passwordless auth)
-- ============================================================
CREATE TABLE magic_links (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_magic_links_token ON magic_links(token_hash) WHERE used_at IS NULL;

-- ============================================================
-- REFRESH TOKENS
-- ============================================================
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL UNIQUE,
    device_id   TEXT,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash) WHERE revoked_at IS NULL;

-- ============================================================
-- PROFILES
-- ============================================================
CREATE TABLE profiles (
    user_id             UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    display_name        TEXT NOT NULL,
    birth_date          DATE NOT NULL,
    gender_identity     TEXT NOT NULL,
    bio                 TEXT,
    occupation          TEXT,
    relationship_intent TEXT NOT NULL DEFAULT 'open_to_anything'
                        CHECK (relationship_intent IN ('long_term','casual_dating','friendship','open_to_anything','marriage')),
    lat                 DOUBLE PRECISION,
    lng                 DOUBLE PRECISION,
    city                TEXT,
    photo_url           TEXT,

    -- Age & gender preferences
    pref_min_age        INT NOT NULL DEFAULT 18,
    pref_max_age        INT NOT NULL DEFAULT 99,
    pref_genders        TEXT[] NOT NULL DEFAULT '{}',
    pref_max_dist_km    INT NOT NULL DEFAULT 100,

    -- 128-dim semantic embedding of bio + occupation + intent answers
    -- Stored as pgvector, managed via raw SQL
    embedding           vector(128),

    profile_complete    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_profiles_embedding ON profiles USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 10);

-- ============================================================
-- INTENT QUESTIONS (rotating daily questions)
-- ============================================================
CREATE TABLE intent_questions (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    text        TEXT NOT NULL,
    category    TEXT NOT NULL DEFAULT 'values',
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed some questions
INSERT INTO intent_questions (text, category) VALUES
    ('What made you genuinely smile this week?', 'connection'),
    ('What is something you are working hard to get better at?', 'growth'),
    ('Describe your ideal weekend in 3 words.', 'values'),
    ('What is a small thing that brings you unexpected joy?', 'vulnerability'),
    ('What does a meaningful connection mean to you?', 'connection'),
    ('What is something you wish more people knew about you?', 'vulnerability'),
    ('What are you most proud of in the last year?', 'growth'),
    ('What would you do with an unexpected free afternoon?', 'values'),
    ('What is something you recently changed your mind about?', 'growth'),
    ('What kind of energy do you bring to a room?', 'connection');

-- ============================================================
-- DAILY INTENTS
-- ============================================================
CREATE TABLE daily_intents (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_id     UUID NOT NULL REFERENCES intent_questions(id),
    answer_text     TEXT NOT NULL,
    intent_date     DATE NOT NULL DEFAULT CURRENT_DATE,
    -- 128-dim embedding of the answer
    answer_embedding vector(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, intent_date)
);

CREATE INDEX idx_daily_intents_user_date ON daily_intents(user_id, intent_date DESC);
CREATE INDEX idx_daily_intents_date ON daily_intents(intent_date);
CREATE INDEX idx_daily_intents_embedding ON daily_intents USING ivfflat (answer_embedding vector_cosine_ops)
    WITH (lists = 10);

-- ============================================================
-- MATCHES
-- ============================================================
CREATE TABLE matches (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_a_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    score           DOUBLE PRECISION NOT NULL DEFAULT 0,
    explainer       TEXT[] NOT NULL DEFAULT '{}',
    status          TEXT NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending','user_a_liked','user_b_liked','mutual','passed','expired')),
    match_date      DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT no_self_match CHECK (user_a_id != user_b_id),
    CONSTRAINT canonical_order CHECK (user_a_id < user_b_id),
    UNIQUE(user_a_id, user_b_id, match_date)
);

CREATE INDEX idx_matches_user_a ON matches(user_a_id, match_date DESC);
CREATE INDEX idx_matches_user_b ON matches(user_b_id, match_date DESC);
CREATE INDEX idx_matches_status ON matches(status) WHERE status IN ('pending','user_a_liked','user_b_liked','mutual');

-- ============================================================
-- CONVERSATIONS (created when match becomes mutual)
-- ============================================================
CREATE TABLE conversations (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    match_id        UUID NOT NULL UNIQUE REFERENCES matches(id) ON DELETE CASCADE,
    user_a_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_conversations_user_a ON conversations(user_a_id, last_message_at DESC);
CREATE INDEX idx_conversations_user_b ON conversations(user_b_id, last_message_at DESC);

-- ============================================================
-- MESSAGES
-- ============================================================
CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content         TEXT NOT NULL,
    read_at         TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messages_conversation ON messages(conversation_id, sent_at DESC);

-- ============================================================
-- TRIGGERS: auto-update updated_at
-- ============================================================
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trig_users_updated BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trig_profiles_updated BEFORE UPDATE ON profiles FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trig_matches_updated BEFORE UPDATE ON matches FOR EACH ROW EXECUTE FUNCTION update_updated_at();
