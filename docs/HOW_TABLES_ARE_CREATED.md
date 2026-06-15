# How Tables Are Created — Local vs Neon (Production)

This document answers two questions:

1. **How did `users` and `scores` appear in your Neon database?**
2. **How does the Java app talk to that database after tables exist?**

For the full request path (Controller → Service → Repository → JDBC), see [How Java Connects to the DB](HOW_JAVA_CONNECTS_TO_DB.md).

---

## Short answer

| Environment | Profile | How tables are created |
|-------------|---------|------------------------|
| **Local** (`./run-dev.sh`) | `dev` | **Hibernate** auto-creates/alters tables from `@Entity` classes (`ddl-auto: update`) |
| **Neon / Render** | `prod` | **Flyway** runs SQL migration files on startup (`V1__init.sql`) |

You did **not** create tables manually in the Neon console. When you started the app with `SPRING_PROFILES_ACTIVE=prod` and `.env.prod` pointing at Neon, Flyway connected and ran the migration.

---

## What you have in Neon right now

After a successful prod startup, Neon should contain:

| Table | Purpose |
|-------|---------|
| `users` | Registered players (email, hashed password, display name, role) |
| `scores` | Leaderboard rows linked to `users` via `user_id` |
| `flyway_schema_history` | Flyway’s own log — which migrations already ran (do not delete) |

Plus indexes on `scores` for fast leaderboard queries:

- `idx_scores_points_desc` — `ORDER BY points DESC`
- `idx_scores_user_id` — lookups by user

You can confirm in the [Neon SQL Editor](https://console.neon.tech) or Adminer:

```sql
\dt                    -- list tables (psql)
SELECT * FROM flyway_schema_history;
```

---

## Path A — Local development (Hibernate creates tables)

When you run `./run-dev.sh`:

```
run-dev.sh
  → sources .env.local
  → sets SPRING_PROFILES_ACTIVE=dev
  → mvn spring-boot:run
```

`application-dev.yml` turns on:

```yaml
spring.jpa.hibernate.ddl-auto: update
```

**What `ddl-auto: update` does:**

1. Hibernate reads your Java entity classes (`User.java`, `Score.java`)
2. Compares them to the actual PostgreSQL schema
3. Runs `CREATE TABLE` / `ALTER TABLE` if something is missing or changed

Example log line you may have seen locally:

```
Hibernate: create table users (...)
Hibernate: create table scores (...)
```

**Why we use this locally:** fast iteration — change an `@Column` and restart; Hibernate adjusts the schema. No hand-written SQL needed while learning.

**Why we do *not* use this in production:** Hibernate might make unexpected schema changes. Production needs **versioned, repeatable** SQL that every deploy runs the same way.

---

## Path B — Neon / production (Flyway creates tables)

When you run with the prod profile (e.g. `source .env.prod` then `mvn spring-boot:run`, or on Render):

```
.env.prod
  → DB_HOST, DB_USER, DB_PASSWORD, DB_NAME
  → SPRING_PROFILES_ACTIVE=prod

application-prod.yml
  → jdbc url with ?sslmode=require  (encrypted connection to Neon)
  → ddl-auto: validate              (schema must already match entities)
  → flyway.enabled: true
```

### Startup sequence on Neon

```
1. Spring Boot starts
2. Reads datasource config from .env.prod
3. HikariCP opens a connection pool to Neon (host: ep-xxx...neon.tech)
4. Flyway runs BEFORE Hibernate validates the schema
5. Flyway checks flyway_schema_history on Neon
6. If V1__init.sql not yet applied → executes it
7. Records success in flyway_schema_history
8. Hibernate validates: entities match tables (ddl-auto: validate)
9. App is ready — Tomcat listens on PORT (8080 locally, Render sets PORT)
```

### The migration file

Location: `src/main/resources/db/migration/V1__init.sql`

Flyway naming rules:

| Part | Meaning |
|------|---------|
| `V` | Versioned migration (runs once, in order) |
| `1` | Version number |
| `__` | Separator (two underscores) |
| `init` | Description |

Contents (simplified):

```sql
CREATE TABLE IF NOT EXISTS users (...);
CREATE TABLE IF NOT EXISTS scores (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ...
);
CREATE INDEX idx_scores_points_desc ON scores (points DESC, created_at ASC);
```

`CREATE TABLE IF NOT EXISTS` makes re-runs safe if the table already exists. Flyway still tracks that `V1` ran — it will not execute the file again on the next deploy.

### Flyway vs Hibernate — who does what in prod?

| Tool | Role in prod |
|------|----------------|
| **Flyway** | Creates and evolves **schema** (tables, indexes, constraints) via SQL files |
| **Hibernate** | Maps **rows ↔ Java objects**; only **validates** schema matches `@Entity` classes |
| **Repositories** | Run **queries** (SELECT, INSERT, UPDATE, DELETE) through Hibernate |

Think of it as:

```
Flyway  = architect (builds the building)
Hibernate = translator (Java ↔ SQL rows)
Repositories = API you call from services
```

---

## How entities match the SQL tables

Java entities must align with what Flyway created, or `ddl-auto: validate` fails at startup.

**`User.java` → `users` table**

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;
    // ...
}
```

**`Score.java` → `scores` table**

```java
@Entity
@Table(name = "scores")
public class Score {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "display_name", nullable = false, length = 20)
    private String displayName;

    @Column(nullable = false)
    private Integer points;   // maps to column "points" in SQL
}
```

If you add a new column later:

1. **Prod:** add `V2__add_something.sql` with `ALTER TABLE ...`
2. **Local dev:** Hibernate `update` may add it automatically, but keep Flyway in sync for prod

---

## How the app communicates with the database (runtime)

Once tables exist, every API call follows the same stack — whether DB is local Docker or Neon.

```
HTTP request (e.g. POST /api/scores)
        │
        ▼
ScoreController          ← parses JSON, returns HTTP response
        │
        ▼
ScoreService             ← @Transactional: one unit of work, commit or rollback
        │
        ▼
ScoreRepository          ← interface; Spring generates implementation
        │
        ▼
Hibernate (JPA)          ← builds SQL, maps rows to Score / User objects
        │
        ▼
HikariCP pool            ← borrows a TCP connection (reuses, does not open per request)
        │
        ▼
PostgreSQL JDBC driver   ← sends SQL over the network
        │
        ▼
PostgreSQL               ← localhost:5432 (local) or Neon host (prod, SSL)
```

### Connection config

**Local** (`.env.local` → `application.yml`):

```
jdbc:postgresql://localhost:5432/mergefruit-alex-123
```

**Neon** (`.env.prod` → `application-prod.yml`):

```
jdbc:postgresql://ep-dawn-glade-....neon.tech:5432/neondb?sslmode=require
```

Same driver, same pool — only host and SSL differ.

### Example: submitting a score to Neon

```
POST /api/scores  { "name": "Alex", "score": 5000 }
        │
        ▼
ScoreService.submitScore()     [@Transactional starts]
        │
        ├─► userRepository.findById(...)  or getOrCreateAnonymousUser()
        │         SQL: SELECT ... FROM users WHERE email = ?
        │
        ├─► scoreRepository.save(score)
        │         SQL: INSERT INTO scores (user_id, display_name, points, created_at) VALUES (...)
        │
        └─► scoreRepository.findLeaderboard(...)
                  SQL: SELECT ... FROM scores ORDER BY points DESC, created_at ASC
        │
        ▼
COMMIT — data is persisted on Neon
        │
        ▼
JSON response to client
```

Parameterized queries (`?` placeholders) are used automatically — safe from SQL injection.

### Example: leaderboard query

`ScoreRepository.findLeaderboard()`:

```java
@Query("SELECT s FROM Score s ORDER BY s.points DESC, s.createdAt ASC")
Page<Score> findLeaderboard(Pageable pageable);
```

Hibernate translates JPQL to SQL similar to:

```sql
SELECT s.id, s.user_id, s.display_name, s.points, s.created_at
FROM scores s
ORDER BY s.points DESC, s.created_at ASC
LIMIT ? OFFSET ?
```

The index `idx_scores_points_desc` helps Postgres sort quickly as the table grows.

---

## Configuration files reference

| File | Role |
|------|------|
| `.env.local` | Local DB credentials (Docker Postgres) |
| `.env.prod` | Neon credentials + `SPRING_PROFILES_ACTIVE=prod` |
| `application.yml` | Base datasource, pool, JPA defaults |
| `application-dev.yml` | `ddl-auto: update`, SQL logging |
| `application-prod.yml` | SSL URL, `ddl-auto: validate`, Flyway on |
| `db/migration/V1__init.sql` | Schema Flyway runs on Neon |
| `entity/User.java`, `entity/Score.java` | Java ↔ table mapping |
| `repository/*.java` | Query interfaces |

---

## Adding a new migration later (prod)

When you need schema changes on Neon:

1. Create `src/main/resources/db/migration/V2__describe_change.sql`
2. Write explicit SQL (`ALTER TABLE`, new indexes, etc.)
3. Deploy / restart with `prod` profile
4. Flyway runs only `V2` (skips `V1` — already in `flyway_schema_history`)
5. Update `@Entity` classes to match

Never edit `V1__init.sql` after it has run on Neon — add a new version instead.

---

## Common questions

**Q: I see tables in Neon but never ran SQL manually — is that normal?**  
Yes. Flyway ran `V1__init.sql` when the app first connected with the prod profile.

**Q: Why is `flyway_schema_history` in my database?**  
Flyway creates it to remember which migrations ran. Required for safe deploys.

**Q: Local tables look slightly different from Neon — why?**  
Local used Hibernate `update`; prod used Flyway SQL. They should be equivalent; if not, align entities + add a Flyway migration.

**Q: What if Flyway and Hibernate disagree?**  
Startup fails with a validation error. Fix the entity or the migration SQL so they match.

**Q: Does the React game talk to Neon directly?**  
No. The browser calls your Spring API (Render); only the Java app connects to Neon.

---

## Things to try

1. In Neon SQL Editor: `SELECT * FROM flyway_schema_history;` — see when `V1` ran.
2. Run locally with `SPRING_PROFILES_ACTIVE=dev` and watch `Hibernate:` create/alter lines.
3. Run once with `source .env.prod` and watch logs for `Flyway` / `Successfully applied 1 migration`.
4. Set a breakpoint in `ScoreService.submitScore()` on `scoreRepository.save()` and step through in the debugger.
