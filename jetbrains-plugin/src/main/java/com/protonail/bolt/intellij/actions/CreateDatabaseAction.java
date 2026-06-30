package com.protonail.bolt.intellij.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.protonail.bolt.intellij.ui.BoltViewerPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Creates a new bbolt or jammdb database file.
 */
public class CreateDatabaseAction extends AnAction implements DumbAware {
    private final BoltViewerPanel panel;

    public CreateDatabaseAction(@NotNull BoltViewerPanel panel) {
        super("New Database", "Create a new bbolt or jammdb database file", AllIcons.General.Add);
        this.panel = panel;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        panel.createDatabaseDialog();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(true);
    }
}
