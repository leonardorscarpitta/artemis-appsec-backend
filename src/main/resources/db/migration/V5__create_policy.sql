CREATE TABLE policy_stage_gate (
    stage    VARCHAR(32) PRIMARY KEY,
    fail_on  VARCHAR(16),
    required BOOLEAN NOT NULL
);

CREATE TABLE policy_remediation_sla (
    severity VARCHAR(16) PRIMARY KEY,
    days     INTEGER NOT NULL CHECK (days BETWEEN 1 AND 365)
);

CREATE TABLE policy_license_rule (
    license_id VARCHAR(64) PRIMARY KEY,
    decision   VARCHAR(16) NOT NULL
);

CREATE TABLE risk_acceptance (
    id            UUID PRIMARY KEY,
    cve           VARCHAR(32)   NOT NULL,
    project_id    UUID          NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    justification VARCHAR(1000) NOT NULL,
    approved_by   VARCHAR(255)  NOT NULL,
    expires_at    DATE          NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    UNIQUE (cve, project_id)
);

-- Organization defaults; a fresh installation starts from these.
INSERT INTO policy_stage_gate (stage, fail_on, required) VALUES
    ('SECRETS', 'LOW', FALSE), ('SAST', 'HIGH', FALSE), ('SCA', 'CRITICAL', FALSE), ('LICENSE', 'NEVER', FALSE),
    ('DOCKERFILE', 'HIGH', FALSE), ('IMAGE', 'CRITICAL', FALSE), ('SBOM', NULL, FALSE), ('IAC', 'HIGH', FALSE),
    ('DAST', 'HIGH', FALSE);

INSERT INTO policy_remediation_sla (severity, days) VALUES
    ('CRITICAL', 7), ('HIGH', 30), ('MEDIUM', 90), ('LOW', 180);
