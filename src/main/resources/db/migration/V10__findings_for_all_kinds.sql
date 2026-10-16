-- Findings are no longer only CVEs: secrets, code issues, configuration and license problems are stored too.
ALTER TABLE finding
    ADD COLUMN kind          VARCHAR(16)   NOT NULL DEFAULT 'VULNERABILITY',
    ADD COLUMN severity      VARCHAR(16),
    ADD COLUMN rule_id       VARCHAR(200),
    ADD COLUMN location_path VARCHAR(1024),
    ADD COLUMN location_line INTEGER CHECK (location_line IS NULL OR location_line >= 0),
    ADD COLUMN fingerprint   VARCHAR(64);

ALTER TABLE finding
    ALTER COLUMN cve DROP NOT NULL,
    ALTER COLUMN package_name DROP NOT NULL,
    ALTER COLUMN installed_version DROP NOT NULL,
    ALTER COLUMN ecosystem DROP NOT NULL,
    ALTER COLUMN cvss_vector DROP NOT NULL,
    ALTER COLUMN cvss_score DROP NOT NULL;

-- Existing rows are vulnerabilities; severity and fingerprint follow the same rules as the application.
UPDATE finding
SET severity    = CASE
                      WHEN cvss_score >= 9.0 THEN 'CRITICAL'
                      WHEN cvss_score >= 7.0 THEN 'HIGH'
                      WHEN cvss_score >= 4.0 THEN 'MEDIUM'
                      ELSE 'LOW' END,
    fingerprint = encode(sha256(convert_to(lower('vulnerability|' || cve || '|' || package_name || '|' || installed_version), 'UTF8')), 'hex')
WHERE fingerprint IS NULL;

-- Severity and fingerprint are always written by the application (nullable only so legacy dev seed inserts still apply).
ALTER TABLE finding DROP CONSTRAINT IF EXISTS finding_project_id_cve_package_name_installed_version_key;
ALTER TABLE finding ADD CONSTRAINT finding_project_fingerprint_key UNIQUE (project_id, fingerprint);
CREATE INDEX idx_finding_project_stage ON finding (project_id, stage);

CREATE SEQUENCE finding_reference_seq START WITH 10000;
