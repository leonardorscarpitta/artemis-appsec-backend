-- A risk acceptance now belongs to one finding of any kind (not only CVEs) and keeps that finding accepted.
ALTER TABLE risk_acceptance ADD COLUMN finding_id UUID REFERENCES finding (id) ON DELETE CASCADE;
ALTER TABLE risk_acceptance ALTER COLUMN cve DROP NOT NULL;
ALTER TABLE risk_acceptance DROP CONSTRAINT risk_acceptance_cve_project_id_key;

UPDATE risk_acceptance a
SET finding_id = (SELECT f.id FROM finding f WHERE f.project_id = a.project_id AND f.cve = a.cve
                  ORDER BY f.detected_at LIMIT 1);

CREATE UNIQUE INDEX risk_acceptance_finding_idx ON risk_acceptance (finding_id);

-- Findings accepted before acceptances were linked to findings get one, so they show up and expire.
INSERT INTO risk_acceptance (id, cve, project_id, justification, approved_by, expires_at, created_at, finding_id)
SELECT gen_random_uuid(), f.cve, f.project_id,
       COALESCE((SELECT e.note FROM finding_event e
                 WHERE e.finding_id = f.id AND e.status = 'ACCEPTED' AND e.note IS NOT NULL
                 ORDER BY e.occurred_at DESC LIMIT 1),
                'Accepted before risk acceptances were linked to findings'),
       'unknown', CURRENT_DATE + 90, now(), f.id
FROM finding f
WHERE f.status = 'ACCEPTED'
  AND NOT EXISTS (SELECT 1 FROM risk_acceptance a WHERE a.finding_id = f.id);
