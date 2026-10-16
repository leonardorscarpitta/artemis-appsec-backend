CREATE TABLE integration (
    id           VARCHAR(32)  PRIMARY KEY,
    name         VARCHAR(64)  NOT NULL,
    short_code   VARCHAR(8)   NOT NULL,
    category     VARCHAR(32)  NOT NULL,
    status       VARCHAR(32)  NOT NULL,
    detail       VARCHAR(255),
    removable    BOOLEAN      NOT NULL DEFAULT TRUE,
    position     INT          NOT NULL,
    last_sync_at TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ  NOT NULL
);

CREATE TABLE deploy_key (
    id           UUID PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    project_id   UUID         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    key_type     VARCHAR(32)  NOT NULL,
    public_key   TEXT         NOT NULL,
    fingerprint  VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    last_used_at TIMESTAMPTZ,
    UNIQUE (project_id, fingerprint)
);

CREATE TABLE member (
    id             UUID PRIMARY KEY,
    name           VARCHAR(120) NOT NULL,
    email          VARCHAR(254) NOT NULL UNIQUE,
    role           VARCHAR(32)  NOT NULL,
    active         BOOLEAN      NOT NULL,
    last_active_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX member_single_owner ON member (role) WHERE role = 'OWNER';

CREATE TABLE invitation (
    id      UUID PRIMARY KEY,
    email   VARCHAR(254) NOT NULL UNIQUE,
    role    VARCHAR(32)  NOT NULL,
    sent_at TIMESTAMPTZ  NOT NULL
);

-- Single-row table: one organization per deployment.
CREATE TABLE organization_settings (
    id                        SMALLINT PRIMARY KEY CHECK (id = 1),
    name                      VARCHAR(120) NOT NULL,
    slug                      VARCHAR(40)  NOT NULL,
    default_language          VARCHAR(8)   NOT NULL,
    timezone                  VARCHAR(64)  NOT NULL,
    oidc_issuer               VARCHAR(512) NOT NULL,
    oidc_client_id            VARCHAR(128) NOT NULL,
    oidc_client_secret_set    BOOLEAN      NOT NULL,
    session_timeout           VARCHAR(16)   NOT NULL,
    enforce_sso               BOOLEAN      NOT NULL,
    findings_retention        VARCHAR(16)  NOT NULL,
    logs_retention            VARCHAR(16)  NOT NULL,
    reports_retention         VARCHAR(16)  NOT NULL,
    updated_at                TIMESTAMPTZ  NOT NULL
);

CREATE TABLE organization_notification (
    settings_id SMALLINT    NOT NULL REFERENCES organization_settings (id) ON DELETE CASCADE,
    event       VARCHAR(32) NOT NULL,
    channel     VARCHAR(32) NOT NULL,
    PRIMARY KEY (settings_id, event, channel)
);

CREATE TABLE api_token (
    id           UUID PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    token_hash   VARCHAR(64)     NOT NULL UNIQUE,
    last_four    VARCHAR(4)     NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    last_used_at TIMESTAMPTZ
);

CREATE TABLE api_token_scope (
    token_id UUID        NOT NULL REFERENCES api_token (id) ON DELETE CASCADE,
    scope    VARCHAR(32) NOT NULL,
    PRIMARY KEY (token_id, scope)
);

-- Built-in organization so the platform works before anyone edits the settings.
INSERT INTO organization_settings (id, name, slug, default_language, timezone, oidc_issuer, oidc_client_id,
    oidc_client_secret_set, session_timeout, enforce_sso, findings_retention, logs_retention, reports_retention, updated_at)
VALUES (1, 'My organization', 'my-org', 'en-US', 'UTC', 'https://auth.example.com', 'artemis-web',
    FALSE, 'EIGHT_HOURS', TRUE, 'DAYS_730', 'DAYS_90', 'FOREVER', NOW());

INSERT INTO integration (id, name, short_code, category, status, removable, position, updated_at) VALUES
    ('github',        'GitHub',          'GH',  'SCM',             'DISCONNECTED', TRUE,  1,  NOW()),
    ('gitlab',        'GitLab',          'GL',  'SCM',             'DISCONNECTED', TRUE,  2,  NOW()),
    ('bitbucket',     'Bitbucket',       'BB',  'SCM',             'DISCONNECTED', TRUE,  3,  NOW()),
    ('githubActions', 'GitHub Actions',  'GA',  'CI',              'DISCONNECTED', TRUE,  4,  NOW()),
    ('gitlabCi',      'GitLab CI',       'GC',  'CI',              'DISCONNECTED', TRUE,  5,  NOW()),
    ('jenkins',       'Jenkins',         'JK',  'CI',              'DISCONNECTED', TRUE,  6,  NOW()),
    ('slack',         'Slack',           'SL',  'NOTIFICATIONS',   'DISCONNECTED', TRUE,  7,  NOW()),
    ('teams',         'Microsoft Teams', 'MT',  'NOTIFICATIONS',   'DISCONNECTED', TRUE,  8,  NOW()),
    ('email',         'E-mail',          '@',   'NOTIFICATIONS',   'DISCONNECTED', TRUE,  9,  NOW()),
    ('webhook',       'Webhook',         'WH',  'NOTIFICATIONS',   'DISCONNECTED', TRUE,  10, NOW()),
    ('jira',          'Jira',            'JI',  'ISSUES',          'DISCONNECTED', TRUE,  11, NOW()),
    ('githubIssues',  'GitHub Issues',   'GI',  'ISSUES',          'DISCONNECTED', TRUE,  12, NOW()),
    ('nvd',           'NIST NVD',        'NVD', 'VULNERABILITY_DATA', 'CONNECTED', FALSE, 13, NOW());
