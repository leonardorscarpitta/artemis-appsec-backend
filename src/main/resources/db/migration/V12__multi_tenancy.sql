-- Multi-tenancy: every piece of data belongs to an organization. Data of the former single organization
-- is moved into an organization created from its settings.
CREATE TABLE organization (
    id         UUID         PRIMARY KEY,
    slug       VARCHAR(40)  NOT NULL UNIQUE,
    name       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

INSERT INTO organization (id, slug, name, created_at)
SELECT gen_random_uuid(), slug, name, now() FROM organization_settings WHERE id = 1;

-- Settings and subscription: one row per organization.
ALTER TABLE organization_settings ADD COLUMN organization_id UUID REFERENCES organization (id) ON DELETE CASCADE;
UPDATE organization_settings SET organization_id = (SELECT id FROM organization);
ALTER TABLE organization_notification ADD COLUMN organization_id UUID;
UPDATE organization_notification SET organization_id = (SELECT id FROM organization);
ALTER TABLE organization_notification DROP CONSTRAINT organization_notification_pkey,
    DROP CONSTRAINT organization_notification_settings_id_fkey,
    DROP COLUMN settings_id,
    ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE organization_settings DROP CONSTRAINT organization_settings_pkey,
    DROP COLUMN id, DROP COLUMN name, DROP COLUMN slug,
    ALTER COLUMN organization_id SET NOT NULL,
    ADD PRIMARY KEY (organization_id);
ALTER TABLE organization_notification ADD PRIMARY KEY (organization_id, event, channel),
    ADD FOREIGN KEY (organization_id) REFERENCES organization_settings (organization_id) ON DELETE CASCADE;

ALTER TABLE subscription ADD COLUMN organization_id UUID REFERENCES organization (id) ON DELETE CASCADE;
UPDATE subscription SET organization_id = (SELECT id FROM organization);
ALTER TABLE subscription DROP CONSTRAINT subscription_pkey, DROP COLUMN id,
    ALTER COLUMN organization_id SET NOT NULL,
    ADD PRIMARY KEY (organization_id);

-- Integrations keep their catalogue code; the primary key becomes a surrogate.
ALTER TABLE integration RENAME COLUMN id TO code;
ALTER TABLE integration DROP CONSTRAINT integration_pkey,
    ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN organization_id UUID REFERENCES organization (id) ON DELETE CASCADE;
UPDATE integration SET organization_id = (SELECT id FROM organization);
ALTER TABLE integration ALTER COLUMN id DROP DEFAULT,
    ALTER COLUMN organization_id SET NOT NULL,
    ADD PRIMARY KEY (id),
    ADD UNIQUE (organization_id, code);

-- Policies: one set per organization.
ALTER TABLE policy_stage_gate DROP CONSTRAINT policy_stage_gate_pkey,
    ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN organization_id UUID REFERENCES organization (id) ON DELETE CASCADE;
ALTER TABLE policy_remediation_sla DROP CONSTRAINT policy_remediation_sla_pkey,
    ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN organization_id UUID REFERENCES organization (id) ON DELETE CASCADE;
ALTER TABLE policy_license_rule DROP CONSTRAINT policy_license_rule_pkey,
    ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN organization_id UUID REFERENCES organization (id) ON DELETE CASCADE;
UPDATE policy_stage_gate SET organization_id = (SELECT id FROM organization);
UPDATE policy_remediation_sla SET organization_id = (SELECT id FROM organization);
UPDATE policy_license_rule SET organization_id = (SELECT id FROM organization);
ALTER TABLE policy_stage_gate ALTER COLUMN id DROP DEFAULT, ALTER COLUMN organization_id SET NOT NULL,
    ADD PRIMARY KEY (id), ADD UNIQUE (organization_id, stage);
ALTER TABLE policy_remediation_sla ALTER COLUMN id DROP DEFAULT, ALTER COLUMN organization_id SET NOT NULL,
    ADD PRIMARY KEY (id), ADD UNIQUE (organization_id, severity);
ALTER TABLE policy_license_rule ALTER COLUMN id DROP DEFAULT, ALTER COLUMN organization_id SET NOT NULL,
    ADD PRIMARY KEY (id), ADD UNIQUE (organization_id, license_id);

-- Repositories; findings, runs, deploy keys and risk acceptances are scoped through them.
ALTER TABLE project ADD COLUMN organization_id UUID REFERENCES organization (id) ON DELETE CASCADE;
UPDATE project SET organization_id = (SELECT id FROM organization);
ALTER TABLE project ALTER COLUMN organization_id SET NOT NULL,
    DROP CONSTRAINT project_name_key,
    ADD UNIQUE (organization_id, name);

ALTER TABLE report ADD COLUMN organization_id UUID REFERENCES organization (id) ON DELETE CASCADE;
UPDATE report SET organization_id = (SELECT id FROM organization);
ALTER TABLE report ALTER COLUMN organization_id SET NOT NULL;
CREATE INDEX report_organization_idx ON report (organization_id, generated_at DESC);

ALTER TABLE api_token ADD COLUMN organization_id UUID REFERENCES organization (id) ON DELETE CASCADE;
UPDATE api_token SET organization_id = (SELECT id FROM organization);
ALTER TABLE api_token ALTER COLUMN organization_id SET NOT NULL;
CREATE INDEX api_token_organization_idx ON api_token (organization_id);

-- Members are linked to identity provider accounts by subject (bound on first sign-in with a verified e-mail).
ALTER TABLE member ADD COLUMN organization_id UUID REFERENCES organization (id) ON DELETE CASCADE,
    ADD COLUMN user_subject VARCHAR(64);
UPDATE member SET organization_id = (SELECT id FROM organization), email = lower(email);
ALTER TABLE member ALTER COLUMN organization_id SET NOT NULL,
    DROP CONSTRAINT member_email_key,
    ADD UNIQUE (organization_id, email),
    ADD UNIQUE (organization_id, user_subject);
DROP INDEX member_single_owner;
CREATE UNIQUE INDEX member_single_owner ON member (organization_id) WHERE role = 'OWNER';
CREATE INDEX member_subject_idx ON member (user_subject);
CREATE INDEX member_email_idx ON member (email);

ALTER TABLE invitation ADD COLUMN organization_id UUID REFERENCES organization (id) ON DELETE CASCADE,
    ADD COLUMN expires_at TIMESTAMPTZ,
    ADD COLUMN invited_by VARCHAR(255);
UPDATE invitation SET organization_id = (SELECT id FROM organization), email = lower(email),
    expires_at = sent_at + INTERVAL '7 days';
ALTER TABLE invitation ALTER COLUMN organization_id SET NOT NULL,
    ALTER COLUMN expires_at SET NOT NULL,
    DROP CONSTRAINT invitation_email_key,
    ADD UNIQUE (organization_id, email);
CREATE INDEX invitation_email_idx ON invitation (email);

-- A fresh installation has only the built-in defaults: nobody could ever access that organization, so it goes.
DELETE FROM organization o
WHERE NOT EXISTS (SELECT 1 FROM member m WHERE m.organization_id = o.id)
  AND NOT EXISTS (SELECT 1 FROM project p WHERE p.organization_id = o.id);
