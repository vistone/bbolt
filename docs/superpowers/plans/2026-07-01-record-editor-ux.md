# Record Editor UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the unclear key-value edit flow with a database-client-style Record Editor and a single Add Key-Value dialog.

**Architecture:** Keep bbolt/jammdb data operations behind the existing `DatabaseConnection` API. Concentrate UI changes in `BoltViewerPanel`, adding small inner dialog/helper classes and state fields for the selected record editor baseline. Preserve existing table/tree/search/pagination behavior.

**Tech Stack:** Java 17, Swing/JetBrains UI components, IntelliJ Platform `DialogWrapper`, JUnit 5, Gradle IntelliJ plugin.

---

### Task 1: Record Editor State Tests

**Files:**
- Test: `jetbrains-plugin/src/test/java/com/protonail/bolt/intellij/ui/RecordEditorStateTest.java`
- Modify: `jetbrains-plugin/src/main/java/com/protonail/bolt/intellij/ui/BoltViewerPanel.java`

- [ ] **Step 1: Write failing tests for dirty state and action enablement**

Create `RecordEditorStateTest` with a small package-visible test target API:

```java
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
```

- [ ] **Step 2: Run the new test and verify RED**

Run:

```powershell
$env:JAVA_HOME = 'C:\Users\stone\AppData\Local\Temp\codex-tools\jdk17\jdk-17.0.19+10'
$env:Path = "C:\msys64\mingw64\bin;C:\msys64\usr\bin;C:\Program Files\Git\usr\bin;$env:JAVA_HOME\bin;$env:Path"
$bashExe = 'C:\msys64\usr\bin\bash.exe'
Push-Location jetbrains-plugin
try { & $bashExe ./gradlew test --tests com.protonail.bolt.intellij.ui.RecordEditorStateTest }
finally { Pop-Location }
```

Expected: compilation fails because `BoltViewerPanel.RecordEditorState` does not exist.

- [ ] **Step 3: Add minimal `RecordEditorState`**

Add a static package-visible inner class to `BoltViewerPanel`:

```java
static final class RecordEditorState {
    private String key = "";
    private String loadedValue = "";
    private boolean hasRecord = false;
    private boolean editable = false;

    void clear() {
        key = "";
        loadedValue = "";
        hasRecord = false;
        editable = false;
    }

    void load(String key, String loadedValue, boolean editable) {
        this.key = key == null ? "" : key;
        this.loadedValue = loadedValue == null ? "" : loadedValue;
        this.hasRecord = true;
        this.editable = editable;
    }

    String key() { return key; }
    String loadedValue() { return loadedValue; }
    boolean hasRecord() { return hasRecord; }
    boolean editable() { return hasRecord && editable; }
    boolean isDirty(String currentValue) { return hasRecord && !loadedValue.equals(currentValue == null ? "" : currentValue); }
    boolean canApply(String currentValue) { return editable() && isDirty(currentValue); }
    boolean canRevert(String currentValue) { return hasRecord && isDirty(currentValue); }
    boolean canCopy() { return hasRecord; }
    boolean canDelete() { return editable(); }
}
```

- [ ] **Step 4: Run the test and verify GREEN**

Run the same Gradle command. Expected: `RecordEditorStateTest` passes.

### Task 2: Add Key-Value Dialog Tests

**Files:**
- Test: `jetbrains-plugin/src/test/java/com/protonail/bolt/intellij/ui/AddKeyValueDialogModelTest.java`
- Modify: `jetbrains-plugin/src/main/java/com/protonail/bolt/intellij/ui/BoltViewerPanel.java`

- [ ] **Step 1: Write failing validation tests**

Create `AddKeyValueDialogModelTest`:

```java
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
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
Push-Location jetbrains-plugin
try { & $bashExe ./gradlew test --tests com.protonail.bolt.intellij.ui.AddKeyValueDialogModelTest }
finally { Pop-Location }
```

Expected: compilation fails because `AddKeyValueDialogModel` does not exist.

- [ ] **Step 3: Add the model**

Add a static package-visible inner class to `BoltViewerPanel`:

```java
static final class AddKeyValueDialogModel {
    boolean isValidKey(String key) {
        return key != null && !key.trim().isEmpty();
    }

    String normalizeValue(String value) {
        return value == null ? "" : value;
    }
}
```

- [ ] **Step 4: Run the test and verify GREEN**

Expected: `AddKeyValueDialogModelTest` passes.

### Task 3: Record Editor UI

**Files:**
- Modify: `jetbrains-plugin/src/main/java/com/protonail/bolt/intellij/ui/BoltViewerPanel.java`

- [ ] **Step 1: Replace detail editor fields with Record Editor fields**

Add fields:

```java
private JBLabel recordTitleLabel;
private JBLabel recordMetaLabel;
private JButton copyKeyBtn;
private JButton copyValueBtn;
private JButton deleteRecordBtn;
private final RecordEditorState recordEditorState = new RecordEditorState();
```

Keep existing `detailValueArea`, `applyValueBtn`, `revertValueBtn`, and `detailEntry` for minimal disruption.

- [ ] **Step 2: Replace `createDetailEditor()` header**

Change the header to show title, metadata, and actions:

```java
recordTitleLabel = new JBLabel("Record Editor");
recordMetaLabel = new JBLabel("No record selected");
recordMetaLabel.setForeground(UIUtil.getContextHelpForeground());

copyKeyBtn = new JButton("Copy Key", AllIcons.Actions.Copy);
copyValueBtn = new JButton("Copy Value", AllIcons.Actions.Copy);
deleteRecordBtn = new JButton("Delete", AllIcons.Actions.GC);
copyKeyBtn.addActionListener(e -> copySelectedColumn(true));
copyValueBtn.addActionListener(e -> copySelectedColumn(false));
deleteRecordBtn.addActionListener(e -> deleteSelectedValue());
```

The action order should be Copy Key, Copy Value, Revert, Apply, Delete.

- [ ] **Step 3: Improve value editor size**

Set:

```java
detailValueArea = new JTextArea(8, 40);
detailValueArea.setLineWrap(false);
detailValueArea.setTabSize(2);
```

### Task 4: Record Editor Behavior

**Files:**
- Modify: `jetbrains-plugin/src/main/java/com/protonail/bolt/intellij/ui/BoltViewerPanel.java`

- [ ] **Step 1: Update `updateDetailEditor()`**

When no record is selected, call `recordEditorState.clear()`, set title to `Record Editor`, metadata to `No record selected`, clear and disable value area.

When a record is selected, load the full value if available, call:

```java
recordEditorState.load(detailEntry.getKeyString(), value, currentConnection != null && currentConnection.supportsEditing());
```

Set the title to `Key: <key>`, metadata to include bytes and editable/read-only state, populate the value area, and update buttons.

- [ ] **Step 2: Update `updateDetailButtons()`**

Use `recordEditorState`:

```java
String currentText = detailValueArea == null ? "" : detailValueArea.getText();
applyValueBtn.setEnabled(recordEditorState.canApply(currentText));
revertValueBtn.setEnabled(recordEditorState.canRevert(currentText));
copyKeyBtn.setEnabled(recordEditorState.canCopy());
copyValueBtn.setEnabled(recordEditorState.canCopy());
deleteRecordBtn.setEnabled(recordEditorState.canDelete());
```

Set `recordMetaLabel` to append `Modified` when dirty.

- [ ] **Step 3: Update `applyDetailValue()`**

Keep the user's typed value on failure. On success, update the baseline to the applied value before refreshing:

```java
String newValue = detailValueArea.getText();
runEdit("Updated key: " + key, () -> currentConnection.putValue(new ArrayList<>(currentBucketPath), detailEntry.key, bytes(newValue)));
```

After refresh, selection can reload from table as before.

- [ ] **Step 4: Update `editSelectedValue()`**

Remove the small value input dialog. If a row is selected, select and focus `detailValueArea`:

```java
detailValueArea.requestFocusInWindow();
detailValueArea.selectAll();
statusLabel.setText("Editing key: " + entry.getKeyString());
```

### Task 5: Add Key-Value Dialog

**Files:**
- Modify: `jetbrains-plugin/src/main/java/com/protonail/bolt/intellij/ui/BoltViewerPanel.java`

- [ ] **Step 1: Add `AddKeyValueDialog` inner class**

Use `DialogWrapper` with `JBTextField keyField` and `JTextArea valueArea`. `doValidate()` returns `new ValidationInfo("Key is required", keyField)` when the key is blank. Expose `getKeyText()` and `getValueText()`.

- [ ] **Step 2: Update `addKeyValue()`**

Replace sequential `Messages.showInputDialog` calls with:

```java
AddKeyValueDialog dialog = new AddKeyValueDialog(project);
if (!dialog.showAndGet()) return;
String key = dialog.getKeyText();
String value = dialog.getValueText();
runEdit("Added key: " + key, () -> currentConnection.putValue(new ArrayList<>(currentBucketPath), bytes(key), bytes(value)));
```

### Task 6: Version, Verification, Release

**Files:**
- Modify: `jetbrains-plugin/build.gradle.kts`
- Modify: `jetbrains-plugin/src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Bump version**

Change `version = "1.0.20"` to `version = "1.0.21"`.

- [ ] **Step 2: Add change note**

Add a `1.0.21` note describing the Record Editor UX.

- [ ] **Step 3: Run full local verification**

Run:

```powershell
$native = (Resolve-Path rust\jammdb-jna\target\release\jammdb_jna.dll).Path.Replace('\','/')
Push-Location jetbrains-plugin
try { & $bashExe ./gradlew "-Dbbolt.jammdb.native.path=$native" clean test buildPlugin }
finally { Pop-Location }
```

- [ ] **Step 4: Commit, push, PR, CI, merge, tag**

Commit implementation, push `codex/record-editor-ux`, create PR to `master`, wait for CI, merge after green, wait for `master` CI, create annotated tag `v1.0.21`, and wait for Release workflow.
