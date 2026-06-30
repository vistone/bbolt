#!/usr/bin/env bash
set -euo pipefail

TARGET="${1:?target is required}"
OUT_DIR="${2:-native-artifacts}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="$ROOT/rust/jammdb-jna/Cargo.toml"

case "$TARGET" in
  windows-x86-64)
    cargo build --manifest-path "$MANIFEST" --release
    DEST="$OUT_DIR/win32-x86-64"
    mkdir -p "$DEST"
    cp "$ROOT/rust/jammdb-jna/target/release/jammdb_jna.dll" "$DEST/jammdb_jna.dll"
    ;;
  linux-x86-64)
    cargo build --manifest-path "$MANIFEST" --release
    DEST="$OUT_DIR/linux-x86-64"
    mkdir -p "$DEST"
    cp "$ROOT/rust/jammdb-jna/target/release/libjammdb_jna.so" "$DEST/libjammdb_jna.so"
    ;;
  darwin-x86-64)
    rustup target add x86_64-apple-darwin
    cargo build --manifest-path "$MANIFEST" --release --target x86_64-apple-darwin
    DEST="$OUT_DIR/darwin-x86-64"
    mkdir -p "$DEST"
    cp "$ROOT/rust/jammdb-jna/target/x86_64-apple-darwin/release/libjammdb_jna.dylib" "$DEST/libjammdb_jna.dylib"
    ;;
  darwin-aarch64)
    rustup target add aarch64-apple-darwin
    cargo build --manifest-path "$MANIFEST" --release --target aarch64-apple-darwin
    DEST="$OUT_DIR/darwin-aarch64"
    mkdir -p "$DEST"
    cp "$ROOT/rust/jammdb-jna/target/aarch64-apple-darwin/release/libjammdb_jna.dylib" "$DEST/libjammdb_jna.dylib"
    ;;
  *)
    echo "Unsupported target: $TARGET" >&2
    exit 2
    ;;
esac
