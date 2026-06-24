#!/bin/bash
set -e

# 交叉编译bbolt原生库，支持Linux、Windows、Mac三个平台
# 要求：已安装Go 1.18+，且安装了对应平台的交叉编译工具链

export GOPATH="$(pwd)"
export GO111MODULE=off
OUTPUT_DIR="jetbrains-plugin/src/main/resources/native"
SRC_DIR="protonail.com/bolt-jna"

echo "=== 开始交叉编译原生库 ==="

# 1. Linux x86-64
echo "编译 Linux x86-64..."
mkdir -p "$OUTPUT_DIR/linux-x86-64"
CGO_ENABLED=1 GOOS=linux GOARCH=amd64 go build -buildmode=c-shared -o "$OUTPUT_DIR/linux-x86-64/libbolt.so" $SRC_DIR

# 2. Windows x86-64
echo "编译 Windows x86-64..."
mkdir -p "$OUTPUT_DIR/win32-x86-64"
CGO_ENABLED=1 GOOS=windows GOARCH=amd64 CC=x86_64-w64-mingw32-gcc go build -buildmode=c-shared -o "$OUTPUT_DIR/win32-x86-64/bolt.dll" $SRC_DIR

# 3. Mac x86-64
if [ "$(uname)" = "Darwin" ]; then
  echo "编译 Mac x86-64..."
  mkdir -p "$OUTPUT_DIR/darwin-x86-64"
  CGO_ENABLED=1 GOOS=darwin GOARCH=amd64 go build -buildmode=c-shared -o "$OUTPUT_DIR/darwin-x86-64/libbolt.dylib" $SRC_DIR

  # 4. Mac arm64 (Apple Silicon)
  echo "编译 Mac arm64..."
  mkdir -p "$OUTPUT_DIR/darwin-aarch64"
  CGO_ENABLED=1 GOOS=darwin GOARCH=arm64 go build -buildmode=c-shared -o "$OUTPUT_DIR/darwin-aarch64/libbolt.dylib" $SRC_DIR
else
  echo "跳过 Mac 平台编译（当前非 macOS 系统，如需编译请在 Mac 机器上执行本脚本）"
fi

echo "=== 编译完成 ==="
echo "输出目录: $OUTPUT_DIR"
ls -la $OUTPUT_DIR/*/
