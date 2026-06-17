package com.protonail.bolt.intellij;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads the native bbolt library bundled with the plugin.
 * Extracts the platform-specific library to a temp directory and loads it
 * before any JNA calls are made.
 *
 * Also ensures JNA's own native library (jnidispatch) can be loaded,
 * which is required for JNA to function under IntelliJ's plugin classloader.
 */
public final class BoltNativeLoader {
    private static final Logger LOG = Logger.getInstance(BoltNativeLoader.class);
    private static volatile boolean loaded = false;

    private BoltNativeLoader() {}

    /**
     * Loads the native library if not already loaded.
     * Thread-safe, idempotent.
     */
    public static synchronized void ensureLoaded() {
        if (loaded) return;
        try {
            // Step 1: Ensure JNA's own native library is loadable.
            // Under IntelliJ's plugin classloader, JNA may fail to extract jnidispatch.so
            // from its jar. We extract it manually and set jna.boot.library.path.
            ensureJnaNativeLibrary();

            // Step 2: Extract and load the bolt native library.
            loadBoltLibrary();

            loaded = true;
            LOG.info("bbolt native library loaded successfully");
        } catch (IOException e) {
            LOG.error("Failed to load bbolt native library", e);
            throw new RuntimeException("Failed to load bbolt native library", e);
        }
    }

    /**
     * Ensures JNA's own native library (jnidispatch) can be loaded.
     *
     * The IDE sets jna.boot.library.path to its own bundled JNA, which may
     * be too old for the current JDK (e.g., JDK 25). We work around this by:
     * 1. Clearing jna.boot.library.path so JNA doesn't use the IDE's old version
     * 2. Setting jnidispatch.path to our extracted library so JNA loads ours
     *
     * This must happen BEFORE any JNA class is initialized.
     */
    private static void ensureJnaNativeLibrary() throws IOException {
        // Determine JNA native library resource path
        String jnaResourcePath = getJnaNativeResourcePath();
        String jnaLibName = getJnaNativeLibraryName();

        // Try to load from JNA jar resources
        String resource = "/com/sun/jna/" + jnaResourcePath + "/" + jnaLibName;
        try (InputStream is = BoltNativeLoader.class.getResourceAsStream(resource)) {
            if (is != null) {
                // Extract to temp directory
                Path tempDir = Path.of(System.getProperty("java.io.tmpdir"),
                        "bbolt-jna-" + getPluginVersion());
                Files.createDirectories(tempDir);

                Path tempLib = tempDir.resolve(jnaLibName);
                Files.copy(is, tempLib, StandardCopyOption.REPLACE_EXISTING);

                if (!SystemInfo.isWindows) {
                    tempLib.toFile().setExecutable(true);
                }

                String libPath = tempLib.toAbsolutePath().toString();

                // Clear the IDE's JNA properties so JNA uses our bundled version
                // The IDE sets these JVM args pointing to its own (older) JNA:
                //   -Djna.boot.library.path=...  (path to IDE's jnidispatch.so)
                //   -Djna.nosys=true             (don't use system loadLibrary)
                //   -Djna.noclasspath=true       (don't load from classpath) <-- THIS CAUSES THE FAILURE
                String oldPath = System.clearProperty("jna.boot.library.path");
                if (oldPath != null) {
                    LOG.info("Cleared IDE's jna.boot.library.path: " + oldPath);
                }
                // Clear jna.noclasspath so JNA will load jnidispatch from our jar's classpath
                String oldNoClasspath = System.clearProperty("jna.noclasspath");
                if (oldNoClasspath != null) {
                    LOG.info("Cleared IDE's jna.noclasspath: " + oldNoClasspath);
                }
                // Clear jna.nosys to allow system loadLibrary as fallback
                System.clearProperty("jna.nosys");

                LOG.info("JNA native library (jnidispatch) prepared at: " + libPath);
            } else {
                LOG.warn("JNA native library resource not found: " + resource +
                        ". JNA will try to load from java.library.path");
            }
        }
    }

    /**
     * Extracts and loads the bolt native library.
     * Sets jna.library.path so Native.register("bolt") can find libbolt.so.
     */
    private static void loadBoltLibrary() throws IOException {
        String resourcePath = getPlatformResourcePath();
        String libraryName = getPlatformLibraryName();

        String resource = "/native/" + resourcePath + "/" + libraryName;
        try (InputStream is = BoltNativeLoader.class.getResourceAsStream(resource)) {
            if (is == null) {
                throw new IOException("Native library not found in plugin resources: " + resource +
                        ". Platform: " + SystemInfo.OS_NAME + " " + SystemInfo.OS_ARCH);
            }

            // Extract to temp directory
            Path tempDir = Path.of(System.getProperty("java.io.tmpdir"),
                    "bbolt-native-" + getPluginVersion());
            Files.createDirectories(tempDir);

            Path tempLib = tempDir.resolve(libraryName);
            Files.copy(is, tempLib, StandardCopyOption.REPLACE_EXISTING);

            // Set executable on Unix
            if (!SystemInfo.isWindows) {
                tempLib.toFile().setExecutable(true);
            }

            // Set jna.library.path so Native.register("bolt") can find libbolt.so.
            // JNA's Native.register looks up libraries by name and searches:
            //   1. jna.library.path
            //   2. java.library.path
            //   3. system paths
            // We point jna.library.path to our temp directory.
            String boltDir = tempDir.toAbsolutePath().toString();
            System.setProperty("jna.library.path", boltDir);
            LOG.info("Set jna.library.path to: " + boltDir);

            // Also preload via System.load() so the library is in JVM's loaded list
            // (helps in case JNA's lookup still fails)
            try {
                System.load(tempLib.toAbsolutePath().toString());
                LOG.info("bolt native library preloaded: " + tempLib);
            } catch (UnsatisfiedLinkError e) {
                LOG.info("bolt native library already loaded: " + e.getMessage());
            }
        }
    }

    private static String getJnaNativeResourcePath() {
        if (SystemInfo.isMac) {
            if ("aarch64".equals(SystemInfo.OS_ARCH) || "arm64".equals(SystemInfo.OS_ARCH)) {
                return "darwin-aarch64";
            }
            return "darwin-x86-64";
        } else if (SystemInfo.isWindows) {
            if ("aarch64".equals(SystemInfo.OS_ARCH) || "arm64".equals(SystemInfo.OS_ARCH)) {
                return "win32-aarch64";
            }
            return "win32-x86-64";
        } else if (SystemInfo.isLinux) {
            if ("aarch64".equals(SystemInfo.OS_ARCH) || "arm64".equals(SystemInfo.OS_ARCH)) {
                return "linux-aarch64";
            }
            return "linux-x86-64";
        }
        throw new UnsupportedOperationException("Unsupported platform: " + SystemInfo.OS_NAME + " " + SystemInfo.OS_ARCH);
    }

    private static String getJnaNativeLibraryName() {
        if (SystemInfo.isMac) {
            return "libjnidispatch.jnilib";
        } else if (SystemInfo.isWindows) {
            return "jnidispatch.dll";
        } else {
            return "libjnidispatch.so";
        }
    }

    private static String getPlatformResourcePath() {
        if (SystemInfo.isMac) {
            if ("aarch64".equals(SystemInfo.OS_ARCH) || "arm64".equals(SystemInfo.OS_ARCH)) {
                return "darwin-aarch64";
            }
            return "darwin-x86-64";
        } else if (SystemInfo.isWindows) {
            return "win32-x86-64";
        } else if (SystemInfo.isLinux) {
            return "linux-x86-64";
        }
        throw new UnsupportedOperationException("Unsupported platform: " + SystemInfo.OS_NAME + " " + SystemInfo.OS_ARCH);
    }

    private static String getPlatformLibraryName() {
        if (SystemInfo.isMac) {
            return "libbolt.dylib";
        } else if (SystemInfo.isWindows) {
            return "bolt.dll";
        } else {
            return "libbolt.so";
        }
    }

    private static String getPluginVersion() {
        try {
            return ApplicationInfo.getInstance().getBuild().asString();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
