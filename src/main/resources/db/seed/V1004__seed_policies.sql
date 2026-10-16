-- Demo data for local development only (loaded by the "dev" profile).
UPDATE policy_stage_gate SET required = TRUE WHERE organization_id = '00000000-0000-0000-0009-000000000001' AND stage IN ('SECRETS', 'SCA');

INSERT INTO policy_license_rule (id, organization_id, license_id, decision)
SELECT gen_random_uuid(), '00000000-0000-0000-0009-000000000001', r.* FROM (VALUES
    ('MIT', 'ALLOWED'), ('Apache-2.0', 'ALLOWED'), ('BSD-3-Clause', 'ALLOWED'), ('ISC', 'ALLOWED'),
    ('GPL-3.0', 'DENIED'), ('AGPL-3.0', 'DENIED'), ('SSPL-1.0', 'DENIED')
) AS r(license_id, decision);

INSERT INTO risk_acceptance (id, cve, project_id, justification, approved_by, expires_at, created_at) VALUES
    ('00000000-0000-0000-0000-000000000501', 'CVE-2024-21490', '00000000-0000-0000-0000-000000000003',
     'AngularJS is only used in an internal admin page scheduled for removal.', 'Security team', '2026-12-31', '2026-10-01T12:00:00Z'),
    ('00000000-0000-0000-0000-000000000502', 'CVE-2024-55565', '00000000-0000-0000-0000-000000000003',
     'nanoid is not used to generate security tokens.', 'Security team', '2027-03-31', '2026-10-01T12:00:00Z');
