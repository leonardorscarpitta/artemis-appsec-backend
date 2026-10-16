-- Demo data for local development only (loaded by the "dev" profile).
-- The demo organization (Acme), with the same defaults a new organization gets.
INSERT INTO organization (id, slug, name, created_at) VALUES ('00000000-0000-0000-0009-000000000001', 'acme', 'Acme Corp', '2026-07-01T12:00:00Z');

INSERT INTO organization_settings (organization_id, default_language, timezone, oidc_issuer, oidc_client_id,
    oidc_client_secret_set, session_timeout, enforce_sso, findings_retention, logs_retention, reports_retention, updated_at)
VALUES ('00000000-0000-0000-0009-000000000001', 'en-US', 'UTC', 'https://auth.example.com', 'artemisappsec-web',
    FALSE, 'EIGHT_HOURS', TRUE, 'DAYS_730', 'DAYS_90', 'FOREVER', NOW());

INSERT INTO subscription (organization_id, plan, started_at) VALUES ('00000000-0000-0000-0009-000000000001', 'FREE', '2026-07-01T12:00:00Z');

INSERT INTO integration (id, organization_id, code, name, short_code, category, status, removable, position, updated_at)
SELECT gen_random_uuid(), '00000000-0000-0000-0009-000000000001', i.* , NOW() FROM (VALUES
    ('github',        'GitHub',          'GH',  'SCM',             'DISCONNECTED', TRUE,  1),
    ('gitlab',        'GitLab',          'GL',  'SCM',             'DISCONNECTED', TRUE,  2),
    ('bitbucket',     'Bitbucket',       'BB',  'SCM',             'DISCONNECTED', TRUE,  3),
    ('githubActions', 'GitHub Actions',  'GA',  'CI',              'DISCONNECTED', TRUE,  4),
    ('gitlabCi',      'GitLab CI',       'GC',  'CI',              'DISCONNECTED', TRUE,  5),
    ('jenkins',       'Jenkins',         'JK',  'CI',              'DISCONNECTED', TRUE,  6),
    ('slack',         'Slack',           'SL',  'NOTIFICATIONS',   'DISCONNECTED', TRUE,  7),
    ('teams',         'Microsoft Teams', 'MT',  'NOTIFICATIONS',   'DISCONNECTED', TRUE,  8),
    ('email',         'E-mail',          '@',   'NOTIFICATIONS',   'DISCONNECTED', TRUE,  9),
    ('webhook',       'Webhook',         'WH',  'NOTIFICATIONS',   'DISCONNECTED', TRUE,  10),
    ('jira',          'Jira',            'JI',  'ISSUES',          'DISCONNECTED', TRUE,  11),
    ('githubIssues',  'GitHub Issues',   'GI',  'ISSUES',          'DISCONNECTED', TRUE,  12),
    ('nvd',           'NIST NVD',        'NVD', 'VULNERABILITY_DATA', 'CONNECTED', FALSE, 13)
) AS i(code, name, short_code, category, status, removable, position);

INSERT INTO policy_stage_gate (id, organization_id, stage, fail_on, required)
SELECT gen_random_uuid(), '00000000-0000-0000-0009-000000000001', g.* FROM (VALUES
    ('SECRETS', 'LOW', FALSE), ('SAST', 'HIGH', FALSE), ('SCA', 'CRITICAL', FALSE), ('LICENSE', 'NEVER', FALSE),
    ('DOCKERFILE', 'HIGH', FALSE), ('IMAGE', 'CRITICAL', FALSE), ('SBOM', NULL, FALSE), ('IAC', 'HIGH', FALSE),
    ('DAST', 'HIGH', FALSE)
) AS g(stage, fail_on, required);

INSERT INTO policy_remediation_sla (id, organization_id, severity, days)
SELECT gen_random_uuid(), '00000000-0000-0000-0009-000000000001', s.* FROM (VALUES ('CRITICAL', 7), ('HIGH', 30), ('MEDIUM', 90), ('LOW', 180)) AS s(severity, days);

INSERT INTO project (organization_id, id, name, ssh_url, branch, stack, scan_schedule, dockerfile_mode, last_scan_at, created_at) VALUES
    ('00000000-0000-0000-0009-000000000001', '00000000-0000-0000-0000-000000000001', 'storefront-web', 'git@github.com:acme/storefront-web.git', 'main', 'REACT',       'EVERY_24_HOURS', 'RECOMMENDED', '2026-10-15T22:10:00Z', '2026-08-01T12:00:00Z'),
    ('00000000-0000-0000-0009-000000000001', '00000000-0000-0000-0000-000000000002', 'payments-api',   'git@github.com:acme/payments-api.git',   'main', 'JAVA_SPRING', 'ON_PUSH',        'RECOMMENDED', '2026-10-15T21:42:00Z', '2026-08-03T12:00:00Z'),
    ('00000000-0000-0000-0009-000000000001', '00000000-0000-0000-0000-000000000003', 'backoffice',     'git@gitlab.com:acme/backoffice.git',     'main', 'ANGULAR',     'EVERY_7_DAYS',   'RECOMMENDED', '2026-10-12T08:00:00Z', '2026-08-10T12:00:00Z');

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
