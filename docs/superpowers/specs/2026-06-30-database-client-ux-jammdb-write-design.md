# Database Client UX And Jammdb Write Design

## Goal

Make the plugin feel like a normal database client instead of a read-only browser with ad hoc buttons. Both bbolt and jammdb should expose the same user-facing edit and search workflows where the underlying format supports them.

## Current Problem

The bbolt path can write because the plugin delegates to the existing Go/JNA bridge, which already owns transaction, page allocation, freelist, and commit behavior. The jammdb path currently uses `JammdbReader`, a Java NIO parser that reads meta pages, B+tree pages, leaf elements, branch elements, and overflow pages. It does not implement write transactions, page allocation, freelist maintenance, copy-on-write updates, or dual-meta commit handling.

Directly adding writes to `JammdbReader` would be risky because a bug can corrupt the database file. The official Rust `jammdb` crate already provides ACID transactions, multiple readers, one writer, bucket creation/deletion, and key-value put/delete APIs. The plugin should call that crate through a small native bridge for writes.

## User Experience

The tool window should follow common database-client habits:

- Left side: database and bucket tree.
- Tree context menu: new bucket, delete bucket, refresh.
- Right side: key-value grid for the selected bucket.
- Grid context menu: add key, edit value, delete key, copy key, copy value.
- Double-click a value cell to edit it.
- Keyboard shortcuts: Insert adds a row, Delete removes selected rows, Enter edits selected value, Esc clears search when focused.
- Search stays in the data area and filters the current bucket by key or value.
- Details editor below the grid shows the selected key and a multiline value editor with Apply and Revert.
- Read/write availability is shown in status text, not discovered only after a failed click.

## Architecture

Keep the current `DatabaseConnection` abstraction. bbolt writes continue to use the Go/JNA bridge. jammdb writes will use a new Rust `cdylib` bridge and a small Java JNA wrapper, while reads continue through `JammdbReader`.

The native jammdb bridge exposes operation-level functions:

- put value in bucket path
- delete value from bucket path
- create bucket under parent path
- delete bucket under parent path

Each function opens the database, starts a writable transaction, performs one edit, commits, and returns either success or an error string. The Java side closes and reopens the `JammdbReader` after successful writes so the table/tree reflects the new file contents.

Bucket paths are encoded as UTF-8 display names joined by an internal separator for the first implementation. This matches the existing UI, which already treats bucket names as UTF-8 strings.

## Build And Packaging

Add a Rust native build step for the jammdb bridge alongside the existing Go native build. GitHub Actions must build and package these native resources for:

- Windows x86-64
- Linux x86-64
- macOS x86-64
- macOS aarch64

The final plugin zip verification must check both the existing bbolt native library and the new jammdb native bridge for all platforms.

## Testing

Use test-first coverage for:

- jammdb `supportsEditing()` becomes true.
- jammdb put updates a copied test database and `JammdbReader` can read the new value.
- jammdb delete removes a key.
- jammdb create bucket makes the bucket visible in the tree/listing.
- jammdb delete bucket removes it.
- UI command state no longer hides valid operations behind disabled or misleading buttons.

## Versioning

This is a new feature release after `1.0.17`, so the plugin version becomes `1.0.18`.
