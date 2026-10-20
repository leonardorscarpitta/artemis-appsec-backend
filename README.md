<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset=".github/assets/logo-dark.svg">
    <img src=".github/assets/logo-light.svg" alt="Artemis" width="232" height="64">
  </picture>
</p>

<p align="center">Continuous application security testing — API</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-0b4f8a" alt="Java 25">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1-0b4f8a" alt="Spring Boot 4.1">
  <img src="https://img.shields.io/badge/PostgreSQL-18-0b4f8a" alt="PostgreSQL 18">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-5a6474" alt="License: MIT"></a>
</p>

---

Backend of Artemis, a SAST platform. The frontend lives in the [artemis-appsec-frontend](https://github.com/leonardorscarpitta/artemis-appsec-frontend) repository.

**Naming:** users see the product as **Artemis**; technical identifiers use **artemisappsec** — Java package `io.github.artemiscore.artemisappsec`, Maven artifact, `spring.application.name`, configuration prefix `artemisappsec.*`, database name/user, OIDC client `artemisappsec-web`, audience `artemisappsec-api` and the local auth host `auth.artemisappsec.localhost`.

## Running locally

Everything runs in Docker:

```sh
cp .env.example .env             # first time only; set passwords, SCANNER_TOKEN and KEYCLOAK_ADMIN_API_CLIENT_SECRET
                                 # (openssl rand -hex 32 each; compose refuses to start without the Keycloak secret)
docker compose up -d
./keycloak/create-dev-users.sh   # first time only (once Keycloak is up); prints the development logins
```

| Service | URL |
| --- | --- |
| API | http://localhost:8080/api/v1 |
| OpenAPI / Swagger UI | http://localhost:8080/api/docs/ui (dev profile only; elsewhere off unless `API_DOCS_ENABLED=true`) |
| Health | http://localhost:8080/actuator/health |
| Keycloak (sign-in, registration, account) | https://auth.artemisappsec.localhost:9443/realms/artemisappsec/account |
| Keycloak admin console | https://auth.artemisappsec.localhost:9443/admin (`KEYCLOAK_ADMIN_*` in `.env`) |
| Mailpit (dev e-mails) | http://localhost:8025 |
| Remote debugger (JDWP) | `localhost:5005` (host only; the scanner reaches the API through `api-gateway`, never the debug port) |
| PostgreSQL | `localhost:5432` |

Every port is published on **127.0.0.1 only** (the dev stack has weak defaults and JDWP has no authentication); nothing is reachable from the network.

The `api` container runs `mvn spring-boot:run` with the `dev` profile. Maven compiles once at startup, so after changing code run `docker compose restart api` (DevTools also restarts the app when an IDE recompiles classes into `target/`). Dependencies are cached in `.m2/` (git-ignored).

The `dev` profile loads demo data (`src/main/resources/db/seed`): the **Acme Corp** organization (slug `acme`) whose members `ana.souza@acme.example` (owner), `carla.mendes@…` (analyst) and `daniel.rocha@…` (developer) match the Keycloak dev users and are linked on their first sign-in. Set `AUTH_ENABLED=false` in `.env` to skip authentication entirely (local debugging only: every request then acts as owner of the organization in the header).

**Resetting the dev database.** The seeds are rewritten when the schema changes in ways they depend on (e.g. multi-tenancy), so an existing dev database may fail Flyway validation. Reset only the **application** database (never the `keycloak` one, which holds the dev users):

```sh
docker compose stop api
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "DROP SCHEMA public CASCADE" -c "CREATE SCHEMA public"'
docker compose start api
```

Production image: `docker build -t artemisappsec-api .` (multi-stage, non-root, layered jar).

**Memory limits** (the stack is sized for a 16 GB machine, ~4.5 GB in total): `db` 512 MB, `api` 1.5 GB (Maven `-Xmx384m`, application JVM `-Xmx640m -XX:+UseSerialGC`), `keycloak` 768 MB, `mailpit` 64 MB, `auth-proxy` 64 MB, `api-gateway` 64 MB, `scanner` 2 GB (+ 2 CPUs, 512 processes).

## Authentication (Keycloak)

Keycloak 26.4 is the OpenID Connect provider (realm `artemisappsec`, imported from `keycloak/realm-artemisappsec.json` on first start); the API is an OAuth2 resource server.

```
browser ──https──> Caddy :9443 (auth-proxy) ──> Keycloak :8080
API ──http──> keycloak:8080/realms/artemisappsec/protocol/openid-connect/certs   (internal, signing keys)
```

- **TLS:** Caddy terminates HTTPS with its local CA at `https://auth.artemisappsec.localhost:9443` (`*.localhost` resolves to your machine). Trust Caddy's root CA once (`docker compose cp auth-proxy:/data/caddy/pki/authorities/local/root.crt .`) or accept the warning. Keycloak runs with `--proxy-headers=xforwarded` and a fixed public `--hostname`.
- **Sign-in pages (Artemis theme):** `keycloak/themes/artemis/login` extends Keycloak's `keycloak.v2` login theme with the Artemis layout (navy information panel, logo, IBM Plex fonts served by Keycloak itself, light/dark from the OS, en/pt-BR). Only `template.ftl` (layout), `resources/css/artemis.css` (colors on PatternFly variables) and `messages/` are customised, so every Keycloak page — sign-in, registration, password reset, Terms, required actions — keeps Keycloak's logic and security; passwords are never handled by the app. The realm sets `loginTheme: artemis`; the client's `baseUrl` feeds the “Back to Artemis” and legal links. Dev mounts the folder and disables theme caching (edit and reload); where the folder can't be mounted (Kubernetes) use the image built by `keycloak/Dockerfile` (official Keycloak + this theme, published as `artemis-appsec/keycloak:latest`); for an existing realm: `kcadm.sh update realms/artemisappsec -s loginTheme=artemis`. The app passes `ui_locales` so the pages open in the app's language.
- **Realm:** self-registration (e-mail verification **off**: no outgoing mailbox yet; e-mails stay unique per account), Terms of Use / Privacy Policy acceptance on first sign-in (required action with links to the app), password policy (12–128 chars, not username/e-mail, history 3), brute-force protection, en/pt-BR, reset password, account deletion, Keycloak Organizations **off** (unused: tenancy lives in Artemis), user and admin events stored for 90 days (`eventsEnabled`, `adminEventsEnabled` without representations, `eventsExpiration`/`adminEventsExpiration` 7776000 s; Keycloak admin console → Events) — the audit trail of sign-ins and of what the `artemisappsec-admin-api` service account does. Dev e-mails go to **Mailpit** (http://localhost:8025); set `KC_SMTP_*` for a real server.
- **Client:** `artemisappsec-web`, public, Authorization Code + PKCE (S256), redirect `ARTEMISAPPSEC_WEB_ORIGIN/auth/callback`, post-logout redirect to the app, audience mapper adding `artemisappsec-api` to access tokens. `prompt=create` opens the registration page.
- **No offline tokens:** the SPA client has no optional `offline_access` scope (long-lived offline refresh tokens in browser storage would outlive the SSO session if stolen). The realm file is only imported on first start; to apply this to an existing realm:
  ```sh
  docker compose exec keycloak sh -c '
    k=/opt/keycloak/bin/kcadm.sh
    $k config credentials --server http://localhost:8080 --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD"
    CID=$($k get clients -r artemisappsec -q clientId=artemisappsec-web --fields id --format csv --noquotes)
    SID=$($k get client-scopes -r artemisappsec --fields id,name --format csv --noquotes | grep ",offline_access$" | cut -d, -f1)
    $k delete clients/$CID/optional-client-scopes/$SID -r artemisappsec'
  ```
- **No refresh tokens for the SPA:** `artemisappsec-web` has `use.refresh.tokens: false`, so the browser storage holds only the 15-minute access token (an XSS can't steal a long-lived refresh token); the app signs in again when it expires (the SSO session usually makes that silent).
- **Applying realm changes to an existing realm** (the realm file is imported only on first start; dev users stay):
  ```sh
  docker compose exec keycloak sh -c '
    k=/opt/keycloak/bin/kcadm.sh
    $k config credentials --server http://localhost:8080 --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD"
    $k update realms/artemisappsec -s eventsEnabled=true -s eventsExpiration=7776000 -s adminEventsEnabled=true \
      -s adminEventsDetailsEnabled=false -s organizationsEnabled=false -s attributes.adminEventsExpiration=7776000
    CID=$($k get clients -r artemisappsec -q clientId=artemisappsec-web --fields id --format csv --noquotes)
    $k update clients/$CID -r artemisappsec -s "attributes.\"use.refresh.tokens\"=false"'
  ```
- **Service account scope (accepted risk):** `artemisappsec-admin-api` holds `realm-management` `manage-users`, because Keycloak cannot grant "delete users" alone; with its secret someone could also reset passwords or change users. Treat `KEYCLOAK_ADMIN_API_CLIENT_SECRET` like the deploy-key master key (secrets manager, never in logs or tickets, rotate if exposed); the admin events above record every action it takes.
- **API validation:** issuer, expiry and audience (`artemisappsec-api`); principal = `preferred_username`. Authorization is per organization (below); the only role read from the token is the realm role `artemis-admin` (`realm_access.roles` → `ROLE_ARTEMIS_ADMIN`, see "Platform administrators").
- **Database:** Keycloak uses its own database/role (`keycloak`) in the same PostgreSQL instance, created idempotently by the `keycloak-db-init` job (`KEYCLOAK_DB_PASSWORD`).
- **Dev users:** `./keycloak/create-dev-users.sh` creates `ana.souza`, `carla.mendes`, `daniel.rocha` and the platform administrator `admin.artemis` (realm role `artemis-admin`) (verified e-mail, random passwords) and writes `keycloak/secrets/dev-credentials.txt` (git-ignored).
- `GET /api/v1/me` returns the signed-in user (`username`, `name`, `email`, `emailVerified`, `platformAdmin`).

## Organizations and permissions

Artemis is multi-tenant: every repository, finding, run, report, policy, integration, key, token, member, invitation, and setting belongs to an **organization**. Tenancy and roles live in Artemis (not in Keycloak).

- **Tenant header:** every organization-scoped request carries `X-Artemis-Organization: <slug>` (the frontend sends the slug of its `/<slug>/…` URL). Missing header → `400` `organization-required`; unknown slug, not a member or inactive member → `404` `organization-not-found` (existence is not revealed; Artemis administrators get a read-only view instead, see "Platform administrators"); missing permission → `403` `forbidden` with `permission`. Endpoints without the header: `/me`, `/me/**`, `POST /organizations`, `/pipeline/stages`, `/admin/**`.
- **Membership:** a member is linked to the Keycloak account by `sub`. Members created before the account existed (seeds) are linked on first use only when the token's e-mail is **verified** (`email_verified`); e-mail verification is off in the realm, so new accounts join through invitation links.
- **Roles → permissions** — defaults (`organization/domain/Role.java`), editable **per organization** (table `organization_role_permission`, V32; `organization/persistence/RolePermissionStore`):

  | Role | viewFindings | triage | pipelines | integrations | team | billing |
  | --- | --- | --- | --- | --- | --- | --- |
  | owner | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
  | admin | ✓ | ✓ | ✓ | ✓ | ✓ | |
  | analyst | ✓ | ✓ | ✓ | | | |
  | developer | ✓ | ✓ | | | | |
  | viewer | ✓ | | | | | |

  `viewFindings`: every `GET`. `triage`: finding status, risk acceptances, reports. `pipelines`: create/configure repositories and pipelines, run/re-run/cancel scans, edit policies. `integrations`: integrations, deploy keys, API tokens. `team`: invitations, roles, removing members, organization settings. `billing` (owner; shown as "Ownership and deletion" — the code keeps its old name): ownership transfer, deleting the organization.

  **Editing:** `GET /roles` → `[{role, permissions, assignable, editable}]` (`editable` is computed for the caller); `PUT /roles/{role}/permissions` `{"permissions": [...]}` → the updated entry. Needs the `team` permission and a **role** of owner or admin (403 `forbidden`); admins edit analyst, developer and viewer only — the admin row is owner-only (403 `owner-only`) — and can only **add** permissions their own role has (403 `permission-not-held`; removing is always allowed). Invariants (enforced on write and on read): the owner always has every permission and is not editable (400 `role-not-editable`); every role keeps `viewFindings` (400 `permission-required`); `billing` is never granted to other roles (400 `owner-only-permission`). New organizations get the defaults above; changes apply on the members' next request (the tenant's permissions come from the matrix).
- **Enforcement:** `organization/api/TenantInterceptor` resolves the tenant for every `/api/v1/**` handler (unless annotated `@WithoutOrganization`) and checks `@RequiresPermission`; `GET` defaults to `viewFindings` and any other method **without** the annotation is refused (fail closed). Controllers receive a `Tenant` parameter and pass `tenant.organizationId()` to services; every query is filtered by organization (directly or through its repositories), so ids/numbers from another organization return `404`.
- **Rules:** only the owner manages admins (admins cannot assign, invite, demote or remove admins → 403 `owner-only`); one owner per organization; ownership is transferred (`POST /members/transfer-ownership`, the old owner becomes admin), never assigned; nobody changes their own role or removes themselves (they leave); the owner cannot leave. Each account can own **one organization** (409 `organization-limit` on creation; ownership can only be transferred to someone who owns no organization). Organizations owned before this rule stay available.
- **Account endpoints:** `GET /me/organizations` (`[{slug, name, role, permissions, features}]`), `POST /organizations` (`{name, defaultLanguage?}`, slug derived from the name → the creator becomes owner; default settings, integration catalogue and policies are provisioned), `GET /me/invitations` (pending, non-expired invitations for the account's e-mail), `POST /me/invitations/{id}/accept|decline`, `POST /me/organizations/{slug}/leave`.
- **Slugs:** `^[a-z0-9](?:[a-z0-9-]{1,38}[a-z0-9])$`, unique (`409 slug-taken`), not an app route (`409 slug-reserved`: `organizations`, `invite`, `auth`, `login`, `help`, `privacy`, `terms`, `api`, …). The slug is always derived from the name (lowercase, accents dropped, spaces and symbols → `-`, max 40) and cannot be edited; `general.slug` is ignored on `PUT /settings`. Renaming the organization needs the `organizationRename` feature (enabled by the Artemis administrators; `403 feature-not-enabled` otherwise) and changes the slug, so the organization's URLs change too (old URLs are not redirected).
- **Invitations** are joined through a **one-time link**: `POST /invitations` and `POST /invitations/{id}/resend` return a `token` (256-bit random, base64url) once — only its SHA-256 is stored, `GET /invitations` never includes it, and resending replaces it. The inviter shares the link (the frontend builds `<origin>/invite/<token>`); `POST /me/invitations/accept` `{"token"}` (any signed-in account, no org header) joins with the invited role and consumes the invitation (`404 invitation-not-found` if unknown/expired, `409 already-member`, `409 email-in-use` if another member already has the account's e-mail). `POST /me/invitations/preview` `{"token"}` → `{organizationName, organizationSlug, role}` shows what the link offers **without consuming it** (same `404 invitation-not-found`), so the frontend asks before joining; the token travels in the body, never in a URL the API sees. Links expire after 7 days. `GET /me/invitations` and accept/decline by id only match a **verified** e-mail (e-mail verification is off, so an unverified address must not be enough to claim an invitation). Invitations are not e-mailed.
- **Personal data:** `GET /invitations` needs the `team` permission, and `GET /members` returns `email: null` to callers without it (data minimisation: only owners and admins see members' addresses).
- Deleting an organization (`DELETE /organization`, owner) removes everything in it (database `ON DELETE CASCADE`).

## Configuration

All environment-specific values come from environment variables (secrets in production); see `.env.example`.

| Variable | Purpose |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `OIDC_ISSUER_URI` | Expected `iss` claim; also used for discovery when no JWKS URL is set |
| `OIDC_JWK_SET_URI` | Optional URL of the signing keys (internal URL in Docker) |
| `OIDC_AUDIENCE` | Required `aud` claim (default `artemisappsec-api`) |
| `AUTH_ENABLED` | `dev` profile only: `false` disables authentication |
| `AUTH_PUBLIC_URL`, `ARTEMISAPPSEC_WEB_ORIGIN` | Keycloak public URL (`--hostname`) and the frontend origin (redirect/CORS in the realm) |
| `KEYCLOAK_ADMIN_USERNAME`, `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_DB_PASSWORD` | Keycloak bootstrap admin and database credentials |
| `KC_SMTP_HOST`, `KC_SMTP_PORT`, `KC_SMTP_FROM` | Outgoing e-mail for password reset (Mailpit by default) |
| `API_DOCS_ENABLED` | `true` to serve the OpenAPI document and Swagger UI under `/api/docs` outside the `dev` profile (default `false`: 404) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins allowed to call `/api/**` |
| `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` | Optional regex of the reverse proxies whose `X-Forwarded-*` headers are trusted (`forward-headers-strategy: native`; default: Tomcat's loopback and private ranges). Clients connecting directly cannot spoof forwarded headers |
| `SCANNER_TOKEN` | Shared secret of the scanner runner (≥ 32 random characters; the internal job API is disabled otherwise). Also `SCANNER_STAGE_TIMEOUT_SECONDS` (600), `SCANNER_MAX_REPO_SIZE_MB` (500), `SCANNER_STALE_RUN_MINUTES` (60) |
| `SCANNER_RUN_TIMEOUT_SECONDS` | Wall-clock limit for one whole scan run in the runner (default 3600) |
| `DEPLOY_KEY_ENCRYPTION_KEY`, `DEPLOY_KEY_ENCRYPTION_KEY_ID` | Master key (base64 of 32 random bytes, `openssl rand -base64 32`) and its label (`v1`) for encrypting the private deploy keys of private repositories. Without a valid key, private repositories are disabled (`503 deploy-keys-disabled`) |
| `KEYCLOAK_ADMIN_API_CLIENT_SECRET` | Secret (≥ 32 characters, `openssl rand -hex 32`) of the confidential client `artemisappsec-admin-api` used for user management; also fed to the realm import. Without it the `/admin/users` endpoints answer `503 user-management-disabled`. Also `KEYCLOAK_ADMIN_API_URL` (internal Keycloak URL, `http://keycloak:8080` in Docker), `KEYCLOAK_ADMIN_API_REALM` (`artemisappsec`), `KEYCLOAK_ADMIN_API_CLIENT_ID` (`artemisappsec-admin-api`) |
| `ANTHROPIC_API_KEY` | Claude API key (console.anthropic.com) for AI-written report texts and finding explanations; empty = disabled (reports use the standard texts, `POST /findings/{id}/insight` answers 503). Also `ANTHROPIC_MODEL` (`claude-haiku-5-5`), `AI_TIMEOUT_SECONDS` (60), `AI_REPORTS_PER_DAY` (10) and `AI_INSIGHTS_PER_DAY` (50) — AI **attempts** per organization per UTC day, failed ones included — and `AI_SBOM_MAX_PACKAGES` (200, 0–5000: dependencies listed for the SBOM supply chain review, ~12 input tokens each) |

## Architecture

Service-oriented modular monolith. Each business module is a package with the same layers:

```
<module>/
  api/          REST controllers (HTTP only, no business logic)
  service/      public service interface + records it exposes, and its package-private implementation
  domain/       JPA entities and enums (private to the module)
  persistence/  Spring Data repositories (private to the module)
```

Rules:

- Modules talk to each other **only through `service` interfaces and their records** — never through another module's entities or repositories.
- Controllers return service records; entities never leave their module.
- `shared/` holds cross-cutting code: configuration (security, CORS, OpenAPI), error handling (RFC 7807 `ProblemDetail`) and shared domain enums (`Severity`, `Stage`, `Phase`, `FailThreshold`).
- Schema changes are Flyway migrations in `db/migration`; demo data in `db/seed` (`V1000+`, dev only). Hibernate only validates the schema.
- No Lombok.

| Module | Responsibility | Base path |
| --- | --- | --- |
| `project` | Monitored repositories, scan settings, pipeline configuration | `/api/v1/repositories`, `/api/v1/pipeline/stages` |
| `finding` | Findings of every kind (vulnerability, secret, code, config, license), status workflow and timeline; ingestion of scanner results with deduplication, auto-fix and reopen; provides open counts to `project` via `ProjectFindingStats` | `/api/v1/findings` |
| `pipelinerun` | Pipeline executions: list/filter, 7-day summary, run detail with stage logs and stored SBOMs, SBOM download, re-run, cancel and manual scans (quota-checked; a repository's first scan is queued when it is connected) | `/api/v1/pipeline-runs`, `/api/v1/repositories/{name}/scans` |
| `scanjob` | Internal job API for the scanner runner: claim queued runs, record the scanned commit, stage results and findings, complete/fail runs; fails runs stuck in `running` | `/internal/v1/jobs` (runner only) |
| `dashboard` | Read-only aggregation of findings and repositories | `/api/v1/dashboard/summary`, `/api/v1/dashboard/trends` |
| `report` | Security reports with optional topics (executive summary, recommendations, SBOM), generated in the background (see [Reports](#reports)); text localized by `Accept-Language` (en-US, pt-BR) | `/api/v1/reports` |
| `insight` | AI explanations of findings, on request and cached per organization (see [AI finding explanations](#ai-finding-explanations)) | `/api/v1/findings/{id}/insight` |
| `policy` | Organization-wide stage gates (fail threshold, required), remediation SLAs, license allow/deny lists, risk acceptances | `/api/v1/policies`, `/api/v1/risk-acceptances` |
| `account` | The signed-in user from the JWT | `/api/v1/me` |
| `usage` | Artemis is free (no plans or payments). Monthly scan quota: **25 scans per organization** and UTC month (`organization_scan_usage` counter, V31: one row per org and month, incremented in the same transaction that queues a scan with a single conditional `INSERT … ON CONFLICT DO UPDATE … WHERE scans < limit`, so parallel requests cannot overshoot and deleting a repository does not give scans back). The run stores the reserved month (`pipeline_run.quota_month`, V34); runs that end before the runner reports any stage (access check or clone failed, runner rejected the job, cancelled, swept as stale) give the scan back (`quota_refunded`, shown on the run), while runs that ran stages count even when they fail. Settling and refunding are single conditional updates on the run row, so a scan is refunded at most once. Scans, re-runs and first scans are refused (`409`) once the month's limit is reached. `GET /usage` → `{scansThisMonth, scanLimit, periodStart, resetsAt, features}` | `/api/v1/usage` |
| `organization` | Organizations (tenancy, memberships, creation, invitations for the signed-in account, permission enforcement), integrations, SSH deploy keys, team (members, invitations, roles, ownership), organization settings, API tokens. Optional features per organization (`organization_feature`, V41; `OrganizationFeatures` service used by other modules to gate them) and the platform administration API. Publishes `OrganizationCreated` (policy provisions its defaults) and counts repositories through the `RepositoryCounts` port (implemented by `project`) | `/api/v1/organizations`, `/api/v1/me/organizations`, `/api/v1/me/invitations`, `/api/v1/integrations`, `/api/v1/deploy-keys`, `/api/v1/members`, `/api/v1/invitations`, `/api/v1/roles`, `/api/v1/organization`, `/api/v1/api-tokens`, `/api/v1/admin/organizations` |

Query parameters accept the same enum values as the JSON (`?status=falsePositive&severity=critical`), via `shared/config/WireEnumConverterFactory`.

Security notes for `organization`:

- Deploy keys accept **public keys only** (private keys are rejected); the type and key data are validated (ed25519, ECDSA, RSA ≥ 2048 bits) and the OpenSSH `SHA256:` fingerprint is computed server-side.
- API tokens (`art_…`) are generated with `SecureRandom` and returned **once**; only their SHA-256 hash and last four characters are stored.
- The OIDC client secret is never stored or returned — it belongs in the deployment's secret store; the API only reports whether it is configured.

JSON enum values match the frontend (`"Java + Spring"`, `"24h"`, `"sast"`, `"critical"`...).

## Scanner runner

Security tools run **self-hosted** in the `scanner` service (image `artemisappsec/runner`, built from `scanner/`). No cloud scanning service is used.

| Stage | Tool | License |
| --- | --- | --- |
| Secrets | Gitleaks 8.30.1 | MIT |
| SAST | Semgrep CE 1.181.0 (engine) with Artemis's own 31 rules in `scanner/rules/` (JavaScript/TypeScript, Java + Spring, Python incl. Flask, Django and FastAPI) — never the Semgrep Registry | LGPL-2.1 |
| Dependencies (SCA), licenses, IaC, Dockerfile | Trivy 0.76.0 | Apache-2.0 |
| Dependencies (SCA, alternative) | Grype 0.120.1 | Apache-2.0 |
| Dockerfile | Hadolint 2.15.1 | GPL-3.0 |
| SBOM | Syft 1.54.1 | Apache-2.0 |
| Container image | Trivy 0.76.0 `image --image-src remote` on the **published** image in the repository's `containerImage` setting (e.g. `ghcr.io/acme/app:1.4`; skipped when empty). Pulled straight from the registry — never built or run, no container socket; the registry host must resolve to public addresses (default HTTPS port), max 700 MB compressed, layers unpacked in the in-memory workspace (`TMPDIR`) and wiped after the stage. Public registries only (anonymous pulls; Docker Hub rate-limits them). | Apache-2.0 |
| DAST | — not available yet (needs an authorized, verified running target in its own sandbox); reported as skipped | |

Licensing (see `scanner/THIRD_PARTY_NOTICES.md`, shipped in the image with every tool's license under `/usr/share/licenses/`): each tool is installed **unmodified** from its official release and executed as a **separate program**; no tool code is part of Artemis, so the GPL/AGPL terms do not extend to it. Vulnerability data comes from public databases (NVD, GitHub Advisory Database, OSV…): *This product uses data from the NVD API but is not endorsed or certified by the NVD.*

**How it works.** The worker (`scanner/worker/`, Python standard library only) polls the API's internal job endpoints (`/internal/v1/jobs/…`, `Authorization: Bearer $SCANNER_TOKEN`), clones the repository, runs the enabled stages, normalizes every tool's output to one finding format and reports it back. The API decides stage/run status from the configured fail thresholds.

**Security and privacy measures**

- *Supply chain:* pinned tool versions; every download verified against a pinned SHA-256 (cross-checked with the official checksum files where published); multi-stage build, no build tools in the final image.
- *Sandbox:* non-root user (uid 10001), read-only root filesystem, all Linux capabilities dropped, `no-new-privileges`, memory/CPU/process limits, no Docker socket, no database credentials. The `scanner` network is shared only with `api-gateway` (Caddy forwarding port 8080 to the API, `ARTEMIS_API_URL=http://api-gateway:8080`) — the API container itself (with its unauthenticated JDWP debug port 5005), the database and Keycloak are unreachable.
- *Process hygiene:* the worker reads `SCANNER_TOKEN` once, removes it from its environment and marks itself non-dumpable (`prctl(PR_SET_DUMPABLE, 0)`), so a tool compromised by a crafted repository (same uid) cannot read the token or decrypted deploy keys from `/proc/<pid>/environ` or memory. Every tool runs in its own process group that is killed as a whole on timeout; leftover processes are reaped between jobs (the worker is PID 1 — no `init`, which would keep the token readable in its own environment). One wall-clock budget per run, `SCANNER_RUN_TIMEOUT_SECONDS` (default 3600), caps all stages and folders together: when it runs out, the remaining stages are reported as skipped and the run fails.
- *Untrusted repository config:* Gitleaks would read `.gitleaks.toml`/`.gitleaksignore` from the scanned tree, so they are deleted from the run's copy first (a repository cannot switch secret detection off). Trivy downloads remote Terraform modules while evaluating IaC and ignores `--offline-scan`/proxy settings for it (verified), which would let a repository make the runner request internal URLs; before the IaC stage every Terraform module `source` that is not a local `./`/`../` path is pointed at a missing local path, and `trivy config` runs with `--skip-check-update --tf-exclude-downloaded-modules`. `trivy image` gets `--` before the reference. The local `scan --dir` mode scans a copy of the directory.
- *Clone:* **SSH only**, with the repository's Artemis-generated read-only deploy key (see "Private repositories") — public repositories too, so every scan proves the user was granted access by the repository's owner. Jobs for non-SSH repositories are refused by both the API and the runner (the runner's local `scan --url` dev mode can still clone public HTTPS). Credentials in URLs and hosts resolving to private, loopback, link-local or metadata addresses are refused; `--depth 1 --single-branch --no-tags`, no submodules, hooks, LFS, symlinks or redirects; size limit (500 MB) and timeouts. No repository code is ever executed (no installs, no builds).
- *Privacy:* source code lives in an in-memory workspace that is deleted after each run (and on start-up); only the dependency list (CycloneDX SBOM from Syft) is sent to the API and stored with the run. Secrets are redacted by Gitleaks and the worker never forwards secret values or code snippets — only rule, file and line. Logs are size-limited and redacted. Telemetry and update checks are disabled (`--metrics=off`, `*_CHECK_FOR_APP_UPDATE=false`); the only outbound traffic is the git clone and vulnerability-database downloads, which contain no user data. Trivy runs with `--offline-scan`, so it never asks package registries about the repository's dependencies (e.g. Maven Central for parent POMs, which also rate-limits with HTTP 429); the trade-off is that versions defined only in a remote parent POM/BOM may stay unresolved.

**Run it.** Set `SCANNER_TOKEN` in `.env` (`openssl rand -hex 32`, at least 32 characters) and `docker compose up -d`; the first scan downloads the Trivy/Grype databases into the `scanner_cache` volume.

**Local mode** (no API, for testing tools and rules):

```sh
./scanner/test-fixtures/make-secret-fixture.sh     # generates a fake credential (git-ignored)
docker run --rm --read-only --cap-drop ALL --tmpfs /tmp --tmpfs /workspace:uid=10001,gid=10001 \
  -v artemis-scan-cache:/cache -v "$PWD/scanner/test-fixtures/vulnerable-app:/fixture:ro" \
  artemisappsec/runner:dev scan --dir /fixture                # or: scan --url https://github.com/org/repo.git --branch main
```

Options: `--stages secrets,sast,...` and `--tool sca=Grype` / `--tool dockerfile=Trivy`. Output is the normalized JSON sent to the API.

## Scanning (API side)

Scans run in the separate scanner runner (see "Scanner runner"); the API only queues work and records results.

1. A scan is queued by `POST /api/v1/repositories/{name}/scans` (manual, counts against the monthly quota → `409` when exhausted), by re-running a run, by the scheduler (when enabled, see "Scheduled scans"), or automatically when a public repository is connected (private ones wait until their deploy key is added). Runs keep a snapshot of each enabled stage: tool and fail threshold.
2. The runner claims the oldest queued run (`SELECT … FOR UPDATE SKIP LOCKED`, so several runners never share a run). Stages that are not available yet (`dast`) are skipped at that point.
3. For each stage the runner reports `completed`, `skipped` or `error` plus normalized findings. The API decides the stage status: `error` → `failed`; `completed` → `failed` when a finding reaches the stage's fail threshold (`failedBy`), `warning` when there are findings below it, `passed` otherwise.
4. On `complete`, stages that were not reported are skipped and the run becomes `success` or `failed`; the repository's last scan time is updated. Runs still `running` after `SCANNER_STALE_RUN_MINUTES` (default 60) are failed by a scheduled sweep.

Repository URLs must be SSH (`git@host:owner/repo.git`, `ssh://[git@]host[:port]/owner/repo(.git)`) — HTTPS is rejected with 400. The API also rejects hosts that cannot be a public Git server — single-label names (`db`, `api`, `localhost`), names starting with `-`, and IP literals in loopback, private, link-local (incl. `169.254.169.254`), shared (100.64/10), multicast or unique-local ranges — and URLs whose repository name would be empty, `.` or `..` (the runner still checks resolved addresses before connecting). Every repository is cloned with an Artemis-generated deploy key (see "Private repositories"); `V18` converted existing `https://host/owner/repo` URLs to `git@host:owner/repo.git` (access pending). Queueing a scan (`POST …/scans`, re-run) returns `409` until the repository's access is **verified**, so no quota is spent on scans that cannot clone.

### Optional features and platform administrators

Artemis is free; there are no plans or payments. Three optional features are enabled **per organization** by the Artemis administrators: `aiTriage` (AI report texts and AI finding explanations), `scheduledScans` and `organizationRename`. They live in `organization_feature` (V41: `organization_id, feature, granted_by, granted_at`); V41 enabled all three for organizations that were on the former PRO plan (the `subscription` table is no longer read). Ungranted features answer `403` `feature-not-enabled` (with `feature`).

- **Platform administrator** = Keycloak realm role `artemis-admin` (in the realm import; the default `roles` client scope puts it in the access token's `realm_access.roles`). Grant it with `kcadm.sh add-roles -r artemisappsec --uusername <user> --rolename artemis-admin` (inside the Keycloak container, after `kcadm.sh config credentials …`); the user must sign in again to get a new token. For realms imported before the role existed, create it first: `kcadm.sh create roles -r artemisappsec -s name=artemis-admin`.
- `GET /api/v1/admin/organizations` → `[{slug, name, createdAt, ownerName, memberCount, repositoryCount, features}]`, `GET /api/v1/admin/organizations/{slug}` → one entry, `PUT /api/v1/admin/organizations/{slug}/features` `{"features": [...]}` → the updated entry (`granted_by` = the admin's username) and `DELETE /api/v1/admin/organizations/{slug}` → `204` (same cascade as the owner's `DELETE /organization`: repositories, deploy keys, findings, runs, reports, members, invitations, settings, features, usage; logged at INFO with the admin's username and the slug). No organization header; `403` without the role.
- **Read-only view of every organization:** the role grants **no membership**, but an administrator who is not a member of an organization resolves there as a fixed viewer — role `viewer`, permissions exactly `["viewFindings"]`, no member row (`memberId` null) — so every `GET` works (repositories, findings, runs, reports, dashboard, CSV export, team without e-mail addresses) and every write answers `403` `forbidden`. The organization's editable viewer row does not apply, so owners cannot widen it. If the administrator is also a member, the membership role wins. Each read-only visit is logged at INFO (`Artemis administrator <username> opened organization <slug> read-only`, at most once per hour per administrator and organization).
- **Users:** `GET /api/v1/admin/users?search=&first=0&max=100` (`max` ≤ 200; `search` matches username, name and e-mail in Keycloak) → `{users: [{id, username, name, email, createdAt, enabled, platformAdmin, organizations: [{slug, name, role}]}], total}`. `id` is the Keycloak user id (`sub`); organizations come from the active `member` rows linked to it (`user_subject`); accounts without organizations are included. `DELETE /api/v1/admin/users/{id}` → `204`: removes every `member` row of the account, then deletes the Keycloak account (which ends its sessions); invitations it sent stay. Access tokens already issued stay valid until they expire (JWTs are checked offline), so the API refuses the deleted account's tokens on every `/api/v1/**` call (`401 unauthenticated`) for 20 minutes (`organization/service/DeletedAccounts`, in memory — per API instance; Artemis runs one). Refused with `409`: `cannot-delete-self`, `platform-admin` (remove the `artemis-admin` role in Keycloak first) and `owns-organizations` (with `organizations: [slugs]`; delete those organizations or have the owner transfer ownership first). Unknown id → `404`. Logged at INFO with the admin's username, the user id and username (no e-mail). The user can register again later (as a new account).
- **Keycloak Admin API access:** the API calls Keycloak's Admin REST API with a client-credentials token (cached until 30 s before expiry) of the confidential client **`artemisappsec-admin-api`** (no browser, password or device flows; service account only) whose service account has exactly the `realm-management` roles `view-users`, `query-users` and `manage-users` (it can't change realm settings or grant roles). Platform-admin status is read per user from `users/{id}/role-mappings/realm/composite`. Missing secret → `503 user-management-disabled`; Keycloak unreachable or credentials rejected → `503 user-directory-unavailable` (details in the API log). The realm import creates the client on a fresh Keycloak (secret = `KEYCLOAK_ADMIN_API_CLIENT_SECRET`); `./keycloak/create-dev-users.sh` creates it on an existing dev realm (writing a generated secret to `.env` when empty). By hand, inside the Keycloak container:
  ```sh
  docker compose exec keycloak sh -c '
    k=/opt/keycloak/bin/kcadm.sh
    $k config credentials --server http://localhost:8080 --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD"
    $k create clients -r artemisappsec -s clientId=artemisappsec-admin-api -s "name=Artemis API (user administration)" \
      -s enabled=true -s publicClient=false -s clientAuthenticatorType=client-secret -s serviceAccountsEnabled=true \
      -s standardFlowEnabled=false -s implicitFlowEnabled=false -s directAccessGrantsEnabled=false -s frontchannelLogout=false
    $k add-roles -r artemisappsec --uusername service-account-artemisappsec-admin-api --cclientid realm-management \
      --rolename view-users --rolename query-users --rolename manage-users
    CID=$($k get clients -r artemisappsec -q clientId=artemisappsec-admin-api --fields id --format csv --noquotes)
    $k get clients/$CID/client-secret -r artemisappsec          # read the generated secret ("value")
    # or set your own: $k update clients/$CID -r artemisappsec -s secret=<openssl rand -hex 32>
    # or rotate:       $k create clients/$CID/client-secret -r artemisappsec'
  ```
  Put the secret in `KEYCLOAK_ADMIN_API_CLIENT_SECRET` (secrets manager in production) and recreate the API (`docker compose up -d api`).
  **Empty secret:** Keycloak accepts an empty client secret, so a realm imported while `KEYCLOAK_ADMIN_API_CLIENT_SECRET` was empty lets anyone obtain the service account's token with `client_secret=`. `docker-compose.yml` now refuses to start without the variable. To fix a realm imported that way, re-run `./keycloak/create-dev-users.sh` (it sets the client secret from `.env` through stdin, not on the command line) or by hand:
  ```sh
  set -a; . ./.env; set +a
  printf '{"secret":"%s"}' "$KEYCLOAK_ADMIN_API_CLIENT_SECRET" | docker compose exec -T keycloak sh -c '
    k=/opt/keycloak/bin/kcadm.sh
    $k config credentials --server http://localhost:8080 --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" </dev/null
    CID=$($k get clients -r artemisappsec -q clientId=artemisappsec-admin-api --fields id --format csv --noquotes </dev/null)
    $k update clients/$CID -r artemisappsec -f -'
  # must print 401:
  curl -sk -o /dev/null -w '%{http_code}\n' -d grant_type=client_credentials -d client_id=artemisappsec-admin-api -d client_secret= \
    https://auth.artemisappsec.localhost:9443/realms/artemisappsec/protocol/openid-connect/token
  ```

### Scheduled scans (optional feature)

A repository's `schedule` is `manual` (default), `push` (stored only; no webhooks yet) or a timed interval: `6h`, `24h`, `7d`. Timed schedules need the `scheduledScans` feature, enabled per organization by the Artemis administrators: choosing one without it returns `403` with `code: feature-not-enabled`; schedules saved earlier stay stored but inactive.

- `project.next_scan_at` (V36, UTC) is set to *now + interval* whenever the schedule changes and exposed as `nextScanAt` on repositories (null for manual/push).
- `ScheduledScans` runs every minute: it locks up to 50 due repositories (`SELECT … FOR UPDATE SKIP LOCKED`) and moves `next_scan_at` one interval ahead in the same transaction, so several API instances never queue the same scan twice. Each due repository is then queued in its own transaction through the normal scan path (trigger `schedule`, actor `Artemis scheduler`): quota, policies and the runner's access re-check apply as for manual scans.
- Skipped until the next interval (nothing is recorded): organizations without the feature, repositories whose access is not verified, repositories with a run already queued or running, and scans refused by the quota or by having no enabled stages.

### Private repositories

1. **Key per repository.** Connecting an SSH URL generates an **ed25519** key pair in the API (JDK `Ed25519`), stores the public key and the **OpenSSH private key encrypted with AES-256-GCM** (`deploy_key.private_key_ciphertext/_nonce/encryption_key_id`; random 96-bit nonce; AAD = deploy key id + key id, so ciphertexts cannot be moved between rows) with the master key `DEPLOY_KEY_ENCRYPTION_KEY`. The plaintext exists only in memory; it is never returned by the public API, logged or put in exceptions (`SshCredentials.toString()` is redacted). No first scan is queued: the repository waits for its key to be added (`accessStatus: pending`).
2. **The user adds the public key** to the repository as a **read-only deploy key** (GitHub: Settings → Deploy keys; GitLab: Settings → Repository → Deploy keys; Bitbucket: Repository settings → Access keys) and asks for an **access check**. The runner runs `git ls-remote --heads <url> refs/heads/<branch>` with the key; the result (`ok` only when the branch exists) sets `accessStatus` to `verified` or `failed` with a sanitized message (permission denied, host key mismatch, repository/branch not found, unreachable). Each check (and the detection clone after it) uses the shared runner, so an organization can request at most **30 access checks per hour** (`429 access-check-limit`; a repeated request while a check is queued or running reuses it and is not counted).
3. **Host keys are always verified** (`StrictHostKeyChecking=yes`). `github.com`, `gitlab.com` and `bitbucket.org` use the official keys pinned in `scanner/known_hosts` (collected with `ssh-keyscan` and checked against each provider's documented fingerprints; GitHub's also against `https://api.github.com/meta` — sources in the file header). Other servers: the first **successful** access check records the key read with `ssh-keyscan` (trust on first use, shown to users with its SHA256 fingerprint) and every later connection must match it; scans of such a server fail until a check has recorded it.
4. **Scans** of private repositories get `credentials` in the claim; the runner writes the key to the in-memory workspace (mode 0600), clones through `GIT_SSH_COMMAND` (`-F /dev/null`, `IdentitiesOnly`, `IdentityAgent=none`, `BatchMode`, publickey only, own `known_hosts`, timeouts) with only the `ssh` protocol allowed, and **deletes the key right after the clone**, before any tool runs. A run of a private repository without a key, or whose host key is unknown, is failed at claim time and never reaches the runner.
5. **Rotation and revocation.** `POST /repositories/{name}/access/key` generates a new key and deletes the old one (status back to `pending`); deleting the managed key under `/deploy-keys` does the same without a replacement. Removing the repository deletes its keys (both with and without purge) — users should also remove the deploy key from the provider. Every release of a key to the scanner is logged with its fingerprint and purpose (`run #N` / `access check <id>`) and updates `lastUsedAt`.
6. **Master key.** Keep `DEPLOY_KEY_ENCRYPTION_KEY` in a secrets manager and back it up: losing it means generating new deploy keys for every private repository. Its id (`DEPLOY_KEY_ENCRYPTION_KEY_ID`) is stored with every ciphertext; a key whose id differs from the server's cannot be decrypted (rotate the master key by introducing a new id and rotating each repository's deploy key — re-encryption tooling is not built yet).

Public endpoints (organization-scoped):

| Method and path | Permission | Response |
| --- | --- | --- |
| `GET /api/v1/repositories/{name}/access` | viewFindings | `{type: "public"\|"ssh", deployKey: {id, publicKey, fingerprint, createdAt, lastUsedAt} \| null, host, hostKeyFingerprint, hostKeyPinned, lastCheck: {id, status: queued\|running\|ok\|failed, message, requestedAt, finishedAt} \| null}` (public repositories: only `type`, the rest `null`/`false`) |
| `POST /api/v1/repositories/{name}/access/key` | integrations | `200` access view with the new key; `409` for public repositories; `503 deploy-keys-disabled` without a master key |
| `POST /api/v1/repositories/{name}/access/checks` | pipelines | `202` access view (an already queued/running check is reused); `409` when there is no key |
| `GET /api/v1/deploy-keys` | viewFindings | items `{id, name, keyType, fingerprint, repository, addedAt, lastUsedAt, managed, publicKey}` — `managed` keys were generated by Artemis (`publicKey` set); older public-only keys cannot clone. Keys are no longer pasted (`POST` removed) |
| `DELETE /api/v1/deploy-keys/{id}` | integrations | `204` |

Repository summaries also carry `access` (`public` \| `ssh`) and `accessStatus` (`pending` \| `verified` \| `failed`, `null` for public).

### Internal job API

`/internal/**` has its own security chain: no CORS, no OIDC; the runner sends `Authorization: Bearer $SCANNER_TOKEN` (compared in constant time). With a token shorter than 32 characters the whole internal API answers `503`. It is hidden from the OpenAPI docs.

| Method and path | Body | Response |
| --- | --- | --- |
| `POST /internal/v1/jobs/claim` | — | `204` when nothing is queued. Access checks are handed out first: `200` `{type: "access-check", checkId, repository: {name, cloneUrl, branch, access: "ssh"}, credentials: {sshPrivateKey, knownHosts}}`; otherwise the oldest run: `{type: "scan", runId, number, repository: {name, cloneUrl, branch, paths, image, access: "public"\|"ssh"}, credentials: {sshPrivateKey, knownHosts} \| null, stages: [{id, tool, failOn}], limits: {stageTimeoutSeconds, maxRepoSizeMb}}`. `knownHosts` is the recorded host key of non-pinned servers (else `null`) |
| `PUT /internal/v1/access-checks/{checkId}` | `{status: ok\|failed, message, hostKey}` | `204`; `hostKey` (the `ssh-keyscan` lines used) is validated and recorded only on `ok` for non-pinned servers without one; `409` if the check already finished. Checks still running after 10 minutes are failed by the sweep |
| `POST /internal/v1/jobs/{runId}/source` | `{commitSha, commitMessage}` | `204` |
| `PUT /internal/v1/jobs/{runId}/stages/{stage}` | `{status: completed\|skipped\|error, tool, durationSeconds, log, findings: [Finding]}` | `{status: passed\|warning\|failed\|skipped}`; `409` if the run is not running or the stage was already reported; `400` if the tool differs from the queued one |
| `PUT /internal/v1/jobs/{runId}/sbom?folder=` | the CycloneDX JSON file written by Syft (`Content-Type: application/json`, ≤ 20 MB) | `{components}`; sent before the SBOM stage's report (`409` if the run is not running or the stage was already reported, `400` for an unknown folder or a body that is not CycloneDX JSON, `413` above 20 MB) |
| `POST /internal/v1/jobs/{runId}/complete` | — | `204` |
| `POST /internal/v1/jobs/{runId}/fail` | `{reason, accessLost?}` | `204` (e.g. the repository could not be cloned) |

**Monorepos:** repository settings carry `folders: [{path, stack}]` (create and `PUT /settings`; empty = whole repository; up to 20 unique relative paths of letters/digits/`.`/`_`/`-` segments, no `.`/`..`, ≤ 120 chars; table `project_folder`, V14). The repository's `stack` becomes the first folder's. A scan may pick some of them: `POST /api/v1/repositories/{name}/scans` with optional `{"folders": ["frontend"]}` (unknown folder → 400; omitted/empty = all); the selection is stored on the run (`pipeline_run_folder`, V17, exposed as `folders` on runs) and reused by re-runs (folders since removed are dropped). The claim sends the run's folders as `repository.paths`; the runner fails the run if a folder is missing on the branch, otherwise runs every stage on each folder in turn and merges the results (findings capped at 5 000 per stage, logs prefixed with `[folder]`), prefixing finding paths so they stay relative to the repository root.

**SBOMs:** after Syft succeeds the runner streams the CycloneDX JSON to `PUT /internal/v1/jobs/{runId}/sbom?folder=` (one per scanned monorepo folder; empty = whole repository) and adds a line to the stage log. SBOMs above 20 MB are not uploaded ("SBOM too large to store"), and a failed upload only adds a warning to the log — the stage never fails because of it. The API checks the run is running with its SBOM stage pending, the size and that the body is a JSON object with `"bomFormat": "CycloneDX"` (streaming parse, counting `components`), then stores it gzip-compressed in `pipeline_run_sbom` (V45: `run_id` → `pipeline_run` `ON DELETE CASCADE`, `folder`, `content`, `size_bytes`, `components`, `created_at`; upsert per run and folder), so it is deleted with the run, a purged repository or the organization. Run details include `sboms: [{folder, components, sizeBytes}]`; `GET /api/v1/pipeline-runs/{number}/sbom?folder=` (`viewFindings`, tenant-scoped, also for removed repositories' runs) downloads it as `application/vnd.cyclonedx+json` with `Content-Disposition: attachment; filename="<repository>-run-<number>[-<folder>]-sbom.cdx.json"` (characters outside `A-Za-z0-9._-` replaced by `-`); `404` when the run stored none.

**Findings CSV:** `GET /api/v1/findings/export` (same filters as `GET /findings`, `viewFindings`) → `text/csv; charset=UTF-8` with a BOM, `Content-Disposition: attachment; filename="artemis-findings-<org>-<date>.csv"`; RFC 4180 quoting and CSV-injection protection (cells starting with `= + - @` tab/CR get a leading `'`).

**Stack detection:** after a successful access check (`ls-remote` OK) the runner shallow-clones the branch with the same hardened clone, reads manifest files only (never executes or imports anything; stdlib `json` + plain text, 512 KB per file and 2 MiB of Python manifests per folder, depth 3, skipping `node_modules`, `vendor`, virtualenvs, build output and hidden folders) and reports `detected: {stack, folders:[{path, stack}]} | null` in `PUT /internal/v1/access-checks/{id}`; the copy is wiped right after. Rules: `package.json` with `@angular/core` → Angular, else `react`/`react-dom`/`next` → React; `pom.xml`/`build.gradle(.kts)` mentioning `spring-boot` → Java + Spring; `requirements*.txt`/`pyproject.toml`/`Pipfile`/`setup.py`/`setup.cfg` mentioning django / fastapi / flask → the matching Python stack. Matches in sub-folders (shallowest per subtree, max 20) make a monorepo; otherwise a root match sets the single stack. The detection clone is smaller than a scan's (200 MB, at most 120 s). A failed clone/detection never fails the access check. The API validates the labels and folder paths and applies them only when `project.stack_source` (V33) is `PENDING` or `DETECTED` — `MANUAL` (any user change of stack or folders) is never overridden. `POST /repositories` with `stack: null` and no folders = `PENDING` (summaries expose `stack: null` and `stackSource`); `PUT …/settings` accepts an optional `stack` for non-monorepo repositories.

**Access re-check before every scan:** after loading the deploy key and before cloning, the runner runs `git ls-remote` (same as Test access). If the key no longer works it fails the run with `accessLost: true`; the API records a failed access check and sets the repository's access to `failed`, so scans stay locked (409) until Test access succeeds again. A missing branch fails the run without touching access.

**Removing a repository:** `DELETE /api/v1/repositories/{name}[?purge=true]` (`pipelines` permission) → `204`.
- Default (`purge=false`, keep history): soft delete — `project.deleted_at` is set (V40). The repository disappears from `/repositories`, `GET /repositories/{name}` and every per-repository endpoint answer `404`, its managed deploy key is deleted, an active access check is finished as failed and queued/running runs are cancelled (`ProjectRemoved` event). Its findings, runs, reports and risk acceptances stay visible (findings and runs carry `repositoryRemoved: true`; the findings CSV has a `repository_removed` column); re-running such a run → `409`. Active findings of removed repositories are left out of `FindingService.findActive` (dashboard counters) and of the repositories' open counts, but keep their status. The name can be connected again (unique index `project_organization_name_active` on non-removed rows only); filters by repository name prefer the connected one.
- `purge=true`: hard delete; the database cascades findings, runs, reports, deploy keys and risk acceptances.
- `GET /api/v1/removed-repositories` → `[{id, name, removedAt}]`; `DELETE /api/v1/removed-repositories/{id}` (`pipelines`) purges a removed repository's kept history (`204`).
- Neither path changes the monthly scan usage (`organization_scan_usage` counts scans when they are queued).

Stages run in pipeline order and **a failed stage stops the pipeline**: as soon as the API answers `failed` for a stage, the runner does not run the remaining stages and reports them as `skipped` ("Not run: the <stage> stage failed"), then completes the run (which ends `failed`). Warnings do not stop it.

Finding payload:

```json
{ "kind": "vulnerability | secret | code | config | license", "ruleId": "…", "title": "…", "description": "…",
  "severity": "critical | high | medium | low", "cve": "CVE-…", "cvssScore": 9.8, "cvssVector": "CVSS:3.1/…", "cwe": "CWE-79",
  "package": { "name": "…", "version": "…", "fixedVersion": "…", "ecosystem": "npm | maven | pypi | …" },
  "location": { "path": "relative/path", "line": 42 } }
```

- `package` is required for `vulnerability` and `license`, `location` for the other kinds. Paths must be relative to the repository (no leading `/`, no `..`).
- Up to 5 000 findings and 64 KiB of log per stage; text is truncated to column sizes and NUL characters are removed. Secrets must never be sent — only rule, file and line.
- Deduplication per repository (`fingerprint`): vulnerabilities by `(cve or ruleId, package, version)`, licenses by `(ruleId, package, version)`, everything else by `(kind, ruleId, path)`. A known finding is refreshed (and reopened if it was fixed); active findings of a completed stage that are not reported again become `fixed`, with a timeline note. Stages that error or are skipped never fix anything.
- Severity of vulnerabilities comes from the CVSS score when present, otherwise from the tool.

### Reports

`POST /reports {sections: [summary, recommendations, sbom] (subset; null = summary + recommendations), scope: <repository>|null, period: week|month|quarter}` → `202` with the report `generating` (period = the last complete one, UTC). `POST /reports/{id}/retry` (triage) puts a `failed` report back to `generating` (`409 report-not-failed` otherwise). An organization can have at most **3 reports `generating`** at a time; requesting or retrying another → `429 report-queue-full`. Summaries carry `status: generating|ready|failed`, `error` (`generation-failed`, `too-many-attempts`) and `narrative` (`ai`|`artemis`, who wrote the texts).

- **Worker** (`ReportGenerator`, every 5 s, up to 10 reports per tick): claims the oldest `GENERATING` report with `SELECT … FOR UPDATE SKIP LOCKED` (safe with several API instances), records the attempt (`attempts`, `started_at`, V42) and commits; builds the content and calls the AI outside any transaction; saves it as `READY`. Exceptions → `FAILED` (`generation-failed`, only the exception class is logged). A report left `GENERATING` by a crashed instance is claimed again after 10 minutes and fails after 3 attempts (`too-many-attempts`). The scheduler pool has 3 threads so a slow AI call doesn't delay other jobs.
- **Figures** (`ReportBuilder`, from `FindingService.search`, scoped to the repository when set): detected = first detections in the period; fixed = findings now `fixed` whose last status change is in the period; open = open/triaged now, removed repositories left out; MTTR = mean days from detection to fix of those fixed; SLA compliance = findings with a deadline that are open within it or were fixed in time ÷ all of them (100 % when none); trend = 7-day blocks from the start of the period (ISO week labels); severities, top 15 risks (open critical/high by CVSS — or 9.0/7.0 when unscored — then age) and OWASP Top 10 2021 categories describe the open findings. OWASP mapping (`OwaspTop10`): dependency vulnerabilities → A06, secrets → A07 (CWE-798), code/config findings by their CWE (static map of the CWEs OWASP lists per category), config without a mapped CWE → A05, the rest (and licenses) → `OTHER` (shown only when non-zero).
- **Topics** (`ReportSection`, `report.sections` JSONB, V46): figures (KPIs, trend, severities, top risks, OWASP) are always built; `summary`/`recommendations` texts and the SBOM part only when chosen, and the AI schema asks only for the chosen fields (no AI call when nothing needs AI). New reports are all type `summary` ("Security report"); `compliance`/`dependencies` stay only for older reports, whose `getSections()` is derived from the type.
- **SBOM topic** (period ignored): `PipelineRunService.latestSboms` returns, per connected repository in scope, every stored SBOM of its most recent run that has SBOMs (`RunSboms.latestPerProject`, `DISTINCT ON (project_id) … ORDER BY number DESC`). `SbomDigest` streams each gzip CycloneDX file one component at a time (name, version, Maven group, purl type as ecosystem, first license id/name/expression; `file` components skipped). `content.dependencies` holds totals (components counted once per repository across monorepo folders, unique `ecosystem|group|name|version` across repositories), ecosystems, top 10 licenses, components without a license, one row per repository (run number, scan date, folders, `image` = `settings.containerImage`), up to 25 vulnerable components (open vulnerability findings of the same repository matching name — or `group:name` — and version; worst severity first, ≤ 3 ids) and `withoutSbom`. `componentNotes`/`imageNotes` are only set by AI.
- **Texts**: `ReportTexts` writes the summary and up to 6 recommendations in en-US and pt-BR (past-SLA findings, "Upgrade X to Y or later: it fixes N open vulnerabilities, C critical", leaked secrets to rotate, top OWASP categories, vulnerable dependencies without a fix). With the SBOM topic the summary adds a sentence on the SBOM and the recommendations add license review and enabling the SBOM stage where it is missing (upgrades already come from the findings).
- **AI narrative** (`ReportNarrator` + `shared/ai/ClaudeClient`): only when the organization has the `aiTriage` feature **and** `ANTHROPIC_API_KEY` is set. One Messages API call through the official Java SDK (`com.anthropic:anthropic-java`): `system` = `AiReportPayload.SYSTEM_INSTRUCTION`, user message = the compact JSON payload, `output_config` = effort `low` + a JSON schema `{summary, recommendations[]}` (structured output), `max_tokens` 1024. The text is written **only in the organization's default language** (`lang` in the payload) to avoid paying for a translation nobody reads; readers using the other language get it with a note (`textLanguage`), while the standard texts stay in both languages, no retries. The answer is accepted only with `stop_reason` `end_turn` and after validation (≤ 1200/500 chars, 1–6 recommendations, control characters and markdown stripped); refusals, errors, timeouts and invalid answers fall back to the standard texts (`aiNotice: unavailable`), the daily limit to `aiNotice: limit`. The key is never logged.
- **AI limits and concurrency:** every AI call is counted **before** it is made in `organization_ai_usage` (V44, one row per organization, UTC day and kind `insight`/`report`; atomic `INSERT … ON CONFLICT … WHERE attempts < limit`, never decremented), so failed, refused or timed-out attempts count too. At most **4 AI calls run at once** per API instance (`ClaudeClient` semaphore); a finding explanation that finds no free slot gets `503 ai-busy`, a report waits up to 2 minutes and then uses the standard texts. The SDK does not retry (`maxRetries(0)`).
- **Data sent to Claude (Anthropic)** — built in one place, `AiReportPayload` (only for the chosen topics): period length in days, KPIs, open counts by severity and by kind, number past their SLA, OWASP category counts, and the top 8 risks as `{id (CVE or rule id), kind, title (not for secrets or licenses), severity, cvss, cwe, package, installedVersion, fixedVersion, ageDays}`; package names and versions are sent only when they look like identifiers (`^[@A-Za-z0-9][A-Za-z0-9@._/:+-]{0,99}$`, `ClaudeClient.safeIdentifier`) because they come from the scanned repository's manifests. **Never sent:** source code, file paths or lines, secret values or matches (secrets only by rule id), repository names or URLs, organization name, people's names or e-mails, deploy keys, tokens, logs. With the SBOM topic the payload adds `sbom` (`AiReportPayload.dependencies`): repository/component/unique/no-license counts, ecosystems, license ids (only identifier-like values, `[A-Za-z0-9.+()\- ]{1,64}`), up to 8 vulnerable components (`pkg`, severity, count, ≤ 3 ids), the public `containerImage` references and up to `AI_SBOM_MAX_PACKAGES` (default 200) `ecosystem:name@version` refs from public ecosystems (vulnerable ones first, `ClaudeClient.packageRef`) so the model can flag little-known publishers, low adoption, typosquat-like names, unmaintained or deprecated packages (`components[]` with an enum `concern`, ≤ 8) and rate each image (`official|widely-used|unknown`); notes about packages or images that were not sent are dropped. Package authors, suppliers and other SBOM metadata are never sent. `max_tokens` = 1024 for the texts + 640 for the SBOM review; ≈ 2.5–4k tokens per report with every topic (≈ US$0.0006 on Haiku 5.5). The Privacy Policy describes exactly this; update both together. Under Anthropic’s commercial terms API data is not used to train models.
- **Token budget:** the payload is compact (short keys, zero counts dropped, `pkg` as `name@installed→fixed`, titles ≤ 70 chars, at most 8 risks), the system instruction is a few lines, effort is `low` (thinking can't be fully turned off on current models; low effort keeps it minimal) and answers are short (summary ≤ 80 words, 3–5 recommendations ≤ 25 words) — roughly 1–2k tokens per report: ≈ US$0.0006 on the default `claude-haiku-5-5` (≈ US$0.024 on `claude-opus-5-5`, set with `ANTHROPIC_MODEL`). The system prompt is below the minimum cacheable size, so prompt caching wouldn't apply.

### AI finding explanations

The Claude client is shared by every AI feature: `shared/ai/ClaudeClient` (`json(system, data, schema, maxTokens, type)`: one Messages API call, effort `low`, JSON-schema structured output, accepted only with `stop_reason` `end_turn`; callers validate the answer; nothing but the HTTP status or exception class is logged).

- `GET /findings/{id}/insight` (`viewFindings`): the cached explanation (organization language first, else any language) or `404`. No AI call.
- `POST /findings/{id}/insight` (`triage`, because generating spends the organization's AI budget): returns the cached explanation or generates one. Never automatic. Errors (ProblemDetail `code`): `403 feature-not-enabled` (no `aiTriage`), `503 ai-unavailable` (no `ANTHROPIC_API_KEY`; cached ones are still returned), `503 ai-busy` (all AI call slots in use), `429 ai-limit` (`AI_INSIGHTS_PER_DAY` attempts per organization per UTC day, default 50, failed ones included; cached ones are free), `502 ai-failed` (error, timeout, refusal or invalid answer; nothing is cached, the attempt counts).
- Answer: `{impact (≤ 40 words), exploitable (≤ 30), priority: high|medium|low, fix (≤ 25), language, model, createdAt}`, validated (≤ 400/300/250 chars, enum, control characters and markdown stripped). Written only in the organization's default language; readers using the other language get a note.
- **Cache** (`finding_insight`, V43, unique `(organization_id, cache_key, language)`): `cache_key` = `kind|CVE or rule id|ECOSYSTEM|group:package|installedVersion` for dependency findings, `kind|ruleId|CWE` for the other kinds. The same CVE in the same package version is explained once per organization, whatever the repository or occurrence; never shared across organizations; no regenerate. Concurrent generation keeps the first row (unique constraint).
- **Data sent** — built in one place, `InsightPayload`: `lang`, `kind`, `id` (CVE or rule id), `title` (≤ 100 chars) and `desc` (scanner description ≤ 400 chars) — neither for secrets nor licenses, `sev`, `cvss`, `vector` (CVSS vector), `cwe`, `pkg` (`name@installed→fixed`, only identifier-like parts, see above), `eco`, and the repository `stack` (e.g. `Java + Spring`, or the folder stacks of a monorepo). **Never sent:** source code, file paths or lines, secret values, repository or organization names, people, logs. Keep it in sync with the Privacy Policy.
- **Token budget:** `max_tokens` 400; roughly 250–350 input and ~150–250 output tokens per explanation (≈ US$0.0002 on `claude-haiku-5-5`).

### Policies (enforced)

Policies are stored per organization (`policy` module) and applied through two ports, so modules keep their dependency direction: `project.service.PipelinePolicy` (stage gates) and `finding.service.FindingPolicy` (SLAs, license rules), both implemented by `policy.service.PolicyRules` (repositories only).

- **Stage gates:** a gate's `failOn` is the organization's **minimum** strictness and `required` forces the stage on. `PUT /repositories/{name}/pipeline` (and creation) returns `409` when a required stage is disabled or a threshold is looser than the gate; runs use `ProjectService.effectivePipeline()` (required stages enabled, thresholds raised to the gate). Unavailable stages (DAST) cannot be required (`400`).
- **Risk acceptances:** one per finding (`risk_acceptance.finding_id`, V19; legacy CVE-only rows keep `cve`). `POST /findings/{id}/status` with `status: accepted` requires `note` (justification, ≥ 10 chars) and `expiresAt` (future, ≤ 1 year) and publishes `RiskAccepted` → the policy module records the acceptance; leaving `accepted` publishes `RiskAcceptanceEnded` → it is removed. `POST /risk-acceptances` `{finding: "F-1101" | "CVE-…", repository, justification, expiresAt}` accepts the matching open finding(s) and returns the created acceptances (list); `DELETE /risk-acceptances/{id}` reopens the finding. An hourly job reopens findings whose acceptance expired (timeline note). Accepted and false-positive findings are still recorded but never fail a stage.
- **Bulk status:** `POST /findings/status` `{ids: [uuid…] (1–500), status, note?, expiresAt?}` (`triage` permission) applies one status change to several findings in one transaction with the same rules as the single endpoint (acceptance needs justification + expiry); findings already in that status or that cannot move to it are skipped → `{updated, skipped}`. Unknown or other-organization ids → 404 and nothing changes.
- **Remediation SLAs:** findings expose `dueAt` (first detection + SLA days for the severity) and `overdue` (open/triaged past `dueAt`); the dashboard summary has `overdueFindings`.
- **License rules:** license findings (`ruleId` `license:<SPDX>`) are matched case-insensitively: allowed → not recorded, denied → `high`, no rule → `medium`. Only licenses Trivy classifies as forbidden/restricted/reciprocal reach the API, so a deny rule on a permissive license has no effect.

### Findings JSON

`GET /api/v1/findings` items (also `finding` in the detail and `recent` in the dashboard summary):

`id, reference, kind, ruleId, cve, repository, packageName, packageGroup, installedVersion, fixedVersion, ecosystem, title, cvss, severity, status, stage, tool, location: {path, line} | null, detectedAt, statusChangedAt`

`cve`, `cvss`, the package fields and `ecosystem` are `null` for secrets, code and config findings; `location` is `null` for package-based ones. Sorting by CVSS puts findings without a score last; `q` also matches rule ids and file paths. The dashboard's `averageCvss` covers only findings with a score.

### Dashboard JSON

- `GET /api/v1/dashboard/summary` adds `byKind [{kind, count}]` (open + triaged findings per kind) and `topRepositories [{repository, total, critical, high, medium, low}]` (top 5 by open findings) to the KPIs.
- `GET /api/v1/dashboard/trends?days=30|90&tz=<IANA zone>` (default `30`, `UTC`; other values → `400`) returns `{periodDays, timeZone, days: [{date, critical, high, medium, low, detected, fixed, runsSucceeded, runsFailed, runsCancelled}], meanTimeToRemediateDays, fixedInPeriod}`, one entry per day (oldest first, today included), with days in `tz`.
- History is replayed in SQL from each finding's timeline (`finding_event`): a finding is open on a day when its last event at the end of that day (in `tz`) is `OPEN` or `TRIAGED`. Approximations: findings are counted under their **current** severity; `detected` counts first detections only (reopens are not "new"); `fixed` counts every transition to fixed (automatic or manual); mean time to remediate = first detection → fix, over the fixes in the period (`null` when there were none). Runs are counted on the day they started; running/queued runs are left out. Deleted repositories take their history with them.

## Contributing

- **How to contribute:** see [CONTRIBUTING.md](CONTRIBUTING.md) (contributions are licensed under MIT).
- **Branches:** `main` (stable) and `dev` (integration). Create `feat/*` or `bugfix/*` branches from `dev`.
- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/).
- Update this README when modules, endpoints or conventions change.

## License

Artemis is fully open source under the [MIT License](LICENSE): anyone may use, copy, modify, distribute and sell it, with no restrictions beyond keeping the license notice. Contributions are welcome and are licensed under the same terms (see [CONTRIBUTING.md](CONTRIBUTING.md)). The security tools run by the scanner (Gitleaks, Semgrep CE, Trivy, Grype, Syft, Hadolint) are separate programs under their own licenses; see the in-app Third-party notices page (`/licenses`) and the backend's `scanner/THIRD_PARTY_NOTICES.md`.

## About

Artemis is a **fictional product** created for an academic presentation and developed by [leonardorscarpitta](https://github.com/leonardorscarpitta). The developer claims no ownership of the code or of the name: there is no Artemis company, "Artemis" is not a registered trademark, and the project is not affiliated with NASA's Artemis program or any other product named Artemis. The in-app texts (Privacy Policy, Terms of Use, footer) are written as if Artemis were a real service, for the demonstration. Anyone running their own instance is responsible for it and for the data it stores.
