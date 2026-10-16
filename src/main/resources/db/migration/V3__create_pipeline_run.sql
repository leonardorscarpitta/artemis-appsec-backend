CREATE SEQUENCE pipeline_run_number_seq START WITH 1;

CREATE TABLE pipeline_run (
    id               UUID PRIMARY KEY,
    number           BIGINT       NOT NULL UNIQUE,
    project_id       UUID         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    branch           VARCHAR(255) NOT NULL,
    commit_sha       VARCHAR(64)  NOT NULL,
    commit_message   VARCHAR(500) NOT NULL,
    trigger_type     VARCHAR(32)  NOT NULL,
    actor            VARCHAR(255) NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    started_at       TIMESTAMPTZ  NOT NULL,
    duration_seconds INTEGER      NOT NULL DEFAULT 0,
    new_findings     INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX pipeline_run_project_idx ON pipeline_run (project_id);
CREATE INDEX pipeline_run_started_idx ON pipeline_run (started_at DESC);

CREATE TABLE pipeline_run_stage (
    id               UUID PRIMARY KEY,
    run_id           UUID        NOT NULL REFERENCES pipeline_run (id) ON DELETE CASCADE,
    position         INTEGER     NOT NULL,
    stage            VARCHAR(32) NOT NULL,
    tool             VARCHAR(64) NOT NULL,
    status           VARCHAR(16) NOT NULL,
    duration_seconds INTEGER     NOT NULL DEFAULT 0,
    findings         INTEGER     NOT NULL DEFAULT 0,
    failed_by        VARCHAR(16),
    log              TEXT        NOT NULL DEFAULT '',
    UNIQUE (run_id, stage)
);
