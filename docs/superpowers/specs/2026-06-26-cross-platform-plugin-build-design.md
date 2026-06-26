# Cross-Platform Plugin Build Design

## Goal

Produce one JetBrains plugin zip that works on Windows x64, Linux x64, macOS Intel, and macOS Apple Silicon.

The final plugin package must include these native resources:

- `native/win32-x86-64/bolt.dll`
- `native/linux-x86-64/libbolt.so`
- `native/darwin-x86-64/libbolt.dylib`
- `native/darwin-aarch64/libbolt.dylib`

## Current State

The plugin loader already maps runtime platforms to the expected resource directories. Windows and Linux native libraries are currently present in `jetbrains-plugin/src/main/resources/native`, but macOS libraries are missing.

The existing local native build script can build Linux and Windows from suitable toolchains and can build macOS libraries when run on macOS. Because the native library uses Go with cgo, macOS dylibs should be produced on a macOS runner instead of being cross-compiled from Windows.

## Recommended Approach

Use GitHub Actions as the source of truth for release-grade native builds:

- `windows-latest` builds `win32-x86-64/bolt.dll`.
- `ubuntu-latest` builds `linux-x86-64/libbolt.so`.
- `macos-latest` builds both `darwin-x86-64/libbolt.dylib` and `darwin-aarch64/libbolt.dylib`.
- A final packaging job downloads all native artifacts, places them under `jetbrains-plugin/src/main/resources/native`, runs the plugin Gradle build with JDK 17, and uploads the final plugin zip.

Local Windows builds remain supported for fast development, but they are not the authoritative way to produce macOS native binaries.

## Build Components

### Native Build Script

Provide a deterministic script interface that accepts a target and output directory. The script should produce exactly one native artifact per target, except macOS where the workflow invokes it twice for x64 and arm64.

Expected targets:

- `windows-x86-64`
- `linux-x86-64`
- `darwin-x86-64`
- `darwin-aarch64`

The script should fail loudly if required tools are missing, such as Go or the MinGW compiler for Windows builds.

### CI Workflow

Add or update a workflow that:

1. Checks out the repository.
2. Sets up Go and JDK 17.
3. Builds platform-specific native artifacts in OS-specific jobs.
4. Uploads native artifacts with stable names.
5. Downloads all native artifacts in a package job.
6. Installs Maven local dependencies with tests skipped if native test prerequisites are unavailable.
7. Runs `buildPlugin`.
8. Verifies the final zip contains all four native resources.
9. Uploads the plugin distribution zip.

### Packaging Verification

Add a small verification step that inspects the plugin zip and fails if any required native resource is missing. This catches packaging regressions before release.

## Testing

Minimum verification:

- Windows local: build plugin with JDK 17 and verify the zip contains all four native resource paths.
- CI: each native build job must produce a non-empty library file.
- CI package job: `buildPlugin` must succeed.

Platform runtime verification:

- Windows can be smoke-tested locally by launching `runIde`.
- Linux and macOS runtime loading should be verified through CI or a machine on each OS when available.

## Non-Goals

- Do not add Windows ARM64 or Linux ARM64 support in this pass.
- Do not replace the Java/JNA binding architecture.
- Do not attempt unsupported Windows-to-macOS cgo cross-compilation.

## Risks

macOS arm64 cgo builds may require explicit SDK or compiler configuration on GitHub Actions. The workflow should keep the build commands simple first and only add extra compiler configuration if the runner requires it.

The existing Maven module tests fail on Windows when the native test library is unavailable in Maven test resources. Release packaging can still proceed with `-DskipTests`, but native test coverage should be revisited separately.
