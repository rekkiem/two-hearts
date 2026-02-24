# Two Hearts 💕

A fully functional open-source dating app — no swipes, no infinite scroll, just meaningful connections.

## Stack
- **Backend**: Kotlin 2 + Ktor 3 (monolithic)
- **Database**: PostgreSQL 16 + pgvector (semantic matching)
- **Email**: Mailhog (local SMTP dev server)
- **Storage**: MinIO (S3-compatible photo storage)
- **Android**: Kotlin + Jetpack Compose + Hilt

---

## Prerequisites
- Docker + Docker Compose v2
- JDK 21 (for building backend locally)
- Android Studio Ladybug or newer (for Android app)
- Python 3.8+ (optional, for seed script)

---

## Quick Start (Local)

### 1. Clone and start everything

```bash
git clone https://github.com/your-org/two-hearts
cd two-hearts

# Start all services (builds backend Docker image on first run)
docker compose up --build
```

Wait for all services to be healthy (~60-90 seconds on first run).

### 2. Verify services

| Service | URL |
|---|---|
| Backend API | http://localhost:8080/health |
| Mailhog (email UI) | http://localhost:8025 |
| MinIO Console | http://localhost:9001 (user: twohearts / twohearts123) |

### 3. Seed test data

```bash
pip install requests
python3 scripts/seed.py --url http://localhost:8080
```

This creates 5 test users with profiles and daily intents. Check http://localhost:8025 for magic link emails.

---

## Manual API Testing

### Auth flow (Passwordless)

```bash
# 1. Request magic link
curl -X POST http://localhost:8080/api/v1/auth/magic-link \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com"}'

# 2. Open http://localhost:8025 → click the link → copy the token
# OR: Open the verify URL directly in browser:
# http://localhost:8080/api/v1/auth/verify-web?token=<TOKEN>

# 3. Verify token (get JWT)
curl -X POST http://localhost:8080/api/v1/auth/verify \
  -H "Content-Type: application/json" \
  -d '{"token": "<TOKEN_FROM_EMAIL>", "deviceId": "test"}'

# Response: {"accessToken":"...", "refreshToken":"...", "userId":"..."}
export TOKEN="<ACCESS_TOKEN>"
```

### Profile

```bash
# Create profile
curl -X POST http://localhost:8080/api/v1/profiles \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "Test User",
    "birthDate": "1995-06-15",
    "genderIdentity": "woman",
    "bio": "I love hiking and cooking experimental recipes.",
    "occupation": "Designer",
    "relationshipIntent": "long_term",
    "city": "Barcelona",
    "prefGenders": ["man"],
    "prefMinAge": 25,
    "prefMaxAge": 40
  }'

# Get my profile
curl http://localhost:8080/api/v1/profiles/me \
  -H "Authorization: Bearer $TOKEN"
```

### Daily Intent

```bash
# Get today's question
curl http://localhost:8080/api/v1/intents/question \
  -H "Authorization: Bearer $TOKEN"

# Submit answer (use question ID from above)
curl -X POST http://localhost:8080/api/v1/intents \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"questionId": "<QUESTION_ID>", "answer": "I discovered a tiny bookshop that changed how I think about storytelling. That kind of serendipity makes everything feel possible."}'
```

### Matches

```bash
# Get today's matches (generated on first call)
curl http://localhost:8080/api/v1/matches \
  -H "Authorization: Bearer $TOKEN"

# Like a match
curl -X POST http://localhost:8080/api/v1/matches/<MATCH_ID>/interact \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"action": "like"}'
```

### Chat

```bash
# Get conversations (only after mutual match)
curl http://localhost:8080/api/v1/conversations \
  -H "Authorization: Bearer $TOKEN"

# Get messages
curl http://localhost:8080/api/v1/conversations/<CONV_ID>/messages \
  -H "Authorization: Bearer $TOKEN"

# Send message (REST fallback)
curl -X POST http://localhost:8080/api/v1/conversations/<CONV_ID>/messages \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"content": "Hey, I noticed we both love hiking!"}'
```

### WebSocket (real-time chat)

```bash
# Connect with wscat (npm install -g wscat)
wscat -c "ws://localhost:8080/ws/chat/<CONV_ID>?token=$TOKEN"

# Send a message
{"type":"message","content":"Hello!"}

# Typing indicator
{"type":"typing","content":"start"}
```

---

## Android App Setup

1. Open `android/` folder in Android Studio
2. The emulator uses `10.0.2.2` to reach localhost — already configured in `BuildConfig`
3. For a real device: edit `app/build.gradle.kts` and set `API_BASE_URL` to your machine's local IP
4. Run the app → enter your email → check Mailhog → paste token → create profile → see matches

---

## Running Backend Tests

```bash
cd backend
./gradlew test
```

---

## Deployment to Seenode / VPS

### Environment Variables (required)

```env
DATABASE_URL=jdbc:postgresql://<host>:5432/twohearts
DATABASE_USER=twohearts
DATABASE_PASSWORD=<strong-password>
JWT_SECRET=<min-32-character-secret>
MAIL_HOST=<smtp-host>
MAIL_PORT=587
MAIL_FROM=noreply@yourapp.com
MAIL_TLS=true
MINIO_ENDPOINT=https://your-minio-or-s3-compatible
MINIO_ACCESS_KEY=<key>
MINIO_SECRET_KEY=<secret>
MINIO_BUCKET=photos
APP_BASE_URL=https://api.yourdomain.com
PORT=8080
```

### Single-container deploy

```bash
# Build the backend image
docker build -t twohearts-backend ./backend

# Push to your registry
docker tag twohearts-backend registry.seenode.com/your-project/twohearts-backend
docker push registry.seenode.com/your-project/twohearts-backend

# Deploy on Seenode:
# 1. Create PostgreSQL + MinIO services on Seenode
# 2. Set all environment variables
# 3. Deploy the backend image with PORT=8080 exposed
```

### Fly.io (alternative simple deploy)

```bash
fly launch --name twohearts-api --image ghcr.io/your-org/twohearts-backend
fly postgres create --name twohearts-db
fly postgres attach --postgres-app twohearts-db
fly secrets set JWT_SECRET=$(openssl rand -base64 32)
fly deploy
```

---

## Architecture Overview

```
Android App (Compose)
       │
       ▼  HTTPS/WSS
API Gateway (Nginx) ─── optional for production
       │
       ▼
Ktor Backend (Single process, modular)
  ├── Auth module     → magic links, JWT ES256, refresh rotation
  ├── Profile module  → CRUD, photo upload, embedding generation
  ├── Matching module → pgvector ANN search, preference filtering, scoring
  ├── Intent module   → daily questions, answer embedding, profile refresh
  └── Chat module     → WebSocket server, REST fallback, message persistence
       │
       ├── PostgreSQL 16 + pgvector (all persistent data)
       ├── MinIO (photo storage)
       └── Mailhog (local) / SMTP (production)
```

### Matching Algorithm

Score = `0.5 × semantic_similarity + 0.3 × intent_similarity + 0.2 × geo_score`

- **Semantic similarity**: pgvector cosine distance between profile embeddings (bio + occupation + recent intent)
- **Intent similarity**: cosine distance between today's intent answer embeddings
- **Geo score**: Exponential decay `exp(-2.5 × distance/maxDistance)`
- Hard filters: age range (mutual), gender preference (mutual), distance limit
- Returns top-3 candidates per user per day (Slow Dating Mode)

---

## Project Structure

```
two-hearts/
├── docker-compose.yml              ← Run everything with one command
├── backend/
│   ├── Dockerfile
│   ├── build.gradle.kts
│   ├── src/main/
│   │   ├── kotlin/com/twohearts/
│   │   │   ├── Application.kt      ← Ktor entry + plugin setup
│   │   │   ├── database/           ← DatabaseFactory + Exposed tables
│   │   │   ├── models/             ← API request/response models
│   │   │   ├── services/           ← Business logic
│   │   │   │   ├── AuthService.kt
│   │   │   │   ├── ProfileService.kt
│   │   │   │   ├── MatchingService.kt
│   │   │   │   ├── ChatService.kt
│   │   │   │   ├── EmbeddingService.kt
│   │   │   │   ├── MailService.kt
│   │   │   │   └── MinioService.kt
│   │   │   └── routes/             ← Ktor route handlers
│   │   │       ├── AuthRoutes.kt
│   │   │       ├── ProfileRoutes.kt
│   │   │       ├── MatchingRoutes.kt
│   │   │       └── ChatRoutes.kt   ← includes WebSocket handler
│   │   └── resources/
│   │       ├── application.conf
│   │       ├── logback.xml
│   │       └── db/migration/V1__init.sql
│   └── src/test/                   ← Unit tests
├── android/
│   └── app/src/main/kotlin/com/twohearts/
│       ├── TwoHeartsApp.kt + MainActivity.kt
│       ├── di/AppModule.kt         ← Hilt modules + TokenStore
│       ├── data/api/               ← ApiService + Models
│       ├── navigation/NavGraph.kt
│       └── ui/
│           ├── login/LoginScreen.kt
│           ├── profile/CreateProfileScreen.kt
│           ├── matches/MatchesScreen.kt
│           └── chat/ChatScreen.kt
└── scripts/seed.py                 ← Creates 5 test users
```
