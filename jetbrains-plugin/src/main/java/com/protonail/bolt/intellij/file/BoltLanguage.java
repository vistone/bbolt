package com.protonail.bolt.intellij.file;

import com.intellij.lang.Language;

/**
 * Language definition for bbolt (placeholder for file type association).
 */
public final class BoltLanguage extends Language {
    public static final BoltLanguage INSTANCE = new BoltLanguage();

    private BoltLanguage() {
        super("BBOLT");
    }
}
