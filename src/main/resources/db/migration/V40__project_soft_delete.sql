-- A removed repository keeps its history (findings, runs, reports, risk acceptances) until it is purged.
-- Its name can be connected again, so names are unique only among repositories that are not removed.
ALTER TABLE project ADD COLUMN deleted_at TIMESTAMPTZ;

ALTER TABLE project DROP CONSTRAINT project_organization_id_name_key;
CREATE UNIQUE INDEX project_organization_name_active ON project (organization_id, name) WHERE deleted_at IS NULL;
CREATE INDEX project_organization_idx ON project (organization_id);
