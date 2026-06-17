package com.protonail.bolt.intellij.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.protonail.bolt.intellij.jammdb.JammdbReader;
import com.protonail.bolt.intellij.ui.BoltToolWindowFactory;
import com.protonail.bolt.intellij.ui.BoltViewerPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Action shown in the editor/project-view context menu ("Open In | bbolt Browser")
 * and in the toolbar. Opens the selected database file (bbolt or jammdb, detected
 * by magic number) in the bbolt Browser tool window.
 */
public final class OpenInBoltBrowserAction extends AnAction implements DumbAware {

    private static final Logger LOG = Logger.getInstance(OpenInBoltBrowserAction.class);

    public OpenInBoltBrowserAction() {
        super("Open in bbolt Browser",
                "Open this database file in the bbolt Browser tool window",
                com.protonail.bolt.intellij.icons.BoltIcons.Database);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || file == null || file.isDirectory()) {
            return;
        }
        if (!isDatabaseFile(file)) {
            return;
        }

        try {
            BoltViewerPanel panel = ensureToolWindowPanel(project);
            if (panel != null) {
                BoltToolWindowFactory.activateToolWindow(project);
                panel.openDatabase(file.getPath());
            }
        } catch (Exception ex) {
            LOG.warn("Failed to open database via 'Open in bbolt Browser': " + file.getPath(), ex);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean enabled = file != null && !file.isDirectory() && isDatabaseFile(file);
        e.getPresentation().setEnabledAndVisible(enabled);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private static boolean isDatabaseFile(@NotNull VirtualFile file) {
        try {
            if (JammdbReader.isJammdbDatabase(Path.of(file.getPath()))) {
                return true;
            }
            return isBboltDatabase(file);
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean isBboltDatabase(@NotNull VirtualFile file) {
        // Only read the first 32 bytes via stream — never load the whole file
        // (database files can be huge, and update() is called on every right-click).
        try (java.io.InputStream in = file.getInputStream()) {
            byte[] header = new byte[32];
            int read = 0;
            while (read < header.length) {
                int n = in.read(header, read, header.length - read);
                if (n < 0) break;
                read += n;
            }
            if (read < 20) return false;
            // bbolt page header is 16 bytes (id:8 + flags:2 + count:2 + overflow:4).
            // The meta struct begins right after, so the 4-byte magic 0xED0CDAED
            // is at offset 16, written in little-endian => bytes ED DA 0C ED.
            return (header[16] & 0xFF) == 0xED
                    && (header[17] & 0xFF) == 0xDA
                    && (header[18] & 0xFF) == 0x0C
                    && (header[19] & 0xFF) == 0xED;
        } catch (Exception ex) {
            LOG.warn("bbolt detection failed for " + file.getPath() + ": " + ex.getMessage());
            return false;
        }
    }

    @Nullable
    private static BoltViewerPanel ensureToolWindowPanel(@NotNull Project project) {
        var toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                .getToolWindow(BoltToolWindowFactory.TOOL_WINDOW_ID);
        if (toolWindow == null) {
            return null;
        }
        if (toolWindow.getContentManager().getContentCount() == 0) {
            new BoltToolWindowFactory().createToolWindowContent(project, toolWindow);
        }
        return BoltToolWindowFactory.getViewerPanel(project);
    }
}
