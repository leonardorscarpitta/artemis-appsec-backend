-- Monorepo folders a run scans (a snapshot taken when it is queued); none = the whole repository.
CREATE TABLE pipeline_run_folder (
    run_id   UUID         NOT NULL REFERENCES pipeline_run (id) ON DELETE CASCADE,
    position INT          NOT NULL,
    path     VARCHAR(120) NOT NULL,
    PRIMARY KEY (run_id, position)
);
