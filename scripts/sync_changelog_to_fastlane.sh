#!/usr/bin/env bash
# Ensure the current version's "What's New" exists at
# fastlane/metadata/android/en-US/changelogs/<code>.txt — the one file both the
# official F-Droid repo and Google Play read (en-US is F-Droid's fallback
# locale, so it covers every language).
#
# A COMMITTED file wins and is never rewritten. Play caps "What's New" at 500
# characters and F-Droid truncates long entries in-client, so this file is a
# hand-written summary, not a copy of the CHANGELOG.md section — those run to
# thousands of characters. Write it when cutting a release, keep it under 500,
# and commit it.
#
# Only when the file is missing does this fall back to extracting the
# CHANGELOG.md section, so the self-hosted release pipeline always has
# something to publish. The extraction matches the awk used for the Gitea
# release notes.
set -euo pipefail
cd "$(dirname "$0")/.."   # repo root

LIMIT=500

VERSION=$(bash scripts/version_info.sh version)
VERSION_CODE=$(bash scripts/version_info.sh version_code)

# Betas only ship to Codeberg, whose notes come from CHANGELOG.md. A What's New
# file for one would sit in the tree F-Droid and Play read, so none is wanted.
if [ "$(bash scripts/version_info.sh channel)" = beta ]; then
  echo "Beta $VERSION: no store What's New (betas only ship to Codeberg)."
  exit 0
fi

CL_DIR="fastlane/metadata/android/en-US/changelogs"
mkdir -p "$CL_DIR"
OUT="$CL_DIR/${VERSION_CODE}.txt"

if [ -s "$OUT" ]; then
  ACTION="Kept"
else
  ACTION="Generated"
  awk -v ver="$VERSION" '
    $0 ~ "^## \\[" ver "\\]" { flag = 1; next }
    /^## \[/ { flag = 0 }
    flag' CHANGELOG.md > "$OUT"
  # Trim leading blank lines (same as the pipeline did).
  sed -i -e '/./,$!d' "$OUT"
  [ -s "$OUT" ] || echo "See CHANGELOG.md for $VERSION." > "$OUT"
fi

CHARS=$(wc -m < "$OUT" | tr -d ' ')
echo "$ACTION $OUT (version $VERSION, code $VERSION_CODE, ${CHARS} chars)"
if [ "$CHARS" -gt "$LIMIT" ]; then
  echo "  warning: >${LIMIT} chars — Play rejects this and F-Droid truncates it." >&2
  echo "  Replace $OUT with a hand-written summary under ${LIMIT} chars." >&2
fi
