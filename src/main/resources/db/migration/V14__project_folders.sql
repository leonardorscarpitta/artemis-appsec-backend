-- Monorepos: several folders per repository, each with its own stack. No folders = the whole repository.
CREATE TABLE project_folder (
    project_id UUID         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    position   INT          NOT NULL,
    path       VARCHAR(120) NOT NULL,
    stack      VARCHAR(32)  NOT NULL,
    PRIMARY KEY (project_id, position),
    UNIQUE (project_id, path)
);

INSERT INTO project_folder (project_id, position, path, stack)
SELECT id, 0, directory, stack FROM project WHERE directory IS NOT NULL;

ALTER TABLE project DROP COLUMN directory;
