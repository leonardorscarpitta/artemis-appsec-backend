-- Scheduled scans (PRO): when the scheduler queues the repository's next run (UTC). NULL = not scheduled or due now.
ALTER TABLE project ADD COLUMN next_scan_at TIMESTAMPTZ;

UPDATE project
SET next_scan_at = GREATEST(now(), COALESCE(last_scan_at, created_at) + CASE scan_schedule
        WHEN 'EVERY_6_HOURS' THEN INTERVAL '6 hours'
        WHEN 'EVERY_24_HOURS' THEN INTERVAL '24 hours'
        ELSE INTERVAL '7 days' END)
WHERE scan_schedule IN ('EVERY_6_HOURS', 'EVERY_24_HOURS', 'EVERY_7_DAYS');

CREATE INDEX project_scheduled_scan_idx ON project (next_scan_at)
    WHERE scan_schedule IN ('EVERY_6_HOURS', 'EVERY_24_HOURS', 'EVERY_7_DAYS');
