# SaaS Layer with Keycloak — Implementation Plan

Plan for turning `terrapi-api` into a multi-tenant SaaS using **Keycloak** as the
identity provider, **opaque API keys** for the data API, and **Stripe** for billing.

## Decisions

| Topic | Choice |
|---|---|
| Product shape | B2B — organizations with multiple users/members |
| Machine-to-machine auth | Local opaque API keys (`tp_live_…`), hashed in DB |
| Billing | Stripe, with a free plan and trials |
| Code placement | New `account` module |
| Usage metering | Caffeine (in-memory) now, designed to move to Redis later |

## Current state

Spring Boot 4 / Java 25, modules `core` (JPA entities + Spring Data repos), `web`
(`/api/v1/**` controllers), `import`, `application`. Postgres + PostGIS, Flyway *and*
`ddl-auto: update`, Lombok, CORS wide open, **no security today**.

## Module layout

```
core        -> add ONLY TenantContext (thread-local) so web + account can read tenant
               without a dependency cycle
account     -> NEW. depends on core. owns: SaaS entities/repos/services, security,
               Keycloak admin, Stripe, usage/rate-limit, console + webhook controllers
web         -> unchanged logic; now runs behind security; CorsConfig tightened
application -> depends on account; broaden JPA scanning; add config + docker-compose
```

`TenantContext` lives in `core` so `web`'s data services can scope/attribute usage
without a `web -> account` dependency. The `SecurityFilterChain` defined in `account`
applies app-wide because it is a servlet filter in the shared Spring context (no
compile dependency from `web` is required).

## Domain model (new entities, `pt.terrapi.account.entities`, Lombok like `GeoUnit`)

> Terminology: "client" is overloaded. In Keycloak a *client* = an app/credential;
> in SaaS a *client* = the customer. The customer is modelled as an **Organization**.

| Entity | Key columns |
|---|---|
| `Organization` | id (UUID), name, slug (unique), status, `stripe_customer_id`, timestamps |
| `AppUser` | id, `keycloak_sub` (unique), email, full_name, last_seen_at |
| `OrganizationMember` | org_id, app_user_id, role (OWNER/ADMIN/MEMBER), unique(org, user) |
| `ApiKey` | id, org_id, label, `prefix`, `key_hash` (sha-256), last_four, status, created_by, last_used_at, expires_at?, revoked_at |
| `Plan` | code (FREE/PRO/ENTERPRISE), `stripe_price_id`?, included_units, rate_limit_per_min, overage_unit_price?, `trial_days`, active |
| `Subscription` | org_id (unique), plan_id, `stripe_subscription_id`?, status (TRIALING/ACTIVE/PAST_DUE/CANCELED/FREE), period_start/end, `trial_end`?, cancel_at_period_end |
| `UsageCounter` | PK(org_id, period `yyyy-MM`), units (bigint) — persisted aggregate |

## Users + Keycloak sync

Keycloak owns credentials/login; the app DB owns org membership, roles, ownership,
and usage. The app mirrors users, it does not duplicate credentials.

- Console logs in via Keycloak (Authorization Code + PKCE). The API is an OAuth2
  **resource server** validating the realm JWT.
- **JIT provisioning**: a post-JWT filter upserts `AppUser` by `sub` from claims —
  no separate sync job.
- **Invites**: `OrganizationService.invite()` calls the **Keycloak Admin REST API**
  (`keycloak-admin-client`) to create the user + send the invite, then writes
  `OrganizationMember`.
- Single realm; org membership lives in the app DB. Keycloak 25+ "Organizations"
  can map enterprise SSO later without changing this.

## Security (`account` module, applies app-wide)

- Dependencies: `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`.
- One `SecurityFilterChain`:
  - `oauth2ResourceServer().jwt()` + a `JwtAuthenticationConverter` (Keycloak realm
    roles -> authorities) that JIT-upserts the user.
  - `ApiKeyAuthFilter` registered before the bearer filter: if the request carries
    `Authorization: Bearer tp_live_…`, hash and look up `ApiKey`, then set the
    Authentication + `TenantContext`.
  - Permit: swagger, `/actuator/health`, `POST /stripe/webhook` (verified by Stripe
    signature, not auth). `/api/v1/**` data API requires an API key or JWT;
    `/api/v1/console/**` requires JWT + org role (`@EnableMethodSecurity`).
- Replace the wildcard `CorsConfig` with a configured allow-list.

## API tokens — opaque keys

- Generate a random key, store only a **hash** (sha-256) + a short prefix for
  display; show the full key once on creation.
- `ApiKeyAuthFilter` resolves the key -> organization -> sets `TenantContext` and an
  authenticated principal.
- Keycloak human JWTs and API keys both flow through the single `SecurityFilterChain`;
  the data API accepts either.

## Usage metering + rate limiting (Caffeine now, Redis-ready)

Two interfaces so the swap is a single bean change later:

- `UsageStore` -> `CaffeineUsageStore` (in-memory counter per org/period;
  `@Scheduled` flush to `usage_counter`). Future: `RedisUsageStore`.
- `RateLimiter` -> Bucket4j local buckets held in a Caffeine cache keyed by org.
  Future: Bucket4j-Redis.

A `HandlerInterceptor` on billable `/api/v1/**`:

1. Resolve org from `TenantContext`.
2. Rate limit: `RateLimiter.tryConsume(orgId, plan.rateLimitPerMin)` -> `429` if
   exceeded, set `X-RateLimit-*` headers.
3. Quota: check current-period units vs `plan.includedUnits`; over-quota free plan
   without overage -> `402`.
4. On success: `usageStore.increment(orgId, units)`.

## Billing — Stripe with free plan + trials

- `Plan` seeded via Flyway: FREE (no Stripe price, small quota), PRO
  (`stripe_price_id`, `trial_days = 14`), ENTERPRISE.
- New org -> `Subscription` on **FREE** immediately (no card required).
- Upgrade: backend creates a Stripe Customer + **Checkout Session**
  (`subscription` mode, `trial_period_days`) -> returns the redirect URL.
- `/stripe/webhook` (signature-verified) handles `customer.subscription.*`,
  `invoice.paid`, `invoice.payment_failed` -> updates `Subscription` status / period /
  trial_end.
- Stripe Billing Portal endpoint for self-serve management.

## Endpoints (`account`)

- `GET /api/v1/me`
- `POST /api/v1/orgs`, `GET/PATCH /api/v1/orgs/{id}`
- `GET/POST /api/v1/orgs/{id}/members`, invite, role change, remove
- `GET/POST /api/v1/orgs/{id}/api-keys` (create returns plaintext once),
  `DELETE /api/v1/api-keys/{id}` (revoke)
- `GET /api/v1/orgs/{id}/usage?period=`
- `GET /api/v1/plans`
- `POST /api/v1/orgs/{id}/billing/checkout`, `POST /api/v1/orgs/{id}/billing/portal`,
  `GET /api/v1/orgs/{id}/subscription`
- `POST /stripe/webhook`

## Wiring changes (must-do)

- `JpaConfig`: `@EnableJpaRepositories` currently pins `pt.terrapi.core.repository`.
  **Broaden to `pt.terrapi`** (or add `pt.terrapi.account.repository`) or it will not
  see the account repos. Entity auto-scan from `pt.terrapi` already covers account.
- `application.yaml`: add `spring.security.oauth2.resourceserver.jwt.issuer-uri`,
  Keycloak admin (`url` / `realm` / `client` / `secret`), Stripe (`secret-key`,
  `webhook-secret`, price ids), `terrapi.account.*` (key prefix, default plan, trial
  days). All via `${ENV:default}`.
- Flyway: add `V2__account_core.sql`, `V3__plans_seed.sql`; flip `ddl-auto: update` ->
  **`validate`** so identity/billing tables are not auto-mutated.
- Root `pom.xml` dependencyManagement: caffeine, bucket4j, keycloak-admin-client,
  stripe-java. `account/pom.xml` consumes those + web/security/oauth2/validation.
  `application/pom.xml` adds the `account` dependency.

## Infrastructure

- `docker-compose.yml`: Keycloak (`quay.io/keycloak/keycloak`) + its DB, with a
  `terrapi` **realm-export JSON** (realm, `terrapi-console` public + PKCE client, API
  audience, roles) for reproducible setup, alongside the existing Postgres on :5445.

## Build order

1. Keycloak up + resource-server security + JIT upsert + lock CORS/endpoints.
2. `Organization` + members + invite (Keycloak Admin API).
3. API keys + `ApiKeyAuthFilter` + `TenantContext`.
4. Usage (`UsageStore` / `RateLimiter` Caffeine) + interceptor.
5. Plans + `Subscription` + Stripe checkout/portal/webhooks + trials.
6. Console controllers + OpenAPI.
