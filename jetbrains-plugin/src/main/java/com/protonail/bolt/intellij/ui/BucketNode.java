package com.protonail.bolt.intellij.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Node data for the bucket tree.
 * Represents either a root database node or a bucket.
 * Stores the full path of bucket names from root to this bucket,
 * so nested buckets (e.g., settings/general) can be located correctly.
 */
public class BucketNode {
    private final String name;
    /** Full path of bucket names from the root bucket (exclusive) to this bucket.
     *  Empty list for the root node. */
    private final List<byte[]> pathFromRoot;
    private final boolean isRoot;
    /** The database connection this node belongs to. Null only for the very top
     *  "No database open" placeholder. */
    private final DatabaseConnection connection;

    public BucketNode(String name, List<byte[]> pathFromRoot, boolean isRoot) {
        this(name, pathFromRoot, isRoot, null);
    }

    public BucketNode(String name, List<byte[]> pathFromRoot, boolean isRoot, DatabaseConnection connection) {
        this.name = name;
        this.pathFromRoot = pathFromRoot != null
                ? Collections.unmodifiableList(new ArrayList<>(pathFromRoot))
                : Collections.emptyList();
        this.isRoot = isRoot;
        this.connection = connection;
    }

    public String getName() {
        return name;
    }

    /** Returns the full path of bucket names from root to this bucket. */
    public List<byte[]> getPathFromRoot() {
        return pathFromRoot;
    }

    public boolean isRoot() {
        return isRoot;
    }

    public DatabaseConnection getConnection() {
        return connection;
    }

    @Override
    public String toString() {
        return name;
    }
}
