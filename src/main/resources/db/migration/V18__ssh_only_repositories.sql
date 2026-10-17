-- Only SSH repositories with an Artemis deploy key are scanned: existing HTTPS URLs become their SSH form
-- (https://host/owner/repo(.git) -> git@host:owner/repo.git) and wait for a key to be added (access pending).
UPDATE project
SET ssh_url = 'git@' || substring(ssh_url FROM '^https://([^/:]+)') || ':'
        || regexp_replace(substring(ssh_url FROM '^https://[^/]+/(.+)$'), '(\.git)?/*$', '') || '.git',
    access_status = 'PENDING'
WHERE ssh_url LIKE 'https://%';
