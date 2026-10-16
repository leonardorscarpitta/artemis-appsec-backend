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
</p>

---

Backend of Artemis, a SAST platform. The frontend lives in a separate repository.

## Running locally

Everything runs in Docker:

```sh
cp .env.example .env   # first time only; adjust values
docker compose up -d
```

| Service | URL |
| --- | --- |
| API | http://localhost:8080/api/v1 |
| OpenAPI / Swagger UI | http://localhost:8080/api/docs/ui |
| Health | http://localhost:8080/actuator/health |
| Remote debugger (JDWP) | `localhost:5005` |
| PostgreSQL | `localhost:5432` |

The `api` container runs `mvn spring-boot:run` with the `dev` profile. Maven compiles once at startup, so after changing code run `docker compose restart api` (DevTools also restarts the app when an IDE recompiles classes into `target/`). Dependencies are cached in `.m2/` (git-ignored).

The `dev` profile loads demo data (`src/main/resources/db/seed`) and **disables authentication** (`AUTH_ENABLED=false`). Any other profile requires a valid JWT from the OpenID Connect issuer in `OIDC_ISSUER_URI` (Authelia).

Production image: `docker build -t artemis-api .` (multi-stage, non-root, layered jar).

## Configuration

All environment-specific values come from environment variables (secrets in production); see `.env.example`.

| Variable | Purpose |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `OIDC_ISSUER_URI` | OpenID Connect issuer used to validate JWTs |
| `AUTH_ENABLED` | `dev` profile only: turn authentication on |
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

Query parameters accept the same enum values as the JSON (`?status=falsePositive&severity=critical`), via `shared/config/WireEnumConverterFactory`.

JSON enum values match the frontend (`"Java + Spring"`, `"24h"`, `"sast"`, `"critical"`...).

## Contributing

- **Branches:** `main` (stable) and `dev` (integration). Create `feat/*` or `bugfix/*` branches from `dev`.
- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/).
- Update this README when modules, endpoints or conventions change.
