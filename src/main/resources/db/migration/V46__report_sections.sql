-- Optional topics chosen when the report was requested ("summary", "recommendations", "sbom"); NULL for older reports,
-- whose type decides their contents.
ALTER TABLE report ADD COLUMN sections JSONB;
