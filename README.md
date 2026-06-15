# Merge Fruit — Java Backend (Learning Project)

Spring Boot backend for your React Merge Fruit game. This is a **50/50 learning setup**: about half is implemented as reference code; the rest is left for you with `TODO (Student)` comments.

## Quick start

```bash
cd backend

# 1. Create local secrets file (gitignored — never commit .env)
cp .env.example .env
# Edit .env — all three secrets are required:
#   DB_PASSWORD=...              (must match Postgres container)
#   JWT_SECRET=...               (min 32 characters)
#   ANONYMOUS_USER_PASSWORD=...  (any internal string)

# 2. Load env vars (needed for Docker; Maven loads .env automatically in dev)
set -a && source .env && set +a

# 3. Start Docker (Colima on macOS)
colima start

# 4. Start PostgreSQL (credentials from .env)
docker run -d --name mergefruit-db \
  -e POSTGRES_DB="$DB_NAME" \
  -e POSTGRES_USER="$DB_USER" \
  -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  -p "${DB_PORT}:5432" \
  postgres:16-alpine

# 5. Run the API — dev profile auto-loads .env and creates tables
chmod +x run-dev.sh
./run-dev.sh
```

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

Start **Adminer** — a simple web UI for PostgreSQL:

```bash
chmod +x start-db-ui.sh
./start-db-ui.sh
```

Open **http://localhost:8081** and log in:

| Field | Value |
|-------|--------|
| System | PostgreSQL |
| Server | `mergefruit-db` (or `host.docker.internal` if that fails) |
| Username | from `.env` → `DB_USER` |
| Password | from `.env` → `DB_PASSWORD` |
| Database | from `.env` → `DB_NAME` |

Then click **users** or **scores** to browse tables, or run SQL in the **SQL command** tab.

> Adminer runs on port **8081** so it doesn't conflict with the API on **8080**.

> **Secrets:** All passwords and keys live in `backend/.env` only.

### Troubleshooting: `WeakKeyException` / JWT key is 0 bits

`JWT_SECRET` is empty or missing. Create `backend/.env` from `.env.example` and set `JWT_SECRET` to at least 32 characters.

### Troubleshooting: `Connection to localhost:5432 refused`

This means PostgreSQL is not running. Check in order:

1. **Colima/Docker running?** `colima status` → if not, run `colima start`
2. **Postgres container running?** `docker ps` → should show `mergefruit-db` on port 5432
3. **Start Postgres** if missing (command above). If the container already exists but stopped: `docker start mergefruit-db`

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
