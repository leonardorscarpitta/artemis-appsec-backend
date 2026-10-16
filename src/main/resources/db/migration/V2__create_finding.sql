CREATE TABLE finding (
    id                UUID PRIMARY KEY,
    reference         VARCHAR(32)   NOT NULL UNIQUE,
    cve               VARCHAR(32)   NOT NULL,
    project_id        UUID          NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    package_name      VARCHAR(255)  NOT NULL,
    package_group     VARCHAR(255),
    installed_version VARCHAR(64)   NOT NULL,
    fixed_version     VARCHAR(64),
    ecosystem         VARCHAR(16)   NOT NULL,
    title             VARCHAR(255)  NOT NULL,
    description       TEXT          NOT NULL,
    cvss_vector       VARCHAR(128)  NOT NULL,
    cvss_score        NUMERIC(3, 1) NOT NULL CHECK (cvss_score BETWEEN 0 AND 10),
    cwe               VARCHAR(16),
    stage             VARCHAR(32)   NOT NULL,
    tool              VARCHAR(64)   NOT NULL,
    status            VARCHAR(32)   NOT NULL,
    detected_at       TIMESTAMPTZ   NOT NULL,
    status_changed_at TIMESTAMPTZ   NOT NULL,
    UNIQUE (project_id, cve, package_name, installed_version)
);

CREATE INDEX idx_finding_project ON finding (project_id);
CREATE INDEX idx_finding_status ON finding (status);

CREATE TABLE finding_event (
    id          UUID PRIMARY KEY,
    finding_id  UUID          NOT NULL REFERENCES finding (id) ON DELETE CASCADE,
    type        VARCHAR(32)   NOT NULL,
    status      VARCHAR(32)   NOT NULL,
    note        VARCHAR(1000),
    occurred_at TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_finding_event_finding ON finding_event (finding_id, occurred_at);
