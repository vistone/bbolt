#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <target> <native-library>" >&2
  exit 2
fi

TARGET="$1"
LIBRARY="$2"
REQUIRED_SYMBOLS=(
  Free
  Result_Free
  Error_Free
  Sequence_Free
)

if [[ ! -s "$LIBRARY" ]]; then
  echo "Native library not found or empty: $LIBRARY" >&2
  exit 1
fi

case "$TARGET" in
  linux-x86-64)
    command -v nm >/dev/null 2>&1 || { echo "nm is required" >&2; exit 1; }
    SYMBOLS="$(nm -D -g "$LIBRARY")"
    PREFIX=""
    ;;
  darwin-x86-64|darwin-aarch64)
    command -v nm >/dev/null 2>&1 || { echo "nm is required" >&2; exit 1; }
    SYMBOLS="$(nm -gU "$LIBRARY")"
    PREFIX="_"
    ;;
  windows-x86-64)
    command -v objdump >/dev/null 2>&1 || { echo "objdump is required" >&2; exit 1; }
    SYMBOLS="$(objdump -p "$LIBRARY" | awk '/\[Ordinal\/Name Pointer\] Table/{in_names=1; next} in_names && /^[[:space:]]*The /{in_names=0} in_names {print}')"
    PREFIX=""
    ;;
  *)
    echo "Unsupported target: $TARGET" >&2
    exit 2
    ;;
esac

for symbol in "${REQUIRED_SYMBOLS[@]}"; do
  expected="${PREFIX}${symbol}"
  if ! printf '%s\n' "$SYMBOLS" | grep -Eq "[[:space:]]${expected}$"; then
    echo "Missing exported symbol ${expected} in ${LIBRARY}" >&2
    exit 1
  fi
  echo "Verified exported symbol ${expected} in ${LIBRARY}"
done
