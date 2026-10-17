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
  <img src="https://img.shields.io/badge/license-PolyForm%20Noncommercial%201.0.0-5a6474" alt="License: PolyForm Noncommercial 1.0.0">
</p>

---

Backend of Artemis, a SAST platform. The frontend lives in the [artemis-appsec-frontend](https://github.com/Artemis-Core/artemis-appsec-frontend) repository.

**Naming:** users see the product as **Artemis**; technical identifiers use **artemisappsec** — Java package `io.github.artemiscore.artemisappsec`, Maven artifact, `spring.application.name`, configuration prefix `artemisappsec.*`, database name/user, OIDC client `artemisappsec-web`, audience `artemisappsec-api` and the local auth host `auth.artemisappsec.localhost`.

## Running locally

Everything runs in Docker:

```sh
cp .env.example .env             # first time only; set passwords and SCANNER_TOKEN
docker compose up -d
./keycloak/create-dev-users.sh   # first time only (once Keycloak is up); prints the development logins
```

| Service | URL |
| --- | --- |
| API | http://localhost:8080/api/v1 |
| OpenAPI / Swagger UI | http://localhost:8080/api/docs/ui |
| Health | http://localhost:8080/actuator/health |
| Keycloak (sign-in, registration, account) | https://auth.artemisappsec.localhost:9443/realms/artemisappsec/account |
| Keycloak admin console | https://auth.artemisappsec.localhost:9443/admin (`KEYCLOAK_ADMIN_*` in `.env`) |
| Mailpit (dev e-mails) | http://localhost:8025 |
| Remote debugger (JDWP) | `localhost:5005` |
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

**Memory limits** (the stack is sized for a 16 GB machine, ~4.5 GB in total): `db` 512 MB, `api` 1.5 GB (Maven `-Xmx384m`, application JVM `-Xmx640m -XX:+UseSerialGC`), `keycloak` 768 MB, `mailpit` 64 MB, `auth-proxy` 64 MB, `scanner` 2 GB (+ 2 CPUs, 512 processes).

## Authentication (Keycloak)

Keycloak 26.4 is the OpenID Connect provider (realm `artemisappsec`, imported from `keycloak/realm-artemisappsec.json` on first start); the API is an OAuth2 resource server.

```
browser ──https──> Caddy :9443 (auth-proxy) ──> Keycloak :8080
API ──http──> keycloak:8080/realms/artemisappsec/protocol/openid-connect/certs   (internal, signing keys)
```

- **TLS:** Caddy terminates HTTPS with its local CA at `https://auth.artemisappsec.localhost:9443` (`*.localhost` resolves to your machine). Trust Caddy's root CA once (`docker compose cp auth-proxy:/data/caddy/pki/authorities/local/root.crt .`) or accept the warning. Keycloak runs with `--proxy-headers=xforwarded` and a fixed public `--hostname`.
- **Realm:** self-registration (e-mail verification **off**: no outgoing mailbox yet; e-mails stay unique per account), Terms of Use / Privacy Policy acceptance on first sign-in (required action with links to the app), password policy (12–128 chars, not username/e-mail, history 3), brute-force protection, en/pt-BR, reset password, account deletion, Organizations enabled. Dev e-mails go to **Mailpit** (http://localhost:8025); set `KC_SMTP_*` for a real server.
- **Client:** `artemisappsec-web`, public, Authorization Code + PKCE (S256), redirect `ARTEMISAPPSEC_WEB_ORIGIN/auth/callback`, post-logout redirect to the app, audience mapper adding `artemisappsec-api` to access tokens. `prompt=create` opens the registration page.
- **API validation:** issuer, expiry and audience (`artemisappsec-api`); principal = `preferred_username`. Tokens carry no roles: authorization is per organization (below).
- **Database:** Keycloak uses its own database/role (`keycloak`) in the same PostgreSQL instance, created idempotently by the `keycloak-db-init` job (`KEYCLOAK_DB_PASSWORD`).
- **Dev users:** `./keycloak/create-dev-users.sh` creates `ana.souza`, `carla.mendes`, `daniel.rocha` (verified e-mail, random passwords) and writes `keycloak/secrets/dev-credentials.txt` (git-ignored).
- `GET /api/v1/me` returns the signed-in user (`username`, `name`, `email`, `emailVerified`).

## Organizations and permissions

Artemis is multi-tenant: every repository, finding, run, report, policy, integration, key, token, member, invitation, setting and subscription belongs to an **organization**. Tenancy and roles live in Artemis (not in Keycloak).

- **Tenant header:** every organization-scoped request carries `X-Artemis-Organization: <slug>` (the frontend sends the slug of its `/<slug>/…` URL). Missing header → `400` `organization-required`; unknown slug, not a member or inactive member → `404` `organization-not-found` (existence is not revealed); missing permission → `403` `forbidden` with `permission`. Endpoints without the header: `/me`, `/me/**`, `POST /organizations`, `/roles`, `/pipeline/stages`, `/billing/plans`.
- **Membership:** a member is linked to the Keycloak account by `sub`. Members created before the account existed (seeds) are linked on first use only when the token's e-mail is **verified** (`email_verified`); e-mail verification is off in the realm, so new accounts join through invitation links.
- **Roles → permissions** (`organization/domain/Role.java`):

  | Role | viewFindings | triage | pipelines | integrations | team | billing |
  | --- | --- | --- | --- | --- | --- | --- |
  | owner | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
  | admin | ✓ | ✓ | ✓ | ✓ | ✓ | |
  | analyst | ✓ | ✓ | ✓ | | | |
  | developer | ✓ | ✓ | | | | |
  | viewer | ✓ | | | | | |

  `viewFindings`: every `GET`. `triage`: finding status, risk acceptances, reports. `pipelines`: create/configure repositories and pipelines, run/re-run/cancel scans, edit policies. `integrations`: integrations, deploy keys, API tokens. `team`: invitations, roles, removing members, organization settings. `billing` (owner): plan changes, ownership transfer, deleting the organization.
- **Enforcement:** `organization/api/TenantInterceptor` resolves the tenant for every `/api/v1/**` handler (unless annotated `@WithoutOrganization`) and checks `@RequiresPermission`; `GET` defaults to `viewFindings` and any other method **without** the annotation is refused (fail closed). Controllers receive a `Tenant` parameter and pass `tenant.organizationId()` to services; every query is filtered by organization (directly or through its repositories), so ids/numbers from another organization return `404`.
- **Rules:** only the owner manages admins (admins cannot assign, invite, demote or remove admins → 403 `owner-only`); one owner per organization; ownership is transferred (`POST /members/transfer-ownership`, the old owner becomes admin), never assigned; nobody changes their own role or removes themselves (they leave); the owner cannot leave. A user may own at most 5 organizations;.
- **Account endpoints:** `GET /me/organizations` (`[{slug, name, role, permissions, plan}]`), `POST /organizations` (`{name, slug, defaultLanguage?}` → the creator becomes owner; FREE plan, default settings, integration catalogue and policies are provisioned), `GET /me/invitations` (pending, non-expired invitations for the account's e-mail), `POST /me/invitations/{id}/accept|decline`, `POST /me/organizations/{slug}/leave`.
- **Slugs:** `^[a-z0-9](?:[a-z0-9-]{1,38}[a-z0-9])$`, unique (`409 slug-taken`), not an app route (`409 slug-reserved`: `organizations`, `invite`, `auth`, `login`, `help`, `privacy`, `terms`, `api`, …). Renaming the slug in the settings changes the organization's URLs.
- **Invitations** are joined through a **one-time link**: `POST /invitations` and `POST /invitations/{id}/resend` return a `token` (256-bit random, base64url) once — only its SHA-256 is stored, `GET /invitations` never includes it, and resending replaces it. The inviter shares the link (the frontend builds `<origin>/invite/<token>`); `POST /me/invitations/accept` `{"token"}` (any signed-in account, no org header) joins with the invited role and consumes the invitation (`404 invitation-not-found` if unknown/expired, `409 already-member`, `409 email-in-use` if another member already has the account's e-mail). Links expire after 7 days. `GET /me/invitations` and accept/decline by id only match a **verified** e-mail (e-mail verification is off, so an unverified address must not be enough to claim an invitation). Invitations are not e-mailed.
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
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins allowed to call `/api/**` |
| `SCANNER_TOKEN` | Shared secret of the scanner runner (≥ 32 random characters; the internal job API is disabled otherwise). Also `SCANNER_STAGE_TIMEOUT_SECONDS` (600), `SCANNER_MAX_REPO_SIZE_MB` (500), `SCANNER_STALE_RUN_MINUTES` (60) |
| `DEPLOY_KEY_ENCRYPTION_KEY`, `DEPLOY_KEY_ENCRYPTION_KEY_ID` | Master key (base64 of 32 random bytes, `openssl rand -base64 32`) and its label (`v1`) for encrypting the private deploy keys of private repositories. Without a valid key, private repositories are disabled (`503 deploy-keys-disabled`) |
| `BILLING_ENABLED` | Allows plan changes (default `false`). Paid plans still require a payment platform, which is not integrated yet |

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
| `pipelinerun` | Pipeline executions: list/filter, 7-day summary, run detail with stage logs, re-run, cancel and manual scans (quota-checked; a repository's first scan is queued when it is connected) | `/api/v1/pipeline-runs`, `/api/v1/repositories/{name}/scans` |
| `scanjob` | Internal job API for the scanner runner: claim queued runs, record the scanned commit, stage results and findings, complete/fail runs; fails runs stuck in `running` | `/internal/v1/jobs` (runner only) |
| `dashboard` | Read-only aggregation of findings and repositories | `/api/v1/dashboard/summary` |
| `report` | Security summary and OWASP compliance reports; text localized by `Accept-Language` (en-US, pt-BR). Requested reports stay `generating` until a report generator exists | `/api/v1/reports` |
| `policy` | Organization-wide stage gates (fail threshold, required), remediation SLAs, license allow/deny lists, risk acceptances | `/api/v1/policies`, `/api/v1/risk-acceptances` |
| `account` | The signed-in user from the JWT | `/api/v1/me` |
| `billing` | Plans (FREE: 90 scans/month; PRO: R$ 24,99/month, 300 scans/month and AI triage), each organization's subscription, monthly scan usage per organization (`ScanUsage` port, implemented by `pipelinerun`) and entitlements. Re-runs are refused once the month's limit is reached. Plan changes are disabled by default and no payment platform is integrated | `/api/v1/billing`, `/api/v1/billing/plans` (public) |
| `organization` | Organizations (tenancy, memberships, creation, invitations for the signed-in account, permission enforcement), integrations, SSH deploy keys, team (members, invitations, roles, ownership), organization settings, API tokens. Publishes `OrganizationCreated` (billing and policy provision their defaults) and reads plans through the `PlanLookup` port (implemented by `billing`) | `/api/v1/organizations`, `/api/v1/me/organizations`, `/api/v1/me/invitations`, `/api/v1/integrations`, `/api/v1/deploy-keys`, `/api/v1/members`, `/api/v1/invitations`, `/api/v1/roles`, `/api/v1/organization`, `/api/v1/api-tokens` |

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
- *Sandbox:* non-root user (uid 10001), read-only root filesystem, all Linux capabilities dropped, `no-new-privileges`, memory/CPU/process limits, no Docker socket, no database credentials. The `scanner` network is shared only with the API — the database and Keycloak are unreachable.
- *Clone:* **SSH only**, with the repository's Artemis-generated read-only deploy key (see "Private repositories") — public repositories too, so every scan proves the user was granted access by the repository's owner. Jobs for non-SSH repositories are refused by both the API and the runner (the runner's local `scan --url` dev mode can still clone public HTTPS). Credentials in URLs and hosts resolving to private, loopback, link-local or metadata addresses are refused; `--depth 1 --single-branch --no-tags`, no submodules, hooks, LFS, symlinks or redirects; size limit (500 MB) and timeouts. No repository code is ever executed (no installs, no builds).
- *Privacy:* source code lives in an in-memory workspace that is deleted after each run (and on start-up). Secrets are redacted by Gitleaks and the worker never forwards secret values or code snippets — only rule, file and line. Logs are size-limited and redacted. Telemetry and update checks are disabled (`--metrics=off`, `*_CHECK_FOR_APP_UPDATE=false`); the only outbound traffic is the git clone and vulnerability-database downloads, which contain no user data. Trivy runs with `--offline-scan`, so it never asks package registries about the repository's dependencies (e.g. Maven Central for parent POMs, which also rate-limits with HTTP 429); the trade-off is that versions defined only in a remote parent POM/BOM may stay unresolved.

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

1. A scan is queued by `POST /api/v1/repositories/{name}/scans` (manual, counts against the plan's monthly quota → `409` when exhausted), by re-running a run, or automatically when a public repository is connected (private ones wait until their deploy key is added). Runs keep a snapshot of each enabled stage: tool and fail threshold.
2. The runner claims the oldest queued run (`SELECT … FOR UPDATE SKIP LOCKED`, so several runners never share a run). Stages that are not available yet (`dast`) are skipped at that point.
3. For each stage the runner reports `completed`, `skipped` or `error` plus normalized findings. The API decides the stage status: `error` → `failed`; `completed` → `failed` when a finding reaches the stage's fail threshold (`failedBy`), `warning` when there are findings below it, `passed` otherwise.
4. On `complete`, stages that were not reported are skipped and the run becomes `success` or `failed`; the repository's last scan time is updated. Runs still `running` after `SCANNER_STALE_RUN_MINUTES` (default 60) are failed by a scheduled sweep.

Repository URLs must be SSH (`git@host:owner/repo.git`, `ssh://[git@]host[:port]/owner/repo(.git)`) — HTTPS is rejected with 400. Every repository is cloned with an Artemis-generated deploy key (see "Private repositories"); `V18` converted existing `https://host/owner/repo` URLs to `git@host:owner/repo.git` (access pending). Queueing a scan (`POST …/scans`, re-run) returns `409` until the repository's access is **verified**, so no quota is spent on scans that cannot clone.

### Private repositories

1. **Key per repository.** Connecting an SSH URL generates an **ed25519** key pair in the API (JDK `Ed25519`), stores the public key and the **OpenSSH private key encrypted with AES-256-GCM** (`deploy_key.private_key_ciphertext/_nonce/encryption_key_id`; random 96-bit nonce; AAD = deploy key id + key id, so ciphertexts cannot be moved between rows) with the master key `DEPLOY_KEY_ENCRYPTION_KEY`. The plaintext exists only in memory; it is never returned by the public API, logged or put in exceptions (`SshCredentials.toString()` is redacted). No first scan is queued: the repository waits for its key to be added (`accessStatus: pending`).
2. **The user adds the public key** to the repository as a **read-only deploy key** (GitHub: Settings → Deploy keys; GitLab: Settings → Repository → Deploy keys; Bitbucket: Repository settings → Access keys) and asks for an **access check**. The runner runs `git ls-remote --heads <url> refs/heads/<branch>` with the key; the result (`ok` only when the branch exists) sets `accessStatus` to `verified` or `failed` with a sanitized message (permission denied, host key mismatch, repository/branch not found, unreachable).
3. **Host keys are always verified** (`StrictHostKeyChecking=yes`). `github.com`, `gitlab.com` and `bitbucket.org` use the official keys pinned in `scanner/known_hosts` (collected with `ssh-keyscan` and checked against each provider's documented fingerprints; GitHub's also against `https://api.github.com/meta` — sources in the file header). Other servers: the first **successful** access check records the key read with `ssh-keyscan` (trust on first use, shown to users with its SHA256 fingerprint) and every later connection must match it; scans of such a server fail until a check has recorded it.
4. **Scans** of private repositories get `credentials` in the claim; the runner writes the key to the in-memory workspace (mode 0600), clones through `GIT_SSH_COMMAND` (`-F /dev/null`, `IdentitiesOnly`, `IdentityAgent=none`, `BatchMode`, publickey only, own `known_hosts`, timeouts) with only the `ssh` protocol allowed, and **deletes the key right after the clone**, before any tool runs. A run of a private repository without a key, or whose host key is unknown, is failed at claim time and never reaches the runner.
5. **Rotation and revocation.** `POST /repositories/{name}/access/key` generates a new key and deletes the old one (status back to `pending`); deleting the managed key under `/deploy-keys` does the same without a replacement. Removing the repository deletes its keys (cascade) — users should also remove the deploy key from the provider. Every release of a key to the scanner is logged with its fingerprint and purpose (`run #N` / `access check <id>`) and updates `lastUsedAt`.
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
| `POST /internal/v1/jobs/{runId}/complete` | — | `204` |
| `POST /internal/v1/jobs/{runId}/fail` | `{reason}` | `204` (e.g. the repository could not be cloned) |

**Monorepos:** repository settings carry `folders: [{path, stack}]` (create and `PUT /settings`; empty = whole repository; up to 20 unique relative paths of letters/digits/`.`/`_`/`-` segments, no `.`/`..`, ≤ 120 chars; table `project_folder`, V14). The repository's `stack` becomes the first folder's. A scan may pick some of them: `POST /api/v1/repositories/{name}/scans` with optional `{"folders": ["frontend"]}` (unknown folder → 400; omitted/empty = all); the selection is stored on the run (`pipeline_run_folder`, V17, exposed as `folders` on runs) and reused by re-runs (folders since removed are dropped). The claim sends the run's folders as `repository.paths`; the runner fails the run if a folder is missing on the branch, otherwise runs every stage on each folder in turn and merges the results (findings capped at 5 000 per stage, logs prefixed with `[folder]`), prefixing finding paths so they stay relative to the repository root.

**Findings CSV:** `GET /api/v1/findings/export` (same filters as `GET /findings`, `viewFindings`) → `text/csv; charset=UTF-8` with a BOM, `Content-Disposition: attachment; filename="artemis-findings-<org>-<date>.csv"`; RFC 4180 quoting and CSV-injection protection (cells starting with `= + - @` tab/CR get a leading `'`).

**Removing a repository:** `DELETE /api/v1/repositories/{name}` (`pipelines` permission) → `204`; the database cascades its findings, runs, reports, deploy keys and risk acceptances.

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

### Findings JSON

`GET /api/v1/findings` items (also `finding` in the detail and `recent` in the dashboard summary):

`id, reference, kind, ruleId, cve, repository, packageName, packageGroup, installedVersion, fixedVersion, ecosystem, title, cvss, severity, status, stage, tool, location: {path, line} | null, detectedAt, statusChangedAt`

`cve`, `cvss`, the package fields and `ecosystem` are `null` for secrets, code and config findings; `location` is `null` for package-based ones. Sorting by CVSS puts findings without a score last; `q` also matches rule ids and file paths. The dashboard's `averageCvss` covers only findings with a score.

## Contributing

- **How to contribute:** see [CONTRIBUTING.md](CONTRIBUTING.md) (contribution terms included).
- **Branches:** `main` (stable) and `dev` (integration). Create `feat/*` or `bugfix/*` branches from `dev`.
- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/).
- Update this README when modules, endpoints or conventions change.

## License

Artemis is source-available under the [PolyForm Noncommercial License 1.0.0](LICENSE.md). You may use, study, modify and share it for noncommercial purposes; commercial use (selling it, offering it as a paid service, charging for access) is reserved to the copyright holder. Contributions are welcome under the terms in [CONTRIBUTING.md](CONTRIBUTING.md).
