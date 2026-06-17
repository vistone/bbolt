package com.protonail.bolt.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.protonail.bolt.intellij.actions.CloseDatabaseAction;
import com.protonail.bolt.intellij.actions.OpenDatabaseAction;
import com.protonail.bolt.intellij.actions.RefreshAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Main panel for browsing bbolt/jammdb databases.
 * Uses JetBrains UI components (SimpleToolWindowPanel, JBTable, ActionToolbar)
 * for native IDE look-and-feel. Supports multiple open databases and pagination.
 */
public class BoltViewerPanel extends SimpleToolWindowPanel {
    private static final Logger LOG = Logger.getInstance(BoltViewerPanel.class);

    private final Project project;

    /** All currently open database connections, keyed by file path. */
    private final Map<String, DatabaseConnection> connections = new LinkedHashMap<>();

    private Tree bucketTree;
    private JBTable keyValueTable;
    private JBLabel statusLabel;

    // Pagination state for the currently selected bucket
    private static final int PAGE_SIZE = 500;
    private long currentOffset = 0;
    private long currentTotal = 0;
    @Nullable private DatabaseConnection currentConnection = null;
    @Nullable private List<byte[]> currentBucketPath = null;
    @Nullable private DefaultMutableTreeNode currentParentNode = null;
    private JButton prevPageBtn;
    private JButton nextPageBtn;
    private JBLabel pageLabel;

    public BoltViewerPanel(@NotNull Project project) {
        super(true, true);
        this.project = project;
        initUI();
    }

    private void initUI() {
        // --- Tree for bucket navigation ---
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("No database open");
        DefaultTreeModel treeModel = new DefaultTreeModel(root);
        bucketTree = new Tree(treeModel);
        bucketTree.setCellRenderer(new BoltTreeCellRenderer());
        bucketTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        bucketTree.setRootVisible(false);
        bucketTree.setShowsRootHandles(true);
        // Enable tree connection lines (horizontal + vertical, like classic tree views)
        bucketTree.putClientProperty("JTree.lineStyle", "Angled");
        bucketTree.addTreeSelectionListener(e -> onBucketSelected());

        JBScrollPane treeScroll = new JBScrollPane(bucketTree);

        // --- Table for key-value pairs ---
        DefaultTableModel tableModel = new DefaultTableModel(new Object[]{"Key", "Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        keyValueTable = new JBTable(tableModel);
        keyValueTable.setAutoCreateRowSorter(true);
        keyValueTable.setRowHeight(JBUI.scale(22));
        keyValueTable.getTableHeader().setReorderingAllowed(false);
        keyValueTable.getEmptyText().setText("Select a bucket to view its key-value pairs");

        JBScrollPane tableScroll = new JBScrollPane(keyValueTable);

        // --- Splitter ---
        JBSplitter splitter = new JBSplitter(false, 0.3f);
        splitter.setFirstComponent(treeScroll);
        splitter.setSecondComponent(tableScroll);
        splitter.setHonorComponentsMinimumSize(true);

        // --- Status bar (bottom-left) ---
        statusLabel = new JBLabel(" ");
        statusLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
        statusLabel.setForeground(UIUtil.getContextHelpForeground());
        statusLabel.setBorder(JBUI.Borders.empty(2, 6));

        // --- Pagination bar (bottom-center) ---
        prevPageBtn = new JButton(AllIcons.Actions.Back);
        prevPageBtn.setToolTipText("Previous page");
        prevPageBtn.setEnabled(false);
        prevPageBtn.setMargin(JBUI.emptyInsets());
        prevPageBtn.setBorder(JBUI.Borders.empty(2, 4));
        prevPageBtn.addActionListener(e -> goToPage(currentOffset - PAGE_SIZE));

        nextPageBtn = new JButton(AllIcons.Actions.Forward);
        nextPageBtn.setToolTipText("Next page");
        nextPageBtn.setEnabled(false);
        nextPageBtn.setMargin(JBUI.emptyInsets());
        nextPageBtn.setBorder(JBUI.Borders.empty(2, 4));
        nextPageBtn.addActionListener(e -> goToPage(currentOffset + PAGE_SIZE));

        pageLabel = new JBLabel(" ");
        pageLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
        pageLabel.setForeground(UIUtil.getContextHelpForeground());

        JBPanel<?> paginationBar = new JBPanel<>(new FlowLayout(FlowLayout.CENTER, 4, 0));
        paginationBar.setBorder(JBUI.Borders.empty());
        paginationBar.add(prevPageBtn);
        paginationBar.add(pageLabel);
        paginationBar.add(nextPageBtn);

        // --- Bottom panel: status (left) + pagination (center) ---
        JBPanel<?> bottomPanel = new JBPanel<>(new BorderLayout());
        bottomPanel.setBorder(JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0));
        bottomPanel.add(statusLabel, BorderLayout.WEST);
        bottomPanel.add(paginationBar, BorderLayout.CENTER);

        // --- Main content: splitter + bottom bar ---
        JPanel content = new JPanel(new BorderLayout());
        content.add(splitter, BorderLayout.CENTER);
        content.add(bottomPanel, BorderLayout.SOUTH);
        setContent(content);

        // --- Toolbar (top) ---
        setToolbar(createActionToolbar());
    }

    @NotNull
    private JComponent createActionToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new OpenDatabaseAction(this));
        group.addSeparator();
        group.add(new CloseDatabaseAction(this));
        group.add(new RefreshAction(this));

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(
                ActionPlaces.UNKNOWN, group, true);
        toolbar.setTargetComponent(this);
        return toolbar.getComponent();
    }

    /**
     * Opens a database file dialog.
     */
    public void openDatabaseDialog() {
        com.intellij.openapi.fileChooser.FileChooserDescriptor descriptor =
            new com.intellij.openapi.fileChooser.FileChooserDescriptor(true, false, false, false, false, false);
        descriptor.withTitle("Select Database File");
        descriptor.withDescription("Select a bbolt or jammdb database file (any extension)");

        com.intellij.openapi.vfs.VirtualFile file =
            com.intellij.openapi.fileChooser.FileChooser.chooseFile(descriptor, project, null);
        if (file != null) {
            openDatabase(file.getPath());
        }
    }

    /**
     * Opens a database file for browsing. Auto-detects bbolt vs jammdb format.
     * Multiple databases can be open simultaneously; each is added to the tree.
     */
    public void openDatabase(@NotNull String dbPath) {
        if (connections.containsKey(dbPath)) {
            statusLabel.setText("Already open: " + dbPath);
            return;
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                DatabaseConnection conn = DatabaseConnection.open(dbPath);
                synchronized (connections) {
                    connections.put(dbPath, conn);
                }
                SwingUtilities.invokeLater(() -> {
                    updateBucketTree();
                    statusLabel.setText("Opened (" + conn.getFormatName() + "): " + dbPath);
                });
            } catch (Exception e) {
                LOG.error("Failed to open database: " + dbPath, e);
                SwingUtilities.invokeLater(() ->
                    Messages.showErrorDialog(project, "Failed to open database: " + e.getMessage(), "Error"));
            }
        });
    }

    /**
     * Closes the database that owns the currently selected tree node.
     */
    public void closeSelectedDatabase() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) bucketTree.getLastSelectedPathComponent();
        DatabaseConnection conn = findConnectionForNode(node);
        if (conn == null) {
            Messages.showInfoMessage(project, "Select a database node first.", "Close Database");
            return;
        }
        closeDatabase(conn.getDbPath());
    }

    /**
     * Closes a single database connection by path.
     */
    public void closeDatabase(@NotNull String dbPath) {
        DatabaseConnection conn;
        synchronized (connections) {
            conn = connections.remove(dbPath);
        }
        if (conn != null) {
            try { conn.close(); } catch (Exception e) {
                LOG.warn("Error closing database " + dbPath, e);
            }
        }
        if (currentConnection == conn) {
            currentConnection = null;
            currentBucketPath = null;
            currentParentNode = null;
            currentOffset = 0;
            currentTotal = 0;
            DefaultTableModel tm = (DefaultTableModel) keyValueTable.getModel();
            tm.setRowCount(0);
            updatePaginationControls();
        }
        updateBucketTree();
        statusLabel.setText("Closed: " + dbPath);
    }

    /**
     * Closes all open database connections.
     */
    public void closeAllDatabases() {
        synchronized (connections) {
            for (DatabaseConnection conn : connections.values()) {
                try { conn.close(); } catch (Exception e) {
                    LOG.warn("Error closing database " + conn.getDbPath(), e);
                }
            }
            connections.clear();
        }
        currentConnection = null;
        currentBucketPath = null;
        currentParentNode = null;
        currentOffset = 0;
        currentTotal = 0;
        DefaultTableModel tm = (DefaultTableModel) keyValueTable.getModel();
        tm.setRowCount(0);
        updatePaginationControls();
        updateBucketTree();
    }

    /**
     * Refreshes the current view by reloading the selected bucket.
     */
    public void refresh() {
        if (currentBucketPath != null && currentConnection != null) {
            loadBucketPage();
        }
    }

    public boolean hasOpenDatabase() {
        synchronized (connections) {
            return !connections.isEmpty();
        }
    }

    /** True if a database node (not a bucket) is currently selected. */
    public boolean hasSelectedDatabase() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) bucketTree.getLastSelectedPathComponent();
        return findConnectionForNode(node) != null;
    }

    /** True if a bucket (non-root) is currently selected. */
    public boolean hasSelectedBucket() {
        return currentBucketPath != null && currentConnection != null;
    }

    /**
     * Rebuilds the bucket tree with one top-level node per open database.
     */
    private void updateBucketTree() {
        DefaultMutableTreeNode root;
        synchronized (connections) {
            if (connections.isEmpty()) {
                root = new DefaultMutableTreeNode("No database open");
                ((DefaultTreeModel) bucketTree.getModel()).setRoot(root);
                bucketTree.setRootVisible(true);
                return;
            }

            root = new DefaultMutableTreeNode("Databases");
            bucketTree.setRootVisible(false);

            for (DatabaseConnection conn : connections.values()) {
                DefaultMutableTreeNode dbNode = new DefaultMutableTreeNode(
                    new BucketNode(conn.getDisplayName(), new ArrayList<>(), true, conn));
                root.add(dbNode);

                try {
                    List<byte[]> rootBuckets = conn.listRootBuckets();
                    for (byte[] key : rootBuckets) {
                        String bucketName = new String(key, StandardCharsets.UTF_8);
                        List<byte[]> path = new ArrayList<>();
                        path.add(key);
                        DefaultMutableTreeNode bucketNode = new DefaultMutableTreeNode(
                            new BucketNode(bucketName, path, false, conn));
                        dbNode.add(bucketNode);
                    }
                } catch (Exception e) {
                    LOG.error("Failed to list root buckets for " + conn.getDbPath(), e);
                }
            }
        }

        ((DefaultTreeModel) bucketTree.getModel()).setRoot(root);
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode dbNode = (DefaultMutableTreeNode) root.getChildAt(i);
            bucketTree.expandPath(new javax.swing.tree.TreePath(dbNode.getPath()));
        }
    }

    /**
     * Called when a bucket is selected in the tree.
     */
    private void onBucketSelected() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) bucketTree.getLastSelectedPathComponent();
        if (node == null) return;

        Object userObject = node.getUserObject();
        if (!(userObject instanceof BucketNode)) return;

        BucketNode bucketNode = (BucketNode) userObject;
        if (bucketNode.isRoot()) {
            DefaultTableModel tm = (DefaultTableModel) keyValueTable.getModel();
            tm.setRowCount(0);
            currentConnection = bucketNode.getConnection();
            currentBucketPath = null;
            currentParentNode = null;
            currentOffset = 0;
            currentTotal = 0;
            updatePaginationControls();
            if (currentConnection != null) {
                statusLabel.setText("Database: " + currentConnection.getDisplayName() +
                        " (" + currentConnection.getFormatName() + ")");
            }
            return;
        }

        DatabaseConnection conn = bucketNode.getConnection();
        if (conn == null) return;

        currentOffset = 0;
        currentConnection = conn;
        currentBucketPath = bucketNode.getPathFromRoot();
        currentParentNode = node;
        loadBucketPage();
    }

    private void goToPage(long newOffset) {
        if (currentBucketPath == null || currentConnection == null) return;
        if (newOffset < 0) newOffset = 0;
        if (newOffset >= currentTotal) return;
        currentOffset = newOffset;
        loadBucketPage();
    }

    private void loadBucketPage() {
        if (currentBucketPath == null || currentConnection == null || currentParentNode == null) return;

        prevPageBtn.setEnabled(false);
        nextPageBtn.setEnabled(false);
        pageLabel.setText("Loading...");
        pageLabel.setIcon(AllIcons.Process.Step_passive);

        DefaultTableModel tableModel = (DefaultTableModel) keyValueTable.getModel();
        tableModel.setRowCount(0);
        currentParentNode.removeAllChildren();

        final DatabaseConnection conn = currentConnection;
        final List<byte[]> bucketPath = new ArrayList<>(currentBucketPath);
        final long offset = currentOffset;
        final int limit = PAGE_SIZE;

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                DatabaseConnection.EntryPage page = conn.listBucketEntries(bucketPath, offset, limit);
                SwingUtilities.invokeLater(() -> {
                    if (currentConnection != conn || currentBucketPath == null ||
                        !pathsEqual(currentBucketPath, bucketPath)) {
                        return;
                    }
                    currentTotal = page.total;
                    renderEntries(page.entries);
                    updatePaginationControls();
                });
            } catch (Exception e) {
                LOG.error("Failed to load bucket page", e);
                SwingUtilities.invokeLater(() -> {
                    if (currentConnection == conn) {
                        statusLabel.setText("Error: " + e.getMessage());
                        pageLabel.setText("Error");
                        pageLabel.setIcon(AllIcons.General.Error);
                    }
                });
            }
        });
    }

    private void renderEntries(List<DatabaseConnection.Entry> entries) {
        DefaultTableModel tableModel = (DefaultTableModel) keyValueTable.getModel();
        for (DatabaseConnection.Entry entry : entries) {
            String keyStr = entry.getKeyString();
            if (entry.isBucket) {
                List<byte[]> childPath = new ArrayList<>(currentBucketPath);
                childPath.add(entry.key);
                DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(
                    new BucketNode(keyStr, childPath, false, currentConnection));
                currentParentNode.add(childNode);
            } else {
                String valueStr = entry.getValueString();
                if (entry.valueTruncated) {
                    valueStr = valueStr + "... (" + entry.fullValueSize + " bytes, truncated)";
                }
                tableModel.addRow(new Object[]{keyStr, valueStr});
            }
        }
        ((DefaultTreeModel) bucketTree.getModel()).reload(currentParentNode);
    }

    private void updatePaginationControls() {
        int currentPage = (int) (currentOffset / PAGE_SIZE) + 1;
        int totalPages = (int) Math.ceil((double) currentTotal / PAGE_SIZE);
        if (totalPages == 0) totalPages = 1;

        pageLabel.setText("Page " + currentPage + " / " + totalPages +
                "  (" + currentTotal + " entries)");
        pageLabel.setIcon(null);
        prevPageBtn.setEnabled(currentOffset > 0);
        nextPageBtn.setEnabled(currentOffset + PAGE_SIZE < currentTotal);

        String pathDesc = currentBucketPath != null ? describePath(currentBucketPath) : "";
        long shown = currentBucketPath == null ? 0
                : Math.min(PAGE_SIZE, Math.max(0, currentTotal - currentOffset));
        if (currentBucketPath != null) {
            statusLabel.setText("Bucket: " + pathDesc +
                    " | showing " + shown + " of " + currentTotal + " key(s)");
        }
    }

    @Nullable
    private DatabaseConnection findConnectionForNode(@Nullable DefaultMutableTreeNode node) {
        while (node != null) {
            Object uo = node.getUserObject();
            if (uo instanceof BucketNode) {
                BucketNode bn = (BucketNode) uo;
                if (bn.getConnection() != null) return bn.getConnection();
            }
            node = (DefaultMutableTreeNode) node.getParent();
        }
        return null;
    }

    private static boolean pathsEqual(List<byte[]> a, List<byte[]> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!java.util.Arrays.equals(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    private static String describePath(@NotNull List<byte[]> path) {
        StringBuilder sb = new StringBuilder();
        for (byte[] name : path) {
            if (sb.length() > 0) sb.append('/');
            sb.append(new String(name, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
