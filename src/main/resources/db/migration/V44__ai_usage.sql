-- Daily AI attempts per organization ('insight' | 'report'); every attempt counts, including failed ones.
CREATE TABLE organization_ai_usage (
    organization_id UUID        NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    day             DATE        NOT NULL,
    kind            VARCHAR(16) NOT NULL,
    attempts        INT         NOT NULL,
    PRIMARY KEY (organization_id, day, kind)
);
