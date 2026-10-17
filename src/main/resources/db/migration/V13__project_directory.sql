-- Optional folder inside the repository to scan (monorepos); NULL scans the whole repository.
ALTER TABLE project ADD COLUMN directory VARCHAR(255);
