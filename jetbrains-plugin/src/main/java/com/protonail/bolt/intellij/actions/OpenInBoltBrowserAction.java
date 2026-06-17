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
        try {
            byte[] header = file.contentsToByteArray();
            if (header.length < 16) return false;
            // bbolt magic: 0xED0CDAED at offset 8 (little-endian)
            return (header[8] & 0xFF) == 0xED
                    && (header[9] & 0xFF) == 0xDA
                    && (header[10] & 0xFF) == 0x0C
                    && (header[11] & 0xFF) == 0xED;
        } catch (Exception ex) {
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
