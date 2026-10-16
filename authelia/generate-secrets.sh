#!/usr/bin/env sh
# Generates Authelia secrets and development users into authelia/secrets/ (git-ignored).
# Runs every tool inside the Authelia container; nothing is installed on the host.
set -eu

cd "$(dirname "$0")"
SECRETS_DIR="$(pwd)/secrets"
IMAGE="authelia/authelia:4.39"

if [ -f "$SECRETS_DIR/users.yml" ]; then
  echo "authelia/secrets already exists; delete it to regenerate." >&2
  exit 1
fi
mkdir -p "$SECRETS_DIR"

authelia() {
  docker run --rm -u "$(id -u):$(id -g)" -v "$SECRETS_DIR:/out" "$IMAGE" authelia "$@"
}

random() {
  authelia crypto rand --length 64 --charset alphanumeric | sed 's/^Random Value: //'
}

for name in session_secret storage_encryption_key oidc_hmac_secret reset_password_jwt_secret; do
  random > "$SECRETS_DIR/$name"
done
authelia crypto pair rsa generate --bits 4096 --directory /out --file.private-key oidc_jwks.pem --file.public-key oidc_jwks.pub.pem >/dev/null

password_hash() {
  authelia crypto hash generate argon2 --password "$1" | sed 's/^Digest: //'
}

{
  echo "users:"
  while IFS=: read -r user name email group; do
    password="$(random | cut -c1-20)"
    printf '%s  %s\n' "$user" "$password" >> "$SECRETS_DIR/dev-credentials.txt"
    printf '  %s:\n    displayname: "%s"\n    email: %s\n    groups: [%s]\n    password: "%s"\n' \
      "$user" "$name" "$email" "$group" "$(password_hash "$password")"
  done <<USERS
ana.souza:Ana Souza:ana.souza@acme.example:owners
carla.mendes:Carla Mendes:carla.mendes@acme.example:analysts
daniel.rocha:Daniel Rocha:daniel.rocha@acme.example:developers
USERS
} > "$SECRETS_DIR/users.yml"

chmod 600 "$SECRETS_DIR"/*
echo "Secrets written to authelia/secrets/. Development logins (user  password):"
cat "$SECRETS_DIR/dev-credentials.txt"
