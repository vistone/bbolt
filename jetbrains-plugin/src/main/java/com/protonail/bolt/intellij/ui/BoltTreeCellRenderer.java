package com.protonail.bolt.intellij.ui;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.protonail.bolt.intellij.icons.BoltIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

/**
 * Tree cell renderer that shows different icons and colors for database nodes,
 * bucket nodes, and key nodes. Uses JetBrains UI colors for consistent look-and-feel.
 */
public class BoltTreeCellRenderer extends DefaultTreeCellRenderer {

    @Override
    public Component getTreeCellRendererComponent(@NotNull JTree tree, Object value,
                                                  boolean sel, boolean expanded,
                                                  boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        setBorder(JBUI.Borders.empty(1, 2));

        if (value instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObject = node.getUserObject();

            if (node.isRoot()) {
                // Top-level "Databases" root
                setIcon(BoltIcons.Database);
                setTextNonSelectionColor(JBColor.GRAY);
            } else if (userObject instanceof BucketNode) {
                BucketNode bn = (BucketNode) userObject;
                if (bn.isRoot()) {
                    // Database file node - blue, bold-ish
                    setIcon(BoltIcons.Database);
                    setTextNonSelectionColor(new JBColor(0x1F618D, 0x5DADE2));
                } else {
                    // Bucket node - dark blue/teal
                    setIcon(BoltIcons.Bucket);
                    setTextNonSelectionColor(new JBColor(0x1A5276, 0x85C1E9));
                }
            } else {
                // Key node - default foreground
                setIcon(BoltIcons.Key);
                setTextNonSelectionColor(UIManager.getColor("Tree.textForeground"));
            }
        }

        return this;
    }
}
