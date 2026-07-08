#!/usr/bin/env bash
# Generate a release keystore for local signing or CI (base64 export).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE="${ROOT}/release.keystore"
PROPS="${ROOT}/keystore.properties"

if [[ -f "$KEYSTORE" ]]; then
  echo "Keystore already exists: $KEYSTORE"
  exit 0
fi

keytool -genkeypair \
  -v \
  -keystore "$KEYSTORE" \
  -alias androwatch \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass "${KEYSTORE_PASSWORD:-changeit}" \
  -keypass "${KEY_PASSWORD:-changeit}" \
  -dname "CN=AndroWatch, OU=Mobile, O=LTechnologies, L=Local, ST=NA, C=XX"

cat >"$PROPS" <<EOF
storeFile=release.keystore
storePassword=${KEYSTORE_PASSWORD:-changeit}
keyAlias=androwatch
keyPassword=${KEY_PASSWORD:-changeit}
EOF

echo ""
echo "Created:"
echo "  $KEYSTORE"
echo "  $PROPS"
echo ""
echo "For GitHub Actions, add these secrets:"
echo "  RELEASE_KEYSTORE_BASE64=$(base64 -w0 "$KEYSTORE" 2>/dev/null || base64 <"$KEYSTORE" | tr -d '\n')"
echo "  RELEASE_KEYSTORE_PASSWORD=${KEYSTORE_PASSWORD:-changeit}"
echo "  RELEASE_KEY_ALIAS=androwatch"
echo "  RELEASE_KEY_PASSWORD=${KEY_PASSWORD:-changeit}"
