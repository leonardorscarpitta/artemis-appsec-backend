#!/usr/bin/env sh
# Creates development users in the artemisappsec realm (random passwords, e-mail already verified).
# Runs Keycloak's admin CLI inside the running container; nothing is installed on the host.
set -eu
cd "$(dirname "$0")/.."
mkdir -p keycloak/secrets
OUT=keycloak/secrets/dev-credentials.txt
. ./.env

kcadm() {
  docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@" </dev/null
}

kcadm config credentials --server http://localhost:8080 --realm master \
  --user "$KEYCLOAK_ADMIN_USERNAME" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null

: > "$OUT"
while IFS=: read -r username first last email; do
  password="$(openssl rand -base64 18 | tr -d '/+=' | cut -c1-20)"
  if ! kcadm get users -r artemisappsec -q "username=$username" --fields id | grep -q '"id"'; then
    kcadm create users -r artemisappsec -s "username=$username" -s "firstName=$first" -s "lastName=$last" \
      -s "email=$email" -s emailVerified=true -s enabled=true >/dev/null
  fi
  kcadm set-password -r artemisappsec --username "$username" --new-password "$password" >/dev/null
  printf '%s  %s\n' "$username" "$password" >> "$OUT"
done <<USERS
ana.souza:Ana:Souza:ana.souza@acme.example
carla.mendes:Carla:Mendes:carla.mendes@acme.example
daniel.rocha:Daniel:Rocha:daniel.rocha@acme.example
USERS
chmod 600 "$OUT"
echo "Development logins (user  password) — also in $OUT:"
cat "$OUT"
