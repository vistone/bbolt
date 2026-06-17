package com.protonail.bolt.intellij.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.protonail.bolt.intellij.icons.BoltIcons;
import com.protonail.bolt.intellij.ui.BoltViewerPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Closes the currently selected database.
 */
public class CloseDatabaseAction extends AnAction implements DumbAware {
    private final BoltViewerPanel panel;

    public CloseDatabaseAction(@NotNull BoltViewerPanel panel) {
        super("Close Database", "Close the currently selected database", BoltIcons.Close);
        this.panel = panel;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        panel.closeSelectedDatabase();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(panel.hasSelectedDatabase());
    }
}
