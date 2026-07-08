#!/usr/bin/env bash
# Enable all free GitHub features for AndroWatch (public repo).
# Run after: gh auth login (admin on the repository)
set -euo pipefail

OWNER="${GITHUB_OWNER:-LTechnologies0}"
REPO="${GITHUB_REPO:-AndroWatch}"
FULL="${OWNER}/${REPO}"

echo "==> Repository settings: ${FULL}"

gh repo edit "${FULL}" \
  --delete-branch-on-merge \
  --enable-discussions \
  --enable-auto-merge \
  --enable-squash-merge \
  --enable-rebase-merge \
  --enable-merge-commit

gh api "repos/${FULL}" -X PATCH \
  -f web_commit_signoff_required=false \
  -f allow_update_branch=true \
  -f allow_forking=true \
  -f has_downloads=true

echo "==> Security features"
gh api "repos/${FULL}/vulnerability-alerts" -X PUT 2>/dev/null || true
gh api "repos/${FULL}/automated-security-fixes" -X PUT 2>/dev/null || true
gh api "repos/${FULL}" -X PATCH \
  -f security_and_analysis[dependabot_security_updates][status]=enabled \
  -f security_and_analysis[secret_scanning][status]=enabled \
  -f security_and_analysis[secret_scanning_push_protection][status]=enabled \
  2>/dev/null || true
gh api -X PUT "repos/${FULL}/private-vulnerability-reporting" 2>/dev/null || \
  echo "(Private vulnerability reporting: enable in Settings → Code security if API returns 404)"

echo "==> GitHub Pages (Actions source)"
gh api "repos/${FULL}/pages" -X PUT \
  -f build_type=workflow 2>/dev/null || \
gh api "repos/${FULL}/pages" -X POST \
  -f build_type=workflow 2>/dev/null || true

echo "==> Actions permissions"
gh api "repos/${FULL}/actions/permissions" -X PUT \
  -f enabled=true \
  -f allowed_actions=all
gh api "repos/${FULL}/actions/permissions/workflow" -X PUT \
  -f default_workflow_permissions=read-and-write \
  -f can_approve_pull_request_reviews=false

echo "==> Branch protection (main)"
gh api "repos/${FULL}/branches/main/protection" -X PUT --input - <<'JSON'
{
  "required_status_checks": {
    "strict": true,
    "checks": [
      {"context": "test-and-debug"},
      {"context": "codeql"}
    ]
  },
  "enforce_admins": false,
  "required_linear_history": false,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "block_creations": false,
  "required_conversation_resolution": true,
  "lock_branch": false,
  "allow_fork_syncing": true
}
JSON

echo "==> Labels"
for label in "android:1B4D89:Android app/collector code" \
             "documentation:0075CA:Docs and KDoc" \
             "dependencies:0366D6:Dependency updates" \
             "ci/cd:5319E7:GitHub Actions workflows" \
             "security:D93F0B:Security-related" \
             "i18n:FBCA04:Translations" \
             "stale:FEF2C0:Inactive issues/PRs" \
             "good first issue:7057FF:Good for newcomers" \
             "enhancement:A2EEEF:New feature or request" \
             "bug:D73A4A:Something isn't working"; do
  name="${label%%:*}"
  rest="${label#*:}"
  color="${rest%%:*}"
  desc="${rest#*:}"
  gh label create "$name" --color "$color" --description "$desc" --repo "${FULL}" 2>/dev/null || \
    gh label edit "$name" --color "$color" --description "$desc" --repo "${FULL}" 2>/dev/null || true
done

echo ""
echo "Done. Verify at: https://github.com/${FULL}/settings"
echo "  - Code security: secret scanning, Dependabot, private reporting"
echo "  - Branches: main protection with CI + CodeQL checks"
echo "  - Actions: stale, labeler, release-drafter, greetings, triage"
