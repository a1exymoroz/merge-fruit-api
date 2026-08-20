# How Keycloak Auth Works

This document explains the second, Keycloak-backed auth path in this backend: how a token gets validated, how roles are mapped, and why it lives in its own filter chain instead of merging into the existing JWT auth.

**Short answer:** `GET /api/whoami` trusts access tokens issued by a central Keycloak realm (`company`), shared across all frontends. The token's signature, issuer, expiry, and audience (`backend-api`) are all checked before the request is treated as authenticated. This is separate from the app's own signup/login JWTs — see [How Passwords Are Stored](HOW_PASSWORDS_ARE_STORED.md) for that path.

For the full list of libraries and layers, see [Technology Stack](TECH_STACK.md).

---

## The stack (Keycloak auth)

| Layer | Technology | Role |
|-------|------------|------|
| Identity provider | Keycloak realm `company` | Issues access tokens after hosted login; shared by all frontends |
| Config | `application.yml` | `spring.security.oauth2.resourceserver.jwt.issuer-uri` (env `KEYCLOAK_ISSUER_URI`) |
| Signature + issuer + expiry check | `JwtDecoders.fromIssuerLocation()` + `JwtValidators.createDefaultWithIssuer()` | Fetches Keycloak's public keys (JWKS), verifies signature, checks `iss` and `exp`/`nbf` |
| Audience check | `JwtClaimValidator<List<String>>` on `aud` | Spring's default validator does **not** check audience — this is added explicitly |
| Combined validation | `DelegatingOAuth2TokenValidator` | Token must pass **both** the default checks and the audience check |
| Role mapping | `KeycloakRealmRoleConverter` | Reads `realm_access.roles` → `ROLE_`-prefixed `GrantedAuthority`s |
| Converter wiring | `JwtAuthenticationConverter` | Combines the role mapping into the `Authentication` Spring Security builds from the `Jwt` |
| Filter chain | `KeycloakSecurityConfig.keycloakSecurityFilterChain` | `@Order(1)`, scoped to `/api/whoami/**` only |
| Smoke-test endpoint | `WhoAmIController` | `GET /api/whoami` → username + roles from the validated token |

---

## The big picture

```
GET /api/whoami
Authorization: Bearer <Keycloak access token>
    ↓
KeycloakSecurityConfig's filter chain (securityMatcher: /api/whoami/**)
    ↓
Spring's BearerTokenAuthenticationFilter extracts the token
    ↓
keycloakJwtDecoder
    ├─ fetch Keycloak's public keys (JWKS) → verify RS256 signature
    ├─ check iss == KEYCLOAK_ISSUER_URI, token not expired/not-yet-valid
    └─ check aud contains "backend-api"      ← explicit, not Spring's default
    ↓ (all pass)
keycloakJwtAuthenticationConverter
    └─ realm_access.roles → ROLE_xxx GrantedAuthority list
    ↓
SecurityContext holds a JwtAuthenticationToken (principal = Jwt, authorities = roles)
    ↓
WhoAmIController.whoAmI() reads preferred_username + authorities
```

If any check fails — bad signature, wrong issuer, expired, or missing `backend-api` from `aud` — the request is rejected before it reaches the controller.

---

## Step 1 — Issuer config, not a hardcoded URL

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8081/realms/company}
```

`KEYCLOAK_ISSUER_URI` lets local dev, staging, and prod point at different Keycloak realms without a code change. Spring Boot would normally auto-configure a default `JwtDecoder` from this property alone — but the default decoder does **not** check `aud`, which is why Step 2 replaces it with a custom bean.

---

## Step 2 — Custom decoder: signature + issuer + audience

```java
@Bean
JwtDecoder keycloakJwtDecoder() {
    NimbusJwtDecoder jwtDecoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri);

    OAuth2TokenValidator<Jwt> defaultValidators = JwtValidators.createDefaultWithIssuer(issuerUri);
    OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
            "aud", aud -> aud != null && aud.contains(requiredAudience));

    jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaultValidators, audienceValidator));
    return jwtDecoder;
}
```

- `JwtDecoders.fromIssuerLocation(issuerUri)` calls Keycloak's `/.well-known/openid-configuration` to discover its JWKS endpoint, then verifies the token's RS256 signature against Keycloak's public key.
- `JwtValidators.createDefaultWithIssuer(issuerUri)` adds the standard `iss`/`exp`/`nbf` checks.
- The `JwtClaimValidator` adds the audience check the task required: `requiredAudience` is `"backend-api"`, configured via `app.keycloak.required-audience` in `application.yml` (not hardcoded in Java).
- `DelegatingOAuth2TokenValidator` requires **all** delegates to pass — one failure rejects the token.

---

## Step 3 — Role mapping (`realm_access.roles` → `ROLE_*`)

Keycloak puts realm roles here, not in `scope`:

```json
{
  "realm_access": { "roles": ["user", "admin"] }
}
```

`KeycloakRealmRoleConverter` reads that claim and prefixes each role with `ROLE_`, so `@PreAuthorize("hasRole('admin')")` works the same way it would against any other Spring Security authority:

```java
Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
// realmAccess.get("roles") → List<String> → "ROLE_" + role
```

This converter is plugged into a `JwtAuthenticationConverter`, which Spring Security uses to build the `Authentication` object from the validated `Jwt`.

---

## Step 4 — Why a separate filter chain, not a merge into `SecurityConfig`

The existing custom auth (`AuthController` → `JwtService`, HS256, this app's own `JWT_SECRET`) and Keycloak both use `Authorization: Bearer <token>`. Spring's OAuth2 resource server filter (`BearerTokenAuthenticationFilter`) doesn't know or care which system a token came from — if it runs on a request, it tries to validate **any** bearer token against Keycloak.

That means:
- One shared chain would make the Keycloak decoder try to validate the app's own tokens (and vice versa) — a **real bug we hit**: the old `JwtAuthenticationFilter` threw an uncaught `UnsupportedJwtException` when it received an RS256 Keycloak token, because it unconditionally tried to parse every bearer token as its own HS256 format.
- The fix has two parts:
  1. `KeycloakSecurityConfig`'s chain is scoped with `.securityMatcher("/api/whoami/**")` and given `@Order(1)` (evaluated first). Every other request falls through, unchanged, to `SecurityConfig`'s chain (now explicitly `@Order(2)` — required once a second chain exists).
  2. `JwtAuthenticationFilter` now catches `JwtException`/`IllegalArgumentException` around its own parse attempt, so a token it doesn't recognize (e.g. a Keycloak token sent to `/api/scores`) is treated as "not authenticated via this filter" instead of crashing the request.

Routes outside `/api/whoami/**` still only understand the app's own JWTs. Keycloak tokens sent to those routes are currently just ignored (not recognized as an identity) — extending Keycloak auth to more routes is a separate, bigger decision (which routes, and what happens to the existing signup/login system).

---

## Step 5 — The smoke-test endpoint

```java
@GetMapping
public WhoAmIResponse whoAmI(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
    String username = jwt.getClaimAsString("preferred_username");
    if (username == null) username = jwt.getSubject();

    List<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toList();

    return new WhoAmIResponse(username, roles);
}
```

Try it with a real token from any frontend:

```bash
curl -s http://localhost:8080/api/whoami -H "Authorization: Bearer $TOKEN" | jq
```

---

## Files to read in the codebase

| File | Role |
|------|------|
| `config/KeycloakSecurityConfig.java` | Decoder, converter, and the scoped `SecurityFilterChain` |
| `security/KeycloakRealmRoleConverter.java` | `realm_access.roles` → `ROLE_*` authorities |
| `controller/WhoAmIController.java` | `GET /api/whoami` |
| `dto/WhoAmIResponse.java` | Response shape (`username`, `roles`) |
| `config/SecurityConfig.java` | Existing custom-JWT chain — unchanged except `@Order(2)` |
| `security/JwtAuthenticationFilter.java` | Now ignores tokens it can't parse instead of throwing |
| `resources/application.yml` | `issuer-uri`, `required-audience`, CORS origins |

---

## Common mistakes (and ones we actually hit)

- **Assuming Spring's default issuer-uri config checks audience** — it doesn't. Skipping the custom `JwtDecoder` would silently accept tokens meant for a completely different backend that happens to share the same Keycloak realm.
- **Wiring Keycloak into the same filter chain as an existing bearer-token scheme** — causes exactly the crash described in Step 4. Scope with `securityMatcher()` or migrate fully; don't silently overlap.
- **Frontend sending the Keycloak token to every endpoint** (typical of an HTTP interceptor) **before the backend is ready for it** — routes not yet migrated to Keycloak will simply not recognize that identity. Decide per-route which auth system owns it.
- **CORS origin mismatch** — a browser 403 with `Invalid CORS request` means the request's `Origin` isn't in `CORS_ALLOWED_ORIGINS`, not a token problem. Check the exact scheme+host+port in the address bar (dev servers sometimes bump ports if one is taken).

---

## Related docs

- [Technology Stack](TECH_STACK.md) — full project stack
- [How Passwords Are Stored](HOW_PASSWORDS_ARE_STORED.md) — the existing custom JWT auth this coexists with
