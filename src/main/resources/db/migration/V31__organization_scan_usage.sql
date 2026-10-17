-- Monthly scan usage per organization, kept apart from runs so deleting a repository (which deletes its runs)
-- does not give the quota back, and incremented atomically so parallel requests cannot exceed the limit.
CREATE TABLE organization_scan_usage (
    organization_id UUID    NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    month           DATE    NOT NULL,
    scans           INTEGER NOT NULL CHECK (scans >= 0),
    PRIMARY KEY (organization_id, month)
);

INSERT INTO organization_scan_usage (organization_id, month, scans)
SELECT p.organization_id, date_trunc('month', r.started_at AT TIME ZONE 'UTC')::date, count(*)
FROM pipeline_run r
JOIN project p ON p.id = r.project_id
GROUP BY 1, 2;
