package com.protonail.bolt.intellij.icons;

import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;

/**
 * Icons used throughout the bbolt plugin.
 */
public final class BoltIcons {
    private BoltIcons() {}

    public static final Icon ToolWindow = IconLoader.getIcon("/icons/etcd.svg", BoltIcons.class);
    public static final Icon Database = IconLoader.getIcon("/icons/etcd.svg", BoltIcons.class);
    public static final Icon Bucket = IconLoader.getIcon("/icons/etcd.svg", BoltIcons.class);
    public static final Icon Key = IconLoader.getIcon("/icons/etcd.svg", BoltIcons.class);
    public static final Icon Refresh = IconLoader.getIcon("/icons/refresh.svg", BoltIcons.class);
    public static final Icon Open = IconLoader.getIcon("/icons/open.svg", BoltIcons.class);
    public static final Icon Close = IconLoader.getIcon("/icons/close.svg", BoltIcons.class);
}
