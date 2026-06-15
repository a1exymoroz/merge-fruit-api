# How Java Connects to PostgreSQL in This Project

This document explains the full path from your Spring Boot app to the database — what each layer does and why it exists.

## The big picture

```
HTTP Request
    ↓
Controller   (ScoreController, AuthController)
    ↓
Service      (ScoreService, AuthService)     ← business rules, @Transactional
    ↓
Repository   (ScoreRepository, UserRepository) ← Spring Data JPA
    ↓
EntityManager / Hibernate                    ← ORM (Object-Relational Mapping)
    ↓
HikariCP connection pool                     ← reuses TCP connections
    ↓
PostgreSQL JDBC driver                       ← speaks PostgreSQL wire protocol
    ↓
PostgreSQL (Docker container on port 5432)
```

You almost never write raw JDBC in this project. Spring Boot wires the stack together from configuration + annotations.

---

## Step 1 — Dependencies (`pom.xml`)

Two Maven dependencies make the DB connection possible:

| Dependency | Role |
|------------|------|
| `spring-boot-starter-data-jpa` | Brings in Hibernate (ORM) + Spring Data JPA + transaction support |
| `postgresql` (runtime) | The PostgreSQL JDBC driver — knows how to talk to Postgres over TCP |

Without the JDBC driver, Java cannot connect to PostgreSQL even with correct config.

---

## Step 2 — Connection settings (`application.yml`)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:mergefruit}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
```

Values come from `.env.local` (gitignored). Create the file using the templates in the project **README** → **Environment variables**:

```bash
set -a && source .env.local && set +a
```

> **How were tables created on Neon?** See [How Tables Are Created](HOW_TABLES_ARE_CREATED.md) — local uses Hibernate `ddl-auto: update`; production uses Flyway migrations.

**What happens at startup:**

1. Spring Boot reads `spring.datasource.*` (password injected from environment)
2. It creates a **HikariCP** `DataSource` (connection pool)
3. HikariCP opens a few connections to `localhost:5432` using the JDBC URL
4. Hibernate asks the pool for a connection to read DB metadata (version, dialect)

**JDBC URL breakdown:**

```
jdbc:postgresql://localhost:5432/mergefruit
  │      │            │      │        └── database name
  │      │            │      └── port (5432 = default Postgres)
  │      │            └── host (Docker maps container → your machine)
  │      └── driver type
  └── Java Database Connectivity protocol
```

**Connection pool (HikariCP):**

```yaml
hikari:
  maximum-pool-size: 10   # max open connections at once
  minimum-idle: 2         # keep 2 connections warm and ready
```

Why a pool? Opening a TCP + DB connection is slow. Under load, creating one per HTTP request would exhaust Postgres `max_connections`. The pool borrows and returns connections.

---

## Step 3 — JPA / Hibernate (`application.yml` + entities)

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate   # production-safe: schema must already exist
    open-in-view: false    # don't keep DB session open during HTTP response
```

With `SPRING_PROFILES_ACTIVE=dev`, `application-dev.yml` overrides:

```yaml
hibernate:
  ddl-auto: update   # auto-create/alter tables from @Entity classes
```

**Entities** (`User.java`, `Score.java`) are Java classes mapped to tables:

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;
    // ...
}
```

Hibernate translates:

- `User` class → `users` table
- `@Column` → column name, nullability, length
- `@ManyToOne` on `Score.user` → foreign key `scores.user_id → users.id`

---

## Step 4 — Repositories (your DB access API)

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
}
```

You write an **interface only**. At runtime Spring generates an implementation that:

1. Parses the method name → SQL (`WHERE LOWER(email) = LOWER(?)`)
2. Uses **parameterized queries** (safe from SQL injection)
3. Maps result rows → `User` objects

Example flow for `userRepository.findByEmailIgnoreCase("you@example.com")`:

```
AuthService.login()
  → userRepository.findByEmailIgnoreCase(...)
    → Spring Data proxy
      → Hibernate generates: SELECT * FROM users WHERE LOWER(email) = LOWER(?)
        → HikariCP borrows connection
          → JDBC driver sends SQL to PostgreSQL
            → rows returned → mapped to User entity
```

---

## Step 5 — Transactions (`@Transactional`)

In `AuthService` and `ScoreService` you'll see:

```java
@Transactional
public AuthResponse signUp(SignUpRequest request) { ... }

@Transactional(readOnly = true)
public PageResponse<ScoreResponse> getLeaderboard(int page, int size) { ... }
```

**What this does:**

- Opens a logical transaction before the method runs
- All repository calls inside share the same DB connection
- On success → `COMMIT`; on exception → `ROLLBACK`
- `readOnly = true` hints Postgres/Hibernate to optimize for SELECT-only work

Without `@Transactional`, each repository call could use a different connection and you lose atomicity (e.g. save user but fail to save related score → inconsistent data).

---

## Step 6 — Docker PostgreSQL

Postgres runs in a container. Credentials are read from `.env` (not hardcoded):

```bash
set -a && source .env && set +a

docker run -d --name mergefruit-db \
  -e POSTGRES_DB="$DB_NAME" \
  -e POSTGRES_USER="$DB_USER" \
  -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  -p "${DB_PORT}:5432" \
  postgres:16-alpine
```

`-p 5432:5432` maps container port 5432 → your Mac's `localhost:5432`, which is exactly what the JDBC URL uses.

---

## Startup sequence (what you see in logs)

When you run `mvn spring-boot:run`:

```
1. HikariPool-1 - Starting...
2. HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@...
3. HikariPool-1 - Start completed.
4. Database version: 16.14
5. Hibernate: create table users ...   (only in dev profile with ddl-auto: update)
6. Initialized JPA EntityManagerFactory
7. Tomcat started on port 8080
```

If step 1–2 fails with **"Connection to localhost:5432 refused"**, Postgres is not running — start Colima + the Docker container first (see `README.md`).

---

## End-to-end example: submitting a score

```
POST /api/scores  { "name": "Alex", "score": 5000 }
        │
        ▼
ScoreController.submitScore()
        │
        ▼
ScoreService.submitScore()          [@Transactional starts]
        │
        ├─► userRepository.findById(...)     or getOrCreateAnonymousUser()
        │         └─► SELECT ... FROM users
        │
        ├─► scoreRepository.save(score)
        │         └─► INSERT INTO scores (...)
        │
        └─► scoreRepository.findLeaderboard(...)
                  └─► SELECT ... FROM scores ORDER BY points DESC
        │
        ▼
[@Transactional commits]
        │
        ▼
JSON response returned to client
```

---

## Key files reference

| File | Purpose |
|------|---------|
| `pom.xml` | JDBC driver + JPA dependencies |
| `application.yml` | DB URL, pool, Hibernate settings (secrets via env vars) |
| `.env.local` / `.env.prod` | Secrets (gitignored) — templates in README |
| `application-dev.yml` | Dev overrides (`ddl-auto: update`, SQL logging) |
| `application-prod.yml` | Prod: SSL, Flyway, `ddl-auto: validate` |
| `entity/User.java`, `entity/Score.java` | Table mapping |
| `repository/*.java` | Query interface (no SQL written by hand) |
| `service/*.java` | Business logic + `@Transactional` |
| `db/migration/V1__init.sql` | Flyway migration — creates tables on Neon |
| [HOW_TABLES_ARE_CREATED.md](HOW_TABLES_ARE_CREATED.md) | Local vs prod schema creation explained |

---

## Things to try yourself

1. **See the SQL** — run with `SPRING_PROFILES_ACTIVE=dev` and watch `Hibernate:` lines in the console.
2. **Break the connection** — stop Postgres (`docker stop mergefruit-db`) and restart the app; read the error.
3. **Trace one query** — set a breakpoint in `AuthService.signUp()` on `userRepository.save(user)` and step through.
4. **Add a repository method** — e.g. `List<Score> findByUserId(Long userId)` and see what SQL Spring generates.

## Common mistakes

| Mistake | Why it's bad |
|---------|--------------|
| Returning `@Entity` from controllers | Leaks internal schema and sensitive fields (password hash) |
| `open-in-view: true` (default in older apps) | Keeps DB connection open while rendering JSON — hurts performance |
| String-concatenated SQL in native queries | SQL injection risk — always use `?` parameters |
| No connection pool limits | Can crash Postgres under traffic |
| Forgetting Postgres is running | `Connection refused` on startup |

---

## How this differs from your Netlify function

Your React app originally used `netlify/functions/leaderboard.ts` with **Netlify Blobs** (key-value storage, no SQL).

This Java backend uses **relational storage**:

- Structured tables with foreign keys (`scores.user_id`)
- SQL queries with indexes for fast leaderboards
- Transactions for data consistency
- JDBC connection pooling for concurrent requests

Same API shape (`GET`/`POST` scores), different persistence technology underneath.
