#!/usr/bin/env bash
# Publish AndroWatch to GitHub and enable free security automation.
# Prerequisites: gh auth login (repo + workflow scopes)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

OWNER="${GITHUB_OWNER:-}"
REPO_NAME="${GITHUB_REPO:-AndroWatch}"

if ! gh auth status >/dev/null 2>&1; then
  echo "Run: gh auth login"
  exit 1
fi

if [[ -z "$OWNER" ]]; then
  OWNER="$(gh api user -q .login)"
fi

FULL="${OWNER}/${REPO_NAME}"

if ! git remote get-url origin >/dev/null 2>&1; then
  gh repo create "${FULL}" \
    --public \
    --source=. \
    --remote=origin \
    --description "Android privacy audit app — see what your device reveals for fingerprinting (Loupe-inspired)" \
    --push
else
  git push -u origin main
fi

echo "Configuring repository metadata and topics..."
gh repo edit "${FULL}" \
  --add-topic android \
  --add-topic privacy \
  --add-topic fingerprinting \
  --add-topic jetpack-compose \
  --add-topic kotlin \
  --add-topic security-audit \
  --add-topic material-design \
  --add-topic open-source \
  --add-topic android-security \
  --add-topic device-fingerprinting \
  --enable-issues=true \
  --enable-wiki=false \
  --enable-projects=true \
  --enable-discussions=false

echo "Enabling GitHub Pages (GitHub Actions source)..."
gh api "repos/${FULL}/pages" -X PUT \
  -f build_type=workflow \
  -f source[branch]=main \
  -f source[path]=/ 2>/dev/null || \
  gh api "repos/${FULL}/pages" -X POST \
    -f build_type=workflow

echo "Enabling free security features..."
gh api "repos/${FULL}" -X PATCH \
  -f security_and_analysis[advanced_security][status]=enabled \
  -f security_and_analysis[secret_scanning][status]=enabled \
  -f security_and_analysis[secret_scanning_push_protection][status]=enabled \
  -f security_and_analysis[dependabot_security_updates][status]=enabled \
  2>/dev/null || echo "(Some security toggles require GitHub Advanced Security or org policy — enable manually in Settings → Code security)"

echo "Enabling private vulnerability reporting..."
gh api "repos/${FULL}/security-advisories/config" -X PUT \
  -f private_vulnerability_reporting_enabled=true 2>/dev/null || true

echo ""
echo "Repository: https://github.com/${FULL}"
echo "API docs (after first docs workflow): https://${OWNER,,}.github.io/${REPO_NAME}/"
echo ""
echo "Next steps for signed releases:"
echo "  1. ./scripts/generate-release-keystore.sh"
echo "  2. gh secret set RELEASE_KEYSTORE_BASE64 < <(base64 -w0 release.keystore)"
echo "  3. gh secret set RELEASE_KEYSTORE_PASSWORD"
echo "  4. gh secret set RELEASE_KEY_ALIAS"
echo "  5. gh secret set RELEASE_KEY_PASSWORD"
echo "  6. git tag v0.1.0 && git push origin v0.1.0"
