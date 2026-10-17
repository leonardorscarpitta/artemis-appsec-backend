-- A run reserves one monthly scan when it is queued. quota_month is the bucket while the reservation can still be
-- given back; it is cleared once the runner reports a stage (the scan counts) or when a run that never got that far
-- is refunded (quota_refunded).
ALTER TABLE pipeline_run
    ADD COLUMN quota_month    DATE,
    ADD COLUMN quota_refunded BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE pipeline_run
SET quota_month = date_trunc('month', started_at AT TIME ZONE 'UTC')::date
WHERE status = 'QUEUED';
