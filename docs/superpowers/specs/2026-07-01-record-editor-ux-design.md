# Record Editor UX Design

## Goal

Improve the bbolt Database tool window editing experience without changing the underlying bbolt or jammdb read/write behavior. The current feature set works, but editing content is not clear enough: add and edit flows rely on small input dialogs, the detail area does not communicate record state well, and common database-client actions are split across several places.

The target experience is a database-client-style record editor: browse records in the table, select one row, then edit the selected record in a dedicated editor panel with explicit state and actions.

## Scope

In scope:

- Redesign the lower detail area into a clear Record Editor.
- Make selected key-value editing happen in the Record Editor rather than a small value input dialog.
- Replace the two-step Add Key-Value input flow with a single dialog containing key and multi-line value fields.
- Add clearer action states for Apply, Revert, Copy Key, Copy Value, and Delete.
- Keep the existing tree, table, search, pagination, and native database operations intact.
- Preserve bbolt and jammdb editing behavior through the existing `DatabaseConnection` API.

Out of scope:

- New binary/hex editing.
- JSON formatting or syntax-aware editors.
- Schema-like abstractions over key-value data.
- Changes to bbolt/jammdb native serialization.

## User Experience

The main layout remains familiar:

- Left: database and bucket tree.
- Right top: selected bucket key-value table.
- Right bottom: Record Editor.

When no row is selected, the Record Editor shows a neutral empty state and all record actions are disabled. When a key-value row is selected, the editor shows:

- Key text.
- Value byte size.
- Database format and editable/read-only status.
- Dirty state when the value differs from the loaded value.
- A larger multi-line value editor.

The primary actions are always in the Record Editor header:

- Apply: writes the current editor value.
- Revert: restores the loaded value.
- Copy Key.
- Copy Value.
- Delete.

The Edit button and double-click table behavior should focus the Record Editor value area. They should not open a small value dialog. This makes the lower editor the obvious place for content work.

## Add Record Flow

Add Key-Value opens a single dialog with:

- Key field.
- Multi-line Value field.
- OK and Cancel actions.

The dialog validates that the key is not empty. After successful save, the current bucket refreshes and the status bar reports the added key.

This replaces the current two sequential input dialogs.

## State Rules

- The value editor is enabled only when a key-value row is selected and the current connection supports editing.
- Apply is enabled only when the selected record is editable and dirty.
- Revert is enabled only when the selected record is dirty.
- Copy actions are enabled when a record is selected.
- Delete is enabled when a record is selected and editable.
- Changing row selection reloads the editor state from the selected record.
- After Apply, the editor updates its clean baseline to the saved value.

## Error Handling

Existing write failures continue through `runEdit(...)` and show an Edit Failed dialog. The Record Editor should also keep the user's attempted value visible if a write fails, so they can adjust and retry.

Loading full values can still fail. In that case, the editor falls back to the table preview and logs the failure, preserving current behavior.

## Implementation Notes

The work should remain mostly inside `BoltViewerPanel`:

- Replace `createDetailEditor()` with a Record Editor header plus value area.
- Track the loaded editor value separately from the current text to compute dirty state.
- Update `editSelectedValue()` to focus the value area instead of opening `Messages.showInputDialog`.
- Add a small `AddKeyValueDialog` using `DialogWrapper`.
- Keep `DatabaseConnection` unchanged unless tests reveal an API gap.

Because `BoltViewerPanel` is already large, helper methods and inner dialog classes should be kept cohesive and named around the Record Editor. Do not refactor unrelated UI code in this change.

## Verification

Local verification should include:

- Gradle `clean test buildPlugin`.
- Existing creation/editing tests.
- Manual source inspection that the old small value edit path is gone.
- A plugin zip check if release packaging is needed.

Release workflow should follow the existing rules:

- Increment the plugin version by one patch version.
- Commit through a feature branch and PR.
- Wait for CI across Windows, Linux, and macOS.
- Tag as `vX.Y.Z` only after `master` is green.
