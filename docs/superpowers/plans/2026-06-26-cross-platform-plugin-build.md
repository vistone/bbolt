# Cross-Platform Plugin Build Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one JetBrains plugin zip that contains Windows x64, Linux x64, macOS Intel, and macOS Apple Silicon native libraries.

**Architecture:** Keep the existing plugin loader resource contract unchanged and make CI responsible for producing release-grade native libraries on their real operating systems. Native jobs upload platform artifacts, and a final package job downloads them into `jetbrains-plugin/src/main/resources/native` before running `buildPlugin`.

**Tech Stack:** GitHub Actions, Go/cgo, Maven, Gradle 8.7, JDK 17, JetBrains IntelliJ Gradle Plugin.

---

## File Structure

- Modify: `build-native.sh`
  - Responsibility: Build one requested native target into an explicit output directory. It should be deterministic and CI-friendly.
- Create: `scripts/verify-plugin-native-resources.ps1`
  - Responsibility: Inspect a built plugin zip and fail if any required native resource is missing or empty.
- Modify: `.github/workflows/ci.yml`
  - Responsibility: Build native artifacts on Windows, Linux, and macOS, then package and verify the plugin zip.
- Modify: `.github/workflows/release.yml`
  - Responsibility: Reuse the same native matrix and package verification for tagged releases before publishing.
- Do not modify: `jetbrains-plugin/src/main/java/com/protonail/bolt/intellij/BoltNativeLoader.java`
  - The loader already maps the required directories: `win32-x86-64`, `linux-x86-64`, `darwin-x86-64`, and `darwin-aarch64`.

## Task 1: Make Native Builds Targeted and Deterministic

**Files:**
- Modify: `build-native.sh`

- [ ] **Step 1: Replace `build-native.sh` with a target-based script**

Use this complete file content:

```bash
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
```

- [ ] **Step 2: Run a syntax check**

Run on any machine with bash:

```bash
bash -n build-native.sh
```

Expected: no output and exit code `0`.

- [ ] **Step 3: Commit the script change**

```bash
git add build-native.sh
git commit -m "build: make native library build target-based"
```

## Task 2: Add Plugin Zip Resource Verification

**Files:**
- Create: `scripts/verify-plugin-native-resources.ps1`

- [ ] **Step 1: Create the verification script**

Use this complete file content:

```powershell
param(
    [Parameter(Mandatory = $true)]
    [string] $ZipPath
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $ZipPath)) {
    throw "Plugin zip not found: $ZipPath"
}

$required = @(
    'native/win32-x86-64/bolt.dll',
    'native/linux-x86-64/libbolt.so',
    'native/darwin-x86-64/libbolt.dylib',
    'native/darwin-aarch64/libbolt.dylib'
)

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ZipPath))
try {
    $entries = @{}
    foreach ($entry in $zip.Entries) {
        $normalized = $entry.FullName -replace '\\', '/'
        $entries[$normalized] = $entry
    }

    foreach ($suffix in $required) {
        $match = $entries.Keys | Where-Object { $_.EndsWith($suffix) } | Select-Object -First 1
        if (-not $match) {
            throw "Missing native resource ending with: $suffix"
        }
        if ($entries[$match].Length -le 0) {
            throw "Native resource is empty: $match"
        }
        Write-Host "Verified $match ($($entries[$match].Length) bytes)"
    }
}
finally {
    $zip.Dispose()
}
```

- [ ] **Step 2: Run it against a zip that is missing macOS resources and verify it fails**

Run after a local `buildPlugin` if the current zip lacks macOS dylibs:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-plugin-native-resources.ps1 -ZipPath jetbrains-plugin/build/distributions/bbolt-jetbrains-plugin-1.0.15.zip
```

Expected: fails with `Missing native resource ending with: native/darwin-x86-64/libbolt.dylib` or `native/darwin-aarch64/libbolt.dylib`.

- [ ] **Step 3: Commit the verification script**

```bash
git add scripts/verify-plugin-native-resources.ps1
git commit -m "build: verify plugin native resources"
```

## Task 3: Update CI to Build Native Artifacts on Real Platforms

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Replace the single CI build job with native jobs and a package job**

Use this complete workflow content:

```yaml
name: CI

on:
  push:
    branches: [master]
  pull_request:
    branches: [master]

permissions:
  contents: read

jobs:
  native-linux:
    name: Native Linux x64
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-go@v5
        with:
          go-version: '1.22'
      - name: Build native library
        run: bash build-native.sh linux-x86-64 native-artifacts
      - uses: actions/upload-artifact@v4
        with:
          name: native-linux-x86-64
          path: native-artifacts/linux-x86-64/libbolt.so
          if-no-files-found: error

  native-windows:
    name: Native Windows x64
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-go@v5
        with:
          go-version: '1.22'
      - name: Build native library
        shell: bash
        run: bash build-native.sh windows-x86-64 native-artifacts
      - uses: actions/upload-artifact@v4
        with:
          name: native-windows-x86-64
          path: native-artifacts/win32-x86-64/bolt.dll
          if-no-files-found: error

  native-macos:
    name: Native macOS universal inputs
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-go@v5
        with:
          go-version: '1.22'
      - name: Build macOS Intel library
        run: bash build-native.sh darwin-x86-64 native-artifacts
      - name: Build macOS Apple Silicon library
        run: bash build-native.sh darwin-aarch64 native-artifacts
      - uses: actions/upload-artifact@v4
        with:
          name: native-macos
          path: |
            native-artifacts/darwin-x86-64/libbolt.dylib
            native-artifacts/darwin-aarch64/libbolt.dylib
          if-no-files-found: error

  package-plugin:
    name: Package plugin
    runs-on: ubuntu-latest
    needs: [native-linux, native-windows, native-macos]
    steps:
      - uses: actions/checkout@v4

      - name: Download native artifacts
        uses: actions/download-artifact@v4
        with:
          path: downloaded-native

      - name: Install native artifacts into plugin resources
        shell: bash
        run: |
          set -euo pipefail
          rm -rf jetbrains-plugin/src/main/resources/native
          mkdir -p jetbrains-plugin/src/main/resources/native
          cp -R downloaded-native/native-linux-x86-64/. jetbrains-plugin/src/main/resources/native/
          cp -R downloaded-native/native-windows-x86-64/. jetbrains-plugin/src/main/resources/native/
          cp -R downloaded-native/native-macos/. jetbrains-plugin/src/main/resources/native/
          find jetbrains-plugin/src/main/resources/native -type f -maxdepth 3 -print

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Cache Maven repository
        uses: actions/cache@v4
        with:
          path: ~/.m2/repository
          key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
          restore-keys: ${{ runner.os }}-maven-

      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('jetbrains-plugin/**/*.gradle*', 'jetbrains-plugin/**/gradle-wrapper.properties') }}
          restore-keys: ${{ runner.os }}-gradle-

      - name: Build and install Maven dependencies
        run: mvn -B install -DskipTests -Dgpg.skip=true

      - name: Build JetBrains plugin
        run: |
          cd jetbrains-plugin
          ./gradlew --no-daemon clean test buildPlugin

      - name: Verify native resources in plugin zip
        shell: pwsh
        run: |
          $zip = Get-ChildItem jetbrains-plugin/build/distributions -Filter '*.zip' | Select-Object -First 1
          ./scripts/verify-plugin-native-resources.ps1 -ZipPath $zip.FullName

      - uses: actions/upload-artifact@v4
        with:
          name: bbolt-jetbrains-plugin-ci
          path: jetbrains-plugin/build/distributions/*.zip
          if-no-files-found: error
```

- [ ] **Step 2: Commit the CI workflow update**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: build plugin with cross-platform native libraries"
```

## Task 4: Update Release Workflow to Use the Same Native Pipeline

**Files:**
- Modify: `.github/workflows/release.yml`

- [ ] **Step 1: Replace the single release job with native jobs and a release package job**

Use the CI workflow structure from Task 3 and make these release-specific changes:

```yaml
on:
  push:
    tags:
      - 'v*.*.*'

permissions:
  contents: write
```

In the final job, after `Verify native resources in plugin zip`, add these release steps:

```yaml
      - name: Extract version
        id: version
        shell: bash
        run: echo "version=${GITHUB_REF_NAME#v}" >> "$GITHUB_OUTPUT"

      - name: Locate plugin zip
        id: zip
        shell: bash
        run: |
          zip_path=$(find jetbrains-plugin/build/distributions -name "*.zip" | head -1)
          echo "path=$zip_path" >> "$GITHUB_OUTPUT"
          echo "Found: $zip_path"

      - name: Verify version consistency
        shell: bash
        run: |
          tag_version="${{ steps.version.outputs.version }}"
          gradle_version=$(grep -oP '(?<=version = ")[^"]+' jetbrains-plugin/build.gradle.kts)
          echo "Tag version:      $tag_version"
          echo "build.gradle.kts: $gradle_version"
          if [ "$tag_version" != "$gradle_version" ]; then
            echo "::error::Version mismatch: tag=$tag_version, build.gradle.kts=$gradle_version"
            exit 1
          fi

      - name: Upload plugin zip artifact
        uses: actions/upload-artifact@v4
        with:
          name: bbolt-jetbrains-plugin-${{ steps.version.outputs.version }}
          path: ${{ steps.zip.outputs.path }}
          if-no-files-found: error

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: ${{ github.ref_name }}
          name: ${{ github.ref_name }}
          generate_release_notes: true
          files: ${{ steps.zip.outputs.path }}
          draft: false
          prerelease: false
```

- [ ] **Step 2: Commit the release workflow update**

```bash
git add .github/workflows/release.yml
git commit -m "ci: release plugin with cross-platform native libraries"
```

## Task 5: Local Windows Verification

**Files:**
- No source changes expected.

- [ ] **Step 1: Install Maven dependencies with the known Windows test limitation**

Run from repository root:

```powershell
$env:JAVA_HOME = 'C:\Users\stone\AppData\Local\Temp\codex-tools\jdk17\jdk-17.0.19+10'
$env:Path = "$env:JAVA_HOME\bin;$env:TEMP\codex-tools\apache-maven-3.9.10\bin;$env:Path"
mvn install '-Dgpg.skip=true' '-DskipTests'
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Build the plugin locally**

Run from `jetbrains-plugin`:

```powershell
$env:JAVA_HOME = 'C:\Users\stone\AppData\Local\Temp\codex-tools\jdk17\jdk-17.0.19+10'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
& "$env:JAVA_HOME\bin\java.exe" -cp .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean buildPlugin --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Verify the local zip before CI artifacts are available**

Run from repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-plugin-native-resources.ps1 -ZipPath jetbrains-plugin/build/distributions/bbolt-jetbrains-plugin-1.0.15.zip
```

Expected before macOS artifacts exist locally: failure on missing `darwin-*` resources. Expected after copying CI macOS artifacts into resources: all four resources verified.

- [ ] **Step 4: Commit no changes**

If Task 5 only ran commands, do not commit generated `build/`, log, or temp output.

## Task 6: CI Verification and Follow-Up

**Files:**
- Modify only if CI reveals a platform-specific build failure.

- [ ] **Step 1: Push the branch and inspect CI**

Run:

```bash
git status --short
git push
```

Expected: only intentional files are changed before push; CI starts on GitHub.

- [ ] **Step 2: If macOS arm64 fails due to cgo toolchain settings, patch only `build-native.sh`**

Use this replacement for the `darwin-aarch64)` case if GitHub Actions reports an SDK or compiler issue:

```bash
  darwin-aarch64)
    if [[ "$(uname -s)" != "Darwin" ]]; then
      echo "darwin-aarch64 must be built on macOS" >&2
      exit 1
    fi
    CC="$(xcrun --find clang)" CGO_ENABLED=1 GOOS=darwin GOARCH=arm64 go build -buildmode=c-shared -o "$OUTPUT_ROOT/darwin-aarch64/libbolt.dylib" "$SRC_PKG"
    test -s "$OUTPUT_ROOT/darwin-aarch64/libbolt.dylib"
    ;;
```

Then run:

```bash
bash -n build-native.sh
git add build-native.sh
git commit -m "build: configure macos arm64 native build"
git push
```

- [ ] **Step 3: Confirm final package artifact**

Expected CI result:

- Native Linux job uploaded `native-artifacts/linux-x86-64/libbolt.so`.
- Native Windows job uploaded `native-artifacts/win32-x86-64/bolt.dll`.
- Native macOS job uploaded both `native-artifacts/darwin-x86-64/libbolt.dylib` and `native-artifacts/darwin-aarch64/libbolt.dylib`.
- Package job uploaded `bbolt-jetbrains-plugin-ci`.

## Self-Review

Spec coverage:

- Four required native resources are covered by Tasks 1, 3, 4, and 5.
- Real-platform native builds are covered by Tasks 3 and 4.
- Zip verification is covered by Task 2 and used in Tasks 3, 4, and 5.
- Local Windows verification is covered by Task 5.

No placeholders are intentionally left. Follow-up patches are scoped to observed CI failures only.
