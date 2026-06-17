package com.protonail.bolt.intellij.file;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.protonail.bolt.intellij.jammdb.JammdbReader;
import com.protonail.bolt.intellij.ui.BoltToolWindowFactory;
import com.protonail.bolt.intellij.ui.BoltViewerPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;

/**
 * When the user double-clicks a database file in the project view, opens it in the
 * bbolt Browser tool window. A lightweight placeholder editor is shown in the
 * editor tab so the platform has something to display.
 *
 * <p>Detection is by magic number, so any file extension works. Non-database files
 * fall through to the default editor.</p>
 */
public final class BoltDatabaseFileEditorProvider implements FileEditorProvider, DumbAware {

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        if (file.isDirectory() || !file.isInLocalFileSystem()) {
            return false;
        }
        try {
            return JammdbReader.isJammdbDatabase(Path.of(file.getPath()))
                    || isBboltDatabase(file);
        } catch (Exception e) {
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
        } catch (Exception e) {
            return false;
        }
    }

    @NotNull
    @Override
    public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        // Open the database in the tool window.
        BoltViewerPanel panel = BoltToolWindowFactory.getViewerPanel(project);
        if (panel != null) {
            BoltToolWindowFactory.activateToolWindow(project);
            panel.openDatabase(file.getPath());
        }
        return new DatabasePlaceholderEditor(file);
    }

    @NotNull
    @Override
    public String getEditorTypeId() {
        return "bbolt-database-editor";
    }

    @NotNull
    @Override
    public FileEditorPolicy getPolicy() {
        // Hide the default text editor — the database is binary and not meant to be
        // viewed as text. The tool window is the real UI.
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
    }

    @Override
    public void disposeEditor(@NotNull FileEditor editor) {
        Disposer.dispose(editor);
    }

    /**
     * Minimal placeholder editor. The actual browsing happens in the tool window;
     * this component just shows a hint pointing the user there.
     */
    private static final class DatabasePlaceholderEditor extends UserDataHolderBase implements FileEditor {
        private final VirtualFile file;
        private final JComponent component;

        DatabasePlaceholderEditor(@NotNull VirtualFile file) {
            this.file = file;
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBackground(JBColor.PanelBackground);
            JLabel label = new JLabel(
                    "<html><body style='text-align:center;'>"
                            + "Database <b>" + file.getName() + "</b> is open in the "
                            + "<b>bbolt Browser</b> tool window.<br>"
                            + "Use the tool window to browse buckets and key-value pairs."
                            + "</body></html>");
            label.setBorder(JBUI.Borders.empty(20));
            panel.add(label);
            this.component = panel;
        }

        @NotNull
        @Override
        public JComponent getComponent() {
            return component;
        }

        @Nullable
        @Override
        public JComponent getPreferredFocusedComponent() {
            return component;
        }

        @NotNull
        @Override
        public String getName() {
            return "bbolt Browser";
        }

        @Override
        public void setState(@NotNull FileEditorState state) {
        }

        @Override
        public boolean isModified() {
            return false;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public void selectNotify() {
        }

        @Override
        public void deselectNotify() {
        }

        @Override
        public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
        }

        @Override
        public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
        }

        @Nullable
        @Override
        public FileEditorLocation getCurrentLocation() {
            return null;
        }

        @Override
        public void dispose() {
        }

        @Nullable
        @Override
        public VirtualFile getFile() {
            return file;
        }
    }
}
