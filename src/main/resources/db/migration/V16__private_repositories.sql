-- Private repositories over SSH: Artemis-generated deploy keys (private part encrypted with AES-256-GCM by the API),
-- access checks run by the scanner, and the host key recorded on first contact for non-pinned Git servers.
ALTER TABLE deploy_key
    ADD COLUMN managed                BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN private_key_ciphertext BYTEA,
    ADD COLUMN private_key_nonce      BYTEA,
    ADD COLUMN encryption_key_id      VARCHAR(32),
    ADD CONSTRAINT deploy_key_managed_secret CHECK (
        NOT managed OR (private_key_ciphertext IS NOT NULL AND private_key_nonce IS NOT NULL AND encryption_key_id IS NOT NULL));

CREATE UNIQUE INDEX deploy_key_one_managed_per_project ON deploy_key (project_id) WHERE managed;

ALTER TABLE project
    ADD COLUMN access_status  VARCHAR(16),
    ADD COLUMN ssh_known_host TEXT;

UPDATE project SET access_status = 'PENDING' WHERE ssh_url LIKE 'git@%' OR ssh_url LIKE 'ssh://%';

CREATE TABLE repository_access_check (
    id           UUID PRIMARY KEY,
    project_id   UUID         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    status       VARCHAR(16)  NOT NULL,
    message      VARCHAR(500),
    requested_at TIMESTAMPTZ  NOT NULL,
    started_at   TIMESTAMPTZ,
    finished_at  TIMESTAMPTZ
);

CREATE INDEX repository_access_check_queue ON repository_access_check (status, requested_at);
CREATE UNIQUE INDEX repository_access_check_one_active ON repository_access_check (project_id)
    WHERE status IN ('QUEUED', 'RUNNING');
