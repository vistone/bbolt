package com.protonail.bolt.intellij.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecordEditorStateTest {
    @Test
    void dirtyStateRequiresEditableRecordAndChangedValue() {
        BoltViewerPanel.RecordEditorState state = new BoltViewerPanel.RecordEditorState();

        state.clear();
        assertFalse(state.hasRecord());
        assertFalse(state.canApply("anything"));
        assertFalse(state.canRevert("anything"));

        state.load("alpha", "one", true);
        assertTrue(state.hasRecord());
        assertEquals("alpha", state.key());
        assertFalse(state.isDirty("one"));
        assertTrue(state.isDirty("two"));
        assertFalse(state.canApply("one"));
        assertTrue(state.canApply("two"));
        assertFalse(state.canRevert("one"));
        assertTrue(state.canRevert("two"));
    }

    @Test
    void readOnlyRecordCannotApplyButCanCopyAndRevertDirtyText() {
        BoltViewerPanel.RecordEditorState state = new BoltViewerPanel.RecordEditorState();

        state.load("beta", "original", false);

        assertTrue(state.hasRecord());
        assertFalse(state.canApply("changed"));
        assertTrue(state.canCopy());
        assertFalse(state.canDelete());
        assertTrue(state.canRevert("changed"));
    }
}
