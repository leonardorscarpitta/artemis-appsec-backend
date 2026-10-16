-- On a fresh database the seed findings (V1001) are inserted after V10; give them the same derived fields.
UPDATE finding
SET severity    = CASE
                      WHEN cvss_score >= 9.0 THEN 'CRITICAL'
                      WHEN cvss_score >= 7.0 THEN 'HIGH'
                      WHEN cvss_score >= 4.0 THEN 'MEDIUM'
                      ELSE 'LOW' END,
    fingerprint = encode(sha256(convert_to(lower('vulnerability|' || cve || '|' || package_name || '|' || installed_version), 'UTF8')), 'hex')
WHERE fingerprint IS NULL;
