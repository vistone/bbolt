package com.protonail.bolt.intellij.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.protonail.bolt.intellij.icons.BoltIcons;
import com.protonail.bolt.intellij.ui.BoltToolWindowFactory;
import com.protonail.bolt.intellij.ui.BoltViewerPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/**
 * Action to open a bbolt database file for browsing.
 */
public class OpenBoltDatabaseAction extends AnAction {
    public OpenBoltDatabaseAction() {
        super("Open bbolt Database", "Open a bbolt database file for browsing", BoltIcons.Open);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false);
        descriptor.withTitle("Select bbolt Database File");
        descriptor.withFileFilter(virtualFile -> "db".equalsIgnoreCase(virtualFile.getExtension()));

        VirtualFile file = FileChooser.chooseFile(descriptor, project, null);
        if (file == null) return;

        String path = file.getPath();
        try {
            BoltViewerPanel viewer = BoltToolWindowFactory.getViewerPanel(project);
            if (viewer != null) {
                viewer.openDatabase(path);
                BoltToolWindowFactory.activateToolWindow(project);
            }
        } catch (Exception ex) {
            Messages.showErrorDialog(project,
                "Failed to open bbolt database: " + ex.getMessage(),
                "Open bbolt Database");
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }
}
