package com.protonail.bolt.intellij.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Factory for creating the bbolt browser tool window.
 */
public class BoltToolWindowFactory implements com.intellij.openapi.wm.ToolWindowFactory {
    public static final String TOOL_WINDOW_ID = "bbolt Browser";

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        BoltViewerPanel panel = new BoltViewerPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, "Browser", false);
        toolWindow.getContentManager().addContent(content);
    }

    @Nullable
    public static BoltViewerPanel getViewerPanel(@NotNull Project project) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
        if (toolWindow == null) return null;
        Content content = toolWindow.getContentManager().getContent(0);
        if (content == null) return null;
        return (BoltViewerPanel) content.getComponent();
    }

    public static void activateToolWindow(@NotNull Project project) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
        if (toolWindow != null) {
            toolWindow.activate(null);
        }
    }
}
