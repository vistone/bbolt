package com.protonail.bolt.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * Startup activity that initializes the native library when a project opens.
 * Replaces the deprecated ApplicationComponent.
 */
public class BoltStartupActivity implements StartupActivity.DumbAware {
    private static final Logger LOG = Logger.getInstance(BoltStartupActivity.class);

    @Override
    public void runActivity(@NotNull Project project) {
        // Load native library in background to avoid blocking IDE startup
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                BoltNativeLoader.ensureLoaded();
                LOG.info("bbolt plugin initialized");
            } catch (Throwable e) {
                LOG.warn("Failed to initialize bbolt native library on startup, will retry on first use", e);
            }
        });
    }
}
