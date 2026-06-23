# How Passwords Are Saved to the Database

This document explains what happens to a user's password from signup through storage in PostgreSQL — and how login checks it later.

**Short answer:** The app never stores the raw password. It stores a **BCrypt hash** in the `users.password` column.

For the full list of libraries and layers (Spring Security, JWT, PostgreSQL, etc.), see [Technology Stack](TECH_STACK.md).

---

## The stack (auth & passwords)

| Layer | Technology | Role in password flow |
|-------|------------|------------------------|
| API | Spring Web + Jackson | JSON in/out for `/api/auth/signup` and `/login` |
| Validation | Jakarta Bean Validation | `SignUpRequest` — password 8–72 chars |
| Business logic | `AuthService` | `encode()` on signup; `authenticate()` on login |
| Password hashing | `BCryptPasswordEncoder` | One-way hash; auto-salted |
| Security framework | Spring Security 6 | `AuthenticationManager`, filter chain |
| User lookup | `CustomUserDetailsService` | Load stored hash by email |
| Auth provider | `DaoAuthenticationProvider` | `matches(plain, hash)` at login |
| Entity bridge | `UserPrincipal` | `User` → `UserDetails` |
| Persistence | Spring Data JPA + Hibernate | `UserRepository.save()` |
| Database | PostgreSQL | `users.password` column (VARCHAR 255) |
| Post-login tokens | JJWT (`JwtService`) | JWT after auth — **no password in token** |
| Config | `SecurityConfig` | Registers encoder, provider, stateless sessions |

---

## The big picture

```
POST /api/auth/signup  { email, password, displayName }
    ↓
AuthController         validates JSON (@Valid SignUpRequest)
    ↓
AuthService.signUp()   passwordEncoder.encode(plainPassword)
    ↓
User entity            user.setPassword(hashedValue)
    ↓
UserRepository.save()  JPA INSERT into users
    ↓
PostgreSQL             password column holds BCrypt string (e.g. $2a$10$...)
```

On login, the flow is different: Spring Security loads the hash from the DB and compares it to the password the user typed — without ever decoding the hash.

```
POST /api/auth/login  { email, password }
    ↓
AuthService.login()   AuthenticationManager.authenticate(...)
    ↓
CustomUserDetailsService   loads User from DB by email
    ↓
DaoAuthenticationProvider  BCrypt.matches(typedPassword, storedHash)
    ↓
JWT issued (password is NOT included in the token)
```

---

## Step 1 — API boundary validation

Signup accepts JSON mapped to `SignUpRequest`:

| Field | Validation |
|-------|------------|
| `email` | Not blank, valid email, max 255 chars |
| `password` | Not blank, **8–72 characters** |
| `displayName` | Not blank, 2–50 characters |

The 72-character cap is intentional: BCrypt only uses the first 72 bytes of a password. Longer input would be silently truncated during hashing, which is confusing and insecure.

`AuthController` applies `@Valid` before calling the service, so invalid passwords never reach the hashing step.

---

## Step 2 — Hashing at signup (`AuthService`)

When a new user registers, `AuthService.signUp()` does the following:

1. Normalizes email: `trim()` + `toLowerCase()`
2. Checks for duplicate email
3. **Hashes the password** with `passwordEncoder.encode(request.password())`
4. Builds a `User` entity and saves it via `UserRepository`

Relevant code:

```java
user.setPassword(passwordEncoder.encode(request.password()));
userRepository.save(user);
```

`passwordEncoder` is Spring Security's `BCryptPasswordEncoder`, configured in `SecurityConfig`:

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

### What BCrypt produces

BCrypt is a **one-way** hash function designed for passwords:

- **Slow by design** — makes brute-force guessing expensive
- **Salted automatically** — each hash includes a random salt, so two users with the same password get different stored values
- **Self-describing output** — the hash string encodes algorithm version, cost factor, salt, and digest

Example stored value (format only — yours will differ every time):

```
$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
 │  │  │                                    │
 │  │  └─ salt (22 chars)                  └─ hash (31 chars)
 │  └─ cost factor (10 = 2^10 iterations)
 └─ BCrypt variant
```

The plaintext password **cannot** be recovered from this string. Login only checks whether a newly typed password produces a matching hash.

---

## Step 3 — Database schema

Flyway migration `V1__init.sql` defines the `users` table:

```sql
CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    ...
);
```

| Column | What is stored |
|--------|----------------|
| `password` | BCrypt hash string (~60 characters), **not** the user's password |

Hibernate maps this column via the `User` entity:

```java
@JsonIgnore
@Column(nullable = false)
private String password;
```

`@JsonIgnore` prevents the hash from leaking if a `User` object is ever serialized to JSON in a response.

---

## Step 4 — Login verification (hash comparison)

Login does **not** hash the password manually in `AuthService`. Instead it delegates to Spring Security:

```java
authenticationManager.authenticate(
    new UsernamePasswordAuthenticationToken(request.email().trim(), request.password()));
```

Behind the scenes:

1. **`CustomUserDetailsService`** loads the user by email and wraps it in `UserPrincipal`
2. **`UserPrincipal.getPassword()`** returns the **stored hash** from the database
3. **`DaoAuthenticationProvider`** (configured with the same `PasswordEncoder`) calls `matches(plainPassword, storedHash)`
4. If they match, authentication succeeds; otherwise an `BadCredentialsException` is thrown

So verification is: *"Does this typed password hash to the same value as what's in the DB?"* — not decryption.

After successful authentication, `AuthService` also checks `emailVerified` before issuing a JWT.

---

## What is NOT stored or exposed

| Item | Behavior |
|------|----------|
| Plaintext password | Never written to DB, logs, or JWT |
| Password in API responses | `User.password` has `@JsonIgnore`; responses use DTOs like `AuthResponse` |
| Password in JWT | JWT contains subject (email) and claims — not credentials |
| Password reset | Not implemented yet (`TODO` in `AuthController` / `AuthService`) |

---

## Other code paths that save passwords

### Anonymous leaderboard user (`ScoreService`)

When an unauthenticated player submits a score, the app may create a system user `anonymous@mergefruit.local`. That user's password is also BCrypt-hashed — using a secret from config (`ANONYMOUS_USER_PASSWORD`), not a human-chosen password:

```java
anonymous.setPassword(passwordEncoder.encode(anonymousUserPassword));
```

This account exists for internal linking (scores → `user_id`), not for interactive login by players.

---

## Files to read in the codebase

| File | Role |
|------|------|
| `controller/AuthController.java` | `POST /api/auth/signup`, `POST /api/auth/login` |
| `dto/SignUpRequest.java` | Password length validation |
| `service/AuthService.java` | `encode()` on signup, `authenticate()` on login |
| `config/SecurityConfig.java` | `BCryptPasswordEncoder` bean, `DaoAuthenticationProvider` |
| `security/CustomUserDetailsService.java` | Loads user + hash from DB for login |
| `security/UserPrincipal.java` | Supplies stored hash to Spring Security |
| `entity/User.java` | JPA mapping for `users.password` |
| `resources/db/migration/V1__init.sql` | Table definition |

---

## Common mistakes (called out in the project)

- **Storing plaintext** — always use `passwordEncoder.encode()` before `save()`
- **Returning `User` from a controller** — would expose the hash without careful DTOs
- **Putting passwords in JWTs** — tokens are only base64-encoded, not encrypted
- **Skipping validation** — enforce rules at the API boundary (`SignUpRequest`)

---

## Related docs

- [Technology Stack](TECH_STACK.md) — full project stack and auth layer diagrams
- [How Java Connects to the DB](HOW_JAVA_CONNECTS_TO_DB.md) — JPA, repositories, and the path to PostgreSQL
- [How Tables Are Created](HOW_TABLES_ARE_CREATED.md) — Flyway migrations and the `users` table lifecycle
