-- AI explanations of findings, generated on request and cached per organization: one row serves every occurrence
-- with the same cache key (e.g. the same CVE in the same package version across repositories).
CREATE TABLE finding_insight (
    id              UUID         PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    cache_key       TEXT         NOT NULL,
    language        VARCHAR(10)  NOT NULL,
    content         JSONB        NOT NULL,
    model           VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT finding_insight_key UNIQUE (organization_id, cache_key, language)
);

CREATE INDEX finding_insight_daily_idx ON finding_insight (organization_id, created_at);
