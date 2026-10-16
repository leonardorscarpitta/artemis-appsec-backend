-- Dev seed: the demo repositories (acme/*) do not exist, so the seeded queued/running runs are cancelled
-- instead of being claimed by the real scanner runner.
UPDATE pipeline_run_stage SET status = 'SKIPPED'
WHERE status IN ('PENDING', 'RUNNING')
  AND run_id IN (SELECT id FROM pipeline_run WHERE status IN ('QUEUED', 'RUNNING') AND number BETWEEN 1273 AND 1284);
UPDATE pipeline_run SET status = 'CANCELLED' WHERE status IN ('QUEUED', 'RUNNING') AND number BETWEEN 1273 AND 1284;
