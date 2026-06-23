# Technology Stack

What this backend is built with — runtime, frameworks, data layer, auth, email, and hosting.

For password-specific flow, see [How Passwords Are Stored](HOW_PASSWORDS_ARE_STORED.md).

---

## At a glance

| Layer | Technology |
|-------|------------|
| Language | Java 21 |
| Build | Maven |
| Framework | Spring Boot 3.4.5 |
| API | Spring Web (REST + JSON) |
| Validation | Jakarta Bean Validation (`@Valid`, `@NotBlank`, …) |
| Security | Spring Security 6 (stateless JWT) |
| Password hashing | BCrypt (`BCryptPasswordEncoder`) |
| Tokens | JJWT 0.12.6 (HS256 signed JWT) |
| Database | PostgreSQL 16 |
| ORM | Hibernate via Spring Data JPA |
| Connection pool | HikariCP |
| Migrations | Flyway |
| Email | Brevo REST API (`RestClient`) |
| Health checks | Spring Boot Actuator |
| Local DB | Docker Compose (Postgres + Adminer) |
| Production host | Render (Docker web service) |
| Production DB | Neon (managed PostgreSQL) |
| Frontend (separate repo) | React + Vite (typical dev port `5173`) |

---

## Application layers

How a typical authenticated request moves through the system:

```
React client (Bearer JWT in Authorization header)
    ↓
CORS filter (SecurityConfig — allowed origins from env)
    ↓
JwtAuthenticationFilter (parse JWT, load user, set SecurityContext)
    ↓
SecurityFilterChain (authorize: public vs authenticated routes)
    ↓
@RestController (AuthController, ScoreController, …)
    ↓
@Service (AuthService, ScoreService, EmailService, …)
    ↓
Repository interface (UserRepository, ScoreRepository, …)
    ↓
Hibernate / JPA
    ↓
HikariCP connection pool
    ↓
PostgreSQL JDBC driver
    ↓
PostgreSQL
```

Signup and login hit the same controller → service → repository path; login also goes through Spring Security's `AuthenticationManager` before a JWT is minted.

---

## Authentication stack

Auth is **stateless**: no server-side HTTP sessions. After login, the client sends a JWT on each request.

### Libraries

| Piece | Library / class | Role |
|-------|-----------------|------|
| Security framework | `spring-boot-starter-security` | Filter chain, auth providers, method security |
| Password encoder | `BCryptPasswordEncoder` | Hash on signup; `matches()` on login |
| Login orchestration | `AuthenticationManager` + `DaoAuthenticationProvider` | Email/password check against DB hash |
| User loading | `CustomUserDetailsService` | `UserDetailsService` → load `User` by email |
| Security adapter | `UserPrincipal` | Maps `User` entity → Spring `UserDetails` |
| JWT create/verify | `JwtService` (JJWT) | Sign with `JWT_SECRET`; HS256; 1h expiry (configurable) |
| Per-request auth | `JwtAuthenticationFilter` | `OncePerRequestFilter` before controllers |
| Central config | `SecurityConfig` | Routes, CORS, stateless sessions, beans |

### Signup stack (password → database)

```
HTTP POST /api/auth/signup
    ↓
Jackson deserializes JSON → SignUpRequest
    ↓
Jakarta Validation (@Valid on controller)
    ↓
AuthService.signUp()
    ↓
BCryptPasswordEncoder.encode(plainPassword)
    ↓
User entity (password field = hash)
    ↓
UserRepository.save() → JPA INSERT
    ↓
PostgreSQL users.password
    ↓
EmailService → Brevo API (verification email)
    ↓
JwtService.generateToken() → AuthResponse JSON
```

### Login stack (password check → JWT)

```
HTTP POST /api/auth/login
    ↓
LoginRequest validated
    ↓
AuthService.login()
    ↓
AuthenticationManager.authenticate(UsernamePasswordAuthenticationToken)
    ↓
DaoAuthenticationProvider
    ├─ CustomUserDetailsService.loadUserByUsername(email)
    │     └─ UserRepository → PostgreSQL (loads BCrypt hash)
    └─ PasswordEncoder.matches(typedPassword, storedHash)
    ↓
AuthService checks emailVerified
    ↓
JwtService.generateToken(UserPrincipal)
    ↓
AuthResponse { accessToken, expiresIn, … }
```

### Protected request stack (JWT, no password)

```
HTTP request + Authorization: Bearer <jwt>
    ↓
JwtAuthenticationFilter
    ├─ JwtService.extractUsername(jwt)
    ├─ CustomUserDetailsService.loadUserByUsername(subject)
    └─ JwtService.isTokenValid(jwt, userDetails)
    ↓
SecurityContextHolder populated
    ↓
Controller method runs (e.g. POST /api/scores)
```

Password is **not** involved after login. The JWT only carries the email (`sub` claim) and expiry — signed with `JWT_SECRET`, not encrypted.

---

## Data stack

| Piece | Technology | Notes |
|-------|------------|-------|
| Driver | `org.postgresql:postgresql` | JDBC to Postgres |
| Pool | HikariCP (Spring Boot default) | `maximum-pool-size: 10` in `application.yml` |
| ORM | Hibernate | `ddl-auto: validate` — schema owned by Flyway |
| Repositories | Spring Data JPA | `JpaRepository` interfaces |
| Migrations | Flyway | `src/main/resources/db/migration/V*.sql` |
| Local schema (dev) | Hibernate `update` | Only when `spring.profiles.active=dev` |

See [How Java Connects to the DB](HOW_JAVA_CONNECTS_TO_DB.md) and [How Tables Are Created](HOW_TABLES_ARE_CREATED.md).

---

## Email stack

| Piece | Technology |
|-------|------------|
| Provider | [Brevo](https://www.brevo.com/) transactional API |
| Client | Spring 6 `RestClient` → `https://api.brevo.com/v3/smtp/email` |
| Use cases | Email verification on signup (password reset planned as student TODO) |
| Config | `BREVO_API_KEY`, `MAIL_FROM`, `MAIL_ENABLED`, `FRONTEND_URL` |

HTTPS is used instead of SMTP because Render blocks outbound port 587.

---

## Configuration & secrets

Loaded from environment (`.env.local` for dev, Render env vars for prod):

| Variable | Used by |
|----------|---------|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | JDBC datasource |
| `JWT_SECRET` | JWT signing (min 32 chars) |
| `ANONYMOUS_USER_PASSWORD` | Internal anonymous user (BCrypt-hashed in DB) |
| `CORS_ALLOWED_ORIGINS` | React app origin(s) |
| `BREVO_API_KEY`, `MAIL_*`, `FRONTEND_URL` | Email |
| `SPRING_PROFILES_ACTIVE` | `dev` (local) vs `prod` (Flyway + validate) |

`application.yml` maps these with `${…}` placeholders; optional `optional:file:.env.local[.properties]` import for local runs.

---

## Local development stack

```
./run-dev.sh
    → Maven spring-boot:run
    → Spring profile: dev
    → Postgres: Docker (docker-compose) OR Neon (.env.prod)
    → API: http://localhost:8080
    → Adminer (optional): http://localhost:8081
```

Dependencies declared in `pom.xml`; tests use H2 in-memory (`scope: test`).

---

## Production stack

```
Render Web Service (Docker)
    → SPRING_PROFILES_ACTIVE=prod
    → Health: GET /actuator/health
    → Neon PostgreSQL (connection string via DB_* env vars)
    → Brevo for outbound email
```

Defined in `render.yaml`. See [Deploy to free hosting](DEPLOY.md).

---

## Maven dependencies (summary)

From `pom.xml`:

| Dependency | Purpose |
|------------|---------|
| `spring-boot-starter-web` | REST API, embedded Tomcat, Jackson |
| `spring-boot-starter-data-jpa` | JPA, Hibernate, transactions |
| `spring-boot-starter-security` | Auth filter chain, BCrypt |
| `spring-boot-starter-validation` | Request DTO validation |
| `spring-boot-starter-actuator` | `/actuator/health` |
| `postgresql` | JDBC driver (runtime) |
| `flyway-core` + `flyway-database-postgresql` | Schema migrations |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` | JWT |
| `spring-boot-devtools` | Hot reload (dev, optional) |
| `spring-boot-starter-test`, `spring-security-test`, `h2` | Tests only |

---

## Related docs

- [How Passwords Are Stored](HOW_PASSWORDS_ARE_STORED.md) — BCrypt, signup/login detail
- [How Java Connects to the DB](HOW_JAVA_CONNECTS_TO_DB.md) — JDBC → JPA path
- [How Tables Are Created](HOW_TABLES_ARE_CREATED.md) — Flyway vs Hibernate
- [Deploy](DEPLOY.md) — Render + Neon setup
