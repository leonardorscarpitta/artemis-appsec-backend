-- One-time invitation links: only the SHA-256 of the random token is stored. Invitations sent before this
-- migration have no link; resending one issues it.
ALTER TABLE invitation ADD COLUMN token_hash VARCHAR(64);
CREATE UNIQUE INDEX invitation_token_hash_key ON invitation (token_hash);
