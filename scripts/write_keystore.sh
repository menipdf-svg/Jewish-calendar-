#!/usr/bin/env bash
# Writes the app signing keystore and key.properties from CI secrets.
#
# Env: KEYSTORE_BASE64, KEY_PASSWORD, KEY_ALIAS
set -euo pipefail
cd "$(dirname "$0")/.."   # repo root
: "${KEYSTORE_BASE64:?}" "${KEY_PASSWORD:?}" "${KEY_ALIAS:?}"

mkdir -p app
echo "$KEYSTORE_BASE64" | base64 --decode > app/upload-keystore.jks
cat > key.properties <<PROPS
storePassword=$KEY_PASSWORD
keyPassword=$KEY_PASSWORD
keyAlias=$KEY_ALIAS
storeFile=upload-keystore.jks
PROPS
