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
cp .env.example .env             # first time only; adjust values
./authelia/generate-secrets.sh   # first time only; prints the development logins
docker compose up -d
```

| Service | URL |
| --- | --- |
| API | http://localhost:8080/api/v1 |
| OpenAPI / Swagger UI | http://localhost:8080/api/docs/ui |
| Health | http://localhost:8080/actuator/health |
| Authelia (sign-in, OIDC provider) | https://auth.artemisappsec.localhost:9443 |
| Remote debugger (JDWP) | `localhost:5005` |
| PostgreSQL | `localhost:5432` |

The `api` container runs `mvn spring-boot:run` with the `dev` profile. Maven compiles once at startup, so after changing code run `docker compose restart api` (DevTools also restarts the app when an IDE recompiles classes into `target/`). Dependencies are cached in `.m2/` (git-ignored).

The `dev` profile loads demo data (`src/main/resources/db/seed`). Set `AUTH_ENABLED=false` in `.env` to skip authentication entirely (local debugging only).

Production image: `docker build -t artemisappsec-api .` (multi-stage, non-root, layered jar).

**Memory limits** (the stack is sized for a 16 GB machine, ~4.5 GB in total): `db` 512 MB, `api` 1.5 GB (Maven `-Xmx384m`, application JVM `-Xmx640m -XX:+UseSerialGC`), `authelia` 384 MB (argon2 password checks use ~64 MB each), `auth-proxy` 64 MB, `scanner` 2 GB (+ 2 CPUs, 512 processes).

## Authentication (Authelia)

Authelia is the OpenID Connect provider; the API is an OAuth2 resource server.

```
browser ──https──> Caddy :9443 (auth-proxy) ──> Authelia :9091
API ──http──> auth-proxy:9080/jwks.json   (internal only, signing keys)
```

- **Why Caddy:** Authelia refuses non-HTTPS public URLs. Caddy terminates TLS with its local CA. `*.localhost` resolves to your machine in browsers, so no hosts-file changes are needed. The first visit to https://auth.artemisappsec.localhost:9443 shows a certificate warning; accept it, or trust Caddy's root CA (`docker compose cp auth-proxy:/data/caddy/pki/authorities/local/root.crt .`).
- **Client:** `artemisappsec-web` is a public client using Authorization Code + PKCE (S256). Access tokens are RFC 9068 JWTs (`typ: at+jwt`) with audience `artemisappsec-api` and the claims `preferred_username`, `name`, `email` and `groups`.
- **API validation:** issuer, audience, expiry and token type are checked with `JwtValidators.createAtJwtValidator()`. The principal name is `preferred_username`, and `groups` become `ROLE_<GROUP>` authorities (not enforced yet).
- **Secrets:** `authelia/generate-secrets.sh` creates them in `authelia/secrets/` (git-ignored): session and storage keys, the HMAC secret, the RSA signing key and `users.yml`. Development users (`ana.souza` owner, `carla.mendes` analyst, `daniel.rocha` developer) get random passwords, listed in `authelia/secrets/dev-credentials.txt`. In production these files come from the secret store.
- `GET /api/v1/me` returns the signed-in user.

## Configuration

All environment-specific values come from environment variables (secrets in production); see `.env.example`.

| Variable | Purpose |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `OIDC_ISSUER_URI` | Expected `iss` claim; also used for discovery when no JWKS URL is set |
| `OIDC_JWK_SET_URI` | Optional URL of the signing keys (internal URL in Docker) |
| `OIDC_AUDIENCE` | Required `aud` claim (default `artemisappsec-api`) |
| `AUTH_ENABLED` | `dev` profile only: `false` disables authentication |
| `AUTH_COOKIE_DOMAIN`, `AUTH_PUBLIC_URL`, `ARTEMISAPPSEC_WEB_ORIGIN`, `ARTEMISAPPSEC_WEB_REDIRECT_URI` | Authelia session domain, public URL, the frontend origin (CORS) and callback URL |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins allowed to call `/api/**` |
| `SCANNER_TOKEN` | Shared secret of the scanner runner (≥ 32 random characters; the internal job API is disabled otherwise). Also `SCANNER_STAGE_TIMEOUT_SECONDS` (600), `SCANNER_MAX_REPO_SIZE_MB` (500), `SCANNER_STALE_RUN_MINUTES` (60) |
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
| `billing` | Plans (FREE: 90 scans/month; PRO: R$ 24,99/month, 300 scans/month and AI triage), the organization's subscription, monthly scan usage (`ScanUsage` port, implemented by `pipelinerun`) and entitlements. Re-runs are refused once the month's limit is reached. Plan changes are disabled by default and no payment platform is integrated | `/api/v1/billing`, `/api/v1/billing/plans` (public) |
| `organization` | Integrations, SSH deploy keys, team (members, invitations, roles), organization settings, API tokens | `/api/v1/integrations`, `/api/v1/deploy-keys`, `/api/v1/members`, `/api/v1/invitations`, `/api/v1/roles`, `/api/v1/organization`, `/api/v1/api-tokens` |

Query parameters accept the same enum values as the JSON (`?status=falsePositive&severity=critical`), via `shared/config/WireEnumConverterFactory`.

Security notes for `organization`:

- Deploy keys accept **public keys only** (private keys are rejected); the type and key data are validated (ed25519, ECDSA, RSA ≥ 2048 bits) and the OpenSSH `SHA256:` fingerprint is computed server-side.
- API tokens (`art_…`) are generated with `SecureRandom` and returned **once**; only their SHA-256 hash and last four characters are stored.
- The OIDC client secret is never stored or returned — it belongs in the deployment's secret store; the API only reports whether it is configured.
- The organization is a single row (`organization_settings`): one organization per deployment.

JSON enum values match the frontend (`"Java + Spring"`, `"24h"`, `"sast"`, `"critical"`...).

## Scanner runner

Security tools run **self-hosted** in the `scanner` service (image `artemisappsec/runner`, built from `scanner/`). No cloud scanning service is used.

| Stage | Tool | License |
| --- | --- | --- |
| Secrets | Gitleaks 8.30.1 | MIT |
| SAST | Semgrep CE 1.181.0 (engine) with Artemis's own rules in `scanner/rules/` — never the Semgrep Registry | LGPL-2.1 |
| Dependencies (SCA), licenses, IaC, Dockerfile | Trivy 0.76.0 | Apache-2.0 |
| Dependencies (SCA, alternative) | Grype 0.120.1 | Apache-2.0 |
| Dockerfile | Hadolint 2.15.1 | GPL-3.0 |
| SBOM | Syft 1.54.1 | Apache-2.0 |
| Container image, DAST | — not available yet (need an isolated builder / authorized running target); reported as skipped | |

Licensing (see `scanner/THIRD_PARTY_NOTICES.md`, shipped in the image with every tool's license under `/usr/share/licenses/`): each tool is installed **unmodified** from its official release and executed as a **separate program**; no tool code is part of Artemis, so the GPL/AGPL terms do not extend to it. Vulnerability data comes from public databases (NVD, GitHub Advisory Database, OSV…): *This product uses data from the NVD API but is not endorsed or certified by the NVD.*

**How it works.** The worker (`scanner/worker/`, Python standard library only) polls the API's internal job endpoints (`/internal/v1/jobs/…`, `Authorization: Bearer $SCANNER_TOKEN`), clones the repository, runs the enabled stages, normalizes every tool's output to one finding format and reports it back. The API decides stage/run status from the configured fail thresholds.

**Security and privacy measures**

- *Supply chain:* pinned tool versions; every download verified against a pinned SHA-256 (cross-checked with the official checksum files where published); multi-stage build, no build tools in the final image.
- *Sandbox:* non-root user (uid 10001), read-only root filesystem, all Linux capabilities dropped, `no-new-privileges`, memory/CPU/process limits, no Docker socket, no database credentials. The `scanner` network is shared only with the API — the database and Authelia are unreachable.
- *Clone:* public **HTTPS** repositories only for now (SSH/private repositories come later); credentials in URLs, non-default ports and hosts resolving to private, loopback, link-local or metadata addresses are refused; `--depth 1 --single-branch --no-tags`, no submodules, hooks, LFS, symlinks or redirects; size limit (500 MB) and timeouts. No repository code is ever executed (no installs, no builds).
- *Privacy:* source code lives in an in-memory workspace that is deleted after each run (and on start-up). Secrets are redacted by Gitleaks and the worker never forwards secret values or code snippets — only rule, file and line. Logs are size-limited and redacted. Telemetry and update checks are disabled (`--metrics=off`, `*_CHECK_FOR_APP_UPDATE=false`); the only outbound traffic is the git clone and vulnerability-database downloads, which contain no user data.

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

1. A scan is queued by `POST /api/v1/repositories/{name}/scans` (manual, counts against the plan's monthly quota → `409` when exhausted), by re-running a run, or automatically when a repository is connected. Runs keep a snapshot of each enabled stage: tool and fail threshold.
2. The runner claims the oldest queued run (`SELECT … FOR UPDATE SKIP LOCKED`, so several runners never share a run). Stages that are not available yet (`image`, `dast`) are skipped at that point.
3. For each stage the runner reports `completed`, `skipped` or `error` plus normalized findings. The API decides the stage status: `error` → `failed`; `completed` → `failed` when a finding reaches the stage's fail threshold (`failedBy`), `warning` when there are findings below it, `passed` otherwise.
4. On `complete`, stages that were not reported are skipped and the run becomes `success` or `failed`; the repository's last scan time is updated. Runs still `running` after `SCANNER_STALE_RUN_MINUTES` (default 60) are failed by a scheduled sweep.

Repository URLs may be `https://host/owner/repo(.git)` or SSH (`git@host:owner/repo.git`, `ssh://…`). The runner clones public HTTPS repositories only for now.

### Internal job API

`/internal/**` has its own security chain: no CORS, no OIDC; the runner sends `Authorization: Bearer $SCANNER_TOKEN` (compared in constant time). With a token shorter than 32 characters the whole internal API answers `503`. It is hidden from the OpenAPI docs.

| Method and path | Body | Response |
| --- | --- | --- |
| `POST /internal/v1/jobs/claim` | — | `204` when nothing is queued; `200` `{runId, number, repository: {name, cloneUrl, branch}, stages: [{id, tool, failOn}], limits: {stageTimeoutSeconds, maxRepoSizeMb}}` |
| `POST /internal/v1/jobs/{runId}/source` | `{commitSha, commitMessage}` | `204` |
| `PUT /internal/v1/jobs/{runId}/stages/{stage}` | `{status: completed\|skipped\|error, tool, durationSeconds, log, findings: [Finding]}` | `{status: passed\|warning\|failed\|skipped}`; `409` if the run is not running or the stage was already reported; `400` if the tool differs from the queued one |
| `POST /internal/v1/jobs/{runId}/complete` | — | `204` |
| `POST /internal/v1/jobs/{runId}/fail` | `{reason}` | `204` (e.g. the repository could not be cloned) |

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
