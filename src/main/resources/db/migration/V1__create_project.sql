CREATE TABLE project (
    id                UUID PRIMARY KEY,
    name              VARCHAR(255) NOT NULL UNIQUE,
    ssh_url           VARCHAR(512) NOT NULL,
    branch            VARCHAR(255) NOT NULL,
    stack             VARCHAR(32)  NOT NULL,
    scan_schedule     VARCHAR(32)  NOT NULL,
    dockerfile_mode   VARCHAR(32)  NOT NULL,
    custom_dockerfile TEXT,
    last_scan_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL
);

CREATE TABLE project_automatic_action (
    project_id UUID        NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    action     VARCHAR(32) NOT NULL,
    PRIMARY KEY (project_id, action)
);

CREATE TABLE pipeline_stage_config (
    id         UUID PRIMARY KEY,
    project_id UUID         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    stage      VARCHAR(32)  NOT NULL,
    enabled    BOOLEAN      NOT NULL,
    tool       VARCHAR(64)  NOT NULL,
    fail_on    VARCHAR(16),
    UNIQUE (project_id, stage)
);
