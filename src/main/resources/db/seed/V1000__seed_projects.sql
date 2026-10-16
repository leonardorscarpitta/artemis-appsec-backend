-- Demo data for local development only (loaded by the "dev" profile).
INSERT INTO project (id, name, ssh_url, branch, stack, scan_schedule, dockerfile_mode, last_scan_at, created_at) VALUES
    ('00000000-0000-0000-0000-000000000001', 'storefront-web', 'git@github.com:acme/storefront-web.git', 'main', 'REACT',       'EVERY_24_HOURS', 'RECOMMENDED', '2026-10-15T22:10:00Z', '2026-08-01T12:00:00Z'),
    ('00000000-0000-0000-0000-000000000002', 'payments-api',   'git@github.com:acme/payments-api.git',   'main', 'JAVA_SPRING', 'ON_PUSH',        'RECOMMENDED', '2026-10-15T21:42:00Z', '2026-08-03T12:00:00Z'),
    ('00000000-0000-0000-0000-000000000003', 'backoffice',     'git@gitlab.com:acme/backoffice.git',     'main', 'ANGULAR',     'EVERY_7_DAYS',   'RECOMMENDED', '2026-10-12T08:00:00Z', '2026-08-10T12:00:00Z');

INSERT INTO project_automatic_action (project_id, action) VALUES
    ('00000000-0000-0000-0000-000000000001', 'OPEN_ISSUE'),
    ('00000000-0000-0000-0000-000000000001', 'NOTIFY_EMAIL'),
    ('00000000-0000-0000-0000-000000000002', 'OPEN_ISSUE'),
    ('00000000-0000-0000-0000-000000000002', 'NOTIFY_EMAIL'),
    ('00000000-0000-0000-0000-000000000002', 'BLOCK_MERGE'),
    ('00000000-0000-0000-0000-000000000003', 'NOTIFY_EMAIL');

-- payments-api uses the complete preset; the others the recommended one.
INSERT INTO pipeline_stage_config (id, project_id, stage, enabled, tool, fail_on)
SELECT gen_random_uuid(), p.id, s.stage,
       CASE WHEN p.name = 'payments-api' THEN TRUE ELSE s.recommended END,
       s.tool, s.fail_on
FROM project p
CROSS JOIN (VALUES
    ('SECRETS',    'Gitleaks',  'LOW',      TRUE),
    ('SAST',       'Semgrep',   'HIGH',     TRUE),
    ('SCA',        'Trivy',     'CRITICAL', TRUE),
    ('LICENSE',    'Trivy',     'NEVER',    FALSE),
    ('DOCKERFILE', 'Hadolint',  'HIGH',     TRUE),
    ('IMAGE',      'Trivy',     'CRITICAL', TRUE),
    ('SBOM',       'Syft',      NULL,       TRUE),
    ('IAC',        'Checkov',   'HIGH',     FALSE),
    ('DAST',       'OWASP ZAP', 'HIGH',     FALSE)
) AS s(stage, tool, fail_on, recommended);
