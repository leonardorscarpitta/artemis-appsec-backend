-- Artemis is free: plans are gone. Optional features are enabled per organization by the Artemis administrators.
CREATE TABLE organization_feature (
    organization_id UUID         NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    feature         VARCHAR(40)  NOT NULL,
    granted_by      VARCHAR(255) NOT NULL,
    granted_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, feature)
);

-- Organizations that were on PRO keep what it included.
INSERT INTO organization_feature (organization_id, feature, granted_by)
SELECT s.organization_id, f.feature, 'migration (former PRO plan)'
FROM subscription s
CROSS JOIN (VALUES ('AI_TRIAGE'), ('SCHEDULED_SCANS'), ('ORGANIZATION_RENAME')) AS f (feature)
WHERE s.plan = 'PRO';

-- The subscription table is no longer read; it stays because the dev seed V1000 still writes to it.
COMMENT ON TABLE subscription IS 'Unused since V41 (plans removed)';
