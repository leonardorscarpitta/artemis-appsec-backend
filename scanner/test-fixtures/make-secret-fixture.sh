#!/usr/bin/env sh
# Writes a fake credential into the fixture at test time. It is generated (not committed) so that
# provider push-protection never sees a secret-shaped string in this repository.
set -eu
target="$(dirname "$0")/vulnerable-app/src/config.generated.js"
key="AKIA$(head -c 64 /dev/urandom | tr -dc 'A-Z2-7' | head -c 16)"
secret="$(head -c 64 /dev/urandom | tr -dc 'A-Za-z0-9' | head -c 40)"
printf "module.exports = {\n  awsAccessKeyId: '%s',\n  awsSecretAccessKey: '%s',\n};\n" "$key" "$secret" > "$target"
echo "$target"
