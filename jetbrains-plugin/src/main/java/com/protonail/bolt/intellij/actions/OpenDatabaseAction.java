package com.protonail.bolt.intellij.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.protonail.bolt.intellij.icons.BoltIcons;
import com.protonail.bolt.intellij.ui.BoltViewerPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Opens a database file dialog.
 */
public class OpenDatabaseAction extends AnAction implements DumbAware {
    private final BoltViewerPanel panel;

    public OpenDatabaseAction(@NotNull BoltViewerPanel panel) {
        super("Open Database", "Open a bbolt or jammdb database file", BoltIcons.Open);
        this.panel = panel;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        panel.openDatabaseDialog();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(true);
    }
}
