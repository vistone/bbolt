package com.protonail.bolt.intellij.file;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.protonail.bolt.intellij.icons.BoltIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * File type for bbolt database files (.db).
 */
public final class BoltFileType extends LanguageFileType {
    public static final BoltFileType INSTANCE = new BoltFileType();

    private BoltFileType() {
        super(BoltLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "Bolt Database";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "bbolt database file";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "db";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return BoltIcons.Database;
    }
}
