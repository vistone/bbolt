package com.protonail.bolt.intellij.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AddKeyValueDialogModelTest {
    @Test
    void rejectsBlankKeys() {
        BoltViewerPanel.AddKeyValueDialogModel model = new BoltViewerPanel.AddKeyValueDialogModel();

        assertFalse(model.isValidKey(null));
        assertFalse(model.isValidKey(""));
        assertFalse(model.isValidKey("   "));
        assertTrue(model.isValidKey("user:1"));
    }

    @Test
    void keepsMultilineValuesUnchanged() {
        BoltViewerPanel.AddKeyValueDialogModel model = new BoltViewerPanel.AddKeyValueDialogModel();

        assertEquals("line1\nline2", model.normalizeValue("line1\nline2"));
        assertEquals("", model.normalizeValue(null));
    }
}
