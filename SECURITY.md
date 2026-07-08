# Security Policy

## Supported Versions

| Version | Supported |
| ------- | --------- |
| 0.1.x   | Yes       |

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

1. Use **GitHub Private Vulnerability Reporting** (Security → Advisories → Report a vulnerability) on this repository.
2. Or email the maintainers with:
   - Affected version(s)
   - Steps to reproduce
   - Impact assessment (data exposure, privilege escalation, etc.)

We aim to acknowledge reports within **72 hours** and provide a fix or mitigation timeline within **14 days** for confirmed issues.

## Scope

In scope:

- AndroWatch application code in this repository
- Gradle build scripts and GitHub Actions workflows
- Data handling in collectors and JSON export

Out of scope:

- Third-party Android OS bugs
- User devices with compromised rooting / malware
- Social engineering against end users

## Security Design

- **Local-first**: fingerprint data stays on-device unless the user explicitly exports JSON.
- **No hardcoded secrets**: signing keys and SDK paths are never committed (`keystore.properties`, `local.properties` are gitignored).
- **Release hardening**: R8 minification, resource shrinking, privacy-safe logging via `PrivacyLog`.
- **Backup disabled**: `android:allowBackup="false"` in the manifest.
- **CI signing**: release keystores are provided via GitHub Actions encrypted secrets only.

## Recommended Repository Settings

Enable these free GitHub security features under **Settings → Code security**:

- Dependabot alerts
- Dependabot security updates
- Secret scanning + push protection
- Code scanning (CodeQL workflow included in `.github/workflows/ci.yml`)
