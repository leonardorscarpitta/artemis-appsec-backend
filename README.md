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

Backend of Artemis, a SAST platform. The frontend lives in a separate repository.

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
| `finding` | Vulnerabilities (CVE per package and repository), status workflow and timeline; provides open counts to `project` via `ProjectFindingStats` | `/api/v1/findings` |
| `pipelinerun` | Pipeline executions: list/filter, 7-day summary, run detail with stage logs, re-run and cancel | `/api/v1/pipeline-runs` |
| `dashboard` | Read-only aggregation of findings and repositories | `/api/v1/dashboard/summary` |
| `report` | Security summary and OWASP compliance reports; text localized by `Accept-Language` (en-US, pt-BR). Requested reports stay `generating` until a report generator exists | `/api/v1/reports` |
| `policy` | Organization-wide stage gates (fail threshold, required), remediation SLAs, license allow/deny lists, risk acceptances | `/api/v1/policies`, `/api/v1/risk-acceptances` |
| `account` | The signed-in user from the JWT | `/api/v1/me` |
| `organization` | Integrations, SSH deploy keys, team (members, invitations, roles), organization settings, API tokens | `/api/v1/integrations`, `/api/v1/deploy-keys`, `/api/v1/members`, `/api/v1/invitations`, `/api/v1/roles`, `/api/v1/organization`, `/api/v1/api-tokens` |

Query parameters accept the same enum values as the JSON (`?status=falsePositive&severity=critical`), via `shared/config/WireEnumConverterFactory`.

Security notes for `organization`:

- Deploy keys accept **public keys only** (private keys are rejected); the type and key data are validated (ed25519, ECDSA, RSA ≥ 2048 bits) and the OpenSSH `SHA256:` fingerprint is computed server-side.
- API tokens (`art_…`) are generated with `SecureRandom` and returned **once**; only their SHA-256 hash and last four characters are stored.
- The OIDC client secret is never stored or returned — it belongs in the deployment's secret store; the API only reports whether it is configured.
- The organization is a single row (`organization_settings`): one organization per deployment.

JSON enum values match the frontend (`"Java + Spring"`, `"24h"`, `"sast"`, `"critical"`...).

## Contributing

- **How to contribute:** see [CONTRIBUTING.md](CONTRIBUTING.md) (contribution terms included).
- **Branches:** `main` (stable) and `dev` (integration). Create `feat/*` or `bugfix/*` branches from `dev`.
- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/).
- Update this README when modules, endpoints or conventions change.

## License

Artemis is source-available under the [PolyForm Noncommercial License 1.0.0](LICENSE.md). You may use, study, modify and share it for noncommercial purposes; commercial use (selling it, offering it as a paid service, charging for access) is reserved to the copyright holder. Contributions are welcome under the terms in [CONTRIBUTING.md](CONTRIBUTING.md).
