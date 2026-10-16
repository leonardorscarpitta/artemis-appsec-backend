CREATE TABLE report (
    id           UUID PRIMARY KEY,
    title        JSONB        NOT NULL,
    type         VARCHAR(32)  NOT NULL,
    project_id   UUID REFERENCES project (id) ON DELETE CASCADE,
    period_start DATE         NOT NULL,
    period_end   DATE         NOT NULL,
    generated_at TIMESTAMPTZ  NOT NULL,
    generated_by VARCHAR(16)  NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    content      JSONB,
    CHECK (period_end >= period_start),
    CHECK (status = 'GENERATING' OR content IS NOT NULL)
);

CREATE INDEX report_generated_at_idx ON report (generated_at DESC);
