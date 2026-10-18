# Contributing to Artemis

Thanks for your interest! Issues, ideas and pull requests are welcome.

## License of contributions

Artemis is open source under the [MIT License](LICENSE). By submitting a contribution (a pull request, patch or any other material) you confirm that it is your own work, or that you otherwise have the right to submit it, and you agree that it is licensed under the same MIT License as the rest of the project.

## How to contribute

1. Fork the repository and create a branch from `dev`: `feat/<short-name>` or `bugfix/<short-name>`.
2. Run everything in Docker — no local toolchain is required: `cp .env.example .env` (first time; set `SCANNER_TOKEN`), `docker compose up -d` and `./keycloak/create-dev-users.sh` (first time).
3. Use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) (`feat: ...`, `fix: ...`, `docs: ...`).
4. Before opening the pull request, the API must start cleanly (`docker compose restart api`, then check `docker compose logs api`).
5. Open the pull request against `dev` and describe what changed and why.

Other rules:

- Keep changes focused; don't mix refactors with features.
- Update the README when structure, routes, endpoints or conventions change.
- Schema changes go in a new Flyway migration in `src/main/resources/db/migration`; never edit an applied one.
- Follow the module rules in the README (modules talk only through `service` interfaces; no Lombok).
- Never commit secrets (`.env`, `keycloak/secrets/` and similar are git-ignored).
