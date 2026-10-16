-- One organization per deployment, so the subscription is a single row; every organization starts on FREE.
CREATE TABLE subscription (
    id         INTEGER PRIMARY KEY CHECK (id = 1),
    plan       VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL
);

INSERT INTO subscription (id, plan, started_at) VALUES (1, 'FREE', now());
