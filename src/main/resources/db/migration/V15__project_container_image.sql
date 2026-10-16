-- Published container image (registry reference) scanned by the image stage; NULL skips the stage.
ALTER TABLE project ADD COLUMN container_image VARCHAR(255);
