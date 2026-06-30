package com.protonail.bolt.intellij.jammdb;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.sun.jna.Native;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class JammdbNativeLoader {
    private static final Logger LOG = Logger.getInstance(JammdbNativeLoader.class);

    private JammdbNativeLoader() {}

    static JammdbNative load() {
        prepareJna();
        String overridePath = System.getProperty("bbolt.jammdb.native.path");
        if (overridePath != null && !overridePath.isBlank()) {
            LOG.info("Loading jammdb native bridge from override path: " + overridePath);
            return Native.load(overridePath, JammdbNative.class);
        }

        try {
            Path library = extractBundledLibrary();
            LOG.info("Loading bundled jammdb native bridge: " + library);
            return Native.load(library.toAbsolutePath().toString(), JammdbNative.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load jammdb native bridge", e);
        }
    }

    private static void prepareJna() {
        System.clearProperty("jna.boot.library.path");
        System.clearProperty("jna.noclasspath");
        System.setProperty("jna.nosys", "true");
    }

    private static Path extractBundledLibrary() throws IOException {
        String platform = getPlatformResourcePath();
        String libraryName = getPlatformLibraryName();
        String resource = "/native/" + platform + "/" + libraryName;

        try (InputStream is = JammdbNativeLoader.class.getResourceAsStream(resource)) {
            if (is == null) {
                throw new IOException("Native jammdb bridge not found in plugin resources: " + resource);
            }

            Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "bbolt-jammdb-native");
            Files.createDirectories(tempDir);

            Path tempLib = tempDir.resolve(libraryName);
            Files.copy(is, tempLib, StandardCopyOption.REPLACE_EXISTING);
            if (!SystemInfo.isWindows) {
                tempLib.toFile().setExecutable(true);
            }
            return tempLib;
        }
    }

    private static String getPlatformResourcePath() {
        if (SystemInfo.isMac) {
            if ("aarch64".equals(SystemInfo.OS_ARCH) || "arm64".equals(SystemInfo.OS_ARCH)) {
                return "darwin-aarch64";
            }
            return "darwin-x86-64";
        }
        if (SystemInfo.isWindows) {
            return "win32-x86-64";
        }
        if (SystemInfo.isLinux) {
            return "linux-x86-64";
        }
        throw new UnsupportedOperationException("Unsupported platform: " + SystemInfo.OS_NAME + " " + SystemInfo.OS_ARCH);
    }

    private static String getPlatformLibraryName() {
        if (SystemInfo.isMac) return "libjammdb_jna.dylib";
        if (SystemInfo.isWindows) return "jammdb_jna.dll";
        return "libjammdb_jna.so";
    }
}
