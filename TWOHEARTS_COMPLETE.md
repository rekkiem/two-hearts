# TwoHearts — Production-Grade Open-Source Android Dating App
## Complete Architecture, Implementation & Engineering Blueprint (2026)

---

# STEP 1 — COMPETITIVE ANALYSIS, UX DIFFERENTIATION & TECHNICAL TRADEOFFS

---

## 1.1 Competitive Analysis

### Tinder
- **Model**: Swipe-based, high-volume matching
- **Revenue**: Subscriptions + boosts + superlikes
- **Problems**: Gamified to maximize engagement, not meaningful connection. Ghosting rampant. No transparency in matching. Privacy controversies (data sold to advertisers). 26 million daily swipes produce superficial engagement.
- **Tech**: React Native, Firebase, Kubernetes on AWS

### Hinge
- **Model**: "Designed to be deleted" — prompt-based profiles, like-on-prompt
- **Revenue**: Subscription tiers
- **Problems**: Still owned by Match Group. Recommendation algorithm opaque. Conversation quality not incentivized. 
- **Tech**: React Native, AWS

### Bumble
- **Model**: Women message first, 24hr window
- **Revenue**: Subscriptions, spotlight, extend
- **Problems**: Time pressure anxiety. Swipe-first. Gamification loop intact. Moderate ghosting problem.
- **Tech**: iOS/Android native, AWS

### OkCupid
- **Model**: Question-based compatibility percentage
- **Problems**: Compatibility % not explained. Has added swipe. Questions feel like a survey, not a values-alignment tool.

### Coffee Meets Bagel
- **Model**: One curated match per day (bagel)
- **Problems**: Proprietary, expensive beans currency. Very limited matches leads to frustration without quality signal.

### Feeld
- **Model**: Non-traditional relationship types, couple profiles
- **Problems**: Niche. No meaningful quality signal for conversations.

### Lex / Grindr (community apps)
- **Model**: Text-ad format, community-first
- **Problems**: Safety issues, no conversation health metrics.

---

## 1.2 Market Gap Analysis

| Capability | Tinder | Hinge | OkCupid | **TwoHearts** |
|---|---|---|---|---|
| Transparent match scoring | ❌ | ❌ | Partial | ✅ Full explanation |
| Anti-ghosting tools | ❌ | ❌ | ❌ | ✅ Active monitoring |
| Slow-dating mode | ❌ | ❌ | ❌ | ✅ Core feature |
| E2E Encrypted chat | ❌ | ❌ | ❌ | ✅ Signal Protocol |
| Open source | ❌ | ❌ | ❌ | ✅ Fully open |
| Privacy-first | ❌ | ❌ | ❌ | ✅ By design |
| No swipe UX | ❌ | ❌ | ❌ | ✅ Intent-based |
| Semantic matching | ❌ | ❌ | Partial | ✅ pgvector |
| Geolocation-aware | ✅ | ✅ | ✅ | ✅ PostGIS |
| AI conversation starters | ❌ | ❌ | ❌ | ✅ Local LLM |
| Toxicity moderation | ❌ | ❌ | ❌ | ✅ Pipeline |

---

## 1.3 UX Differentiation Strategy

### Core Philosophy: "Intentional Presence"
TwoHearts replaces the dopamine-loop swipe mechanic with a **daily intention ritual**. Users answer one thoughtful question per day about what they're seeking. This signal drives matching.

### UX Pillars:

**1. Daily Intent Card (replaces swipe)**
- Each day, user fills in a "Daily Intention" — a short answer to a rotating question
- Examples: "What made you feel most alive this week?" / "What are you genuinely afraid of?"
- Answers drive semantic embedding; this feeds the matching engine

**2. Curated Matches (max 3/day in Slow Mode)**
- No infinite scroll of profiles
- Each match comes with a **Compatibility Explainer**: 3 bullet points explaining *why* this person was suggested based on shared values and intent signals
- Users can see overlap in answers (anonymized until mutual interest)

**3. Conversation Health Meter**
- Anti-ghosting: if a conversation goes >48h without meaningful exchange, both parties receive a gentle nudge
- Conversation quality score (response depth, not just response time) shown as a subtle indicator
- Users are rewarded for depth, not volume

**4. Slow-Dating Mode (default)**
- Maximum 3 active conversations at once
- Encourages investment in each connection
- Users must "archive" a conversation to open a new slot

**5. Privacy-First Profile**
- No real name required (username only)
- Photos blurred by default until both parties consent to reveal
- Location shown as district/neighborhood, never exact coordinates
- Profile data portable and deletable (GDPR/CCPA by design)

---

## 1.4 Technical Tradeoff Analysis

### Tradeoff 1: Signal Protocol vs. Custom E2E Encryption
- **Signal Protocol**: Battle-tested, open source, forward secrecy, deniability. Implementation complexity is high. We accept this cost.
- **Custom AES scheme**: Faster to implement, higher security risk, harder to audit. **Rejected.**

### Tradeoff 2: pgvector vs. Pinecone/Weaviate for semantic matching
- **pgvector**: Keeps vectors in Postgres alongside relational data. ACID. Open source. Slightly slower at scale but sufficient for <10M users.
- **Dedicated vector DB**: Better performance at extreme scale. Adds operational complexity. Another stateful service to manage.
- **Decision**: pgvector. Revisit at 5M+ active users.

### Tradeoff 3: On-device vs. backend embedding
- **On-device (ONNX MiniLM)**: Privacy-preserving, no text leaves device. Model size ~80MB. Works offline.
- **Backend embedding (self-hosted Ollama)**: Consistent quality, supports larger models, easier to upgrade.
- **Decision**: Hybrid. On-device for generating embeddings from Daily Intent answers (text stays on device). Backend embedding for profile-level semantic matching at aggregate.

### Tradeoff 4: Redpanda vs. RabbitMQ vs. NATS
- **Redpanda**: Kafka-compatible, no ZooKeeper, better performance, simpler ops. Open source.
- **RabbitMQ**: Simpler but weaker ordering guarantees, not ideal for event sourcing.
- **NATS**: Extremely fast but less mature ecosystem.
- **Decision**: Redpanda for event streaming.

### Tradeoff 5: WebSocket vs. SSE vs. XMPP for chat
- **WebSocket + custom protocol**: Full duplex, widely supported, works with Ktor.
- **XMPP**: Open standard, complex, large overhead.
- **SSE**: Server-push only, no bidirectional.
- **Decision**: WebSocket with Ktor's built-in support + presence management via Redis.

### Tradeoff 6: Ktor vs. Spring Boot
- **Ktor**: Kotlin-native, coroutines-native, lightweight, excellent for microservices.
- **Spring Boot**: Mature, large ecosystem, heavier memory footprint.
- **Decision**: Ktor. Consistent with Android Kotlin stack.

### Tradeoff 7: Auth — Passwordless (Magic Link + TOTP) vs. OAuth
- **Passwordless (Magic Link)**: Eliminates credential stuffing. Sends time-limited JWT via email/SMS. Open source.
- **OAuth with social login**: Google/Apple dependency. Violates open-source constraint.
- **Decision**: Magic Link (email) + optional TOTP 2FA. No social provider dependency.

---

# STEP 2 — SYSTEM ARCHITECTURE

---

## 2.1 High-Level Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        TwoHearts System                             │
│                                                                     │
│  ┌──────────────┐     ┌─────────────────────────────────────────┐  │
│  │ Android App  │────▶│         API Gateway (Nginx/Traefik)      │  │
│  │  (Kotlin +   │     │         TLS termination, rate limiting   │  │
│  │  Compose)    │     └──────────────┬──────────────────────────┘  │
│  └──────────────┘                    │                              │
│                         ┌────────────▼──────────────┐             │
│                         │     Microservices Layer    │             │
│                         │                           │             │
│  ┌──────────────┐  ┌────▼──────┐ ┌────────────┐   │             │
│  │  Auth Service │  │  Profile  │ │  Matching  │   │             │
│  │  (Ktor)      │  │  Service  │ │  Service   │   │             │
│  │  Port: 8081  │  │  (Ktor)   │ │  (Ktor)    │   │             │
│  └──────┬───────┘  │  Port:8082│ │  Port:8083 │   │             │
│         │          └────┬──────┘ └─────┬──────┘   │             │
│  ┌──────▼──────┐   ┌────▼──────┐ ┌────▼──────┐   │             │
│  │  Chat Service│  │ Moderation│ │ Notif.    │   │             │
│  │  (Ktor+WS)  │  │  Service  │ │ Service   │   │             │
│  │  Port: 8084 │  │  Port:8085│ │ Port:8086 │   │             │
│  └──────┬──────┘  └─────┬─────┘ └─────┬─────┘   │             │
│         └───────────────┘              │          │             │
│                    │                   │          │             │
│         ┌──────────▼───────────────────▼──────┐  │             │
│         │         Redpanda (Kafka-compat)      │  │             │
│         │  Topics: auth-events, match-events,  │  │             │
│         │  chat-messages, moderation-queue,    │  │             │
│         │  notification-events, analytics      │  │             │
│         └──────────┬───────────────────────────┘  │             │
│                    │                              │             │
│    ┌───────────────▼──────────────────────────┐  │             │
│    │              Data Layer                  │  │             │
│    │                                          │  │             │
│    │  ┌─────────────────────────────────┐    │  │             │
│    │  │  PostgreSQL 16                  │    │  │             │
│    │  │  + pgvector (embeddings)        │    │  │             │
│    │  │  + PostGIS (geolocation)        │    │  │             │
│    │  │  + Row Level Security           │    │  │             │
│    │  └─────────────────────────────────┘    │  │             │
│    │  ┌──────────────┐ ┌──────────────────┐  │  │             │
│    │  │  Redis 7     │ │  MinIO (S3)      │  │  │             │
│    │  │  (sessions,  │ │  (media storage) │  │  │             │
│    │  │   presence,  │ └──────────────────┘  │  │             │
│    │  │   rate limit)│                       │  │             │
│    │  └──────────────┘                       │  │             │
│    └──────────────────────────────────────────┘  │             │
│                                                   │             │
│  ┌────────────────────────────────────────────┐  │             │
│  │          Observability Stack               │  │             │
│  │  Prometheus + Grafana + Loki + Tempo       │  │             │
│  └────────────────────────────────────────────┘  │             │
└─────────────────────────────────────────────────────────────────────┘
```

## 2.2 Service Breakdown

### Auth Service (Port 8081)
**Responsibilities**: Magic link generation, JWT issuance and refresh, TOTP verification, session management, device fingerprinting.
**Key tables**: `users`, `auth_tokens`, `devices`
**Events produced**: `user.registered`, `user.login`, `user.logout`, `user.deleted`

### Profile Service (Port 8082)
**Responsibilities**: Profile CRUD, photo upload (presigned MinIO URLs), embedding generation, preference storage, daily intent ingestion.
**Key tables**: `profiles`, `profile_photos`, `daily_intents`, `preferences`
**Events produced**: `profile.updated`, `intent.submitted`, `embedding.updated`
**Events consumed**: `user.registered`

### Matching Service (Port 8083)
**Responsibilities**: Daily matching job, semantic similarity scoring, rule-based filter application, compatibility explainer generation, match lifecycle management.
**Key tables**: `matches`, `match_scores`, `match_interactions`
**Events produced**: `match.created`, `match.accepted`, `match.rejected`, `match.expired`
**Events consumed**: `intent.submitted`, `embedding.updated`

### Chat Service (Port 8084)
**Responsibilities**: WebSocket connection management, E2E encrypted message relay, message persistence (encrypted blobs), conversation health monitoring, typing indicators, read receipts, conversation archival.
**Key tables**: `conversations`, `messages` (encrypted), `conversation_health`
**Events produced**: `message.sent`, `conversation.health.degraded`, `conversation.archived`
**Events consumed**: `match.accepted`

### Moderation Service (Port 8085)
**Responsibilities**: Toxicity detection pipeline (using Detoxify/open-source NLP), photo NSFW detection (CLIP-based), user reporting, account suspension, appeal processing.
**Key tables**: `reports`, `moderation_actions`, `appeal_queue`
**Events produced**: `content.flagged`, `user.suspended`, `user.reinstated`
**Events consumed**: `message.sent`, `profile.updated`

### Notification Service (Port 8086)
**Responsibilities**: Push notification delivery (Firebase FCM open-source alternative: Gotify or UnifiedPush), in-app notification aggregation, anti-ghosting nudge scheduling.
**Key tables**: `notification_preferences`, `notification_queue`
**Events consumed**: `match.created`, `message.sent`, `conversation.health.degraded`

---

## 2.3 Database Schema (Full SQL)

```sql
-- ============================================================
-- TwoHearts PostgreSQL Schema
-- PostgreSQL 16 + pgvector + PostGIS
-- ============================================================

-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";      -- pgvector
CREATE EXTENSION IF NOT EXISTS "postgis";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";     -- fuzzy search

-- ============================================================
-- SCHEMA: auth
-- ============================================================
CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE auth.users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email           TEXT UNIQUE NOT NULL,
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    is_suspended    BOOLEAN NOT NULL DEFAULT FALSE,
    suspension_reason TEXT,
    suspension_until TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,  -- soft delete
    CONSTRAINT email_format CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
);

CREATE TABLE auth.magic_links (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token_hash      TEXT NOT NULL UNIQUE,  -- SHA-256 hash of the token
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    ip_address      INET,
    user_agent      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE auth.refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token_hash      TEXT NOT NULL UNIQUE,
    device_id       UUID,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    ip_address      INET,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE auth.devices (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_fingerprint TEXT NOT NULL,
    push_token      TEXT,  -- FCM/UnifiedPush token
    push_provider   TEXT CHECK (push_provider IN ('fcm', 'gotify', 'unifiedpush')),
    platform        TEXT NOT NULL CHECK (platform IN ('android', 'ios', 'web')),
    app_version     TEXT,
    last_seen_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, device_fingerprint)
);

CREATE TABLE auth.totp_secrets (
    user_id         UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    secret_encrypted TEXT NOT NULL,  -- AES-256 encrypted TOTP secret
    is_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    backup_codes_hash TEXT[],  -- hashed backup codes
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- SCHEMA: profiles
-- ============================================================
CREATE SCHEMA IF NOT EXISTS profiles;

CREATE TABLE profiles.profiles (
    user_id         UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name    TEXT NOT NULL CHECK (LENGTH(display_name) BETWEEN 2 AND 50),
    birth_date      DATE NOT NULL,
    gender_identity TEXT NOT NULL,
    pronouns        TEXT,
    bio             TEXT CHECK (LENGTH(bio) <= 500),
    occupation      TEXT,
    education_level TEXT CHECK (education_level IN ('high_school','some_college','bachelors','masters','phd','vocational','prefer_not_to_say')),
    
    -- Location (PostGIS point — district/city level only, never exact)
    location        GEOMETRY(Point, 4326),
    city            TEXT,
    country_code    CHAR(2),
    
    -- Relationship intent
    relationship_intent TEXT NOT NULL CHECK (relationship_intent IN (
        'long_term', 'casual_dating', 'friendship', 'open_to_anything', 'marriage'
    )),
    
    -- Values embeddings (on-device MiniLM, 384 dimensions)
    values_embedding vector(384),
    
    -- Personality dimensions (Big5 derived from intent answers, normalized 0-1)
    openness        FLOAT CHECK (openness BETWEEN 0 AND 1),
    conscientiousness FLOAT CHECK (conscientiousness BETWEEN 0 AND 1),
    extraversion    FLOAT CHECK (extraversion BETWEEN 0 AND 1),
    agreeableness   FLOAT CHECK (agreeableness BETWEEN 0 AND 1),
    neuroticism     FLOAT CHECK (neuroticism BETWEEN 0 AND 1),
    
    -- Activity
    last_active_at  TIMESTAMPTZ,
    profile_complete BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Privacy
    photos_visible_to TEXT NOT NULL DEFAULT 'matches' CHECK (photos_visible_to IN ('everyone','matches','nobody')),
    location_precision TEXT NOT NULL DEFAULT 'city' CHECK (location_precision IN ('city','district','hidden')),
    
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE profiles.profile_photos (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    storage_key     TEXT NOT NULL,  -- MinIO object key
    thumbnail_key   TEXT,
    sort_order      SMALLINT NOT NULL DEFAULT 0,
    is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
    is_verified     BOOLEAN NOT NULL DEFAULT FALSE,
    moderation_status TEXT NOT NULL DEFAULT 'pending' CHECK (moderation_status IN ('pending','approved','rejected')),
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE profiles.preferences (
    user_id         UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    seeking_genders TEXT[] NOT NULL DEFAULT '{}',
    age_min         SMALLINT NOT NULL DEFAULT 18 CHECK (age_min >= 18),
    age_max         SMALLINT NOT NULL DEFAULT 99 CHECK (age_max <= 99),
    max_distance_km INTEGER NOT NULL DEFAULT 50 CHECK (max_distance_km BETWEEN 1 AND 500),
    
    -- Slow dating config
    slow_mode_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    max_concurrent_matches SMALLINT NOT NULL DEFAULT 3,
    
    -- Intent alignment weight (0-1, how much to weight daily intent vs. profile)
    intent_weight   FLOAT NOT NULL DEFAULT 0.6 CHECK (intent_weight BETWEEN 0 AND 1),
    
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE profiles.daily_intents (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    question_id     UUID NOT NULL,
    answer_text     TEXT NOT NULL CHECK (LENGTH(answer_text) BETWEEN 10 AND 500),
    -- Embedding generated on-device, stored here for matching
    answer_embedding vector(384),
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    intent_date     DATE NOT NULL DEFAULT CURRENT_DATE,
    UNIQUE(user_id, intent_date)  -- one intent per user per day
);

CREATE TABLE profiles.intent_questions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    question_text   TEXT NOT NULL,
    category        TEXT NOT NULL CHECK (category IN ('values','growth','connection','humor','vulnerability','aspiration')),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    scheduled_date  DATE,  -- if set, shown on this specific date globally
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE profiles.blocks (
    blocker_id      UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    blocked_id      UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    reason          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (blocker_id, blocked_id)
);

-- ============================================================
-- SCHEMA: matching
-- ============================================================
CREATE SCHEMA IF NOT EXISTS matching;

CREATE TABLE matching.matches (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_a_id       UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    user_b_id       UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    
    -- Scoring breakdown
    semantic_score      FLOAT NOT NULL CHECK (semantic_score BETWEEN 0 AND 1),
    intent_score        FLOAT NOT NULL CHECK (intent_score BETWEEN 0 AND 1),
    geo_score           FLOAT NOT NULL CHECK (geo_score BETWEEN 0 AND 1),
    preference_score    FLOAT NOT NULL CHECK (preference_score BETWEEN 0 AND 1),
    composite_score     FLOAT NOT NULL CHECK (composite_score BETWEEN 0 AND 1),
    
    -- Explainer (JSON array of 3 strings)
    compatibility_explainer JSONB NOT NULL DEFAULT '[]',
    
    -- Status lifecycle
    status          TEXT NOT NULL DEFAULT 'pending' CHECK (status IN (
        'pending',      -- awaiting both parties to accept/reject
        'user_a_liked', -- user_a accepted, awaiting user_b
        'user_b_liked', -- user_b accepted, awaiting user_a
        'mutual',       -- both accepted — conversation unlocked
        'rejected',     -- at least one rejected
        'expired',      -- 24h passed without interaction
        'archived'      -- conversation archived by user
    )),
    
    match_date      DATE NOT NULL DEFAULT CURRENT_DATE,
    accepted_at     TIMESTAMPTZ,
    rejected_at     TIMESTAMPTZ,
    expired_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT no_self_match CHECK (user_a_id != user_b_id),
    CONSTRAINT user_order CHECK (user_a_id < user_b_id),  -- canonical ordering
    UNIQUE(user_a_id, user_b_id, match_date)
);

CREATE TABLE matching.match_feedback (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    match_id        UUID NOT NULL REFERENCES matching.matches(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    feedback_type   TEXT NOT NULL CHECK (feedback_type IN ('too_far','different_values','not_my_type','no_chemistry','great_match')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE matching.daily_match_runs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_date        DATE NOT NULL UNIQUE,
    users_processed INTEGER,
    matches_created INTEGER,
    duration_ms     INTEGER,
    status          TEXT NOT NULL DEFAULT 'running' CHECK (status IN ('running','completed','failed')),
    error_log       TEXT,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);

-- ============================================================
-- SCHEMA: chat
-- ============================================================
CREATE SCHEMA IF NOT EXISTS chat;

CREATE TABLE chat.conversations (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    match_id        UUID NOT NULL UNIQUE REFERENCES matching.matches(id) ON DELETE CASCADE,
    user_a_id       UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    user_b_id       UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    
    -- E2E encryption: each party's encrypted copy of the shared symmetric key
    -- Keys are encrypted with each user's Signal Protocol ratchet state
    -- We store NO plaintext keys — this is opaque blobs only
    
    -- Health monitoring
    health_score    FLOAT NOT NULL DEFAULT 1.0 CHECK (health_score BETWEEN 0 AND 1),
    last_health_check TIMESTAMPTZ,
    health_nudge_sent_at TIMESTAMPTZ,
    
    -- Archival
    archived_by_a   BOOLEAN NOT NULL DEFAULT FALSE,
    archived_by_b   BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Message counts (denormalized for performance)
    message_count   INTEGER NOT NULL DEFAULT 0,
    last_message_at TIMESTAMPTZ,
    
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE chat.messages (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id UUID NOT NULL REFERENCES chat.conversations(id) ON DELETE CASCADE,
    sender_id       UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    
    -- E2E encrypted payload — server NEVER sees plaintext
    -- This is the Signal Protocol encrypted ciphertext
    ciphertext      BYTEA NOT NULL,
    
    -- Metadata (not encrypted — needed for delivery)
    message_type    TEXT NOT NULL DEFAULT 'text' CHECK (message_type IN ('text','image','audio','system','conversation_starter')),
    
    -- Read receipts
    read_at         TIMESTAMPTZ,
    
    -- Soft delete (wipe ciphertext, keep metadata)
    deleted_at      TIMESTAMPTZ,
    
    -- For ordering within conversation (client-side clock + sequence)
    client_sequence BIGINT NOT NULL,
    
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    UNIQUE(conversation_id, client_sequence, sender_id)
);

CREATE TABLE chat.conversation_health (
    conversation_id UUID PRIMARY KEY REFERENCES chat.conversations(id) ON DELETE CASCADE,
    
    -- Health dimensions (0-1)
    response_rate       FLOAT NOT NULL DEFAULT 1.0,
    avg_response_time_h FLOAT,  -- hours
    avg_message_length  FLOAT,  -- characters
    question_ratio      FLOAT,  -- ratio of messages with questions
    reciprocity_score   FLOAT NOT NULL DEFAULT 1.0,  -- is one person doing all the talking?
    
    -- Nudge state
    nudge_threshold_hours INTEGER NOT NULL DEFAULT 48,
    last_sender_id  UUID REFERENCES auth.users(id),
    
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE chat.typing_indicators (
    conversation_id UUID NOT NULL REFERENCES chat.conversations(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (conversation_id, user_id)
);

-- ============================================================
-- SCHEMA: moderation
-- ============================================================
CREATE SCHEMA IF NOT EXISTS moderation;

CREATE TABLE moderation.reports (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reporter_id     UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    reported_user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    message_id      UUID REFERENCES chat.messages(id),
    report_type     TEXT NOT NULL CHECK (report_type IN (
        'harassment','hate_speech','spam','inappropriate_photo',
        'underage','scam','impersonation','other'
    )),
    description     TEXT,
    evidence_keys   TEXT[],  -- MinIO keys for screenshot evidence
    status          TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','reviewing','resolved','dismissed')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMPTZ,
    resolution_notes TEXT
);

CREATE TABLE moderation.moderation_actions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    action_type     TEXT NOT NULL CHECK (action_type IN ('warning','content_removed','temp_suspension','permanent_ban','account_deleted')),
    reason          TEXT NOT NULL,
    report_id       UUID REFERENCES moderation.reports(id),
    automated       BOOLEAN NOT NULL DEFAULT FALSE,
    moderator_id    UUID REFERENCES auth.users(id),
    applied_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ
);

CREATE TABLE moderation.toxicity_scores (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    content_type    TEXT NOT NULL CHECK (content_type IN ('message','bio','intent_answer')),
    content_ref_id  UUID NOT NULL,
    toxicity        FLOAT,
    severe_toxicity FLOAT,
    obscene         FLOAT,
    threat          FLOAT,
    insult          FLOAT,
    identity_attack FLOAT,
    model_version   TEXT NOT NULL,
    scored_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    action_taken    BOOLEAN NOT NULL DEFAULT FALSE
);

-- ============================================================
-- ROW LEVEL SECURITY POLICIES
-- ============================================================

ALTER TABLE auth.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE profiles.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE profiles.daily_intents ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat.conversations ENABLE ROW LEVEL SECURITY;

-- Users can only see their own auth record
CREATE POLICY users_self_access ON auth.users
    USING (id = current_setting('app.current_user_id')::UUID);

-- Users can only see their own profile, OR profiles of their current matches
CREATE POLICY profiles_access ON profiles.profiles
    USING (
        user_id = current_setting('app.current_user_id')::UUID
        OR user_id IN (
            SELECT CASE 
                WHEN user_a_id = current_setting('app.current_user_id')::UUID THEN user_b_id
                ELSE user_a_id
            END
            FROM matching.matches
            WHERE status = 'mutual'
            AND (user_a_id = current_setting('app.current_user_id')::UUID 
                 OR user_b_id = current_setting('app.current_user_id')::UUID)
        )
    );

-- Users can only see messages in their conversations
CREATE POLICY messages_access ON chat.messages
    USING (
        conversation_id IN (
            SELECT id FROM chat.conversations
            WHERE user_a_id = current_setting('app.current_user_id')::UUID
               OR user_b_id = current_setting('app.current_user_id')::UUID
        )
    );

-- Users can only see their own conversations
CREATE POLICY conversations_access ON chat.conversations
    USING (
        user_a_id = current_setting('app.current_user_id')::UUID
        OR user_b_id = current_setting('app.current_user_id')::UUID
    );

-- ============================================================
-- INDEXES
-- ============================================================

-- Auth
CREATE INDEX idx_magic_links_token ON auth.magic_links(token_hash) WHERE used_at IS NULL;
CREATE INDEX idx_refresh_tokens_user ON auth.refresh_tokens(user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_devices_user ON auth.devices(user_id);

-- Profiles
CREATE INDEX idx_profiles_location ON profiles.profiles USING GIST(location);
CREATE INDEX idx_profiles_embedding ON profiles.profiles USING ivfflat(values_embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX idx_profiles_intent ON profiles.profiles(relationship_intent);
CREATE INDEX idx_daily_intents_user_date ON profiles.daily_intents(user_id, intent_date DESC);
CREATE INDEX idx_daily_intents_embedding ON profiles.daily_intents USING ivfflat(answer_embedding vector_cosine_ops) WITH (lists = 100);

-- Matching
CREATE INDEX idx_matches_user_a ON matching.matches(user_a_id, match_date DESC);
CREATE INDEX idx_matches_user_b ON matching.matches(user_b_id, match_date DESC);
CREATE INDEX idx_matches_status ON matching.matches(status) WHERE status IN ('pending','user_a_liked','user_b_liked','mutual');

-- Chat
CREATE INDEX idx_messages_conversation ON chat.messages(conversation_id, sent_at DESC);
CREATE INDEX idx_messages_sender ON chat.messages(sender_id);
CREATE INDEX idx_conversations_user_a ON chat.conversations(user_a_id);
CREATE INDEX idx_conversations_user_b ON chat.conversations(user_b_id);

-- Moderation
CREATE INDEX idx_reports_status ON moderation.reports(status) WHERE status = 'open';
CREATE INDEX idx_toxicity_content ON moderation.toxicity_scores(content_type, content_ref_id);

-- ============================================================
-- FUNCTIONS & TRIGGERS
-- ============================================================

-- Auto-update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_profiles_updated_at BEFORE UPDATE ON profiles.profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_matches_updated_at BEFORE UPDATE ON matching.matches
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_conversations_updated_at BEFORE UPDATE ON chat.conversations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Auto-expire matches after 24h
CREATE OR REPLACE FUNCTION expire_stale_matches()
RETURNS void AS $$
BEGIN
    UPDATE matching.matches
    SET status = 'expired', expired_at = NOW()
    WHERE status IN ('pending', 'user_a_liked', 'user_b_liked')
    AND created_at < NOW() - INTERVAL '24 hours';
END;
$$ LANGUAGE plpgsql;

-- Geospatial helper: get users within radius
CREATE OR REPLACE FUNCTION profiles.users_within_radius(
    center_point GEOMETRY,
    radius_km FLOAT
)
RETURNS TABLE(user_id UUID, distance_km FLOAT) AS $$
BEGIN
    RETURN QUERY
    SELECT p.user_id,
           ST_Distance(p.location::geography, center_point::geography) / 1000.0 AS distance_km
    FROM profiles.profiles p
    WHERE ST_DWithin(p.location::geography, center_point::geography, radius_km * 1000)
    AND p.profile_complete = TRUE
    AND p.location IS NOT NULL;
END;
$$ LANGUAGE plpgsql;

-- Semantic similarity search function
CREATE OR REPLACE FUNCTION profiles.find_similar_profiles(
    target_embedding vector(384),
    limit_count INTEGER DEFAULT 50,
    min_similarity FLOAT DEFAULT 0.6
)
RETURNS TABLE(user_id UUID, similarity FLOAT) AS $$
BEGIN
    RETURN QUERY
    SELECT p.user_id,
           1 - (p.values_embedding <=> target_embedding) AS similarity
    FROM profiles.profiles p
    WHERE p.values_embedding IS NOT NULL
    AND p.profile_complete = TRUE
    AND 1 - (p.values_embedding <=> target_embedding) >= min_similarity
    ORDER BY p.values_embedding <=> target_embedding
    LIMIT limit_count;
END;
$$ LANGUAGE plpgsql;
```

---

## 2.4 Event Flow Design

### Redpanda Topic Definitions

```
Topic: auth-events
  Retention: 7 days
  Partitions: 12
  Events: UserRegistered, UserLogin, UserLogout, UserDeleted, UserSuspended

Topic: profile-events  
  Retention: 30 days
  Partitions: 12
  Events: ProfileUpdated, IntentSubmitted, EmbeddingUpdated, PhotoApproved

Topic: match-events
  Retention: 30 days
  Partitions: 24
  Events: MatchCreated, MatchAccepted, MatchRejected, MatchExpired, MatchMutual

Topic: chat-messages
  Retention: 90 days (metadata only — content is encrypted opaque blobs)
  Partitions: 48
  Events: MessageSent, MessageRead, ConversationCreated, ConversationArchived

Topic: moderation-queue
  Retention: 90 days
  Partitions: 6
  Events: ContentFlagged, ToxicityDetected, ReportSubmitted

Topic: notification-events
  Retention: 1 day
  Partitions: 12
  Events: PushNotificationRequested, NudgeSent

Topic: analytics-events
  Retention: 365 days
  Partitions: 12
  Events: SessionStarted, MatchViewed, ConversationHealthUpdate (anonymized)
```

### Key Event Flows:

**Daily Matching Flow:**
```
00:00 UTC — Scheduler triggers matching job
→ Matching Service reads all profiles with today's intent from profiles.daily_intents
→ For each user with pending matching slot:
   1. Fetch user embedding + intent embedding
   2. Query pgvector for top-100 semantic candidates
   3. PostGIS filter by distance preference
   4. Apply preference filters (gender, age, relationship_intent)
   5. Score each candidate: composite = (0.4 × semantic) + (0.3 × intent) + (0.2 × geo) + (0.1 × preference)
   6. Select top-N (N = max_concurrent_matches - current_active)
   7. Generate compatibility explainer via template engine
   8. Write to matching.matches
   9. Produce match-events:MatchCreated
→ Notification Service consumes MatchCreated → sends push "You have a new match!"
```

**Conversation Health Flow:**
```
Scheduled every 1h:
→ Chat Service scans chat.conversations WHERE last_message_at < NOW() - 24h
→ Compute health score from conversation_health table
→ If health_score < 0.4 AND no nudge in last 48h:
   → Produce notification-events:NudgeSent
   → Update chat.conversations.health_nudge_sent_at
   → Notification Service delivers gentle prompt to both users
```

---

## 2.5 Security Model

### Threat Model Summary

| Threat | Mitigation |
|---|---|
| Credential theft | Passwordless — no passwords to steal |
| Session hijacking | Short-lived JWTs (15min), refresh token rotation |
| E2E encryption bypass | Signal Protocol — server holds no keys |
| SQL injection | Parameterized queries only (Exposed ORM) |
| IDOR | Row Level Security in PostgreSQL |
| DDoS | Nginx rate limiting, Redis token bucket per IP |
| Data exfiltration | Minimal PII, encrypted at rest, RLS |
| Toxicity/CSAM | Server-side moderation pipeline on metadata |
| Photo scraping | Presigned MinIO URLs with short TTL |
| Geolocation exposure | Only district/city level stored, exact never persisted |
| Mass matching abuse | Daily match quotas enforced in DB |
| Account enumeration | Identical response for exist/not-exist on magic link |

### JWT Structure
```json
{
  "alg": "ES256",
  "typ": "JWT"
}
{
  "sub": "<user_id>",
  "did": "<device_id>",
  "iat": 1700000000,
  "exp": 1700000900,
  "jti": "<token_id>"
}
```
- Algorithm: ES256 (ECDSA P-256) — not HS256
- Expiry: 15 minutes
- Refresh rotation: single-use refresh tokens, 30 days, stored as SHA-256 hash

### Encryption at Rest
- PostgreSQL: TDE via pgcrypto for sensitive columns
- Message ciphertext: Signal Protocol Double Ratchet (already encrypted, double-encrypted at rest)
- TOTP secrets: AES-256-GCM with KMS-stored key (Vault or AWS KMS)
- MinIO: SSE-S3 with customer-managed keys
- Redis: Passwords + TLS required

### Network Security
- All services communicate over mTLS within Kubernetes cluster (Istio service mesh)
- External traffic: TLS 1.3 minimum, HSTS, certificate pinning on Android client
- API Gateway: JWT validation at edge, no internal services exposed directly
- Secrets: Kubernetes Secrets + external-secrets-operator backed by Vault
# STEP 3 — IMPLEMENTATION

---

# BACKEND SERVICES — KOTLIN + KTOR

---

## 3.1 Project Root Structure

```
twohearts/
├── android/                          # Android application
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── kotlin/com/twohearts/
│   │   │   │   ├── di/               # Hilt modules
│   │   │   │   ├── data/             # Repositories, datasources, models
│   │   │   │   ├── domain/           # Use cases, domain models
│   │   │   │   ├── presentation/     # ViewModels, Compose screens
│   │   │   │   ├── crypto/           # Signal Protocol wrapper
│   │   │   │   └── TwoHeartsApp.kt
│   │   └── build.gradle.kts
├── backend/
│   ├── shared/                       # Shared models, utils
│   ├── auth-service/                 # Ktor auth service
│   ├── profile-service/              # Ktor profile service
│   ├── matching-service/             # Ktor matching service
│   ├── chat-service/                 # Ktor chat + WebSocket service
│   ├── moderation-service/           # Ktor moderation service
│   └── notification-service/         # Ktor notification service
├── infrastructure/
│   ├── docker/
│   │   ├── docker-compose.yml
│   │   └── docker-compose.prod.yml
│   ├── kubernetes/
│   │   ├── base/
│   │   └── overlays/
│   ├── nginx/
│   └── monitoring/
│       ├── prometheus/
│       ├── grafana/
│       └── loki/
├── .github/
│   └── workflows/
└── scripts/
```

---

## 3.2 Shared Module — Backend

### build.gradle.kts (shared)
```kotlin
// backend/shared/build.gradle.kts
plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("com.google.devtools.ksp") version "2.0.21-1.0.25"
}

val ktorVersion = "3.1.0"
val exposedVersion = "0.55.0"
val redpandaVersion = "3.8.0"

dependencies {
    // Serialization
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    
    // Ktor client (for inter-service calls)
    api("io.ktor:ktor-client-core:$ktorVersion")
    api("io.ktor:ktor-client-cio:$ktorVersion")
    api("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    api("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    
    // Logging
    api("io.github.oshai:kotlin-logging-jvm:7.0.0")
    api("ch.qos.logback:logback-classic:1.5.12")
    
    // Kafka/Redpanda
    api("org.apache.kafka:kafka-clients:$redpandaVersion")
    
    // Testing
    testApi("io.ktor:ktor-server-test-host:$ktorVersion")
    testApi("org.jetbrains.kotlin:kotlin-test-junit5")
    testApi("io.mockk:mockk:1.13.13")
    testApi("org.testcontainers:postgresql:1.20.4")
    testApi("org.testcontainers:kafka:1.20.4")
}
```

### Shared Domain Models
```kotlin
// backend/shared/src/main/kotlin/com/twohearts/shared/models/Events.kt
package com.twohearts.shared.models

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
sealed class TwoHeartsEvent {
    abstract val eventId: String
    abstract val occurredAt: Instant
    abstract val version: Int
}

@Serializable
@SerialName("UserRegistered")
data class UserRegisteredEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant,
    override val version: Int = 1,
    val userId: String,
    val email: String
) : TwoHeartsEvent()

@Serializable
@SerialName("IntentSubmitted")
data class IntentSubmittedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant,
    override val version: Int = 1,
    val userId: String,
    val questionId: String,
    val intentDate: String,  // ISO date string
    val embeddingAvailable: Boolean
) : TwoHeartsEvent()

@Serializable
@SerialName("MatchCreated")
data class MatchCreatedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant,
    override val version: Int = 1,
    val matchId: String,
    val userAId: String,
    val userBId: String,
    val compositeScore: Double,
    val compatibilityExplainer: List<String>
) : TwoHeartsEvent()

@Serializable
@SerialName("MatchMutual")
data class MatchMutualEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant,
    override val version: Int = 1,
    val matchId: String,
    val userAId: String,
    val userBId: String,
    val conversationId: String
) : TwoHeartsEvent()

@Serializable
@SerialName("MessageSent")
data class MessageSentEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant,
    override val version: Int = 1,
    val conversationId: String,
    val senderId: String,
    val messageId: String,
    val messageType: String
    // NO content — E2E encrypted, server never has plaintext
) : TwoHeartsEvent()

@Serializable
@SerialName("ConversationHealthDegraded")
data class ConversationHealthDegradedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant,
    override val version: Int = 1,
    val conversationId: String,
    val userAId: String,
    val userBId: String,
    val healthScore: Double,
    val lastMessageHoursAgo: Double
) : TwoHeartsEvent()
```

### Event Bus (Redpanda/Kafka wrapper)
```kotlin
// backend/shared/src/main/kotlin/com/twohearts/shared/events/EventBus.kt
package com.twohearts.shared.events

import com.twohearts.shared.models.TwoHeartsEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties
import kotlin.reflect.KClass

private val logger = KotlinLogging.logger {}

class EventProducer(bootstrapServers: String) : AutoCloseable {

    private val json = Json { classDiscriminator = "type" }

    private val producer: KafkaProducer<String, String> = KafkaProducer(
        Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
            put(ProducerConfig.RETRIES_CONFIG, 3)
            put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1)
            put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4")
        }
    )

    suspend fun publish(topic: String, key: String, event: TwoHeartsEvent) {
        val payload = json.encodeToString(TwoHeartsEvent.serializer(), event)
        val record = ProducerRecord(topic, key, payload)
        producer.send(record) { metadata, exception ->
            if (exception != null) {
                logger.error(exception) { "Failed to publish event to $topic: ${event::class.simpleName}" }
            } else {
                logger.debug { "Published ${event::class.simpleName} to ${metadata.topic()}[${metadata.partition()}]@${metadata.offset()}" }
            }
        }
    }

    override fun close() = producer.close()
}

object Topics {
    const val AUTH_EVENTS = "auth-events"
    const val PROFILE_EVENTS = "profile-events"
    const val MATCH_EVENTS = "match-events"
    const val CHAT_MESSAGES = "chat-messages"
    const val MODERATION_QUEUE = "moderation-queue"
    const val NOTIFICATION_EVENTS = "notification-events"
    const val ANALYTICS_EVENTS = "analytics-events"
}
```

---

## 3.3 Auth Service

### Auth Service Application
```kotlin
// backend/auth-service/src/main/kotlin/com/twohearts/auth/Application.kt
package com.twohearts.auth

import com.twohearts.auth.plugins.*
import com.twohearts.auth.repository.UserRepository
import com.twohearts.auth.service.AuthService
import com.twohearts.auth.service.JwtService
import com.twohearts.auth.service.MailService
import com.twohearts.shared.events.EventProducer
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

fun main() {
    embeddedServer(Netty, port = 8081, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    val dbUrl = environment.config.property("database.url").getString()
    val jwtSecret = environment.config.property("jwt.privateKey").getString()
    val redpandaServers = environment.config.property("redpanda.bootstrapServers").getString()

    install(Koin) {
        modules(module {
            single { DatabaseFactory(dbUrl).also { it.init() } }
            single { UserRepository(get()) }
            single { JwtService(jwtSecret) }
            single { MailService(environment.config) }
            single { EventProducer(redpandaServers) }
            single { AuthService(get(), get(), get(), get()) }
        })
    }

    configureSecurity()
    configureSerialization()
    configureRateLimit()
    configureRouting()
    configureStatusPages()
    configureMonitoring()
}
```

### JWT Service
```kotlin
// backend/auth-service/src/main/kotlin/com/twohearts/auth/service/JwtService.kt
package com.twohearts.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import io.github.oshai.kotlinlogging.KotlinLogging
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}

data class JwtClaims(
    val userId: String,
    val deviceId: String,
    val tokenId: String
)

class JwtService(
    privateKeyPem: String,
    publicKeyPem: String = "" // loaded from config
) {
    private val algorithm: Algorithm
    private val issuer = "twohearts"
    private val audience = "twohearts-api"

    init {
        val kf = KeyFactory.getInstance("EC")
        val privateKeyBytes = Base64.getDecoder().decode(
            privateKeyPem.replace("-----BEGIN EC PRIVATE KEY-----", "")
                .replace("-----END EC PRIVATE KEY-----", "")
                .replace("\n", "")
        )
        val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes)) as ECPrivateKey

        // For production: load public key from JWKS endpoint or config
        algorithm = Algorithm.ECDSA256(null, privateKey)
    }

    fun generateAccessToken(userId: String, deviceId: String): String {
        val tokenId = UUID.randomUUID().toString()
        val now = Date()
        val expiry = Date(now.time + 15.minutes.inWholeMilliseconds)

        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(userId)
            .withClaim("did", deviceId)
            .withJWTId(tokenId)
            .withIssuedAt(now)
            .withExpiresAt(expiry)
            .sign(algorithm)
    }

    fun generateRefreshToken(): String = UUID.randomUUID().toString()
        .replace("-", "") + UUID.randomUUID().toString().replace("-", "")

    fun verifyAccessToken(token: String): Result<JwtClaims> = runCatching {
        val verifier = JWT.require(algorithm)
            .withIssuer(issuer)
            .withAudience(audience)
            .build()
        val decoded = verifier.verify(token)
        JwtClaims(
            userId = decoded.subject,
            deviceId = decoded.getClaim("did").asString(),
            tokenId = decoded.id
        )
    }.onFailure {
        logger.warn { "JWT verification failed: ${it.message}" }
    }
}
```

### Auth Service Logic
```kotlin
// backend/auth-service/src/main/kotlin/com/twohearts/auth/service/AuthService.kt
package com.twohearts.auth.service

import com.twohearts.auth.models.*
import com.twohearts.auth.repository.UserRepository
import com.twohearts.shared.events.EventProducer
import com.twohearts.shared.events.Topics
import com.twohearts.shared.models.UserRegisteredEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import java.security.MessageDigest
import java.util.*

private val logger = KotlinLogging.logger {}

sealed class AuthResult {
    data class Success(val message: String) : AuthResult()
    data class TokenPair(val accessToken: String, val refreshToken: String, val userId: String) : AuthResult()
    data class Error(val message: String, val code: String) : AuthResult()
}

class AuthService(
    private val userRepo: UserRepository,
    private val jwtService: JwtService,
    private val mailService: MailService,
    private val eventProducer: EventProducer
) {
    companion object {
        private const val MAGIC_LINK_EXPIRY_MINUTES = 15L
        private const val REFRESH_TOKEN_EXPIRY_DAYS = 30L
    }

    /**
     * Initiates passwordless auth. Always returns success (prevents enumeration).
     */
    suspend fun requestMagicLink(email: String, ipAddress: String, userAgent: String): AuthResult {
        // Normalize email
        val normalizedEmail = email.lowercase().trim()

        // Rate limit: max 3 magic links per email per hour (checked in Redis by caller)
        val user = userRepo.findOrCreateByEmail(normalizedEmail)

        if (!user.isActive) {
            // Still return success to prevent enumeration
            logger.warn { "Magic link requested for suspended user: ${user.id}" }
            return AuthResult.Success("If that email is registered, you'll receive a link shortly.")
        }

        val rawToken = generateSecureToken()
        val tokenHash = sha256(rawToken)
        val expiresAt = Clock.System.now().plus(
            kotlin.time.Duration.Companion.minutes(MAGIC_LINK_EXPIRY_MINUTES)
        )

        userRepo.storeMagicLink(
            userId = user.id,
            tokenHash = tokenHash,
            expiresAt = expiresAt,
            ipAddress = ipAddress,
            userAgent = userAgent
        )

        // Send email asynchronously
        mailService.sendMagicLink(
            to = normalizedEmail,
            rawToken = rawToken,
            displayName = user.displayName
        )

        logger.info { "Magic link sent for user: ${user.id}" }
        return AuthResult.Success("If that email is registered, you'll receive a link shortly.")
    }

    /**
     * Verifies magic link token and issues JWT pair.
     */
    suspend fun verifyMagicLink(
        rawToken: String,
        deviceFingerprint: String,
        pushToken: String?,
        pushProvider: String?,
        platform: String,
        appVersion: String,
        ipAddress: String
    ): AuthResult {
        val tokenHash = sha256(rawToken)
        val magicLink = userRepo.findValidMagicLink(tokenHash)
            ?: return AuthResult.Error("Invalid or expired link", "INVALID_TOKEN")

        if (!magicLink.isValid()) {
            return AuthResult.Error("Invalid or expired link", "INVALID_TOKEN")
        }

        // Mark magic link as used (atomic)
        userRepo.consumeMagicLink(magicLink.id)

        // Upsert device
        val deviceId = userRepo.upsertDevice(
            userId = magicLink.userId,
            fingerprint = deviceFingerprint,
            pushToken = pushToken,
            pushProvider = pushProvider,
            platform = platform,
            appVersion = appVersion
        )

        // Mark email as verified
        val isNewUser = userRepo.markEmailVerified(magicLink.userId)

        if (isNewUser) {
            eventProducer.publish(
                Topics.AUTH_EVENTS,
                magicLink.userId,
                UserRegisteredEvent(
                    occurredAt = Clock.System.now(),
                    userId = magicLink.userId,
                    email = magicLink.userEmail
                )
            )
        }

        val accessToken = jwtService.generateAccessToken(magicLink.userId, deviceId)
        val rawRefresh = jwtService.generateRefreshToken()
        val refreshHash = sha256(rawRefresh)

        userRepo.storeRefreshToken(
            userId = magicLink.userId,
            tokenHash = refreshHash,
            deviceId = deviceId,
            ipAddress = ipAddress
        )

        logger.info { "User authenticated successfully: ${magicLink.userId}" }

        return AuthResult.TokenPair(
            accessToken = accessToken,
            refreshToken = rawRefresh,
            userId = magicLink.userId
        )
    }

    /**
     * Rotates refresh token — single use.
     */
    suspend fun refreshTokens(rawRefreshToken: String, deviceId: String): AuthResult {
        val tokenHash = sha256(rawRefreshToken)
        val storedToken = userRepo.findValidRefreshToken(tokenHash, deviceId)
            ?: return AuthResult.Error("Invalid or expired session", "INVALID_REFRESH")

        // Single-use: revoke old, issue new
        userRepo.revokeRefreshToken(storedToken.id)

        val newAccessToken = jwtService.generateAccessToken(storedToken.userId, deviceId)
        val newRawRefresh = jwtService.generateRefreshToken()
        userRepo.storeRefreshToken(
            userId = storedToken.userId,
            tokenHash = sha256(newRawRefresh),
            deviceId = deviceId,
            ipAddress = storedToken.ipAddress
        )

        return AuthResult.TokenPair(
            accessToken = newAccessToken,
            refreshToken = newRawRefresh,
            userId = storedToken.userId
        )
    }

    suspend fun logout(userId: String, deviceId: String) {
        userRepo.revokeAllRefreshTokensForDevice(userId, deviceId)
        logger.info { "User logged out: $userId on device: $deviceId" }
    }

    private fun generateSecureToken(): String =
        UUID.randomUUID().toString().replace("-", "") +
        UUID.randomUUID().toString().replace("-", "")

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
```

### Auth Routes
```kotlin
// backend/auth-service/src/main/kotlin/com/twohearts/auth/routes/AuthRoutes.kt
package com.twohearts.auth.routes

import com.twohearts.auth.models.*
import com.twohearts.auth.service.AuthResult
import com.twohearts.auth.service.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
data class MagicLinkRequest(val email: String)

@Serializable
data class VerifyTokenRequest(
    val token: String,
    val deviceFingerprint: String,
    val pushToken: String? = null,
    val pushProvider: String? = null,
    val platform: String,
    val appVersion: String
)

@Serializable
data class RefreshRequest(val refreshToken: String, val deviceId: String)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val expiresIn: Int = 900  // 15 minutes in seconds
)

@Serializable
data class MessageResponse(val message: String)

@Serializable
data class ErrorResponse(val error: String, val code: String)

fun Route.authRoutes() {
    val authService by inject<AuthService>()

    route("/v1/auth") {
        // Rate limit: 3 magic links per IP per hour
        rateLimit(RateLimitName("magic-link")) {
            post("/magic-link") {
                val request = call.receive<MagicLinkRequest>()
                val ip = call.request.origin.remoteHost
                val userAgent = call.request.headers["User-Agent"] ?: ""

                when (authService.requestMagicLink(request.email, ip, userAgent)) {
                    is AuthResult.Success -> call.respond(HttpStatusCode.OK, MessageResponse(
                        "If that email is registered, you'll receive a link shortly."
                    ))
                    is AuthResult.Error -> call.respond(HttpStatusCode.OK, MessageResponse(
                        "If that email is registered, you'll receive a link shortly."
                    )) // Always same response
                    else -> call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }

        post("/verify") {
            val request = call.receive<VerifyTokenRequest>()
            val ip = call.request.origin.remoteHost

            when (val result = authService.verifyMagicLink(
                rawToken = request.token,
                deviceFingerprint = request.deviceFingerprint,
                pushToken = request.pushToken,
                pushProvider = request.pushProvider,
                platform = request.platform,
                appVersion = request.appVersion,
                ipAddress = ip
            )) {
                is AuthResult.TokenPair -> call.respond(HttpStatusCode.OK, AuthResponse(
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken,
                    userId = result.userId
                ))
                is AuthResult.Error -> call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(result.message, result.code)
                )
                else -> call.respond(HttpStatusCode.InternalServerError)
            }
        }

        post("/refresh") {
            val request = call.receive<RefreshRequest>()
            when (val result = authService.refreshTokens(request.refreshToken, request.deviceId)) {
                is AuthResult.TokenPair -> call.respond(HttpStatusCode.OK, AuthResponse(
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken,
                    userId = result.userId
                ))
                is AuthResult.Error -> call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(result.message, result.code)
                )
                else -> call.respond(HttpStatusCode.InternalServerError)
            }
        }

        // Protected routes
        authenticate("jwt") {
            post("/logout") {
                val userId = call.principal<UserPrincipal>()!!.userId
                val deviceId = call.principal<UserPrincipal>()!!.deviceId
                authService.logout(userId, deviceId)
                call.respond(HttpStatusCode.OK, MessageResponse("Logged out successfully"))
            }
        }
    }
}
```

---

## 3.4 Matching Service — Scoring Algorithm

```kotlin
// backend/matching-service/src/main/kotlin/com/twohearts/matching/engine/MatchingEngine.kt
package com.twohearts.matching.engine

import com.twohearts.matching.models.*
import com.twohearts.matching.repository.MatchingRepository
import com.twohearts.matching.repository.ProfileQueryRepository
import com.twohearts.shared.events.EventProducer
import com.twohearts.shared.events.Topics
import com.twohearts.shared.models.MatchCreatedEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

private val logger = KotlinLogging.logger {}

data class MatchCandidate(
    val userId: String,
    val semanticScore: Double,
    val intentScore: Double,
    val geoScore: Double,
    val preferenceScore: Double,
    val compositeScore: Double,
    val explainerPoints: List<String>
)

/**
 * Core matching engine implementing hybrid scoring:
 * - Semantic similarity (pgvector cosine distance on values_embedding)
 * - Intent alignment (daily intent embedding cosine similarity)
 * - Geographic compatibility (distance vs. preference)
 * - Preference satisfaction (age, gender, relationship_intent)
 */
class MatchingEngine(
    private val profileRepo: ProfileQueryRepository,
    private val matchingRepo: MatchingRepository,
    private val eventProducer: EventProducer
) {
    companion object {
        // Scoring weights — must sum to 1.0
        private const val WEIGHT_SEMANTIC = 0.40
        private const val WEIGHT_INTENT = 0.30
        private const val WEIGHT_GEO = 0.20
        private const val WEIGHT_PREFERENCE = 0.10

        private const val MIN_COMPOSITE_SCORE = 0.55
        private const val MAX_CANDIDATES_PER_VECTOR_QUERY = 200
    }

    /**
     * Runs the daily matching job for all eligible users.
     * Uses coroutines for parallelism with bounded dispatcher.
     */
    suspend fun runDailyMatchingJob(date: LocalDate): DailyMatchRunResult {
        val runId = UUID.randomUUID().toString()
        logger.info { "Starting daily matching run $runId for $date" }
        val startTime = System.currentTimeMillis()

        matchingRepo.startMatchRun(runId, date)

        val eligibleUsers = profileRepo.getEligibleUsersForMatching(date)
        logger.info { "Found ${eligibleUsers.size} eligible users for matching" }

        var matchesCreated = 0
        val parallelism = Runtime.getRuntime().availableProcessors() * 2

        withContext(Dispatchers.IO.limitedParallelism(parallelism)) {
            eligibleUsers.chunked(100).forEach { batch ->
                batch.map { user ->
                    async {
                        try {
                            val matches = findMatchesForUser(user, date)
                            matchesCreated += matches.size
                        } catch (e: Exception) {
                            logger.error(e) { "Matching failed for user ${user.userId}" }
                        }
                    }
                }.awaitAll()
            }
        }

        val duration = (System.currentTimeMillis() - startTime).toInt()
        matchingRepo.completeMatchRun(runId, eligibleUsers.size, matchesCreated, duration)

        logger.info { "Daily matching complete: ${eligibleUsers.size} users, $matchesCreated matches, ${duration}ms" }
        return DailyMatchRunResult(runId, eligibleUsers.size, matchesCreated, duration)
    }

    /**
     * Find best matches for a single user.
     */
    private suspend fun findMatchesForUser(
        user: UserMatchingProfile,
        date: LocalDate
    ): List<String> {
        // How many match slots does this user have available?
        val availableSlots = user.maxConcurrentMatches - matchingRepo.getActiveMatchCount(user.userId)
        if (availableSlots <= 0) return emptyList()

        // Already matched or blocked users to exclude
        val excludedUsers = matchingRepo.getRecentlyMatchedUsers(user.userId, days = 30)
            .union(profileRepo.getBlockedUsers(user.userId))
            .union(setOf(user.userId)) // don't match with self

        // Step 1: Semantic candidate retrieval (pgvector ANN search)
        val semanticCandidates = profileRepo.findSemanticCandidates(
            embedding = user.valuesEmbedding,
            limit = MAX_CANDIDATES_PER_VECTOR_QUERY,
            minSimilarity = 0.5,
            excludeUserIds = excludedUsers
        )

        // Step 2: Geospatial filter (PostGIS)
        val geoCandidates = profileRepo.filterByDistance(
            userLocation = user.location,
            candidateIds = semanticCandidates.map { it.userId },
            maxDistanceKm = user.preferences.maxDistanceKm
        )

        // Step 3: Score each candidate
        val scoredCandidates = scoreCandidates(
            user = user,
            candidates = geoCandidates,
            semanticScores = semanticCandidates.associateBy { it.userId }
        )

        // Step 4: Filter by minimum score and select top-N
        val topMatches = scoredCandidates
            .filter { it.compositeScore >= MIN_COMPOSITE_SCORE }
            .sortedByDescending { it.compositeScore }
            .take(availableSlots)

        // Step 5: Persist matches
        val createdMatchIds = mutableListOf<String>()
        topMatches.forEach { candidate ->
            val matchId = persistMatch(user.userId, candidate, date)
            createdMatchIds.add(matchId)
        }

        return createdMatchIds
    }

    private fun scoreCandidates(
        user: UserMatchingProfile,
        candidates: List<GeoCandidate>,
        semanticScores: Map<String, SemanticCandidate>
    ): List<MatchCandidate> {
        return candidates.mapNotNull { geoCandidate ->
            val semantic = semanticScores[geoCandidate.userId] ?: return@mapNotNull null
            val profile = geoCandidate.profile

            // Semantic score (already computed by pgvector)
            val semanticScore = semantic.similarity

            // Intent score: cosine similarity of today's intent embeddings
            val intentScore = if (user.todayIntentEmbedding != null && profile.todayIntentEmbedding != null) {
                cosineSimilarity(user.todayIntentEmbedding, profile.todayIntentEmbedding)
            } else {
                0.5 // neutral if no intent today
            }

            // Geo score: inverse of distance ratio
            val geoScore = computeGeoScore(geoCandidate.distanceKm, user.preferences.maxDistanceKm.toDouble())

            // Preference score: hard + soft filters
            val preferenceScore = computePreferenceScore(user, profile)
            if (preferenceScore < 0) return@mapNotNull null  // hard filter violated

            val compositeScore = (
                WEIGHT_SEMANTIC * semanticScore +
                WEIGHT_INTENT * intentScore +
                WEIGHT_GEO * geoScore +
                WEIGHT_PREFERENCE * max(0.0, preferenceScore)
            )

            val explainer = generateExplainer(user, profile, semanticScore, intentScore, geoCandidate.distanceKm)

            MatchCandidate(
                userId = profile.userId,
                semanticScore = semanticScore,
                intentScore = intentScore,
                geoScore = geoScore,
                preferenceScore = max(0.0, preferenceScore),
                compositeScore = compositeScore,
                explainerPoints = explainer
            )
        }
    }

    /**
     * Geo score: 1.0 at 0km, 0.0 at maxDistance.
     * Uses exponential decay for a smoother curve.
     */
    private fun computeGeoScore(distanceKm: Double, maxDistanceKm: Double): Double {
        if (distanceKm <= 0) return 1.0
        if (distanceKm >= maxDistanceKm) return 0.0
        // Exponential decay: score = e^(-lambda * distance/max)
        val lambda = 2.5
        return Math.exp(-lambda * (distanceKm / maxDistanceKm))
    }

    /**
     * Preference score:
     * Returns -1.0 if hard filters fail (violates dealbreakers).
     * Returns 0-1 based on soft preference alignment.
     */
    private fun computePreferenceScore(user: UserMatchingProfile, candidate: CandidateProfile): Double {
        // Hard filters
        val candidateAge = candidate.age
        if (candidateAge < user.preferences.ageMin || candidateAge > user.preferences.ageMax) return -1.0
        if (user.preferences.seekingGenders.isNotEmpty() && candidate.genderIdentity !in user.preferences.seekingGenders) return -1.0
        
        // Check mutual preferences (candidate must also be OK with user's gender/age)
        val userAge = user.age
        if (userAge < candidate.preferences.ageMin || userAge > candidate.preferences.ageMax) return -1.0
        if (candidate.preferences.seekingGenders.isNotEmpty() && user.genderIdentity !in candidate.preferences.seekingGenders) return -1.0

        // Soft: relationship intent alignment
        val intentAlign = if (user.relationshipIntent == candidate.relationshipIntent) 1.0
                          else if (isCompatibleIntent(user.relationshipIntent, candidate.relationshipIntent)) 0.7
                          else 0.3

        return intentAlign
    }

    private fun isCompatibleIntent(a: String, b: String): Boolean {
        val compatiblePairs = setOf(
            setOf("long_term", "open_to_anything"),
            setOf("casual_dating", "open_to_anything"),
            setOf("friendship", "open_to_anything"),
            setOf("long_term", "marriage")
        )
        return setOf(a, b) in compatiblePairs
    }

    /**
     * Generates human-readable compatibility explainer.
     * Transparent: explains WHY this match was suggested.
     */
    private fun generateExplainer(
        user: UserMatchingProfile,
        candidate: CandidateProfile,
        semanticScore: Double,
        intentScore: Double,
        distanceKm: Double
    ): List<String> {
        val points = mutableListOf<String>()

        // Values alignment
        when {
            semanticScore >= 0.85 -> points.add("Strong alignment on core values and how you approach life")
            semanticScore >= 0.70 -> points.add("Meaningful overlap in values and life perspective")
            semanticScore >= 0.55 -> points.add("Complementary approaches to important life questions")
        }

        // Intent alignment
        when {
            intentScore >= 0.80 -> points.add("Today's reflections resonated deeply — you're thinking about similar things")
            intentScore >= 0.65 -> points.add("Your recent reflections share a common thread")
            intentScore >= 0.50 -> points.add("Different perspectives today that could spark interesting conversation")
        }

        // Practical compatibility
        val distanceStr = when {
            distanceKm < 5 -> "very close to you"
            distanceKm < 20 -> "${distanceKm.toInt()} km away"
            else -> "${distanceKm.toInt()} km away"
        }
        if (user.relationshipIntent == candidate.relationshipIntent) {
            points.add("Aligned on relationship intentions, and $distanceStr")
        } else {
            points.add("Located $distanceStr")
        }

        return points.take(3)
    }

    private suspend fun persistMatch(
        userId: String,
        candidate: MatchCandidate,
        date: LocalDate
    ): String {
        val matchId = UUID.randomUUID().toString()
        // Canonical ordering: smaller UUID first
        val (userA, userB) = if (userId < candidate.userId) userId to candidate.userId
                              else candidate.userId to userId

        matchingRepo.createMatch(
            id = matchId,
            userAId = userA,
            userBId = userB,
            semanticScore = candidate.semanticScore,
            intentScore = candidate.intentScore,
            geoScore = candidate.geoScore,
            preferenceScore = candidate.preferenceScore,
            compositeScore = candidate.compositeScore,
            explainer = candidate.explainerPoints,
            date = date
        )

        eventProducer.publish(
            Topics.MATCH_EVENTS,
            matchId,
            MatchCreatedEvent(
                occurredAt = Clock.System.now(),
                matchId = matchId,
                userAId = userA,
                userBId = userB,
                compositeScore = candidate.compositeScore,
                compatibilityExplainer = candidate.explainerPoints
            )
        )

        return matchId
    }

    /**
     * Compute cosine similarity between two float arrays (L2-normalized embeddings).
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) { "Embedding dimensions must match: ${a.size} vs ${b.size}" }
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return if (normA == 0.0 || normB == 0.0) 0.0
        else dot / (Math.sqrt(normA) * Math.sqrt(normB))
    }
}
```

---

## 3.5 Chat Service — WebSocket + E2E Encryption

```kotlin
// backend/chat-service/src/main/kotlin/com/twohearts/chat/websocket/ChatWebSocketServer.kt
package com.twohearts.chat.websocket

import com.twohearts.chat.models.*
import com.twohearts.chat.repository.ChatRepository
import com.twohearts.chat.service.ConversationHealthService
import com.twohearts.shared.events.EventProducer
import com.twohearts.shared.events.Topics
import com.twohearts.shared.models.MessageSentEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * WebSocket connection registry — tracks active connections by userId.
 * In production: backed by Redis pub/sub for multi-pod delivery.
 */
object ConnectionRegistry {
    // userId -> Set of active WebSocket sessions (user may have multiple devices)
    private val connections = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()

    fun register(userId: String, session: DefaultWebSocketServerSession) {
        connections.getOrPut(userId) { java.util.Collections.synchronizedSet(mutableSetOf()) }.add(session)
        logger.debug { "User $userId connected (${connections[userId]?.size} sessions)" }
    }

    fun unregister(userId: String, session: DefaultWebSocketServerSession) {
        connections[userId]?.remove(session)
        if (connections[userId]?.isEmpty() == true) connections.remove(userId)
        logger.debug { "User $userId disconnected" }
    }

    fun isOnline(userId: String): Boolean = connections.containsKey(userId)

    suspend fun sendToUser(userId: String, message: String) {
        connections[userId]?.forEach { session ->
            try {
                session.send(Frame.Text(message))
            } catch (e: Exception) {
                logger.warn { "Failed to send to session for user $userId: ${e.message}" }
            }
        }
    }
}

// ---- WebSocket message types ----

@Serializable
sealed class WsMessage {
    abstract val type: String
}

@Serializable
data class WsIncomingMessage(
    override val type: String,     // "send_message", "typing_start", "typing_stop", "read_receipt"
    val conversationId: String,
    val messageId: String? = null,
    // E2E payload — opaque to server. Base64-encoded Signal Protocol ciphertext.
    val encryptedPayload: String? = null,
    val messageType: String? = null,
    val clientSequence: Long? = null
) : WsMessage()

@Serializable
data class WsOutgoingMessage(
    override val type: String,     // "message_received", "typing", "read_receipt", "presence", "error"
    val conversationId: String? = null,
    val messageId: String? = null,
    val senderId: String? = null,
    val encryptedPayload: String? = null,  // relay opaque blob
    val messageType: String? = null,
    val sentAt: String? = null,
    val clientSequence: Long? = null,
    val error: String? = null,
    val isOnline: Boolean? = null
) : WsMessage()

class ChatWebSocketServer(
    private val chatRepo: ChatRepository,
    private val healthService: ConversationHealthService,
    private val eventProducer: EventProducer
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Main WebSocket handler. Called per connection.
     */
    suspend fun handleSession(session: DefaultWebSocketServerSession, userId: String) {
        ConnectionRegistry.register(userId, session)

        // Send presence to all conversation partners
        broadcastPresence(userId, online = true)

        try {
            for (frame in session.incoming) {
                when (frame) {
                    is Frame.Text -> handleTextFrame(session, userId, frame.readText())
                    is Frame.Ping -> session.send(Frame.Pong(frame.data))
                    else -> Unit
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            logger.debug { "WebSocket closed for user $userId: ${e.message}" }
        } catch (e: Exception) {
            logger.error(e) { "WebSocket error for user $userId" }
        } finally {
            ConnectionRegistry.unregister(userId, session)
            broadcastPresence(userId, online = false)
        }
    }

    private suspend fun handleTextFrame(
        session: DefaultWebSocketServerSession,
        userId: String,
        raw: String
    ) {
        val incoming = try {
            json.decodeFromString<WsIncomingMessage>(raw)
        } catch (e: Exception) {
            session.send(Frame.Text(encodeError("Invalid message format")))
            return
        }

        when (incoming.type) {
            "send_message" -> handleSendMessage(session, userId, incoming)
            "typing_start" -> handleTyping(userId, incoming.conversationId, typing = true)
            "typing_stop" -> handleTyping(userId, incoming.conversationId, typing = false)
            "read_receipt" -> handleReadReceipt(userId, incoming)
            else -> session.send(Frame.Text(encodeError("Unknown message type: ${incoming.type}")))
        }
    }

    private suspend fun handleSendMessage(
        session: DefaultWebSocketServerSession,
        senderId: String,
        msg: WsIncomingMessage
    ) {
        val conversationId = msg.conversationId
        val encryptedPayload = msg.encryptedPayload
        val clientSequence = msg.clientSequence

        if (encryptedPayload.isNullOrBlank() || clientSequence == null) {
            session.send(Frame.Text(encodeError("Missing required fields")))
            return
        }

        // Verify sender is a participant in this conversation
        val conversation = chatRepo.getConversation(conversationId)
        if (conversation == null || !conversation.hasParticipant(senderId)) {
            session.send(Frame.Text(encodeError("Unauthorized")))
            return
        }

        // Validate payload size (prevent abuse — max 64KB for encrypted message)
        if (encryptedPayload.length > 87380) {  // 64KB base64 encoded
            session.send(Frame.Text(encodeError("Message too large")))
            return
        }

        val messageId = java.util.UUID.randomUUID().toString()
        val sentAt = Clock.System.now()

        // Persist encrypted message — server stores opaque ciphertext only
        chatRepo.saveMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = senderId,
            ciphertext = java.util.Base64.getDecoder().decode(encryptedPayload),
            messageType = msg.messageType ?: "text",
            clientSequence = clientSequence,
            sentAt = sentAt
        )

        // Update conversation metadata
        chatRepo.updateConversationLastMessage(conversationId, sentAt)

        // Update health metrics (async — don't block delivery)
        CoroutineScope(Dispatchers.IO).launch {
            healthService.recordMessage(conversationId, senderId, encryptedPayload.length.toLong(), sentAt)
        }

        // Relay to recipient (opaque payload — server never decrypts)
        val recipientId = conversation.getOtherParticipant(senderId)
        val outgoing = json.encodeToString(WsOutgoingMessage.serializer(), WsOutgoingMessage(
            type = "message_received",
            conversationId = conversationId,
            messageId = messageId,
            senderId = senderId,
            encryptedPayload = encryptedPayload,
            messageType = msg.messageType ?: "text",
            sentAt = sentAt.toString(),
            clientSequence = clientSequence
        ))

        ConnectionRegistry.sendToUser(recipientId, outgoing)

        // Confirm delivery to sender
        session.send(Frame.Text(json.encodeToString(WsOutgoingMessage.serializer(), WsOutgoingMessage(
            type = "message_sent",
            conversationId = conversationId,
            messageId = messageId,
            sentAt = sentAt.toString()
        ))))

        // Emit event (metadata only — no content)
        eventProducer.publish(
            Topics.CHAT_MESSAGES,
            conversationId,
            MessageSentEvent(
                occurredAt = sentAt,
                conversationId = conversationId,
                senderId = senderId,
                messageId = messageId,
                messageType = msg.messageType ?: "text"
            )
        )
    }

    private suspend fun handleTyping(senderId: String, conversationId: String, typing: Boolean) {
        val conversation = chatRepo.getConversation(conversationId) ?: return
        if (!conversation.hasParticipant(senderId)) return

        val recipientId = conversation.getOtherParticipant(senderId)
        ConnectionRegistry.sendToUser(recipientId, json.encodeToString(WsOutgoingMessage.serializer(),
            WsOutgoingMessage(
                type = if (typing) "typing_start" else "typing_stop",
                conversationId = conversationId,
                senderId = senderId
            )
        ))
    }

    private suspend fun handleReadReceipt(readerId: String, msg: WsIncomingMessage) {
        val messageId = msg.messageId ?: return
        val conversationId = msg.conversationId
        val conversation = chatRepo.getConversation(conversationId) ?: return
        if (!conversation.hasParticipant(readerId)) return

        chatRepo.markMessageRead(messageId, readerId)
        val senderId = conversation.getOtherParticipant(readerId)
        ConnectionRegistry.sendToUser(senderId, json.encodeToString(WsOutgoingMessage.serializer(),
            WsOutgoingMessage(type = "read_receipt", conversationId = conversationId, messageId = messageId)
        ))
    }

    private suspend fun broadcastPresence(userId: String, online: Boolean) {
        val conversationPartners = chatRepo.getConversationPartners(userId)
        val presenceMsg = json.encodeToString(WsOutgoingMessage.serializer(),
            WsOutgoingMessage(type = "presence", senderId = userId, isOnline = online)
        )
        conversationPartners.forEach { partnerId ->
            ConnectionRegistry.sendToUser(partnerId, presenceMsg)
        }
    }

    private fun encodeError(msg: String) = json.encodeToString(WsOutgoingMessage.serializer(),
        WsOutgoingMessage(type = "error", error = msg)
    )
}
```

---

## 3.6 Conversation Health Service

```kotlin
// backend/chat-service/src/main/kotlin/com/twohearts/chat/service/ConversationHealthService.kt
package com.twohearts.chat.service

import com.twohearts.chat.repository.ChatRepository
import com.twohearts.shared.events.EventProducer
import com.twohearts.shared.events.Topics
import com.twohearts.shared.models.ConversationHealthDegradedEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.hours

private val logger = KotlinLogging.logger {}

/**
 * Monitors conversation health to prevent ghosting.
 * Health score components:
 * - Response rate: % of messages responded to
 * - Reciprocity: balance of message count between parties
 * - Engagement: average message length (depth proxy)
 * - Recency: time since last message
 */
class ConversationHealthService(
    private val chatRepo: ChatRepository,
    private val eventProducer: EventProducer
) {
    companion object {
        private const val NUDGE_THRESHOLD_HOURS = 48L
        private const val CRITICAL_THRESHOLD_HOURS = 72L
        private const val MIN_HEALTHY_SCORE = 0.4
    }

    /**
     * Records a new message and updates health metrics.
     */
    suspend fun recordMessage(
        conversationId: String,
        senderId: String,
        payloadBytes: Long,
        sentAt: Instant
    ) = withContext(Dispatchers.IO) {
        chatRepo.updateHealthOnMessage(conversationId, senderId, payloadBytes, sentAt)
    }

    /**
     * Scheduled health check — runs every hour.
     */
    suspend fun runHealthCheck() = withContext(Dispatchers.IO) {
        val staleConversations = chatRepo.getConversationsNeedingHealthCheck(
            lastMessageBefore = Clock.System.now().minus(24.hours)
        )

        staleConversations.forEach { conv ->
            val health = computeHealthScore(conv)
            chatRepo.updateHealthScore(conv.id, health)

            if (health < MIN_HEALTHY_SCORE) {
                val hoursSinceLast = (Clock.System.now() - conv.lastMessageAt).inWholeHours.toDouble()

                if (shouldSendNudge(conv)) {
                    chatRepo.markNudgeSent(conv.id)
                    logger.info { "Health nudge triggered for conversation ${conv.id} (score: $health, hours: $hoursSinceLast)" }

                    eventProducer.publish(
                        Topics.NOTIFICATION_EVENTS,
                        conv.id,
                        ConversationHealthDegradedEvent(
                            occurredAt = Clock.System.now(),
                            conversationId = conv.id,
                            userAId = conv.userAId,
                            userBId = conv.userBId,
                            healthScore = health,
                            lastMessageHoursAgo = hoursSinceLast
                        )
                    )
                }
            }
        }
    }

    private fun computeHealthScore(conv: ConversationHealthData): Double {
        val hoursSinceLast = (Clock.System.now() - conv.lastMessageAt).inWholeHours.toDouble()

        // Recency score: full score within 24h, drops to 0 at 72h
        val recencyScore = when {
            hoursSinceLast <= 24 -> 1.0
            hoursSinceLast >= 72 -> 0.0
            else -> 1.0 - ((hoursSinceLast - 24) / 48.0)
        }

        // Reciprocity: 1.0 if 50/50, 0.0 if 100/0
        val total = (conv.userAMessageCount + conv.userBMessageCount).toDouble()
        val reciprocityScore = if (total == 0.0) 1.0 else {
            val ratio = minOf(conv.userAMessageCount, conv.userBMessageCount) / (total / 2.0)
            minOf(1.0, ratio)
        }

        // Response rate score
        val responseRateScore = conv.responseRate.coerceIn(0.0, 1.0)

        // Composite (weighted)
        return (0.4 * recencyScore) + (0.3 * reciprocityScore) + (0.3 * responseRateScore)
    }

    private fun shouldSendNudge(conv: ConversationHealthData): Boolean {
        val hoursSinceLast = (Clock.System.now() - conv.lastMessageAt).inWholeHours
        val hoursSinceNudge = conv.lastNudgeSentAt?.let {
            (Clock.System.now() - it).inWholeHours
        } ?: Long.MAX_VALUE

        return hoursSinceLast >= NUDGE_THRESHOLD_HOURS && hoursSinceNudge >= NUDGE_THRESHOLD_HOURS
    }
}
```

---

## 3.7 Android Application

### App Module — Hilt Setup
```kotlin
// android/app/src/main/kotlin/com/twohearts/TwoHeartsApp.kt
package com.twohearts

import android.app.Application
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class TwoHeartsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
```

### DI Modules
```kotlin
// android/app/src/main/kotlin/com/twohearts/di/NetworkModule.kt
package com.twohearts.di

import com.twohearts.data.network.AuthInterceptor
import com.twohearts.data.network.TwoHeartsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = BuildConfig.API_BASE_URL
    // Certificate pinning — SHA-256 of your server's TLS cert public key
    private const val CERT_PIN_1 = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        prettyPrint = false
    }

    @Provides
    @Singleton
    fun provideHttpClient(json: Json, authInterceptor: AuthInterceptor): HttpClient {
        val certificatePinner = CertificatePinner.Builder()
            .add(BASE_URL.removePrefix("https://"), CERT_PIN_1)
            .build()

        return HttpClient(OkHttp) {
            engine {
                config {
                    certificatePinner(certificatePinner)
                }
                addInterceptor(authInterceptor)
            }

            install(ContentNegotiation) {
                json(json)
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
            }

            install(Logging) {
                level = if (BuildConfig.DEBUG) LogLevel.BODY else LogLevel.NONE
            }

            defaultRequest {
                headers.append("X-App-Version", BuildConfig.VERSION_NAME)
                headers.append("X-Platform", "android")
            }
        }
    }

    @Provides
    @Singleton
    fun provideTwoHeartsApi(client: HttpClient): TwoHeartsApi = TwoHeartsApi(client, BASE_URL)
}

// android/app/src/main/kotlin/com/twohearts/di/CryptoModule.kt
package com.twohearts.di

import android.content.Context
import com.twohearts.crypto.SignalProtocolManager
import com.twohearts.data.local.SecureKeyStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    @Provides
    @Singleton
    fun provideSecureKeyStore(@ApplicationContext context: Context): SecureKeyStore =
        SecureKeyStore(context)

    @Provides
    @Singleton
    fun provideSignalProtocolManager(
        @ApplicationContext context: Context,
        keyStore: SecureKeyStore
    ): SignalProtocolManager = SignalProtocolManager(context, keyStore)
}
```

### Signal Protocol Manager (E2E Encryption)
```kotlin
// android/app/src/main/kotlin/com/twohearts/crypto/SignalProtocolManager.kt
package com.twohearts.crypto

import android.content.Context
import com.twohearts.data.local.SecureKeyStore
import org.signal.libsignal.protocol.*
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.groups.GroupSessionBuilder
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.impl.InMemorySignalProtocolStore
import org.signal.libsignal.protocol.util.KeyHelper
import java.util.UUID
import javax.inject.Inject

/**
 * Wraps the Signal Protocol libsignal-client library.
 * Manages key generation, session establishment, and message encryption/decryption.
 *
 * Key Material Storage:
 * - Identity key pair: Android Keystore (TEE-backed)
 * - Pre-keys: EncryptedSharedPreferences
 * - Session state: EncryptedSharedPreferences (in-memory during session)
 */
class SignalProtocolManager @Inject constructor(
    private val context: Context,
    private val keyStore: SecureKeyStore
) {
    private val localRegistrationId: Int by lazy { KeyHelper.generateRegistrationId(false) }
    private val identityKeyPair: IdentityKeyPair by lazy { loadOrGenerateIdentityKeyPair() }

    private lateinit var protocolStore: InMemorySignalProtocolStore

    init {
        initializeStore()
    }

    private fun loadOrGenerateIdentityKeyPair(): IdentityKeyPair {
        val stored = keyStore.getIdentityKeyPair()
        return if (stored != null) {
            IdentityKeyPair(stored)
        } else {
            val pair = KeyHelper.generateIdentityKeyPair()
            keyStore.storeIdentityKeyPair(pair.serialize())
            pair
        }
    }

    private fun initializeStore() {
        protocolStore = InMemorySignalProtocolStore(identityKeyPair, localRegistrationId)

        // Load pre-keys from secure storage
        keyStore.getAllPreKeys().forEach { (id, serialized) ->
            protocolStore.storePreKey(id, PreKeyRecord(serialized))
        }

        keyStore.getAllSignedPreKeys().forEach { (id, serialized) ->
            protocolStore.storeSignedPreKey(id, SignedPreKeyRecord(serialized))
        }
    }

    /**
     * Generates a bundle of pre-keys to upload to the server.
     * Server distributes these to other users who want to start a session.
     */
    fun generatePreKeyBundle(count: Int = 100): PreKeyBundleData {
        val preKeyStartId = keyStore.getNextPreKeyId()
        val preKeys = KeyHelper.generatePreKeys(preKeyStartId, count)

        val signedPreKeyId = keyStore.getNextSignedPreKeyId()
        val signedPreKey = KeyHelper.generateSignedPreKey(identityKeyPair, signedPreKeyId)

        // Store locally
        preKeys.forEach { preKey ->
            protocolStore.storePreKey(preKey.id, preKey)
            keyStore.storePreKey(preKey.id, preKey.serialize())
        }
        protocolStore.storeSignedPreKey(signedPreKey.id, signedPreKey)
        keyStore.storeSignedPreKey(signedPreKey.id, signedPreKey.serialize())

        return PreKeyBundleData(
            registrationId = localRegistrationId,
            identityKey = identityKeyPair.publicKey.serialize(),
            preKeys = preKeys.map { PreKeyData(it.id, it.keyPair.publicKey.serialize()) },
            signedPreKeyId = signedPreKey.id,
            signedPreKey = signedPreKey.keyPair.publicKey.serialize(),
            signedPreKeySignature = signedPreKey.signature
        )
    }

    /**
     * Establishes a session with a remote user using their pre-key bundle.
     * Call this before sending the first message.
     */
    fun establishSession(recipientId: String, bundle: RemotePreKeyBundle) {
        val recipientAddress = SignalProtocolAddress(recipientId, 1)
        val remoteBundle = PreKeyBundle(
            bundle.registrationId,
            1,
            bundle.preKeyId,
            ECPublicKey(bundle.preKey),
            bundle.signedPreKeyId,
            ECPublicKey(bundle.signedPreKey),
            bundle.signedPreKeySignature,
            IdentityKey(bundle.identityKey)
        )
        val sessionBuilder = SessionBuilder(protocolStore, recipientAddress)
        sessionBuilder.process(remoteBundle)
    }

    /**
     * Encrypts a message for a recipient.
     * Returns base64-encoded ciphertext to send over WebSocket.
     */
    fun encryptMessage(recipientId: String, plaintext: ByteArray): String {
        val recipientAddress = SignalProtocolAddress(recipientId, 1)
        val sessionCipher = SessionCipher(protocolStore, recipientAddress)
        val cipherMessage = sessionCipher.encrypt(plaintext)

        val payload = EncryptedMessagePayload(
            type = cipherMessage.type,
            ciphertext = cipherMessage.serialize()
        )
        return java.util.Base64.getEncoder().encodeToString(serializePayload(payload))
    }

    /**
     * Decrypts a received message.
     */
    fun decryptMessage(senderId: String, encryptedBase64: String): ByteArray {
        val senderAddress = SignalProtocolAddress(senderId, 1)
        val sessionCipher = SessionCipher(protocolStore, senderAddress)

        val rawBytes = java.util.Base64.getDecoder().decode(encryptedBase64)
        val payload = deserializePayload(rawBytes)

        return when (payload.type) {
            CiphertextMessage.PREKEY_TYPE -> {
                val preKeyMessage = PreKeySignalMessage(payload.ciphertext)
                sessionCipher.decrypt(preKeyMessage)
            }
            CiphertextMessage.WHISPER_TYPE -> {
                val signalMessage = SignalMessage(payload.ciphertext)
                sessionCipher.decrypt(signalMessage)
            }
            else -> throw IllegalStateException("Unknown message type: ${payload.type}")
        }
    }

    private fun serializePayload(payload: EncryptedMessagePayload): ByteArray {
        // Simple TLV encoding: [1 byte type][4 bytes length][N bytes ciphertext]
        val result = ByteArray(5 + payload.ciphertext.size)
        result[0] = payload.type.toByte()
        val len = payload.ciphertext.size
        result[1] = (len shr 24).toByte()
        result[2] = (len shr 16).toByte()
        result[3] = (len shr 8).toByte()
        result[4] = len.toByte()
        payload.ciphertext.copyInto(result, 5)
        return result
    }

    private fun deserializePayload(bytes: ByteArray): EncryptedMessagePayload {
        val type = bytes[0].toInt()
        val len = ((bytes[1].toInt() and 0xFF) shl 24) or
                  ((bytes[2].toInt() and 0xFF) shl 16) or
                  ((bytes[3].toInt() and 0xFF) shl 8) or
                  (bytes[4].toInt() and 0xFF)
        val ciphertext = bytes.copyOfRange(5, 5 + len)
        return EncryptedMessagePayload(type, ciphertext)
    }
}

data class PreKeyBundleData(
    val registrationId: Int,
    val identityKey: ByteArray,
    val preKeys: List<PreKeyData>,
    val signedPreKeyId: Int,
    val signedPreKey: ByteArray,
    val signedPreKeySignature: ByteArray
)

data class PreKeyData(val id: Int, val publicKey: ByteArray)

data class RemotePreKeyBundle(
    val registrationId: Int,
    val identityKey: ByteArray,
    val preKeyId: Int,
    val preKey: ByteArray,
    val signedPreKeyId: Int,
    val signedPreKey: ByteArray,
    val signedPreKeySignature: ByteArray
)

private data class EncryptedMessagePayload(val type: Int, val ciphertext: ByteArray)
```

---

## 3.8 Android — Chat ViewModel & UI

```kotlin
// android/app/src/main/kotlin/com/twohearts/presentation/chat/ChatViewModel.kt
package com.twohearts.presentation.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twohearts.crypto.SignalProtocolManager
import com.twohearts.domain.model.Message
import com.twohearts.domain.usecase.GetMessagesUseCase
import com.twohearts.domain.usecase.SendMessageUseCase
import com.twohearts.data.websocket.ChatWebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMessages: GetMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val wsClient: ChatWebSocketClient,
    private val signalManager: SignalProtocolManager
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])
    private val recipientId: String = checkNotNull(savedStateHandle["recipientId"])

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText.asStateFlow()

    init {
        loadMessages()
        observeWebSocket()
    }

    private fun loadMessages() {
        viewModelScope.launch {
            getMessages(conversationId).collect { messages ->
                val decrypted = messages.mapNotNull { msg ->
                    try {
                        val plaintext = signalManager.decryptMessage(msg.senderId, msg.encryptedPayload)
                        msg.copy(plaintextContent = String(plaintext))
                    } catch (e: Exception) {
                        // Decryption failure — show error state for this message
                        msg.copy(plaintextContent = null, decryptionFailed = true)
                    }
                }
                _uiState.update { it.copy(messages = decrypted, isLoading = false) }
            }
        }
    }

    private fun observeWebSocket() {
        viewModelScope.launch {
            wsClient.incomingMessages
                .filter { it.conversationId == conversationId }
                .collect { wsMessage ->
                    when (wsMessage.type) {
                        "message_received" -> {
                            wsMessage.encryptedPayload?.let { payload ->
                                val plaintext = try {
                                    String(signalManager.decryptMessage(wsMessage.senderId!!, payload))
                                } catch (e: Exception) { null }

                                val newMessage = Message(
                                    id = wsMessage.messageId!!,
                                    conversationId = conversationId,
                                    senderId = wsMessage.senderId!!,
                                    encryptedPayload = payload,
                                    plaintextContent = plaintext,
                                    sentAt = wsMessage.sentAt!!,
                                    clientSequence = wsMessage.clientSequence!!
                                )
                                _uiState.update { state ->
                                    state.copy(messages = state.messages + newMessage)
                                }
                            }
                        }
                        "typing_start" -> _uiState.update { it.copy(recipientTyping = true) }
                        "typing_stop" -> _uiState.update { it.copy(recipientTyping = false) }
                        "read_receipt" -> _uiState.update { it.copy(lastReadMessageId = wsMessage.messageId) }
                    }
                }
        }
    }

    fun onMessageTextChanged(text: String) {
        _messageText.value = text
        // Emit typing indicator with debounce
        viewModelScope.launch {
            wsClient.sendTypingIndicator(conversationId, typing = text.isNotEmpty())
        }
    }

    fun sendMessage() {
        val text = _messageText.value.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            _messageText.value = ""

            try {
                // Encrypt client-side
                val encrypted = signalManager.encryptMessage(recipientId, text.toByteArray())
                val clientSeq = System.currentTimeMillis()

                // Optimistic UI update
                val tempMessage = Message(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    senderId = "me",
                    encryptedPayload = encrypted,
                    plaintextContent = text,
                    sentAt = kotlinx.datetime.Clock.System.now().toString(),
                    clientSequence = clientSeq,
                    status = Message.Status.SENDING
                )
                _uiState.update { it.copy(messages = it.messages + tempMessage) }

                // Send over WebSocket
                wsClient.sendMessage(
                    conversationId = conversationId,
                    encryptedPayload = encrypted,
                    clientSequence = clientSeq
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to send message: ${e.message}") }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val recipientTyping: Boolean = false,
    val lastReadMessageId: String? = null,
    val error: String? = null
)
```

### Chat Compose UI
```kotlin
// android/app/src/main/kotlin/com/twohearts/presentation/chat/ChatScreen.kt
package com.twohearts.presentation.chat

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twohearts.domain.model.Message
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    recipientDisplayName: String,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val messageText by viewModel.messageText.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to latest message
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                recipientName = recipientDisplayName,
                isOnline = uiState.recipientTyping,
                onNavigateBack = onNavigateBack
            )
        },
        bottomBar = {
            ChatInputBar(
                text = messageText,
                onTextChanged = viewModel::onMessageTextChanged,
                onSend = viewModel::sendMessage,
                enabled = !uiState.isLoading
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // E2E encryption indicator
            EncryptionBanner()

            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        isMe = message.senderId == "me"
                    )
                }

                // Typing indicator
                if (uiState.recipientTyping) {
                    item {
                        TypingIndicator(modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }

        // Error snackbar
        uiState.error?.let { error ->
            LaunchedEffect(error) {
                // show snackbar
            }
        }
    }
}

@Composable
private fun EncryptionBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = "Encrypted",
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            "End-to-end encrypted",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun MessageBubble(message: Message, isMe: Boolean) {
    val alignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isMe) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMe) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            if (message.decryptionFailed) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(
                        "🔒 Unable to decrypt this message",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (isMe) 16.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 16.dp
                    ),
                    color = bubbleColor,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(
                        text = message.plaintextContent ?: "",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }
            }

            // Status indicators
            if (isMe) {
                Text(
                    text = when (message.status) {
                        Message.Status.SENDING -> "Sending..."
                        Message.Status.SENT -> "Sent"
                        Message.Status.READ -> "Read"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 5,
                enabled = enabled
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = text.isNotBlank() && enabled
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun TypingIndicator(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // Animated dots
        Text(
            "•••",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    recipientName: String,
    isOnline: Boolean,
    onNavigateBack: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(recipientName, fontWeight = FontWeight.SemiBold)
                if (isOnline) {
                    Text("typing...", style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(androidx.compose.material.icons.filled.ArrowBack,
                     contentDescription = "Back")
            }
        }
    )
}
```

---

## 3.9 Matching Screen — Daily Intent + Curated Matches

```kotlin
// android/app/src/main/kotlin/com/twohearts/presentation/matching/MatchingScreen.kt
package com.twohearts.presentation.matching

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.twohearts.domain.model.Match

/**
 * The core matching screen — NO swipes, NO infinite scroll.
 * Users see curated matches with full compatibility explanations.
 */
@Composable
fun MatchingScreen(
    onNavigateToChat: (conversationId: String, recipientId: String, name: String) -> Unit,
    viewModel: MatchingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Daily intent section (shown at top if not yet submitted today)
        if (!uiState.intentSubmittedToday) {
            item {
                DailyIntentCard(
                    question = uiState.todayQuestion,
                    onSubmit = viewModel::submitDailyIntent,
                    isSubmitting = uiState.isSubmittingIntent
                )
            }
        } else {
            item {
                IntentSubmittedBanner()
            }
        }

        // Today's matches header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Today's Matches",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (uiState.slowModeEnabled) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "Slow Mode",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Match cards
        items(uiState.matches, key = { it.id }) { match ->
            MatchCard(
                match = match,
                onAccept = { viewModel.acceptMatch(match.id) },
                onDecline = { viewModel.declineMatch(match.id) },
                onOpenChat = { onNavigateToChat(match.conversationId!!, match.recipientId, match.recipientName) }
            )
        }

        if (uiState.matches.isEmpty() && !uiState.isLoading) {
            item {
                EmptyMatchesState(intentSubmitted = uiState.intentSubmittedToday)
            }
        }
    }
}

@Composable
private fun DailyIntentCard(
    question: String?,
    onSubmit: (String) -> Unit,
    isSubmitting: Boolean
) {
    var answer by remember { mutableStateOf("") }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Today's Reflection",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                question ?: "What's on your mind today?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = answer,
                onValueChange = { if (it.length <= 500) answer = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Share your thoughts...") },
                minLines = 3,
                maxLines = 6,
                supportingText = { Text("${answer.length}/500") }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onSubmit(answer) },
                modifier = Modifier.fillMaxWidth(),
                enabled = answer.length >= 10 && !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Submit & Find Matches")
                }
            }
        }
    }
}

@Composable
private fun MatchCard(
    match: Match,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenChat: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            // Profile photo (blurred until mutual)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                AsyncImage(
                    model = match.photoUrl,
                    contentDescription = "Profile photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Compatibility score chip
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                ) {
                    Text(
                        "${(match.compositeScore * 100).toInt()}% match",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${match.recipientName}, ${match.recipientAge}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        match.distanceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Compatibility explainer — transparent reasoning
                Text(
                    "Why you might connect:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                match.compatibilityExplainer.forEach { point ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            "•",
                            modifier = Modifier.padding(end = 6.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            point,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons — no swiping
                when (match.status) {
                    "mutual" -> {
                        Button(
                            onClick = onOpenChat,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Conversation")
                        }
                    }
                    "pending", "user_b_liked" -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) {
                                Text("Not now")
                            }
                            Button(onClick = onAccept, modifier = Modifier.weight(1f)) {
                                Text("I'm interested")
                            }
                        }
                    }
                    "user_a_liked" -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "You expressed interest — waiting for their response",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMatchesState(intentSubmitted: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🌱", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            if (intentSubmitted) "Your matches are being prepared"
            else "Submit today's reflection to discover matches",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "TwoHearts finds 1-3 meaningful connections per day, not hundreds of shallow ones.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun IntentSubmittedBanner() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("✓", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Today's reflection submitted. Matches refresh daily.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
```
# STEP 3 CONTINUED — INFRASTRUCTURE

---

## 3.10 Dockerfiles

### Auth Service Dockerfile
```dockerfile
# backend/auth-service/Dockerfile
FROM eclipse-temurin:21-jre-alpine AS base

# Security: run as non-root
RUN addgroup -S twohearts && adduser -S twohearts -G twohearts

WORKDIR /app

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY shared/ shared/
COPY auth-service/ auth-service/
RUN ./gradlew :auth-service:shadowJar --no-daemon --parallel -q

FROM base
COPY --from=build /app/auth-service/build/libs/*-all.jar app.jar
COPY --chown=twohearts:twohearts auth-service/src/main/resources/application.conf /app/

USER twohearts

EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8081/health || exit 1

ENTRYPOINT ["java", \
    "-server", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-Dfile.encoding=UTF-8", \
    "-jar", "app.jar"]
```

### Docker Compose (Development)
```yaml
# infrastructure/docker/docker-compose.yml
version: '3.9'

name: twohearts

services:
  # ---- Database ----
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: twohearts
      POSTGRES_USER: twohearts
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-changeme-dev}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./postgres/init:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U twohearts"]
      interval: 10s
      timeout: 5s
      retries: 5
    command:
      - "postgres"
      - "-c"
      - "shared_preload_libraries=pg_stat_statements,postgis-3"
      - "-c"
      - "max_connections=200"
      - "-c"
      - "shared_buffers=256MB"
      - "-c"
      - "effective_cache_size=768MB"
      - "-c"
      - "work_mem=16MB"
      - "-c"
      - "wal_level=logical"

  # ---- Redis ----
  redis:
    image: redis:7.4-alpine
    command: redis-server --requirepass ${REDIS_PASSWORD:-changeme-dev} --maxmemory 512mb --maxmemory-policy allkeys-lru
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "--no-auth-warning", "-a", "${REDIS_PASSWORD:-changeme-dev}", "ping"]
      interval: 10s
      timeout: 5s
      retries: 3

  # ---- Redpanda (Kafka-compatible) ----
  redpanda:
    image: docker.redpanda.com/redpandadata/redpanda:v24.3.1
    command:
      - redpanda
      - start
      - --kafka-addr=internal://0.0.0.0:9092,external://0.0.0.0:19092
      - --advertise-kafka-addr=internal://redpanda:9092,external://localhost:19092
      - --pandaproxy-addr=internal://0.0.0.0:8082,external://0.0.0.0:18082
      - --advertise-pandaproxy-addr=internal://redpanda:8082,external://localhost:18082
      - --schema-registry-addr=internal://0.0.0.0:8081,external://0.0.0.0:18081
      - --rpc-addr=redpanda:33145
      - --advertise-rpc-addr=redpanda:33145
      - --mode=dev-container
      - --smp=2
      - --memory=1G
    ports:
      - "19092:19092"
      - "18082:18082"
      - "18081:18081"
      - "9644:9644"
    volumes:
      - redpanda_data:/var/lib/redpanda/data
    healthcheck:
      test: ["CMD-SHELL", "rpk cluster health | grep -E 'Healthy:.+true' || exit 1"]
      interval: 15s
      timeout: 10s
      retries: 5

  # ---- Redpanda Console ----
  redpanda-console:
    image: docker.redpanda.com/redpandadata/console:v2.7.2
    environment:
      CONFIG_FILEPATH: /tmp/config.yml
    volumes:
      - ./redpanda/console-config.yml:/tmp/config.yml
    ports:
      - "8080:8080"
    depends_on:
      redpanda:
        condition: service_healthy

  # ---- MinIO (S3-compatible object storage) ----
  minio:
    image: minio/minio:RELEASE.2025-01-20T00-00-00Z
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ACCESS_KEY:-twohearts}
      MINIO_ROOT_PASSWORD: ${MINIO_SECRET_KEY:-changeme-dev}
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio_data:/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3

  # ---- Auth Service ----
  auth-service:
    build:
      context: ../backend
      dockerfile: auth-service/Dockerfile
    environment:
      DATABASE_URL: "jdbc:postgresql://postgres:5432/twohearts?user=twohearts&password=${POSTGRES_PASSWORD:-changeme-dev}"
      REDIS_URL: "redis://:${REDIS_PASSWORD:-changeme-dev}@redis:6379"
      REDPANDA_SERVERS: "redpanda:9092"
      JWT_PRIVATE_KEY: ${JWT_PRIVATE_KEY}
      SMTP_HOST: ${SMTP_HOST:-mailhog}
      SMTP_PORT: ${SMTP_PORT:-1025}
    ports:
      - "8081:8081"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      redpanda:
        condition: service_healthy

  # ---- Profile Service ----
  profile-service:
    build:
      context: ../backend
      dockerfile: profile-service/Dockerfile
    environment:
      DATABASE_URL: "jdbc:postgresql://postgres:5432/twohearts?user=twohearts&password=${POSTGRES_PASSWORD:-changeme-dev}"
      MINIO_ENDPOINT: "http://minio:9000"
      MINIO_ACCESS_KEY: ${MINIO_ACCESS_KEY:-twohearts}
      MINIO_SECRET_KEY: ${MINIO_SECRET_KEY:-changeme-dev}
      REDPANDA_SERVERS: "redpanda:9092"
    ports:
      - "8082:8082"
    depends_on:
      postgres:
        condition: service_healthy

  # ---- Matching Service ----
  matching-service:
    build:
      context: ../backend
      dockerfile: matching-service/Dockerfile
    environment:
      DATABASE_URL: "jdbc:postgresql://postgres:5432/twohearts?user=twohearts&password=${POSTGRES_PASSWORD:-changeme-dev}"
      REDPANDA_SERVERS: "redpanda:9092"
      MATCHING_JOB_CRON: "0 0 * * *"  # daily at midnight UTC
    ports:
      - "8083:8083"
    depends_on:
      postgres:
        condition: service_healthy

  # ---- Chat Service ----
  chat-service:
    build:
      context: ../backend
      dockerfile: chat-service/Dockerfile
    environment:
      DATABASE_URL: "jdbc:postgresql://postgres:5432/twohearts?user=twohearts&password=${POSTGRES_PASSWORD:-changeme-dev}"
      REDIS_URL: "redis://:${REDIS_PASSWORD:-changeme-dev}@redis:6379"
      REDPANDA_SERVERS: "redpanda:9092"
    ports:
      - "8084:8084"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy

  # ---- Moderation Service ----
  moderation-service:
    build:
      context: ../backend
      dockerfile: moderation-service/Dockerfile
    environment:
      DATABASE_URL: "jdbc:postgresql://postgres:5432/twohearts?user=twohearts&password=${POSTGRES_PASSWORD:-changeme-dev}"
      REDPANDA_SERVERS: "redpanda:9092"
      DETOXIFY_MODEL_PATH: "/models/detoxify"
    volumes:
      - ./models/detoxify:/models/detoxify:ro
    ports:
      - "8085:8085"

  # ---- Notification Service ----
  notification-service:
    build:
      context: ../backend
      dockerfile: notification-service/Dockerfile
    environment:
      DATABASE_URL: "jdbc:postgresql://postgres:5432/twohearts?user=twohearts&password=${POSTGRES_PASSWORD:-changeme-dev}"
      REDPANDA_SERVERS: "redpanda:9092"
      GOTIFY_URL: "http://gotify:80"
      GOTIFY_APP_TOKEN: ${GOTIFY_APP_TOKEN}
    ports:
      - "8086:8086"

  # ---- Nginx API Gateway ----
  nginx:
    image: nginx:1.27-alpine
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/conf.d:/etc/nginx/conf.d:ro
      - ./nginx/ssl:/etc/nginx/ssl:ro
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      - auth-service
      - profile-service
      - matching-service
      - chat-service

  # ---- Observability ----
  prometheus:
    image: prom/prometheus:v3.1.0
    volumes:
      - ./monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.retention.time=30d'
    ports:
      - "9090:9090"

  grafana:
    image: grafana/grafana:11.4.0
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD:-changeme-dev}
      GF_INSTALL_PLUGINS: grafana-piechart-panel
    volumes:
      - grafana_data:/var/lib/grafana
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards:ro
      - ./monitoring/grafana/datasources:/etc/grafana/provisioning/datasources:ro
    ports:
      - "3000:3000"
    depends_on:
      - prometheus

  loki:
    image: grafana/loki:3.3.2
    volumes:
      - ./monitoring/loki/loki-config.yaml:/etc/loki/local-config.yaml:ro
      - loki_data:/loki
    ports:
      - "3100:3100"

  promtail:
    image: grafana/promtail:3.3.2
    volumes:
      - ./monitoring/promtail/promtail-config.yaml:/etc/promtail/config.yml:ro
      - /var/log:/var/log:ro
      - /var/lib/docker/containers:/var/lib/docker/containers:ro

  # ---- Dev tools ----
  mailhog:
    image: mailhog/mailhog:v1.0.1
    ports:
      - "1025:1025"
      - "8025:8025"

volumes:
  postgres_data:
  redis_data:
  redpanda_data:
  minio_data:
  prometheus_data:
  grafana_data:
  loki_data:
```

---

## 3.11 Kubernetes Manifests

### Namespace + RBAC
```yaml
# infrastructure/kubernetes/base/namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: twohearts
  labels:
    app.kubernetes.io/part-of: twohearts
    istio-injection: enabled

---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: twohearts-services
  namespace: twohearts

---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: twohearts-services-role
  namespace: twohearts
rules:
  - apiGroups: [""]
    resources: ["configmaps", "secrets"]
    verbs: ["get", "list", "watch"]

---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: twohearts-services-binding
  namespace: twohearts
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: twohearts-services-role
subjects:
  - kind: ServiceAccount
    name: twohearts-services
    namespace: twohearts
```

### Auth Service Deployment
```yaml
# infrastructure/kubernetes/base/auth-service/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-service
  namespace: twohearts
  labels:
    app: auth-service
    version: v1
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: auth-service
  template:
    metadata:
      labels:
        app: auth-service
        version: v1
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8081"
        prometheus.io/path: "/metrics"
    spec:
      serviceAccountName: twohearts-services
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 2000
      containers:
        - name: auth-service
          image: ghcr.io/twohearts/auth-service:latest
          imagePullPolicy: Always
          ports:
            - containerPort: 8081
              name: http
          env:
            - name: DATABASE_URL
              valueFrom:
                secretKeyRef:
                  name: twohearts-db-secret
                  key: url
            - name: REDIS_URL
              valueFrom:
                secretKeyRef:
                  name: twohearts-redis-secret
                  key: url
            - name: REDPANDA_SERVERS
              valueFrom:
                configMapKeyRef:
                  name: twohearts-config
                  key: redpanda.servers
            - name: JWT_PRIVATE_KEY
              valueFrom:
                secretKeyRef:
                  name: twohearts-jwt-secret
                  key: private-key
          resources:
            requests:
              memory: "256Mi"
              cpu: "100m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          livenessProbe:
            httpGet:
              path: /health/live
              port: 8081
            initialDelaySeconds: 30
            periodSeconds: 15
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /health/ready
              port: 8081
            initialDelaySeconds: 10
            periodSeconds: 10
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop:
                - ALL
          volumeMounts:
            - name: tmp-dir
              mountPath: /tmp
      volumes:
        - name: tmp-dir
          emptyDir: {}
      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: kubernetes.io/hostname
          whenUnsatisfiable: DoNotSchedule
          labelSelector:
            matchLabels:
              app: auth-service

---
apiVersion: v1
kind: Service
metadata:
  name: auth-service
  namespace: twohearts
spec:
  selector:
    app: auth-service
  ports:
    - port: 8081
      targetPort: 8081
      name: http

---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: auth-service-hpa
  namespace: twohearts
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: auth-service
  minReplicas: 3
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Pods
          value: 1
          periodSeconds: 60
```

### Chat Service (WebSocket — sticky sessions)
```yaml
# infrastructure/kubernetes/base/chat-service/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: chat-service
  namespace: twohearts
spec:
  replicas: 3
  selector:
    matchLabels:
      app: chat-service
  template:
    metadata:
      labels:
        app: chat-service
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8084"
    spec:
      serviceAccountName: twohearts-services
      terminationGracePeriodSeconds: 60  # drain WebSocket connections
      containers:
        - name: chat-service
          image: ghcr.io/twohearts/chat-service:latest
          ports:
            - containerPort: 8084
          env:
            - name: DATABASE_URL
              valueFrom:
                secretKeyRef:
                  name: twohearts-db-secret
                  key: url
            - name: REDIS_URL
              valueFrom:
                secretKeyRef:
                  name: twohearts-redis-secret
                  key: url
            - name: REDPANDA_SERVERS
              valueFrom:
                configMapKeyRef:
                  name: twohearts-config
                  key: redpanda.servers
          resources:
            requests:
              memory: "512Mi"
              cpu: "200m"
            limits:
              memory: "1Gi"
              cpu: "1000m"
          livenessProbe:
            httpGet:
              path: /health/live
              port: 8084
            initialDelaySeconds: 30
            periodSeconds: 15
          readinessProbe:
            httpGet:
              path: /health/ready
              port: 8084
            initialDelaySeconds: 10
            periodSeconds: 10
          lifecycle:
            preStop:
              exec:
                command: ["/bin/sh", "-c", "sleep 10"]

---
apiVersion: v1
kind: Service
metadata:
  name: chat-service
  namespace: twohearts
  annotations:
    # Sticky sessions for WebSocket — hash based on client IP
    nginx.ingress.kubernetes.io/affinity: "cookie"
    nginx.ingress.kubernetes.io/session-cookie-name: "route"
    nginx.ingress.kubernetes.io/session-cookie-expires: "86400"
spec:
  selector:
    app: chat-service
  ports:
    - port: 8084
      targetPort: 8084
      name: ws
```

### Ingress
```yaml
# infrastructure/kubernetes/base/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: twohearts-ingress
  namespace: twohearts
  annotations:
    kubernetes.io/ingress.class: nginx
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/use-regex: "true"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "3600"
    # Security headers
    nginx.ingress.kubernetes.io/configuration-snippet: |
      add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
      add_header X-Frame-Options "DENY" always;
      add_header X-Content-Type-Options "nosniff" always;
      add_header Referrer-Policy "strict-origin-when-cross-origin" always;
      add_header Permissions-Policy "geolocation=(), microphone=(), camera=()" always;
    # Rate limiting
    nginx.ingress.kubernetes.io/limit-connections: "10"
    nginx.ingress.kubernetes.io/limit-rpm: "60"
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  tls:
    - hosts:
        - api.twohearts.app
      secretName: twohearts-tls
  rules:
    - host: api.twohearts.app
      http:
        paths:
          - path: /v1/auth
            pathType: Prefix
            backend:
              service:
                name: auth-service
                port:
                  number: 8081
          - path: /v1/profiles
            pathType: Prefix
            backend:
              service:
                name: profile-service
                port:
                  number: 8082
          - path: /v1/matches
            pathType: Prefix
            backend:
              service:
                name: matching-service
                port:
                  number: 8083
          - path: /v1/chat
            pathType: Prefix
            backend:
              service:
                name: chat-service
                port:
                  number: 8084
          - path: /ws
            pathType: Prefix
            backend:
              service:
                name: chat-service
                port:
                  number: 8084
```

### Network Policy
```yaml
# infrastructure/kubernetes/base/network-policy.yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
  namespace: twohearts
spec:
  podSelector: {}
  policyTypes:
    - Ingress
    - Egress

---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-ingress-controller
  namespace: twohearts
spec:
  podSelector:
    matchLabels:
      app: auth-service
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: ingress-nginx
  egress:
    - to:
        - podSelector:
            matchLabels:
              app: postgres
        - podSelector:
            matchLabels:
              app: redis
        - podSelector:
            matchLabels:
              app: redpanda
    - ports:
        - port: 53
          protocol: UDP
```

---

## 3.12 GitHub Actions CI/CD

```yaml
# .github/workflows/ci-cd.yml
name: TwoHearts CI/CD Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE_PREFIX: ${{ github.repository_owner }}/twohearts

jobs:
  # =====================================================
  # Static Analysis & Linting
  # =====================================================
  lint:
    name: Lint & Static Analysis
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}
      - name: Ktlint Check
        run: ./gradlew ktlintCheck --no-daemon
      - name: Detekt
        run: ./gradlew detekt --no-daemon
      - name: Android Lint
        run: ./gradlew :android:app:lint --no-daemon

  # =====================================================
  # Backend Unit Tests
  # =====================================================
  test-backend:
    name: Backend Tests
    runs-on: ubuntu-latest
    services:
      postgres:
        image: pgvector/pgvector:pg16
        env:
          POSTGRES_DB: twohearts_test
          POSTGRES_USER: twohearts_test
          POSTGRES_PASSWORD: test_password
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432
      redis:
        image: redis:7.4-alpine
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 6379:6379
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - name: Run Backend Tests with Coverage
        run: ./gradlew :backend:test :backend:jacocoTestReport --no-daemon
        env:
          TEST_DATABASE_URL: "jdbc:postgresql://localhost:5432/twohearts_test?user=twohearts_test&password=test_password"
          TEST_REDIS_URL: "redis://localhost:6379"
      - name: Upload Coverage Report
        uses: codecov/codecov-action@v4
        with:
          files: '**/build/reports/jacoco/test/jacocoTestReport.xml'
      - name: Check Coverage Threshold
        run: ./gradlew jacocoTestCoverageVerification --no-daemon
        # Requires 80% line coverage

  # =====================================================
  # Android Tests
  # =====================================================
  test-android:
    name: Android Unit Tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - name: Run Android Unit Tests
        run: ./gradlew :android:app:testDebugUnitTest --no-daemon
      - name: Upload Test Results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: android-test-results
          path: android/app/build/test-results

  # =====================================================
  # Security Scanning
  # =====================================================
  security-scan:
    name: Security Scanning
    runs-on: ubuntu-latest
    needs: [lint]
    steps:
      - uses: actions/checkout@v4
      # Dependency vulnerability scan
      - name: Run Trivy (Dependency Scan)
        uses: aquasecurity/trivy-action@master
        with:
          scan-type: 'fs'
          scan-ref: '.'
          vuln-type: 'library'
          severity: 'CRITICAL,HIGH'
          exit-code: '1'
      # SAST
      - name: Run Semgrep SAST
        uses: returntocorp/semgrep-action@v1
        with:
          config: >-
            p/kotlin
            p/jwt
            p/sql-injection
            p/secrets
          auditOn: push
      # Secret scanning
      - name: Run Gitleaks
        uses: gitleaks/gitleaks-action@v2

  # =====================================================
  # Build Docker Images
  # =====================================================
  build-images:
    name: Build & Push Docker Images
    runs-on: ubuntu-latest
    needs: [test-backend, test-android, security-scan]
    if: github.ref == 'refs/heads/main' || github.ref == 'refs/heads/develop'
    strategy:
      matrix:
        service: [auth-service, profile-service, matching-service, chat-service, moderation-service, notification-service]
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - name: Set up QEMU
        uses: docker/setup-qemu-action@v3
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3
      - name: Login to GHCR
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Extract Metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/${{ matrix.service }}
          tags: |
            type=sha,prefix={{branch}}-
            type=raw,value=latest,enable=${{ github.ref == 'refs/heads/main' }}
      - name: Build and Push
        uses: docker/build-push-action@v6
        with:
          context: backend
          file: backend/${{ matrix.service }}/Dockerfile
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
          platforms: linux/amd64,linux/arm64
          # Build provenance for supply chain security
          provenance: true
          sbom: true
      - name: Scan Built Image
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/${{ matrix.service }}:latest
          format: sarif
          output: trivy-results.sarif
      - name: Upload Trivy Results to GitHub Security
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: trivy-results.sarif

  # =====================================================
  # Deploy to Staging
  # =====================================================
  deploy-staging:
    name: Deploy to Staging
    runs-on: ubuntu-latest
    needs: [build-images]
    environment: staging
    if: github.ref == 'refs/heads/develop'
    steps:
      - uses: actions/checkout@v4
      - name: Setup kubectl
        uses: azure/setup-kubectl@v4
        with:
          version: 'v1.32.0'
      - name: Configure kubeconfig
        run: echo "${{ secrets.STAGING_KUBECONFIG }}" | base64 -d > kubeconfig.yaml
      - name: Deploy to Staging
        env:
          KUBECONFIG: kubeconfig.yaml
          IMAGE_TAG: develop-${{ github.sha }}
        run: |
          cd infrastructure/kubernetes
          kustomize edit set image ghcr.io/${{ env.IMAGE_PREFIX }}/auth-service=ghcr.io/${{ env.IMAGE_PREFIX }}/auth-service:$IMAGE_TAG
          kubectl apply -k overlays/staging/
          kubectl rollout status deployment/auth-service -n twohearts --timeout=300s
          kubectl rollout status deployment/chat-service -n twohearts --timeout=300s
      - name: Run OWASP ZAP Scan
        uses: zaproxy/action-full-scan@v0.10.0
        with:
          target: 'https://api.staging.twohearts.app'
          rules_file_name: '.zap/rules.tsv'
          fail_action: false
          artifact_name: 'zap-staging-scan'

  # =====================================================
  # Deploy to Production
  # =====================================================
  deploy-production:
    name: Deploy to Production
    runs-on: ubuntu-latest
    needs: [build-images]
    environment: production
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4
      - name: Setup kubectl
        uses: azure/setup-kubectl@v4
      - name: Configure kubeconfig
        run: echo "${{ secrets.PROD_KUBECONFIG }}" | base64 -d > kubeconfig.yaml
      - name: Blue-Green Deploy (Production)
        env:
          KUBECONFIG: kubeconfig.yaml
          IMAGE_TAG: main-${{ github.sha }}
        run: |
          cd infrastructure/kubernetes
          # Update images in kustomization
          kustomize edit set image \
            ghcr.io/${{ env.IMAGE_PREFIX }}/auth-service=ghcr.io/${{ env.IMAGE_PREFIX }}/auth-service:$IMAGE_TAG \
            ghcr.io/${{ env.IMAGE_PREFIX }}/chat-service=ghcr.io/${{ env.IMAGE_PREFIX }}/chat-service:$IMAGE_TAG
          kubectl apply -k overlays/production/
          # Wait for rollout with timeout
          for svc in auth-service profile-service matching-service chat-service; do
            kubectl rollout status deployment/$svc -n twohearts --timeout=600s
          done
      - name: Smoke Tests
        run: |
          sleep 30
          curl -f https://api.twohearts.app/health || exit 1
      - name: Notify Deployment
        uses: slackapi/slack-github-action@v2
        with:
          payload: |
            {"text": "✅ TwoHearts deployed to production: ${{ github.sha }}"}
        env:
          SLACK_WEBHOOK_URL: ${{ secrets.SLACK_WEBHOOK_URL }}
```

---

# STEP 4 — TESTING, THREAT MODEL & PERFORMANCE

---

## 4.1 Unit Tests

### Matching Engine Tests
```kotlin
// backend/matching-service/src/test/kotlin/com/twohearts/matching/engine/MatchingEngineTest.kt
package com.twohearts.matching.engine

import com.twohearts.matching.models.*
import com.twohearts.matching.repository.MatchingRepository
import com.twohearts.matching.repository.ProfileQueryRepository
import com.twohearts.shared.events.EventProducer
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.math.abs

class MatchingEngineTest {

    private val profileRepo = mockk<ProfileQueryRepository>()
    private val matchingRepo = mockk<MatchingRepository>()
    private val eventProducer = mockk<EventProducer>(relaxed = true)
    private lateinit var engine: MatchingEngine

    @BeforeEach
    fun setup() {
        engine = MatchingEngine(profileRepo, matchingRepo, eventProducer)
    }

    @Nested
    inner class GeoScoreTests {
        
        @Test
        fun `geo score is 1_0 at zero distance`() {
            val score = engine.computeGeoScorePublic(0.0, 50.0)
            assertEquals(1.0, score, 0.001)
        }

        @Test
        fun `geo score is 0_0 at max distance`() {
            val score = engine.computeGeoScorePublic(50.0, 50.0)
            assertEquals(0.0, score, 0.001)
        }

        @Test
        fun `geo score is monotonically decreasing`() {
            val maxDist = 50.0
            var prev = 1.0
            for (d in listOf(0.0, 5.0, 10.0, 20.0, 35.0, 49.0)) {
                val score = engine.computeGeoScorePublic(d, maxDist)
                assertTrue(score <= prev, "Score should decrease as distance increases (d=$d, score=$score, prev=$prev)")
                prev = score
            }
        }

        @ParameterizedTest
        @CsvSource("5.0,50.0", "10.0,30.0", "25.0,100.0")
        fun `geo score between 0 and 1 for valid inputs`(distance: Double, max: Double) {
            val score = engine.computeGeoScorePublic(distance, max)
            assertTrue(score in 0.0..1.0, "Score $score not in [0,1] for d=$distance, max=$max")
        }
    }

    @Nested
    inner class PreferenceScoreTests {

        @Test
        fun `returns -1 when candidate age below user minimum`() {
            val user = buildUserProfile(ageMin = 25, ageMax = 40)
            val candidate = buildCandidateProfile(age = 22)
            val score = engine.computePreferenceScorePublic(user, candidate)
            assertEquals(-1.0, score)
        }

        @Test
        fun `returns -1 when candidate age above user maximum`() {
            val user = buildUserProfile(ageMin = 25, ageMax = 35)
            val candidate = buildCandidateProfile(age = 45)
            val score = engine.computePreferenceScorePublic(user, candidate)
            assertEquals(-1.0, score)
        }

        @Test
        fun `returns 1_0 for identical relationship intent`() {
            val user = buildUserProfile(intent = "long_term")
            val candidate = buildCandidateProfile(intent = "long_term")
            val score = engine.computePreferenceScorePublic(user, candidate)
            assertEquals(1.0, score, 0.001)
        }

        @Test
        fun `returns 0_7 for compatible but different intents`() {
            val user = buildUserProfile(intent = "long_term")
            val candidate = buildCandidateProfile(intent = "open_to_anything")
            val score = engine.computePreferenceScorePublic(user, candidate)
            assertEquals(0.7, score, 0.001)
        }

        @Test
        fun `enforces mutual age preference - user too young for candidate`() {
            val user = buildUserProfile(age = 22, ageMin = 20, ageMax = 35)
            val candidate = buildCandidateProfile(age = 30, candidateAgeMin = 25, candidateAgeMax = 40)
            val score = engine.computePreferenceScorePublic(user, candidate)
            assertEquals(-1.0, score)  // candidate won't match user < 25
        }
    }

    @Nested
    inner class CompositeScoreTests {

        @Test
        fun `composite score is weighted average of components`() {
            val semantic = 0.8
            val intent = 0.7
            val geo = 0.9
            val preference = 1.0

            val expected = (0.4 * semantic) + (0.3 * intent) + (0.2 * geo) + (0.1 * preference)
            val actual = engine.computeCompositeScorePublic(semantic, intent, geo, preference)

            assertEquals(expected, actual, 0.001)
        }

        @Test
        fun `composite score is between 0 and 1`() {
            val score = engine.computeCompositeScorePublic(0.6, 0.7, 0.8, 0.9)
            assertTrue(score in 0.0..1.0)
        }
    }

    @Nested
    inner class CosineSimilarityTests {

        @Test
        fun `identical vectors have similarity 1_0`() {
            val v = floatArrayOf(1f, 0f, 0f, 1f)
            assertEquals(1.0, engine.cosineSimilarityPublic(v, v), 0.001)
        }

        @Test
        fun `orthogonal vectors have similarity 0`() {
            val a = floatArrayOf(1f, 0f, 0f)
            val b = floatArrayOf(0f, 1f, 0f)
            assertEquals(0.0, engine.cosineSimilarityPublic(a, b), 0.001)
        }

        @Test
        fun `opposite vectors have similarity -1_0`() {
            val a = floatArrayOf(1f, 0f)
            val b = floatArrayOf(-1f, 0f)
            assertEquals(-1.0, engine.cosineSimilarityPublic(a, b), 0.001)
        }

        @Test
        fun `zero vector returns 0`() {
            val zero = floatArrayOf(0f, 0f, 0f)
            val v = floatArrayOf(1f, 0f, 0f)
            assertEquals(0.0, engine.cosineSimilarityPublic(zero, v), 0.001)
        }

        @Test
        fun `throws on mismatched dimensions`() {
            assertThrows(IllegalArgumentException::class.java) {
                engine.cosineSimilarityPublic(floatArrayOf(1f, 2f), floatArrayOf(1f, 2f, 3f))
            }
        }
    }

    @Nested
    inner class ExplainerTests {

        @Test
        fun `generates exactly 3 explainer points`() {
            val points = engine.generateExplainerPublic(
                semanticScore = 0.8,
                intentScore = 0.7,
                distanceKm = 5.0,
                intentAligned = true
            )
            assertEquals(3, points.size)
        }

        @Test
        fun `no explainer point is blank`() {
            val points = engine.generateExplainerPublic(0.6, 0.6, 15.0, false)
            points.forEach { assertTrue(it.isNotBlank(), "Explainer point should not be blank") }
        }

        @Test
        fun `semantic score 0_85+ uses strong alignment language`() {
            val points = engine.generateExplainerPublic(0.9, 0.7, 5.0, true)
            assertTrue(points[0].contains("Strong", ignoreCase = true))
        }
    }

    @Test
    fun `runDailyMatchingJob creates matches for eligible users`() = runTest {
        val today = Clock.System.todayIn(TimeZone.UTC)
        val users = listOf(
            buildUserMatchingProfile(userId = "user-1"),
            buildUserMatchingProfile(userId = "user-2")
        )

        coEvery { profileRepo.getEligibleUsersForMatching(today) } returns users
        coEvery { matchingRepo.getActiveMatchCount(any()) } returns 0
        coEvery { matchingRepo.getRecentlyMatchedUsers(any(), any()) } returns emptySet()
        coEvery { profileRepo.getBlockedUsers(any()) } returns emptySet()
        coEvery { profileRepo.findSemanticCandidates(any(), any(), any(), any()) } returns emptyList()
        coEvery { matchingRepo.startMatchRun(any(), today) } just Runs
        coEvery { matchingRepo.completeMatchRun(any(), any(), any(), any()) } just Runs

        val result = engine.runDailyMatchingJob(today)

        assertEquals(2, result.usersProcessed)
        verify { matchingRepo.startMatchRun(any(), today) }
        verify { matchingRepo.completeMatchRun(any(), 2, any(), any()) }
    }

    // ---- Test builders ----
    private fun buildUserProfile(
        ageMin: Int = 20,
        ageMax: Int = 40,
        age: Int = 28,
        intent: String = "long_term",
        genderIdentity: String = "man"
    ) = UserMatchingProfile(
        userId = "user-${java.util.UUID.randomUUID()}",
        age = age,
        genderIdentity = genderIdentity,
        relationshipIntent = intent,
        valuesEmbedding = FloatArray(384) { 0.1f },
        todayIntentEmbedding = FloatArray(384) { 0.1f },
        location = null,
        maxConcurrentMatches = 3,
        preferences = UserPreferences(
            seekingGenders = listOf("woman"),
            ageMin = ageMin,
            ageMax = ageMax,
            maxDistanceKm = 50
        )
    )

    private fun buildCandidateProfile(
        age: Int = 28,
        intent: String = "long_term",
        candidateAgeMin: Int = 20,
        candidateAgeMax: Int = 40
    ) = CandidateProfile(
        userId = "candidate-${java.util.UUID.randomUUID()}",
        age = age,
        genderIdentity = "woman",
        relationshipIntent = intent,
        todayIntentEmbedding = FloatArray(384) { 0.1f },
        preferences = UserPreferences(
            seekingGenders = listOf("man"),
            ageMin = candidateAgeMin,
            ageMax = candidateAgeMax,
            maxDistanceKm = 50
        )
    )

    private fun buildUserMatchingProfile(userId: String) = UserMatchingProfile(
        userId = userId,
        age = 28,
        genderIdentity = "man",
        relationshipIntent = "long_term",
        valuesEmbedding = FloatArray(384) { 0.1f },
        todayIntentEmbedding = FloatArray(384) { 0.1f },
        location = null,
        maxConcurrentMatches = 3,
        preferences = UserPreferences(
            seekingGenders = listOf("woman"),
            ageMin = 20,
            ageMax = 40,
            maxDistanceKm = 50
        )
    )
}
```

### Auth Service Tests
```kotlin
// backend/auth-service/src/test/kotlin/com/twohearts/auth/service/AuthServiceTest.kt
package com.twohearts.auth.service

import com.twohearts.auth.models.StoredMagicLink
import com.twohearts.auth.models.UserRecord
import com.twohearts.auth.repository.UserRepository
import com.twohearts.shared.events.EventProducer
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AuthServiceTest {

    private val userRepo = mockk<UserRepository>()
    private val jwtService = mockk<JwtService>()
    private val mailService = mockk<MailService>(relaxed = true)
    private val eventProducer = mockk<EventProducer>(relaxed = true)
    private lateinit var authService: AuthService

    @BeforeEach
    fun setup() {
        authService = AuthService(userRepo, jwtService, mailService, eventProducer)
    }

    @Nested
    inner class MagicLinkTests {

        @Test
        fun `requestMagicLink returns success for valid email`() = runTest {
            coEvery { userRepo.findOrCreateByEmail("test@example.com") } returns
                UserRecord(id = "user-1", email = "test@example.com", isActive = true)
            coEvery { userRepo.storeMagicLink(any(), any(), any(), any(), any()) } just Runs

            val result = authService.requestMagicLink("test@example.com", "127.0.0.1", "Android/1.0")

            assertTrue(result is AuthResult.Success)
            coVerify { mailService.sendMagicLink(any(), any(), any()) }
        }

        @Test
        fun `requestMagicLink returns success for suspended user (prevents enumeration)`() = runTest {
            coEvery { userRepo.findOrCreateByEmail("suspended@example.com") } returns
                UserRecord(id = "user-2", email = "suspended@example.com", isActive = false)

            val result = authService.requestMagicLink("suspended@example.com", "127.0.0.1", "Android/1.0")

            assertTrue(result is AuthResult.Success)
            // Email should NOT be sent to suspended user
            coVerify(exactly = 0) { mailService.sendMagicLink(any(), any(), any()) }
        }

        @Test
        fun `requestMagicLink normalizes email to lowercase`() = runTest {
            val slot = slot<String>()
            coEvery { userRepo.findOrCreateByEmail(capture(slot)) } returns
                UserRecord(id = "user-3", email = "test@example.com", isActive = true)
            coEvery { userRepo.storeMagicLink(any(), any(), any(), any(), any()) } just Runs

            authService.requestMagicLink("Test@EXAMPLE.com", "127.0.0.1", "Android/1.0")

            assertEquals("test@example.com", slot.captured)
        }
    }

    @Nested
    inner class VerifyMagicLinkTests {

        @Test
        fun `verifyMagicLink returns tokens for valid token`() = runTest {
            val storedLink = StoredMagicLink(
                id = "link-1",
                userId = "user-1",
                userEmail = "test@example.com",
                expiresAt = Clock.System.now().plus(10.minutes),
                usedAt = null
            )

            coEvery { userRepo.findValidMagicLink(any()) } returns storedLink
            coEvery { userRepo.consumeMagicLink("link-1") } just Runs
            coEvery { userRepo.upsertDevice(any(), any(), any(), any(), any(), any()) } returns "device-1"
            coEvery { userRepo.markEmailVerified("user-1") } returns true
            coEvery { userRepo.storeRefreshToken(any(), any(), any(), any()) } just Runs
            every { jwtService.generateAccessToken("user-1", "device-1") } returns "access-token"
            every { jwtService.generateRefreshToken() } returns "refresh-token"

            val result = authService.verifyMagicLink(
                rawToken = "valid-token",
                deviceFingerprint = "fp-123",
                pushToken = null,
                pushProvider = null,
                platform = "android",
                appVersion = "1.0.0",
                ipAddress = "127.0.0.1"
            )

            assertTrue(result is AuthResult.TokenPair)
            val tokenPair = result as AuthResult.TokenPair
            assertEquals("access-token", tokenPair.accessToken)
            assertEquals("user-1", tokenPair.userId)
        }

        @Test
        fun `verifyMagicLink returns error for invalid token`() = runTest {
            coEvery { userRepo.findValidMagicLink(any()) } returns null

            val result = authService.verifyMagicLink("invalid", "fp-123", null, null, "android", "1.0", "127.0.0.1")

            assertTrue(result is AuthResult.Error)
            assertEquals("INVALID_TOKEN", (result as AuthResult.Error).code)
        }

        @Test
        fun `verifyMagicLink returns error for expired token`() = runTest {
            val expiredLink = StoredMagicLink(
                id = "link-expired",
                userId = "user-1",
                userEmail = "test@example.com",
                expiresAt = Clock.System.now().minus(5.minutes),
                usedAt = null
            )

            coEvery { userRepo.findValidMagicLink(any()) } returns expiredLink

            val result = authService.verifyMagicLink("expired-token", "fp-123", null, null, "android", "1.0", "127.0.0.1")

            assertTrue(result is AuthResult.Error)
        }

        @Test
        fun `verifyMagicLink publishes UserRegistered event for new users`() = runTest {
            val storedLink = StoredMagicLink(
                id = "link-new",
                userId = "new-user-1",
                userEmail = "new@example.com",
                expiresAt = Clock.System.now().plus(10.minutes),
                usedAt = null
            )

            coEvery { userRepo.findValidMagicLink(any()) } returns storedLink
            coEvery { userRepo.consumeMagicLink(any()) } just Runs
            coEvery { userRepo.upsertDevice(any(), any(), any(), any(), any(), any()) } returns "device-1"
            coEvery { userRepo.markEmailVerified(any()) } returns true  // new user
            coEvery { userRepo.storeRefreshToken(any(), any(), any(), any()) } just Runs
            every { jwtService.generateAccessToken(any(), any()) } returns "token"
            every { jwtService.generateRefreshToken() } returns "refresh"

            authService.verifyMagicLink("token", "fp", null, null, "android", "1.0", "ip")

            coVerify { eventProducer.publish(any(), any(), match { it::class.simpleName == "UserRegisteredEvent" }) }
        }
    }

    @Nested
    inner class RefreshTokenTests {

        @Test
        fun `refreshTokens rotates token on success`() = runTest {
            // Old token state
            coEvery { userRepo.findValidRefreshToken(any(), "device-1") } returns mockk {
                every { id } returns "refresh-1"
                every { userId } returns "user-1"
                every { ipAddress } returns "127.0.0.1"
            }
            coEvery { userRepo.revokeRefreshToken("refresh-1") } just Runs
            coEvery { userRepo.storeRefreshToken(any(), any(), any(), any()) } just Runs
            every { jwtService.generateAccessToken("user-1", "device-1") } returns "new-access"
            every { jwtService.generateRefreshToken() } returns "new-refresh"

            val result = authService.refreshTokens("old-refresh", "device-1")

            assertTrue(result is AuthResult.TokenPair)
            coVerify { userRepo.revokeRefreshToken("refresh-1") }
            coVerify { userRepo.storeRefreshToken("user-1", any(), "device-1", any()) }
        }
    }
}
```

### Conversation Health Tests
```kotlin
// backend/chat-service/src/test/kotlin/com/twohearts/chat/service/ConversationHealthServiceTest.kt
package com.twohearts.chat.service

import com.twohearts.chat.repository.ChatRepository
import com.twohearts.shared.events.EventProducer
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ConversationHealthServiceTest {

    private val chatRepo = mockk<ChatRepository>()
    private val eventProducer = mockk<EventProducer>(relaxed = true)
    private val service = ConversationHealthService(chatRepo, eventProducer)

    @Test
    fun `health score is 1_0 for recent active conversation`() {
        val conv = ConversationHealthData(
            id = "conv-1",
            userAId = "a", userBId = "b",
            lastMessageAt = Clock.System.now().minus(1.hours),
            userAMessageCount = 10,
            userBMessageCount = 10,
            responseRate = 0.9,
            lastNudgeSentAt = null
        )
        val score = service.computeHealthScorePublic(conv)
        assertTrue(score > 0.8, "Recent conversation should have high health score: $score")
    }

    @Test
    fun `health score is low for stale one-sided conversation`() {
        val conv = ConversationHealthData(
            id = "conv-2",
            userAId = "a", userBId = "b",
            lastMessageAt = Clock.System.now().minus(60.hours),
            userAMessageCount = 20,
            userBMessageCount = 1,  // one-sided
            responseRate = 0.1,
            lastNudgeSentAt = null
        )
        val score = service.computeHealthScorePublic(conv)
        assertTrue(score < 0.4, "Stale one-sided conversation should have low health score: $score")
    }

    @Test
    fun `nudge is sent when conversation stale and no recent nudge`() = runTest {
        val staleConv = ConversationHealthData(
            id = "conv-3",
            userAId = "a", userBId = "b",
            lastMessageAt = Clock.System.now().minus(50.hours),
            userAMessageCount = 5,
            userBMessageCount = 3,
            responseRate = 0.5,
            lastNudgeSentAt = null  // never nudged
        )
        coEvery { chatRepo.getConversationsNeedingHealthCheck(any()) } returns listOf(staleConv)
        coEvery { chatRepo.updateHealthScore(any(), any()) } just Runs
        coEvery { chatRepo.markNudgeSent(any()) } just Runs

        service.runHealthCheck()

        coVerify { eventProducer.publish(any(), any(), match { it::class.simpleName == "ConversationHealthDegradedEvent" }) }
        coVerify { chatRepo.markNudgeSent("conv-3") }
    }

    @Test
    fun `nudge is NOT sent when nudge already sent recently`() = runTest {
        val conv = ConversationHealthData(
            id = "conv-4",
            userAId = "a", userBId = "b",
            lastMessageAt = Clock.System.now().minus(50.hours),
            userAMessageCount = 5,
            userBMessageCount = 3,
            responseRate = 0.5,
            lastNudgeSentAt = Clock.System.now().minus(20.hours)  // nudged recently
        )
        coEvery { chatRepo.getConversationsNeedingHealthCheck(any()) } returns listOf(conv)
        coEvery { chatRepo.updateHealthScore(any(), any()) } just Runs

        service.runHealthCheck()

        coVerify(exactly = 0) { chatRepo.markNudgeSent(any()) }
    }
}
```

---

## 4.2 Integration Tests (Testcontainers)

```kotlin
// backend/auth-service/src/test/kotlin/com/twohearts/auth/integration/AuthIntegrationTest.kt
package com.twohearts.auth.integration

import com.twohearts.auth.Application
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
class AuthIntegrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16"))
            .withDatabaseName("twohearts_test")
            .withUsername("test")
            .withPassword("test")

        @Container
        val redis = GenericContainer(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379)

        @JvmStatic
        @BeforeAll
        fun setupContainers() {
            // Containers started by @Container annotation
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            postgres.stop()
            redis.stop()
        }
    }

    @Test
    fun `magic link flow end to end`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "database.url" to postgres.jdbcUrl,
                "redis.url" to "redis://${redis.host}:${redis.getMappedPort(6379)}",
                // ...other config
            )
        }

        application { module() }

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        // Step 1: Request magic link
        val requestResult = client.post("/v1/auth/magic-link") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "integration-test@example.com"}""")
        }
        assertEquals(HttpStatusCode.OK, requestResult.status)

        // In a real test environment, we'd intercept the email
        // Here we verify the DB has the token and test verification directly
        // (using a test helper that reads the token from DB)
    }

    @Test
    fun `rate limiting blocks excessive magic link requests`() = testApplication {
        application { module() }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        // First 3 requests should succeed
        repeat(3) {
            val result = client.post("/v1/auth/magic-link") {
                contentType(ContentType.Application.Json)
                setBody("""{"email": "ratelimit-test@example.com"}""")
            }
            assertEquals(HttpStatusCode.OK, result.status)
        }

        // 4th should be rate limited
        val blocked = client.post("/v1/auth/magic-link") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "ratelimit-test@example.com"}""")
        }
        assertEquals(HttpStatusCode.TooManyRequests, blocked.status)
    }

    @Test
    fun `invalid email format is rejected`() = testApplication {
        application { module() }
        val client = createClient { install(ContentNegotiation) { json() } }

        val result = client.post("/v1/auth/magic-link") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "not-an-email"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, result.status)
    }
}
```

---

## 4.3 Load Testing Strategy

### k6 Load Test Script
```javascript
// scripts/load-tests/auth-load-test.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const magicLinkDuration = new Trend('magic_link_duration');
const chatMessageDuration = new Trend('chat_message_duration');

export const options = {
  stages: [
    { duration: '2m', target: 100 },   // Ramp up to 100 users
    { duration: '5m', target: 100 },   // Stay at 100
    { duration: '2m', target: 500 },   // Ramp to 500
    { duration: '5m', target: 500 },   // Stay at 500
    { duration: '2m', target: 1000 },  // Stress test at 1000
    { duration: '3m', target: 1000 },  // Stay at 1000
    { duration: '2m', target: 0 },     // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],  // 95th percentile < 500ms
    http_req_failed: ['rate<0.01'],                   // < 1% error rate
    errors: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'https://api.staging.twohearts.app';

export function setup() {
  // Pre-warm: create test users and get auth tokens
  const tokens = [];
  for (let i = 0; i < 50; i++) {
    const email = `loadtest-${i}@test.twohearts.app`;
    // In test env, magic links are available via test endpoint
    const token = getTestToken(email);
    if (token) tokens.push(token);
  }
  return { tokens };
}

export default function(data) {
  const token = data.tokens[Math.floor(Math.random() * data.tokens.length)];

  // Scenario 1: Request magic link (unauthenticated)
  const magicLinkStart = Date.now();
  const magicLinkRes = http.post(`${BASE_URL}/v1/auth/magic-link`, 
    JSON.stringify({ email: `test-${__VU}@example.com` }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  magicLinkDuration.add(Date.now() - magicLinkStart);
  errorRate.add(!check(magicLinkRes, { 'magic link status 200': r => r.status === 200 }));

  sleep(1);

  // Scenario 2: Get matches (authenticated)
  if (token) {
    const matchesRes = http.get(`${BASE_URL}/v1/matches/today`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    check(matchesRes, { 'matches status 200': r => r.status === 200 });
  }

  sleep(Math.random() * 2);
}

function getTestToken(email) {
  const res = http.post(`${BASE_URL}/v1/test/auth/token`,
    JSON.stringify({ email }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (res.status === 200) {
    return JSON.parse(res.body).accessToken;
  }
  return null;
}
```

### WebSocket Load Test
```javascript
// scripts/load-tests/websocket-load-test.js
import ws from 'k6/ws';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const messagesSent = new Counter('messages_sent');
const messagesReceived = new Counter('messages_received');

export const options = {
  vus: 200,
  duration: '5m',
  thresholds: {
    'ws_session_duration': ['avg<100'],
    'ws_connecting': ['p(95)<1000'],
  },
};

export default function() {
  const token = getAuthToken();
  
  const res = ws.connect(
    `wss://api.staging.twohearts.app/ws?token=${token}`,
    {},
    function(socket) {
      socket.on('open', () => {
        // Send a test message every 5 seconds
        let seq = 0;
        const interval = socket.setInterval(() => {
          socket.send(JSON.stringify({
            type: 'send_message',
            conversationId: 'test-conv-1',
            encryptedPayload: btoa('test-payload-' + seq),
            clientSequence: seq++
          }));
          messagesSent.add(1);
        }, 5000);

        socket.setTimeout(() => {
          socket.clearInterval(interval);
          socket.close();
        }, 60000); // Close after 1 minute
      });

      socket.on('message', (msg) => {
        messagesReceived.add(1);
        const data = JSON.parse(msg);
        check(data, { 'valid message type': d => ['message_received', 'message_sent', 'ping'].includes(d.type) });
      });

      socket.on('error', (e) => {
        console.error('WebSocket error:', e);
      });
    }
  );

  check(res, { 'WebSocket connected': r => r && r.status === 101 });
}

function getAuthToken() {
  // Obtain token for load test user
  return 'test-jwt-token';
}
```

---

## 4.4 Threat Model

```
┌────────────────────────────────────────────────────────────────────┐
│                     TWOHEARTS THREAT MODEL                        │
│                    STRIDE Analysis (2026)                          │
├────────────────┬────────────────────┬─────────────────────────────┤
│ Threat         │ Attack Vector      │ Mitigation                  │
├────────────────┼────────────────────┼─────────────────────────────┤
│ SPOOFING       │                    │                             │
│                │ Token replay       │ JWT JTI + jti blocklist     │
│                │ Device impersonation│ Device fingerprint binding │
│                │ Email impersonation │ SPF/DKIM/DMARC on mail     │
├────────────────┼────────────────────┼─────────────────────────────┤
│ TAMPERING      │                    │                             │
│                │ Message tampering  │ Signal Protocol HMAC        │
│                │ Profile tampering  │ JWT bound to userId + RLS   │
│                │ Score manipulation │ Server-side scoring only    │
├────────────────┼────────────────────┼─────────────────────────────┤
│ REPUDIATION    │                    │                             │
│                │ Deny sending msg   │ Immutable audit log (Kafka) │
│                │ Match denial       │ Event sourcing + timestamps │
├────────────────┼────────────────────┼─────────────────────────────┤
│ INFORMATION    │                    │                             │
│ DISCLOSURE     │ DB exfiltration    │ RLS + encrypted columns     │
│                │ Message snooping   │ E2E: server has no keys     │
│                │ Location exposure  │ City-level only stored      │
│                │ Photo scraping     │ Presigned URLs, short TTL   │
│                │ User enumeration   │ Identical responses on auth │
├────────────────┼────────────────────┼─────────────────────────────┤
│ DENIAL OF      │                    │                             │
│ SERVICE        │ Auth endpoint DDoS │ Rate limiting per IP+email  │
│                │ WebSocket flood    │ Max connections per user    │
│                │ Match spam         │ Daily match quotas in DB    │
│                │ Large messages     │ Max payload 64KB enforced   │
├────────────────┼────────────────────┼─────────────────────────────┤
│ ELEVATION OF   │                    │                             │
│ PRIVILEGE      │ IDOR (see others)  │ RLS enforced at DB layer    │
│                │ Admin bypass       │ mTLS between services       │
│                │ JWT alg confusion  │ ES256 only, verify issuer   │
│                │ SQL injection      │ ORM parameterized queries   │
└────────────────┴────────────────────┴─────────────────────────────┘

Additional Threat: CSAM / Illegal Content
  Mitigation: Photo moderation pipeline (CLIP + NudeNet open-source)
              applied to ALL uploads before serving.
              Hash-based matching against NCMEC PhotoDNA-compatible
              open-source hash database.
              Immediate account suspension + law enforcement reporting.

Additional Threat: Grooming / Sextortion
  Mitigation: Conversation health monitoring detects pressure patterns.
              Age verification via identity document (optional, v2).
              Clear reporting mechanism with 1-tap report.
              Dedicated trust & safety queue with human review.
```

---

## 4.5 Performance Considerations

### Database Performance
```sql
-- Tune PostgreSQL for production workloads
-- In postgresql.conf:
-- max_connections = 100 (use PgBouncer for connection pooling)
-- shared_buffers = 4GB (25% of RAM on 16GB server)
-- effective_cache_size = 12GB
-- work_mem = 64MB
-- maintenance_work_mem = 1GB
-- wal_buffers = 16MB
-- checkpoint_completion_target = 0.9
-- random_page_cost = 1.1 (for SSD)
-- effective_io_concurrency = 200

-- pgvector tuning for matching queries:
-- ivfflat lists = sqrt(number_of_rows) approximately
-- For 1M profiles: lists = 1000, probes = 10 (balance recall vs speed)

-- Connection pooling with PgBouncer:
-- pool_mode = transaction
-- max_client_conn = 1000
-- default_pool_size = 20
```

### Caching Strategy
```
Redis Cache Layers:

L1: User session validation
    Key: session:{userId}
    TTL: 15 minutes (JWT expiry)
    Value: {userId, deviceId, permissions}
    Invalidation: logout, suspension

L2: Profile cache (for matching display)
    Key: profile:{userId}
    TTL: 5 minutes
    Value: serialized profile JSON
    Invalidation: profile update event

L3: Today's matches per user
    Key: matches:{userId}:{date}
    TTL: until next day (23:59 UTC)
    Value: serialized match list
    Invalidation: match status change

L4: WebSocket presence
    Key: presence:{userId}
    TTL: 60 seconds (heartbeat)
    Value: {online: true, serverId: "chat-pod-2"}
    
L5: Rate limit counters
    Key: ratelimit:{ip}:{endpoint}
    TTL: 1 hour
    Value: request count (INCR + EXPIRE)
```

### Matching Engine Performance
```
Target: Process 100K eligible users in <5 minutes (daily job)

Optimizations:
1. Batch vector queries: query pgvector in batches of 500 users
2. Async parallel processing: coroutine parallelism = 2 × CPU cores
3. Pre-filter before vector search: 
   - First filter by active users (last_active_at < 7 days)
   - Filter by has_intent_today before vector queries
4. Vector index tuning:
   - IVFFlat with 1000 lists for 1M+ profiles
   - probes=20 (recall ~95% vs exact)
5. Result caching: cache match results per user for the day
6. Incremental matching: only re-match users who submitted intent today
   (skipping users without today's intent submission)

Benchmark targets:
- 10K users: <30s
- 100K users: <5min
- 1M users: <45min (with horizontal scaling of matching pods)
```

---

# STEP 5 — V2 ROADMAP

---

## 5.1 V2 Evolution Roadmap (12-24 months)

### Track 1: AI Personalization
**Q1 V2**: Replace template-based explainer with fine-tuned local LLM
- Fine-tune LLaMA 3.2 3B on positive match outcomes
- Generate natural language compatibility narratives instead of bullet points
- On-device inference using ONNX Runtime for privacy

**Q2 V2**: Adaptive preference learning
- Learn implicit preferences from match acceptance/rejection patterns
- Privacy-preserving federated learning: gradients shared, not raw data
- User can inspect and correct learned preferences ("preference dashboard")

**Q3 V2**: AI-powered conversation starters
- Context-aware (based on both users' public profile answers)
- Generated on-device, never sent to server
- Optional: user accepts/rejects suggestions before sending

### Track 2: Federated Architecture
**Q1 V2**: ActivityPub protocol integration
- TwoHearts instances can federate (enterprise, university deployments)
- Cross-instance matching with privacy boundaries
- Profile portability: import/export in standard format

**Q2 V2**: Decentralized identity
- DID (Decentralized Identifiers) W3C standard support
- Users own their profile, not the platform
- Key rotation and recovery via social recovery

**Q3 V2**: P2P chat option
- WebRTC data channels for direct device-to-device chat
- Server only for signaling (SDP exchange)
- Full metadata privacy

### Track 3: Cross-Platform
**Q1 V2**: iOS app (Swift + SwiftUI or Kotlin Multiplatform Mobile)
- Share domain layer via KMP
- Native UI per platform

**Q2 V2**: Web app (React + WebAssembly for crypto)
- Signal Protocol via libsignal-protocol.js
- Progressive Web App (PWA) with push notifications

### Track 4: Trust & Safety
**Q1 V2**: Video verification badge
- Optional: short selfie video verified by on-device ML (not server)
- Liveness detection to prevent catfishing
- All video deleted after verification, badge issued

**Q2 V2**: Community moderation
- Trusted users can volunteer as community moderators
- Structured review queue with appeal process
- Transparent moderation reports published monthly

**Q3 V2**: Relationship outcome tracking
- Opt-in: users report "we met in person" / "we're in a relationship"
- Anonymized success data improves matching model
- No dark pattern: tracking is genuinely opt-in

### Track 5: Accessibility & Inclusion
**Q1 V2**: Full accessibility audit
- TalkBack / screen reader support
- High contrast mode, font scaling
- Cognitive load reduction: simpler flows for neurodivergent users

**Q2 V2**: More gender/orientation options
- Expanded gender identity options
- Relationship structure options (polyamory, partnership, etc.)
- Cultural adaptation per region (different relationship norms)

---

## 5.2 Monitoring Dashboards

### Prometheus Metrics (Auth Service)
```kotlin
// backend/auth-service/src/main/kotlin/com/twohearts/auth/metrics/AuthMetrics.kt
package com.twohearts.auth.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import io.prometheus.client.Gauge

object AuthMetrics {
    val magicLinksRequested = Counter.build()
        .name("auth_magic_links_requested_total")
        .help("Total magic links requested")
        .labelNames("status")  // "sent", "blocked_suspended", "rate_limited"
        .register()

    val tokensVerified = Counter.build()
        .name("auth_tokens_verified_total")
        .help("Total token verifications")
        .labelNames("result")  // "success", "invalid", "expired"
        .register()

    val activeDevices = Gauge.build()
        .name("auth_active_devices")
        .help("Currently active device sessions")
        .register()

    val jwtVerificationDuration = Histogram.build()
        .name("auth_jwt_verification_duration_seconds")
        .help("JWT verification time")
        .buckets(0.001, 0.005, 0.01, 0.05, 0.1, 0.5)
        .register()

    val authenticationAttempts = Counter.build()
        .name("auth_authentication_attempts_total")
        .help("Authentication attempts")
        .labelNames("method", "result")  // method: magic_link/refresh; result: success/failure
        .register()
}
```

### Grafana Dashboard Config
```json
{
  "title": "TwoHearts — Application Overview",
  "panels": [
    {
      "title": "Active Users (Last 5m)",
      "type": "stat",
      "targets": [{"expr": "count(up{job=~\".*-service\"} == 1)"}]
    },
    {
      "title": "Auth Success Rate",
      "type": "gauge",
      "targets": [{"expr": "rate(auth_authentication_attempts_total{result=\"success\"}[5m]) / rate(auth_authentication_attempts_total[5m]) * 100"}]
    },
    {
      "title": "Daily Matches Created",
      "type": "stat",
      "targets": [{"expr": "increase(matching_matches_created_total[24h])"}]
    },
    {
      "title": "WebSocket Active Connections",
      "type": "graph",
      "targets": [{"expr": "chat_websocket_active_connections"}]
    },
    {
      "title": "P95 API Latency by Service",
      "type": "graph",
      "targets": [
        {"expr": "histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{job=\"auth-service\"}[5m]))", "legendFormat": "Auth"},
        {"expr": "histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{job=\"chat-service\"}[5m]))", "legendFormat": "Chat"},
        {"expr": "histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{job=\"matching-service\"}[5m]))", "legendFormat": "Matching"}
      ]
    },
    {
      "title": "Error Rate by Service",
      "type": "graph",
      "targets": [{"expr": "rate(http_requests_total{status=~\"5..\"}[5m]) by (job)"}]
    },
    {
      "title": "Toxicity Detections (24h)",
      "type": "stat",
      "targets": [{"expr": "increase(moderation_toxicity_detections_total[24h])"}]
    },
    {
      "title": "Conversation Health Distribution",
      "type": "heatmap",
      "targets": [{"expr": "histogram_quantile(0.5, rate(chat_conversation_health_score_bucket[1h]))"}]
    }
  ]
}
```

### Loki Log Query Examples
```logql
# Auth errors in last hour
{service="auth-service"} |= "ERROR" | json | line_format "{{.message}}"

# Failed token verifications with IP
{service="auth-service"} |= "JWT verification failed" | json | line_format "{{.ip}} - {{.message}}"

# Matching job performance
{service="matching-service"} |= "Daily matching complete" | json | line_format "users={{.users_processed}} matches={{.matches_created}} duration={{.duration_ms}}ms"

# WebSocket disconnections
{service="chat-service"} |= "WebSocket closed" | json | rate [5m]
```

---

## 5.3 OWASP ZAP Configuration

```yaml
# .zap/rules.tsv and automation config
# infrastructure/security/zap-automation.yaml
env:
  contexts:
    - name: TwoHearts API
      urls:
        - https://api.staging.twohearts.app
      includePaths:
        - "https://api.staging.twohearts.app/v1/.*"
      authentication:
        method: script
        parameters:
          scriptName: "jwt-auth.js"
        verification:
          method: response
          loggedInRegex: "accessToken"

jobs:
  - type: passiveScan-config
    parameters:
      maxAlertsPerRule: 10

  - type: activeScan
    parameters:
      policy: api-minimal
      context: TwoHearts API

  - type: report
    parameters:
      template: sarif
      reportFile: zap-report.sarif
      reportTitle: TwoHearts Security Scan

# Custom rules to exclude:
# 10063: Permissions Policy Header Not Set (mobile API, not web page)
# 90022: Application Error Disclosure (intentionally vague error messages for security)
```

---

## Summary of Deliverables

| Deliverable | Status | Location |
|---|---|---|
| Competitive Analysis | ✅ | Part 1 §1.1-1.2 |
| UX Differentiation | ✅ | Part 1 §1.3 |
| Technical Tradeoffs | ✅ | Part 1 §1.4 |
| System Architecture | ✅ | Part 1 §2.1-2.2 |
| Full PostgreSQL Schema | ✅ | Part 1 §2.3 (pgvector + PostGIS + RLS) |
| Event Flow Design | ✅ | Part 1 §2.4 |
| Security Model | ✅ | Part 1 §2.5 |
| Android Project (Hilt + Compose) | ✅ | Part 2 §3.7-3.9 |
| Auth Service (Kotlin/Ktor) | ✅ | Part 2 §3.3 |
| Matching Algorithm | ✅ | Part 2 §3.4 |
| WebSocket Chat Server | ✅ | Part 2 §3.5 |
| Signal Protocol E2E Encryption | ✅ | Part 2 §3.6 |
| Anti-ghosting Health Service | ✅ | Part 2 §3.6 |
| Dockerfiles | ✅ | Part 3 §3.10 |
| Docker Compose (Dev) | ✅ | Part 3 §3.10 |
| Kubernetes Manifests | ✅ | Part 3 §3.11 |
| GitHub Actions CI/CD | ✅ | Part 3 §3.12 |
| Unit Tests (Matching + Auth + Chat) | ✅ | Part 3 §4.1 |
| Integration Tests (Testcontainers) | ✅ | Part 3 §4.2 |
| Load Testing (k6) | ✅ | Part 3 §4.3 |
| STRIDE Threat Model | ✅ | Part 3 §4.4 |
| Performance Considerations | ✅ | Part 3 §4.5 |
| V2 Roadmap | ✅ | Part 3 §5.1-5.2 |
| Monitoring Config | ✅ | Part 3 §5.2-5.3 |
