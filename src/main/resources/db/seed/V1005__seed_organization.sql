-- Demo data for local development only (loaded by the "dev" profile).
UPDATE organization_settings
SET name = 'Acme Corp', slug = 'acme', default_language = 'pt-BR', timezone = 'America/Sao_Paulo',
    oidc_issuer = 'https://auth.acme.example', oidc_client_id = 'artemisappsec-web', oidc_client_secret_set = TRUE,
    session_timeout = 'EIGHT_HOURS', enforce_sso = TRUE, findings_retention = 'DAYS_730', logs_retention = 'DAYS_90', reports_retention = 'FOREVER'
WHERE id = 1;

INSERT INTO organization_notification (settings_id, event, channel) VALUES
    (1, 'CRITICAL_CVE',    'EMAIL'),
    (1, 'CRITICAL_CVE',    'SLACK'),
    (1, 'PIPELINE_FAILED', 'SLACK'),
    (1, 'WEEKLY_REPORT',   'EMAIL'),
    (1, 'SLA_BREACH',      'EMAIL'),
    (1, 'SLA_BREACH',      'SLACK');

UPDATE integration SET status = 'CONNECTED', detail = 'acme'                  WHERE id IN ('github', 'gitlab');
UPDATE integration SET status = 'CONNECTED'                                    WHERE id = 'githubActions';
UPDATE integration SET status = 'ERROR',     detail = 'HTTP 401'              WHERE id = 'jenkins';
UPDATE integration SET status = 'CONNECTED', detail = '#appsec-alerts'        WHERE id = 'slack';
UPDATE integration SET status = 'CONNECTED', detail = 'security@acme.example' WHERE id = 'email';
UPDATE integration SET status = 'CONNECTED', detail = 'SEC'                   WHERE id = 'jira';
UPDATE integration SET last_sync_at = '2026-10-16T06:00:00Z'                  WHERE id = 'nvd';

INSERT INTO deploy_key (id, name, project_id, key_type, public_key, fingerprint, created_at, last_used_at) VALUES
    ('00000000-0000-0000-0005-000000000001', 'artemisappsec-storefront', '00000000-0000-0000-0000-000000000001', 'ssh-ed25519',
     'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIL0qrGNfpJtzc07wBsLaHKHYF+S/gmSv9C25YIizu72Q artemisappsec-storefront',
     'SHA256:Wr6kGtBj9U1q+NdsD2RojkrBL49858ZsBY3b1fQxf/Q', '2026-08-02T12:00:00Z', '2026-10-15T22:10:00Z'),
    ('00000000-0000-0000-0005-000000000002', 'artemisappsec-payments', '00000000-0000-0000-0000-000000000002', 'ssh-ed25519',
     'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIP1nu/BJ9/BWLfrO7lNZWvC3z9fPEZEzxi/Uuc1IWhbL artemisappsec-payments',
     'SHA256:wCqDbnhRgd/6mpIShFI5PLKUFf2U1Giws4Wx1RQaU+k', '2026-08-14T12:00:00Z', '2026-10-15T21:42:00Z'),
    ('00000000-0000-0000-0005-000000000003', 'artemisappsec-backoffice', '00000000-0000-0000-0000-000000000003', 'ssh-ed25519',
     'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIKbLu5TRtwih4qTaJTohHnakD16/RKWMsCF9TFYp7dD6 artemisappsec-backoffice',
     'SHA256:YRYtaVx4gd3TmkvkbJ+hYP1Z1Tqapww8YHFzTtcMdRo', '2026-09-01T12:00:00Z', '2026-10-12T08:00:00Z');

INSERT INTO member (id, name, email, role, active, last_active_at, created_at) VALUES
    ('00000000-0000-0000-0006-000000000001', 'Ana Souza',    'ana.souza@acme.example',    'OWNER',     TRUE,  '2026-10-16T12:00:00Z', '2026-07-01T12:00:00Z'),
    ('00000000-0000-0000-0006-000000000002', 'Bruno Lima',   'bruno.lima@acme.example',   'ADMIN',     TRUE,  '2026-10-15T12:00:00Z', '2026-07-02T12:00:00Z'),
    ('00000000-0000-0000-0006-000000000003', 'Carla Mendes', 'carla.mendes@acme.example', 'ANALYST',   TRUE,  '2026-10-16T12:00:00Z', '2026-07-10T12:00:00Z'),
    ('00000000-0000-0000-0006-000000000004', 'Daniel Rocha', 'daniel.rocha@acme.example', 'DEVELOPER', TRUE,  '2026-10-14T12:00:00Z', '2026-07-15T12:00:00Z'),
    ('00000000-0000-0000-0006-000000000005', 'Elisa Prado',  'elisa.prado@acme.example',  'DEVELOPER', TRUE,  '2026-10-09T12:00:00Z', '2026-08-01T12:00:00Z'),
    ('00000000-0000-0000-0006-000000000006', 'Felipe Nunes', 'felipe.nunes@acme.example', 'VIEWER',    FALSE, '2026-09-21T12:00:00Z', '2026-08-20T12:00:00Z');

INSERT INTO invitation (id, email, role, sent_at) VALUES
    ('00000000-0000-0000-0007-000000000001', 'gabriel.alves@acme.example', 'DEVELOPER', '2026-10-14T12:00:00Z'),
    ('00000000-0000-0000-0007-000000000002', 'helena.costa@acme.example',  'ANALYST',   '2026-10-11T12:00:00Z');

-- Demo tokens: only hashes of random, unknown secrets are stored.
INSERT INTO api_token (id, name, token_hash, last_four, created_at, expires_at, last_used_at) VALUES
    ('00000000-0000-0000-0008-000000000001', 'CI runner',   encode(sha256(convert_to(gen_random_uuid()::text || gen_random_uuid()::text, 'UTF8')), 'hex'), 'x7Qa', '2026-07-20T12:00:00Z', '2027-01-20T12:00:00Z', '2026-10-16T05:00:00Z'),
    ('00000000-0000-0000-0008-000000000002', 'SIEM export', encode(sha256(convert_to(gen_random_uuid()::text || gen_random_uuid()::text, 'UTF8')), 'hex'), 'Lp2k', '2026-09-03T12:00:00Z', '2026-12-03T12:00:00Z', '2026-10-15T23:00:00Z');

INSERT INTO api_token_scope (token_id, scope) VALUES
    ('00000000-0000-0000-0008-000000000001', 'SCANS_WRITE'),
    ('00000000-0000-0000-0008-000000000001', 'FINDINGS_READ'),
    ('00000000-0000-0000-0008-000000000002', 'FINDINGS_READ');
