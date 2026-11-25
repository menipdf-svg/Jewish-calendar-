#!/usr/bin/env bash
# Derives versionCode, channel and pre-release flag from the committed versionName.
#
#   X.Y.Z (<= 2.22.3)   stable   X*10000   + Y*100   + Z         (legacy)
#   X.Y.Z-beta.N        beta     X*1000000 + Y*10000 + Z*100 + N   (N = 1..98)
#   X.Y.Z (>= 2.22.4)   stable   X*1000000 + Y*10000 + Z*100 + 99
#
#   scripts/version_info.sh          all values as key=value lines
#   scripts/version_info.sh <key>    one value
#   scripts/version_info.sh --check  fail unless the committed versionCode matches
#   scripts/version_info.sh --pin    rewrite the committed versionCode to the derived one
set -euo pipefail
cd "$(dirname "$0")/.."   # repo root

GRADLE="app/build.gradle.kts"
NAME=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$GRADLE" || true)
COMMITTED=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' "$GRADLE" || true)
[ -n "$NAME" ] || { echo "No versionName in $GRADLE" >&2; exit 1; }

if [[ ! "$NAME" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-beta\.([1-9][0-9]*))?$ ]]; then
  echo "versionName '$NAME' is neither X.Y.Z nor X.Y.Z-beta.N" >&2
  exit 1
fi
MAJOR=${BASH_REMATCH[1]}; MINOR=${BASH_REMATCH[2]}; PATCH=${BASH_REMATCH[3]}
BETA=${BASH_REMATCH[5]}
BASE="$MAJOR.$MINOR.$PATCH"

if [ "$MINOR" -gt 99 ] || [ "$PATCH" -gt 99 ]; then
  echo "versionName '$NAME': MINOR and PATCH each have room for 0-99" >&2
  exit 1
fi

LEGACY=$(( MAJOR * 10000 + MINOR * 100 + PATCH ))
if [ -n "$BETA" ]; then
  if [ "$LEGACY" -lt 22204 ]; then
    echo "versionName '$NAME': betas start at 2.22.4; legacy codes have no room for them" >&2
    exit 1
  fi
  if [ "$BETA" -lt 1 ] || [ "$BETA" -gt 98 ]; then
    echo "versionName '$NAME': beta number must be 1-98 (99 is the stable release)" >&2
    exit 1
  fi
  CHANNEL=beta
  CODE=$(( MAJOR * 1000000 + MINOR * 10000 + PATCH * 100 + BETA ))
elif [ "$LEGACY" -lt 22204 ]; then
  CHANNEL=stable
  CODE=$LEGACY
else
  CHANNEL=stable
  CODE=$(( MAJOR * 1000000 + MINOR * 10000 + PATCH * 100 + 99 ))
fi

if [ "$CHANNEL" = beta ]; then PRERELEASE=true; else PRERELEASE=false; fi

case "${1:-}" in
  "")
    printf 'version=%s\nversion_code=%s\nbase_version=%s\nchannel=%s\nprerelease=%s\n' \
      "$NAME" "$CODE" "$BASE" "$CHANNEL" "$PRERELEASE"
    ;;
  --check)
    if [ "$COMMITTED" != "$CODE" ]; then
      echo "ERROR: $GRADLE has versionCode = ${COMMITTED:-<none>}, but versionName '$NAME' needs $CODE." >&2
      echo "Set versionCode = $CODE (see docs/RELEASING.md for the scheme)." >&2
      exit 1
    fi
    echo "OK: versionName $NAME -> versionCode $CODE ($CHANNEL)."
    ;;
  --pin)
    sed -i "s/versionCode = .*/versionCode = $CODE/" "$GRADLE"
    grep -E 'versionName|versionCode' "$GRADLE"
    ;;
  version) echo "$NAME" ;;
  version_code) echo "$CODE" ;;
  base_version) echo "$BASE" ;;
  channel) echo "$CHANNEL" ;;
  prerelease) echo "$PRERELEASE" ;;
  *) echo "Unknown key '$1'" >&2; exit 2 ;;
esac
