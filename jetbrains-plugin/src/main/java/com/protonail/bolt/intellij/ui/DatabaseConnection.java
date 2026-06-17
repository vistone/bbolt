package com.protonail.bolt.intellij.ui;

import com.protonail.bolt.intellij.BoltNativeLoader;
import com.protonail.bolt.intellij.jammdb.JammdbReader;
import com.protonail.bolt.jna.Bolt;
import com.protonail.bolt.jna.BoltBucket;
import com.protonail.bolt.jna.BoltCursor;
import com.protonail.bolt.jna.BoltFileMode;
import com.protonail.bolt.jna.BoltKeyValue;
import com.protonail.bolt.jna.BoltOptions;
import com.protonail.bolt.jna.BoltTransaction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstracts a single open database connection (either bbolt via JNA or jammdb via pure Java).
 * Allows the UI to treat multiple open databases uniformly and independently.
 */
public abstract class DatabaseConnection implements AutoCloseable {

    protected final String dbPath;
    protected final String displayName;

    protected DatabaseConnection(String dbPath, String displayName) {
        this.dbPath = dbPath;
        this.displayName = displayName;
    }

    public String getDbPath() { return dbPath; }
    public String getDisplayName() { return displayName; }
    public abstract String getFormatName();

    /** Lists the names of top-level buckets in this database. */
    public abstract List<byte[]> listRootBuckets() throws Exception;

    /**
     * Lists a page of entries in a bucket.
     *
     * @param bucketPath path from root to the target bucket (empty = root)
     * @param offset     zero-based index of the first entry to return
     * @param limit      max entries to return (<=0 uses a default)
     * @return a page of entries plus the total count
     */
    public abstract EntryPage listBucketEntries(List<byte[]> bucketPath, long offset, int limit) throws Exception;

    /** Closes the underlying database handle. */
    @Override
    public abstract void close();

    /** A single entry in a bucket: either a KV pair or a sub-bucket. */
    public static class Entry {
        public final byte[] key;
        public final byte[] value;       // null for sub-buckets
        public final boolean isBucket;
        public final long bucketRootPage; // only meaningful for jammdb sub-buckets; -1 otherwise
        public final boolean valueTruncated;
        public final int fullValueSize;

        public Entry(byte[] key, byte[] value, boolean isBucket, long bucketRootPage,
                     boolean valueTruncated, int fullValueSize) {
            this.key = key;
            this.value = value;
            this.isBucket = isBucket;
            this.bucketRootPage = bucketRootPage;
            this.valueTruncated = valueTruncated;
            this.fullValueSize = fullValueSize;
        }

        public String getKeyString() {
            return new String(key, StandardCharsets.UTF_8);
        }

        public String getValueString() {
            return value != null ? new String(value, StandardCharsets.UTF_8) : "";
        }
    }

    /** A page of entries with pagination metadata. */
    public static class EntryPage {
        public final List<Entry> entries;
        public final long total;
        public final long offset;
        public final int limit;

        public EntryPage(List<Entry> entries, long total, long offset, int limit) {
            this.entries = entries;
            this.total = total;
            this.offset = offset;
            this.limit = limit;
        }
    }

    // ========================================================================
    // bbolt implementation
    // ========================================================================

    public static class BoltConnection extends DatabaseConnection {
        private final Bolt bolt;

        public BoltConnection(String dbPath) throws Exception {
            super(dbPath, extractFileName(dbPath));
            BoltNativeLoader.ensureLoaded();
            BoltOptions options = new BoltOptions(5000);
            try {
                this.bolt = new Bolt(dbPath, BoltFileMode.DEFAULT, options);
            } finally {
                options.close();
            }
        }

        @Override
        public String getFormatName() { return "bbolt"; }

        @Override
        public List<byte[]> listRootBuckets() throws Exception {
            List<byte[]> buckets = new ArrayList<>();
            bolt.view(tx -> {
                try (BoltCursor cursor = tx.createCursor()) {
                    BoltKeyValue kv = cursor.first();
                    while (kv != null) {
                        if (kv.getValue() == null) {
                            buckets.add(kv.getKey());
                        }
                        kv = cursor.next();
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to list root buckets", e);
                }
            });
            return buckets;
        }

        @Override
        public EntryPage listBucketEntries(List<byte[]> bucketPath, long offset, int limit) throws Exception {
            if (limit <= 0) limit = 500;
            final int fLimit = limit;
            final long fOffset = offset;
            List<Entry> entries = new ArrayList<>(fLimit);
            long[] totalHolder = new long[]{0};

            bolt.view(tx -> {
                BoltBucket bucket = navigateBboltBucket(tx, bucketPath);
                try (BoltCursor cursor = bucket.createCursor()) {
                    BoltKeyValue kv = cursor.first();
                    while (kv != null) {
                        totalHolder[0]++;
                        if (totalHolder[0] - 1 < fOffset) {
                            // skip
                        } else if (entries.size() < fLimit) {
                            byte[] k = kv.getKey();
                            byte[] v = kv.getValue();
                            if (v == null) {
                                entries.add(new Entry(k, null, true, -1, false, 0));
                            } else {
                                entries.add(new Entry(k, v, false, -1, false, v.length));
                            }
                        }
                        kv = cursor.next();
                    }
                } finally {
                    bucket.close();
                }
            });
            return new EntryPage(entries, totalHolder[0], offset, limit);
        }

        @Override
        public void close() {
            try { bolt.close(); } catch (Exception ignored) {}
        }

        private BoltBucket navigateBboltBucket(BoltTransaction tx, List<byte[]> path) {
            BoltBucket bucket = null;
            try {
                for (byte[] name : path) {
                    if (bucket == null) {
                        bucket = tx.getBucket(name);
                    } else {
                        BoltBucket next = bucket.getBucket(name);
                        bucket.close();
                        bucket = next;
                    }
                }
            } catch (Exception e) {
                if (bucket != null) bucket.close();
                throw new RuntimeException("Failed to navigate to bucket", e);
            }
            if (bucket == null) {
                throw new RuntimeException("Bucket not found at path");
            }
            return bucket;
        }
    }

    // ========================================================================
    // jammdb implementation
    // ========================================================================

    public static class JammdbConnection extends DatabaseConnection {
        private final JammdbReader reader;

        public JammdbConnection(String dbPath) throws IOException {
            super(dbPath, extractFileName(dbPath));
            this.reader = new JammdbReader(Path.of(dbPath));
        }

        @Override
        public String getFormatName() { return "jammdb"; }

        @Override
        public List<byte[]> listRootBuckets() throws Exception {
            return reader.listRootBuckets();
        }

        @Override
        public EntryPage listBucketEntries(List<byte[]> bucketPath, long offset, int limit) throws Exception {
            JammdbReader.BucketEntryPage page = reader.listBucketEntries(bucketPath, offset, limit);
            List<Entry> entries = new ArrayList<>(page.getEntries().size());
            for (JammdbReader.BucketEntry e : page.getEntries()) {
                entries.add(new Entry(e.key, e.value, e.isBucket, e.bucketRootPage,
                        e.valueTruncated, e.fullValueSize));
            }
            return new EntryPage(entries, page.getTotal(), page.getOffset(), page.getLimit());
        }

        @Override
        public void close() {
            try { reader.close(); } catch (Exception ignored) {}
        }
    }

    // ========================================================================
    // Factory
    // ========================================================================

    public static DatabaseConnection open(String dbPath) throws Exception {
        if (JammdbReader.isJammdbDatabase(Path.of(dbPath))) {
            return new JammdbConnection(dbPath);
        }
        return new BoltConnection(dbPath);
    }

    private static String extractFileName(String path) {
        int idx = path.lastIndexOf('/');
        if (idx >= 0) return path.substring(idx + 1);
        idx = path.lastIndexOf('\\');
        if (idx >= 0) return path.substring(idx + 1);
        return path;
    }
}
