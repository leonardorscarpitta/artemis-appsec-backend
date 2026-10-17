-- Per-organization role permissions (owners and admins can change them). The owner always has every permission,
-- so it has no rows; the other roles start with the previous built-in defaults.
CREATE TABLE organization_role_permission (
    organization_id UUID        NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    role            VARCHAR(32) NOT NULL,
    permission      VARCHAR(32) NOT NULL,
    PRIMARY KEY (organization_id, role, permission)
);

INSERT INTO organization_role_permission (organization_id, role, permission)
SELECT o.id, d.role, d.permission
FROM organization o
CROSS JOIN (VALUES
    ('ADMIN', 'VIEW_FINDINGS'), ('ADMIN', 'TRIAGE'), ('ADMIN', 'PIPELINES'), ('ADMIN', 'INTEGRATIONS'), ('ADMIN', 'TEAM'),
    ('ANALYST', 'VIEW_FINDINGS'), ('ANALYST', 'TRIAGE'), ('ANALYST', 'PIPELINES'),
    ('DEVELOPER', 'VIEW_FINDINGS'), ('DEVELOPER', 'TRIAGE'),
    ('VIEWER', 'VIEW_FINDINGS')
) AS d (role, permission);
