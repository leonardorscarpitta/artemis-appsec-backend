-- The OIDC client was renamed to follow the artemisappsec identifiers.
UPDATE organization_settings SET oidc_client_id = 'artemisappsec-web' WHERE oidc_client_id = 'artemis-web';
