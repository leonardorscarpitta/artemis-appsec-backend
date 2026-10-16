-- Fail threshold captured when a run is queued, so later pipeline edits do not change a running scan.
ALTER TABLE pipeline_run_stage ADD COLUMN fail_on VARCHAR(16);
