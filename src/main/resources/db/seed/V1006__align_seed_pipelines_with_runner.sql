-- Dev seed counterpart of V9: the seeded pipelines (V1000) predate the runner tool set.
UPDATE pipeline_stage_config SET tool = 'Gitleaks' WHERE stage = 'SECRETS' AND tool <> 'Gitleaks';
UPDATE pipeline_stage_config SET tool = 'Semgrep' WHERE stage = 'SAST' AND tool <> 'Semgrep';
UPDATE pipeline_stage_config SET tool = 'Trivy' WHERE stage = 'SCA' AND tool NOT IN ('Trivy', 'Grype');
UPDATE pipeline_stage_config SET tool = 'Trivy' WHERE stage IN ('LICENSE', 'IMAGE', 'IAC') AND tool <> 'Trivy';
UPDATE pipeline_stage_config SET tool = 'Hadolint' WHERE stage = 'DOCKERFILE' AND tool NOT IN ('Hadolint', 'Trivy');
UPDATE pipeline_stage_config SET tool = 'Syft' WHERE stage = 'SBOM' AND tool <> 'Syft';
UPDATE pipeline_stage_config SET tool = 'OWASP ZAP' WHERE stage = 'DAST' AND tool <> 'OWASP ZAP';
UPDATE pipeline_stage_config SET enabled = FALSE WHERE stage IN ('IMAGE', 'DAST');
