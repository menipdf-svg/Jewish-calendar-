#!/usr/bin/env bash
# Prints the release notes for VERSION from CHANGELOG.md. A beta without its own
# section falls back to [Unreleased], under a line saying what a beta is.
#
#   scripts/release_notes.sh 2.22.4 > release-notes.md
set -euo pipefail
cd "$(dirname "$0")/.."   # repo root

VERSION=${1:?usage: release_notes.sh VERSION}

section() {
  awk -v ver="$1" '
    $0 ~ "^## \\[" ver "\\]" { flag = 1; next }
    /^## \[/ { flag = 0 }
    flag' CHANGELOG.md | sed -e '/./,$!d'
}

NOTES=$(section "$VERSION")
if [[ "$VERSION" == *-beta.* ]]; then
  [ -n "$NOTES" ] || NOTES=$(section Unreleased)
  printf '%s\n\n' "**Beta of ${VERSION%%-*}, for testing.** It updates in place to later betas and to the stable release. Obtainium only offers betas with *Include prereleases* switched on; F-Droid and Play never get them."
fi
if [ -n "$NOTES" ]; then
  printf '%s\n' "$NOTES"
else
  echo "_No changelog entry for ${VERSION} — see CHANGELOG.md._"
fi
