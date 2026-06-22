# Merge Fruit — Java Backend (Learning Project)

Spring Boot backend for your React Merge Fruit game. This is a **50/50 learning setup**: about half is implemented as reference code; the rest is left for you with `TODO (Student)` comments.

## Quick start (local)

The API always runs via **`./run-dev.sh`** (Maven) — not the Render Docker image. Only the **database** setup differs: local Docker Postgres, or a remote DB (Neon) with no Docker.

Create **`.env.local`** first (see [Environment variables](#environment-variables) below).

| | **With Docker** | **Without Docker** |
|---|-----------------|---------------------|
| Database | Postgres in a container on `localhost:5432` | Neon (cloud) — no Colima/Docker needed |
| Config file | `.env.local` | `.env.prod` |
| Spring profile | `dev` (via `./run-dev.sh`) | `prod` |

---

### Option A — With Docker (local Postgres)

Uses `docker-compose.yml` — Postgres + Adminer on your machine.

```bash
# 1. Start Docker (Colima on macOS)
colima start

# 2. Load secrets and start Postgres + Adminer
set -a && source .env.local && set +a
docker-compose up -d

# 3. Run the API
chmod +x run-dev.sh
./run-dev.sh
```

| Service | URL |
|---------|-----|
| API | http://localhost:8080 |
| Adminer (DB UI) | http://localhost:8081 |

**Alternative — single `docker run` container** (no Compose):

```bash
set -a && source .env.local && set +a

docker run -d --name mergefruit-db \
  -e POSTGRES_DB="$DB_NAME" \
  -e POSTGRES_USER="$DB_USER" \
  -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  -p "${DB_PORT}:5432" \
  postgres:16-alpine

./run-dev.sh
```

For Adminer with this setup: `chmod +x start-db-ui.sh && ./start-db-ui.sh` → http://localhost:8081 (Server: `mergefruit-db`).

**Docker commands (Compose):**

```bash
set -a && source .env.local && set +a
docker-compose up -d      # start
docker-compose down       # stop (data kept in volume)
docker ps                 # check containers
docker-compose logs postgres
```

> **Note:** Use `docker-compose` (with a hyphen). If your install has the Compose plugin, `docker compose` (space) works too — if you see `'compose' is not a docker command`, use `docker-compose`.

---

### Option B — Without Docker (Neon / remote DB)

Use your **Neon** database from the machine — same DB as Render, no local Postgres.

```bash
# 1. Load production env (Neon credentials)
set -a && source .env.prod && set +a

# 2. Run API against Neon (prod profile + Flyway validate)
export SPRING_PROFILES_ACTIVE=prod
mvn spring-boot:run
```

Or in one line:

```bash
set -a && source .env.prod && set +a && SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run
```

| Service | URL |
|---------|-----|
| API | http://localhost:8080 |
| DB UI | [Neon Console](https://console.neon.tech) → SQL Editor |

> Tables already exist on Neon from your Render deploy. Flyway will not re-run `V1__init.sql` if it was applied before.

**Without Docker and without Neon?** Install Postgres locally (e.g. `brew install postgresql@16`), create a database matching `.env.local`, then use **Option A** steps 3+ but skip Docker — run `./run-dev.sh` only.

---

### What runs where

| | **Local (Docker)** | **Local (no Docker)** | **Render (production)** |
|---|-------------------|----------------------|-------------------------|
| API | `./run-dev.sh` | `mvn spring-boot:run` + `.env.prod` | Docker container |
| Database | Docker Postgres | Neon | Neon |
| Config | `.env.local`, `dev` | `.env.prod`, `prod` | `.env.prod`, `prod` |

## Environment variables

Create **`.env.local`** for local development and **`.env.prod`** for Neon/Render. Both files are gitignored — **never commit them**.

### `.env.local` (local Postgres — use with Docker or Homebrew Postgres)

```properties
DB_HOST=localhost
DB_PORT=5432
DB_NAME=mergefruit
DB_USER=mergefruit
DB_PASSWORD=your-local-password

JWT_SECRET=your-random-string-at-least-32-characters-long
ANONYMOUS_USER_PASSWORD=any-internal-string

CORS_ALLOWED_ORIGINS=http://localhost:5173

# Brevo email (optional — for email verification)
BREVO_API_KEY=your-brevo-api-key
MAIL_FROM=your-verified-email@example.com
MAIL_ENABLED=false
FRONTEND_URL=http://localhost:5173
```

Use with **Option A** (Docker) and `./run-dev.sh`.

### Brevo email

1. Sign up at [brevo.com](https://www.brevo.com) → **SMTP & API** → **API keys** → create a key (starts with `xkeysib-`).
2. Set `MAIL_FROM` to a **verified sender** in Brevo (Senders → add your email).
3. In `.env.local` set `BREVO_API_KEY` and `MAIL_ENABLED=true`.
4. On Render, add `BREVO_API_KEY`, `MAIL_FROM`, `MAIL_ENABLED`, and `FRONTEND_URL` to **Environment**.

Test locally:

```bash
curl -X POST "http://localhost:8080/api/dev/test-email"
```

> Keep `MAIL_ENABLED=false` until `BREVO_API_KEY` and `MAIL_FROM` are set — the app logs instead of sending.

### `.env.prod` (Neon + Render — also for local dev without Docker)

Copy values from the [Neon dashboard](https://console.neon.tech) → **Connection details**. Set the same keys on Render → **Environment**.

Use with **Option B** (no Docker) or production deploy.

```properties
SPRING_PROFILES_ACTIVE=prod

DB_HOST=ep-xxxx.region.aws.neon.tech
DB_PORT=5432
DB_NAME=neondb
DB_USER=your-neon-role
DB_PASSWORD=your-neon-password

JWT_SECRET=separate-prod-secret-at-least-32-characters
ANONYMOUS_USER_PASSWORD=separate-prod-internal-string

CORS_ALLOWED_ORIGINS=https://your-frontend.example.com,http://localhost:5173
```

> **Security:** Do not put real passwords or connection URLs in the repo, docs, or chat. If a secret was exposed, rotate it in Neon immediately (Dashboard → **Reset password**).


### Auto-restart on code changes (DevTools)

The project includes **Spring Boot DevTools**. It restarts the app when compiled `.class` files change.

1. Keep `./run-dev.sh` running in a terminal
2. After editing Java files, **recompile** (pick one):
   - **Cursor / VS Code:** enable **Java › Compile On Save** in settings, then save the file
   - **Terminal:** run `mvn compile -q` in another terminal tab
   - **IntelliJ:** **Build → Build Project** (⌘F9), or enable "Build project automatically"
3. Watch the run terminal — you should see `Restarting...` then `Started MergeFruitBackendApplication`

DevTools does a **fast restart** (seconds), not a full cold start. Static resources (`application.yml`) also reload.

If changes don't appear, do a full restart: `Ctrl+C` → `./run-dev.sh` again.

### Browse the database (web UI)

**With Docker (Compose)** — `docker-compose up -d` starts Adminer on **http://localhost:8081**:

| Field | Value |
|-------|--------|
| System | PostgreSQL |
| Server | `postgres` |
| Username | from `.env.local` → `DB_USER` |
| Password | from `.env.local` → `DB_PASSWORD` |
| Database | from `.env.local` → `DB_NAME` |

**With Docker (`docker run`)** — run `./start-db-ui.sh`, then open **http://localhost:8081** (Server: `mergefruit-db`).

**Without Docker** — use the [Neon Console](https://console.neon.tech) → **SQL Editor** (same data as Render).

Then click **users** or **scores** to browse tables, or run SQL.

> Adminer uses port **8081** so it doesn't conflict with the API on **8080**.

> **Secrets:** All passwords and keys live in `.env.local` / `.env.prod` only (gitignored).

### Troubleshooting: `WeakKeyException` / JWT key is 0 bits

`JWT_SECRET` is empty or missing. Create `.env.local` (see **Environment variables** above) and set `JWT_SECRET` to at least 32 characters.

### Troubleshooting: `Connection to localhost:5432 refused`

**If using Docker (Option A):**

1. **Colima/Docker running?** `colima status` → if not, run `colima start`
2. **Postgres container running?** `docker ps` → should show `mergefruit-db` on port 5432
3. **Start Postgres:** `set -a && source .env.local && set +a && docker-compose up -d`
4. **Port 5432 in use?** Stop other Postgres or change `DB_PORT` in `.env.local`
5. **Old manual container?** `docker rm -f mergefruit-db`, then `docker-compose up -d`

**If not using Docker (Option B):** you should not connect to `localhost:5432` — use `.env.prod` with Neon `DB_HOST` and `SPRING_PROFILES_ACTIVE=prod`.

API base URL: `http://localhost:8080`

**New to Java + databases?** Read [How Java Connects to the DB](docs/HOW_JAVA_CONNECTS_TO_DB.md) for JDBC, HikariCP, JPA, and the request path. See [How Tables Are Created](docs/HOW_TABLES_ARE_CREATED.md) for local vs Neon (Flyway vs Hibernate).

**Ready to go live?** See [Deploy to free hosting](docs/DEPLOY.md) (Render + Neon).

## What's implemented (study these)

| Area | Done | Your turn |
|------|------|-----------|
| Auth | `POST /api/auth/signup`, `POST /api/auth/login` | logout, refresh token, password reset |
| Scores | `GET /api/scores`, `POST /api/scores` | `DELETE /api/scores/{id}`, `PUT /api/scores/{id}` |
| Security | JWT filter, BCrypt, stateless sessions, CORS | rate limiting logic, token revocation |
| DB protection | pagination, pool limits, payload caps, indexes | complete `RateLimitFilter` |

## Architecture

```
HTTP Request
    → RateLimitFilter (skeleton)
    → JwtAuthenticationFilter
    → Controller  (thin — HTTP only)
    → Service     (business rules)
    → Repository  (JPA / SQL)
    → PostgreSQL
```

## Postman

Import the API collection and local environment from `backend/postman/`:

1. **File → Import** → select `Merge-Fruit-API.postman_collection.json` and `local.postman_environment.json`
2. Choose the **Merge Fruit — Local** environment (top-right dropdown)
3. Run **Login** or **Sign Up** — the JWT is saved to `{{accessToken}}` automatically
4. Use **Submit Score** — make sure the Body tab has JSON (fixes "Required request body is missing")

## Try the API

**Sign up** (replace `YOUR_PASSWORD` with a password you choose at signup)
```bash
curl -s -X POST http://localhost:8080/api/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"YOUR_PASSWORD","displayName":"Player1"}' | jq
```

**Login**
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"YOUR_PASSWORD"}' | jq -r .accessToken)
```

**Submit score** (works without auth — anonymous scores supported)
```bash
curl -s -X POST http://localhost:8080/api/scores \
  -H 'Content-Type: application/json' \
  -d '{"name":"Player1","score":4200}' | jq
```

**Leaderboard** (paginated)
```bash
curl -s 'http://localhost:8080/api/scores?page=0&size=10' | jq
```

## Your exercises (in order)

### Exercise 1 — DELETE score
1. Implement `ScoreService.deleteScore()`
2. Wire `ScoreController.deleteScore()` — remove the `UnsupportedOperationException`
3. Require JWT; allow delete if user owns the score OR has `ROLE_ADMIN`
4. Return `404` if not found, `403` if not owner

### Exercise 2 — PUT score
1. Implement `ScoreService.updateScore()`
2. Wire `ScoreController.updateScore()`
3. Validate at least one of `name` or `score` is present

### Exercise 3 — Rate limiting
1. Complete `RateLimitFilter` with a sliding window per IP
2. Return `429` when exceeded (see `sendTooManyRequests` helper)

### Exercise 4 — Auth extras
1. `POST /api/auth/logout` — token deny-list
2. `POST /api/auth/refresh` — refresh token rotation
3. Password reset flow

### Exercise 5 — Connect React
Set in your frontend `.env`:
```
VITE_API_URL=http://localhost:8080/api/scores
```

## Database protection (why each exists)

| Practice | Where | Why |
|----------|-------|-----|
| Pagination | `GET /api/scores` | Avoids `SELECT *` returning millions of rows |
| Query size cap | `@Max(100)` on page size | Client can't request unbounded pages |
| Connection pool | `hikari.maximum-pool-size` | Reuses connections; prevents DB connection exhaustion |
| Rate limiting | `RateLimitFilter` | Slows abusive clients before they hit the DB |
| Payload limits | `multipart` + validation `@Max` | Rejects oversized bodies early |
| JPA (not string SQL) | Repositories | Parameterized queries prevent SQL injection |
| Indexes | `V1__init.sql` | `ORDER BY points DESC` stays fast as data grows |
| `open-in-view: false` | `application.yml` | Prevents lazy-loading surprises and long DB sessions |

## Paste your code for review

When you finish an exercise, paste your implementation in chat. I'll review it like a PR: correctness, security, Spring idioms, and tests you might add.
