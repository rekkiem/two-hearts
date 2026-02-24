#!/usr/bin/env python3
"""
Two Hearts — Seed Script
Creates dummy users, profiles, and daily intents for testing.

Usage:
  pip install requests
  python3 scripts/seed.py --url http://localhost:8080
"""

import requests
import argparse
import json
import sys
import time

parser = argparse.ArgumentParser()
parser.add_argument("--url", default="http://localhost:8080", help="Backend base URL")
args = parser.parse_args()

BASE = f"{args.url}/api/v1"

USERS = [
    {
        "email": "alice@twohearts.test",
        "profile": {
            "displayName": "Alice",
            "birthDate": "1995-06-15",
            "genderIdentity": "woman",
            "bio": "I love hiking, reading literary fiction, and cooking experimental recipes. Looking for someone who can hold a real conversation about ideas.",
            "occupation": "UX Designer",
            "relationshipIntent": "long_term",
            "city": "Barcelona",
            "lat": 41.3851,
            "lng": 2.1734,
            "prefGenders": ["man", "non-binary"],
            "prefMinAge": 25,
            "prefMaxAge": 40,
            "prefMaxDistKm": 50
        },
        "intent": "What made you genuinely smile this week?",
        "answer": "I discovered a tiny used bookshop tucked behind a café, and the owner recommended a novel that completely changed how I think about memory and storytelling. That kind of serendipity makes everything feel possible."
    },
    {
        "email": "bruno@twohearts.test",
        "profile": {
            "displayName": "Bruno",
            "birthDate": "1992-03-22",
            "genderIdentity": "man",
            "bio": "Musician, amateur philosopher, enthusiastic cook. I believe the best conversations happen over food or on long walks. Deeply curious about how people see the world.",
            "occupation": "Music teacher",
            "relationshipIntent": "long_term",
            "city": "Barcelona",
            "lat": 41.3800,
            "lng": 2.1600,
            "prefGenders": ["woman"],
            "prefMinAge": 24,
            "prefMaxAge": 38,
            "prefMaxDistKm": 50
        },
        "intent": "What made you genuinely smile this week?",
        "answer": "A student of mine — 8 years old — played their first full song without stopping. The look on their face when they realized they'd done it was one of those moments that reminds you what teaching is really about."
    },
    {
        "email": "cara@twohearts.test",
        "profile": {
            "displayName": "Cara",
            "birthDate": "1997-11-08",
            "genderIdentity": "woman",
            "bio": "Environmental scientist by day, amateur watercolor artist by night. I'm passionate about climate solutions and finding beauty in everyday things.",
            "occupation": "Environmental scientist",
            "relationshipIntent": "open_to_anything",
            "city": "Barcelona",
            "lat": 41.3900,
            "lng": 2.1800,
            "prefGenders": ["man"],
            "prefMinAge": 26,
            "prefMaxAge": 38,
            "prefMaxDistKm": 60
        },
        "intent": "What made you genuinely smile this week?",
        "answer": "I finished a watercolor of the old harbour at sunset. I've been working on it for three weeks and finally captured the light the way I wanted. Small win, but it felt huge."
    },
    {
        "email": "david@twohearts.test",
        "profile": {
            "displayName": "David",
            "birthDate": "1990-07-14",
            "genderIdentity": "man",
            "bio": "Software engineer who actually reads books (shocking, I know). Rock climber, bread baker, terrible dancer but enthusiastic about it.",
            "occupation": "Software engineer",
            "relationshipIntent": "long_term",
            "city": "Barcelona",
            "lat": 41.3700,
            "lng": 2.1400,
            "prefGenders": ["woman", "non-binary"],
            "prefMinAge": 25,
            "prefMaxAge": 38,
            "prefMaxDistKm": 40
        },
        "intent": "What made you genuinely smile this week?",
        "answer": "My sourdough finally had the right ear on it. I've been trying to get consistent scoring results for months. It sounds silly but baking teaches patience in a way nothing else does."
    },
    {
        "email": "eva@twohearts.test",
        "profile": {
            "displayName": "Eva",
            "birthDate": "1994-09-30",
            "genderIdentity": "woman",
            "bio": "Pediatric nurse. I find meaning in small moments of genuine human connection. Outside work: yoga, documentary films, and collecting weird vintage maps.",
            "occupation": "Pediatric nurse",
            "relationshipIntent": "long_term",
            "city": "Barcelona",
            "lat": 41.4000,
            "lng": 2.1900,
            "prefGenders": ["man"],
            "prefMinAge": 28,
            "prefMaxAge": 42,
            "prefMaxDistKm": 50
        },
        "intent": "What made you genuinely smile this week?",
        "answer": "A five-year-old patient gave me a drawing she made of me with wings. She said I was her angel. That one will stay with me for a long time."
    },
]

def wait_for_backend(url, retries=15):
    print(f"Waiting for backend at {url}/health ...")
    for i in range(retries):
        try:
            r = requests.get(f"{url}/health", timeout=3)
            if r.status_code == 200:
                print("✓ Backend is up")
                return True
        except Exception:
            pass
        time.sleep(2)
    print("✗ Backend not available")
    return False

def get_token_via_mailhog(email, mailhog_url="http://localhost:8025"):
    """Poll Mailhog API for the magic link token."""
    time.sleep(1)
    try:
        r = requests.get(f"{mailhog_url}/api/v2/messages?limit=50")
        msgs = r.json().get("items", [])
        for msg in msgs:
            to_addr = msg.get("Raw", {}).get("To", [])
            if any(email in addr for addr in to_addr):
                body = msg.get("Content", {}).get("Body", "")
                # Extract token from body
                import re
                match = re.search(r'token=([A-Za-z0-9_\-]{40,})', body)
                if match:
                    return match.group(1)
    except Exception as e:
        print(f"  Mailhog error: {e}")
    return None

def seed_user(user_data):
    email = user_data["email"]
    print(f"\n→ Seeding {email}")

    # 1. Request magic link
    r = requests.post(f"{BASE}/auth/magic-link", json={"email": email})
    if r.status_code != 200:
        print(f"  ✗ Magic link request failed: {r.text}")
        return None

    # 2. Get token from Mailhog
    token = get_token_via_mailhog(email)
    if not token:
        print(f"  ✗ Could not find token in Mailhog for {email}")
        print(f"    Tip: Check http://localhost:8025 manually")
        return None

    # 3. Verify token
    r = requests.post(f"{BASE}/auth/verify", json={"token": token, "deviceId": "seed-script"})
    if r.status_code != 200:
        print(f"  ✗ Token verification failed: {r.text}")
        return None
    access_token = r.json()["accessToken"]
    user_id      = r.json()["userId"]
    headers      = {"Authorization": f"Bearer {access_token}"}
    print(f"  ✓ Authenticated: userId={user_id}")

    # 4. Create profile
    r = requests.post(f"{BASE}/profiles", json=user_data["profile"], headers=headers)
    if r.status_code not in (200, 201):
        print(f"  ✗ Profile creation failed: {r.text}")
    else:
        print(f"  ✓ Profile created: {user_data['profile']['displayName']}")

    # 5. Submit daily intent
    r = requests.get(f"{BASE}/intents/question", headers=headers)
    if r.status_code == 200:
        question_id = r.json()["id"]
        r2 = requests.post(f"{BASE}/intents", json={
            "questionId": question_id,
            "answer": user_data["answer"]
        }, headers=headers)
        if r2.status_code in (200, 201):
            print(f"  ✓ Daily intent submitted")
        else:
            print(f"  ✗ Intent submission failed: {r2.text}")

    return {"userId": user_id, "accessToken": access_token, "email": email}

def main():
    if not wait_for_backend(args.url):
        sys.exit(1)

    print(f"\n{'='*50}")
    print(f"Two Hearts Seed — {len(USERS)} users")
    print(f"{'='*50}")

    seeded = []
    for user in USERS:
        result = seed_user(user)
        if result:
            seeded.append(result)

    print(f"\n{'='*50}")
    print(f"✓ Seeded {len(seeded)}/{len(USERS)} users")
    if seeded:
        print(f"\nTest credentials:")
        for u in seeded:
            print(f"  {u['email']}")
        print(f"\nTo get matches for any user:")
        print(f"  GET {BASE}/matches (with Bearer token)")
        print(f"\nMailhog UI: http://localhost:8025")
        print(f"Backend health: {args.url}/health")
    print(f"{'='*50}\n")

if __name__ == "__main__":
    main()
