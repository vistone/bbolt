package com.protonail.bolt.intellij.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.protonail.bolt.intellij.icons.BoltIcons;
import com.protonail.bolt.intellij.ui.BoltViewerPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Refreshes the current view by reloading the selected bucket.
 */
public class RefreshAction extends AnAction implements DumbAware {
    private final BoltViewerPanel panel;

    public RefreshAction(@NotNull BoltViewerPanel panel) {
        super("Refresh", "Reload the current bucket contents", BoltIcons.Refresh);
        this.panel = panel;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        panel.refresh();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(panel.hasSelectedBucket());
    }
}
