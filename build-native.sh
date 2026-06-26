#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  ./build-native.sh <target> <output-dir>

Targets:
  linux-x86-64
  windows-x86-64
  darwin-x86-64
  darwin-aarch64
USAGE
}

if [[ $# -ne 2 ]]; then
  usage
  exit 2
fi

TARGET="$1"
OUTPUT_ROOT="$2"
ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
VENDOR_DIR="$ROOT_DIR/vendor"
SRC_PKG="protonail.com/bolt-jna"

export GOPATH="$VENDOR_DIR"
export GO111MODULE=off

mkdir -p "$OUTPUT_ROOT"

require_go() {
  if ! command -v go >/dev/null 2>&1; then
    echo "go is required but was not found on PATH" >&2
    exit 1
  fi
}

build_shared() {
  local goos="$1"
  local goarch="$2"
  local out_path="$3"

  mkdir -p "$(dirname "$out_path")"
  CGO_ENABLED=1 GOOS="$goos" GOARCH="$goarch" go build -buildmode=c-shared -o "$out_path" "$SRC_PKG"
  test -s "$out_path"
}

build_windows() {
  local out_path="$1"
  if [[ "${OS:-}" != "Windows_NT" ]] && ! command -v x86_64-w64-mingw32-gcc >/dev/null 2>&1; then
    echo "x86_64-w64-mingw32-gcc is required to build windows-x86-64 outside Windows" >&2
    exit 1
  fi
  mkdir -p "$(dirname "$out_path")"
  if [[ "${OS:-}" == "Windows_NT" ]]; then
    CGO_ENABLED=1 GOOS=windows GOARCH=amd64 go build -buildmode=c-shared -o "$out_path" "$SRC_PKG"
  else
    CC=x86_64-w64-mingw32-gcc CGO_ENABLED=1 GOOS=windows GOARCH=amd64 go build -buildmode=c-shared -o "$out_path" "$SRC_PKG"
  fi
  test -s "$out_path"
}

require_go

case "$TARGET" in
  linux-x86-64)
    build_shared linux amd64 "$OUTPUT_ROOT/linux-x86-64/libbolt.so"
    ;;
  windows-x86-64)
    build_windows "$OUTPUT_ROOT/win32-x86-64/bolt.dll"
    ;;
  darwin-x86-64)
    if [[ "$(uname -s)" != "Darwin" ]]; then
      echo "darwin-x86-64 must be built on macOS" >&2
      exit 1
    fi
    build_shared darwin amd64 "$OUTPUT_ROOT/darwin-x86-64/libbolt.dylib"
    ;;
  darwin-aarch64)
    if [[ "$(uname -s)" != "Darwin" ]]; then
      echo "darwin-aarch64 must be built on macOS" >&2
      exit 1
    fi
    build_shared darwin arm64 "$OUTPUT_ROOT/darwin-aarch64/libbolt.dylib"
    ;;
  *)
    usage
    exit 2
    ;;
esac

echo "Built $TARGET into $OUTPUT_ROOT"
