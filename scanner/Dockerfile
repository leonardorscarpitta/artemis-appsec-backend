# Artemis scanner runner: open-source security tools, installed unmodified as separate programs,
# driven by a small stdlib-only Python worker. See README "Scanner runner".

# ---- Stage 1: download release binaries and verify them against pinned SHA-256 hashes ----
FROM debian:bookworm-slim AS tools

ARG GITLEAKS_VERSION=8.30.1
ARG GITLEAKS_SHA256=551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb
ARG TRIVY_VERSION=0.76.0
ARG TRIVY_SHA256=e24b1c51293c1b15e4ba8d45bbc7891ba053f90278ec2698f3562c993e145fe7
ARG GRYPE_VERSION=0.120.1
ARG GRYPE_SHA256=0a9ee97ef5ae2ee953b0a80098105052e846cdbe319a57d808b519c33cd1343d
ARG SYFT_VERSION=1.54.1
ARG SYFT_SHA256=c069905b391cc4c20a5ba65ad5c10be2a7ba074f8ea6ad203e24d14e303dad47
ARG HADOLINT_VERSION=2.15.1
ARG HADOLINT_SHA256=c7187db94eeeeca956519a6af171adc31453941a1e777961f6e680f697c8c507
ARG HADOLINT_LICENSE_SHA256=589ed823e9a84c56feb95ac58e7cf384626b9cbf4fda2a907bc36e103de1bad2

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /tmp/dl
RUN set -eu; \
    fetch() { curl -fsSL --proto '=https' --tlsv1.2 -o "$1" "$2" && echo "$3  $1" | sha256sum -c -; }; \
    GH=https://github.com; \
    mkdir -p /opt/tools/bin /opt/tools/licenses/gitleaks /opt/tools/licenses/trivy /opt/tools/licenses/grype \
             /opt/tools/licenses/syft /opt/tools/licenses/hadolint; \
    fetch gitleaks.tgz "$GH/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/gitleaks_${GITLEAKS_VERSION}_linux_x64.tar.gz" "$GITLEAKS_SHA256"; \
    tar -xzf gitleaks.tgz gitleaks LICENSE && mv gitleaks /opt/tools/bin/ && mv LICENSE /opt/tools/licenses/gitleaks/; \
    fetch trivy.tgz "$GH/aquasecurity/trivy/releases/download/v${TRIVY_VERSION}/trivy_${TRIVY_VERSION}_Linux-64bit.tar.gz" "$TRIVY_SHA256"; \
    tar -xzf trivy.tgz trivy LICENSE && mv trivy /opt/tools/bin/ && mv LICENSE /opt/tools/licenses/trivy/; \
    fetch grype.tgz "$GH/anchore/grype/releases/download/v${GRYPE_VERSION}/grype_${GRYPE_VERSION}_linux_amd64.tar.gz" "$GRYPE_SHA256"; \
    tar -xzf grype.tgz grype LICENSE && mv grype /opt/tools/bin/ && mv LICENSE /opt/tools/licenses/grype/; \
    fetch syft.tgz "$GH/anchore/syft/releases/download/v${SYFT_VERSION}/syft_${SYFT_VERSION}_linux_amd64.tar.gz" "$SYFT_SHA256"; \
    tar -xzf syft.tgz syft LICENSE && mv syft /opt/tools/bin/ && mv LICENSE /opt/tools/licenses/syft/; \
    fetch hadolint "$GH/hadolint/hadolint/releases/download/v${HADOLINT_VERSION}/hadolint-linux-x86_64" "$HADOLINT_SHA256"; \
    mv hadolint /opt/tools/bin/hadolint; \
    fetch LICENSE "https://raw.githubusercontent.com/hadolint/hadolint/v${HADOLINT_VERSION}/LICENSE" "$HADOLINT_LICENSE_SHA256"; \
    mv LICENSE /opt/tools/licenses/hadolint/; \
    chmod 0555 /opt/tools/bin/*; \
    rm -rf /tmp/dl/*

# ---- Stage 2: Semgrep CE engine in an isolated virtualenv ----
FROM python:3.13-slim-bookworm AS semgrep

ARG SEMGREP_VERSION=1.181.0
ARG SEMGREP_LICENSE_SHA256=20c17d8b8c48a600800dfd14f95d5cb9ff47066a9641ddeab48dc54aec96e331
RUN python -m venv /opt/semgrep \
    && /opt/semgrep/bin/pip install --no-cache-dir --disable-pip-version-check "semgrep==${SEMGREP_VERSION}" \
    && mkdir -p /opt/tools/licenses/semgrep \
    && python -c "import urllib.request; urllib.request.urlretrieve('https://raw.githubusercontent.com/semgrep/semgrep/v${SEMGREP_VERSION}/LICENSE', '/opt/tools/licenses/semgrep/LICENSE')" \
    && echo "${SEMGREP_LICENSE_SHA256}  /opt/tools/licenses/semgrep/LICENSE" | sha256sum -c -

# ---- Stage 3: runtime ----
FROM python:3.13-slim-bookworm

RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /usr/share/licenses/git && cp /usr/share/doc/git/copyright /usr/share/licenses/git/COPYING \
    && groupadd --system --gid 10001 runner \
    && useradd --system --uid 10001 --gid runner --home-dir /nonexistent --shell /usr/sbin/nologin runner

COPY --from=tools /opt/tools/bin/ /usr/local/bin/
COPY --from=tools /opt/tools/licenses/ /usr/share/licenses/
COPY --from=semgrep /opt/semgrep /opt/semgrep
COPY --from=semgrep /opt/tools/licenses/semgrep/ /usr/share/licenses/semgrep/
RUN ln -s /opt/semgrep/bin/semgrep /usr/local/bin/semgrep

COPY rules/ /opt/artemis/rules/
COPY worker/ /app/worker/
COPY THIRD_PARTY_NOTICES.md /usr/share/licenses/THIRD_PARTY_NOTICES.md

# Writable paths are tmpfs/volumes at runtime (the root filesystem is read-only):
#   /workspace (clones, tool output), /tmp (HOME, caches), /cache (vulnerability databases)
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app \
    HOME=/tmp \
    XDG_CACHE_HOME=/tmp/.cache \
    WORKSPACE=/workspace \
    TRIVY_CACHE_DIR=/cache/trivy \
    TRIVY_NO_PROGRESS=true \
    GRYPE_DB_CACHE_DIR=/cache/grype \
    GRYPE_CHECK_FOR_APP_UPDATE=false \
    SYFT_CHECK_FOR_APP_UPDATE=false \
    SEMGREP_SEND_METRICS=off \
    SEMGREP_ENABLE_VERSION_CHECK=0 \
    GIT_TERMINAL_PROMPT=0 \
    GIT_CONFIG_NOSYSTEM=1

WORKDIR /app
USER runner:runner

HEALTHCHECK --interval=60s --timeout=5s --start-period=30s CMD ["python", "-m", "worker", "healthcheck"]
ENTRYPOINT ["python", "-m", "worker"]
CMD ["run"]
