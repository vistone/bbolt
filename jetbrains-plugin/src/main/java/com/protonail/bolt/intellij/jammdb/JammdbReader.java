package com.protonail.bolt.intellij.jammdb;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Reader for jammdb (Rust) database files.
 * jammdb uses a different file format from bbolt (Go):
 *   - Magic: 0x00ABCDEF (bbolt uses 0xED0CDAED)
 *   - Version: 1 (bbolt uses 2)
 *   - Different page and meta structures
 *
 * This reader only supports read operations (no writes).
 * It directly parses the file using Java NIO, no native libraries required.
 */
public class JammdbReader implements AutoCloseable {
    private static final int JAMMDB_MAGIC = 0x00ABCDEF;
    private static final int JAMMDB_VERSION = 1;

    // Page types
    private static final byte TYPE_BRANCH = 0x01;
    private static final byte TYPE_LEAF = 0x02;
    private static final byte TYPE_META = 0x03;
    private static final byte TYPE_FREELIST = 0x04;

    // Node types (for leaf elements)
    private static final byte NODE_TYPE_DATA = 0x00;
    private static final byte NODE_TYPE_BUCKET = 0x01;

    // Page header size: id(8) + page_type(1) + padding(7) + count(8) + overflow(8) + ptr(8) = 40
    private static final int PAGE_HEADER_SIZE = 32;
    // Meta size: 72 bytes (starts at offset 32 in page, overlapping ptr field)
    private static final int META_OFFSET_IN_PAGE = 32;
    // LeafElement size: node_type(1) + padding(7) + pos(8) + key_size(8) + value_size(8) = 32
    private static final int LEAF_ELEMENT_SIZE = 32;
    // BranchElement size: page(8) + key_size(8) + pos(8) = 24
    private static final int BRANCH_ELEMENT_SIZE = 24;
    // BucketMeta size: root_page(8) + next_int(8) = 16
    private static final int BUCKET_META_SIZE = 16;

    /**
     * Maximum number of bytes of a value to load into memory at once.
     * Values larger than this are truncated when previewed; the full value
     * can be fetched on demand via {@link #getValue(List, byte[])}.
     */
    private static final int VALUE_PREVIEW_LIMIT = 64 * 1024; // 64 KB

    /**
     * Default page size for bucket entry pagination.
     */
    private static final int DEFAULT_PAGE_SIZE = 500;

    private final FileChannel channel;
    private final long pageSize;
    private final long rootBucketPage;

    public JammdbReader(Path dbPath) throws IOException {
        this.channel = FileChannel.open(dbPath, StandardOpenOption.READ);
        // First read meta with a default page size to determine actual page size
        Meta meta = readMetaWithDefaultPageSize();
        this.pageSize = meta.pageSize;
        this.rootBucketPage = meta.rootBucketPage;
    }

    private Meta readMetaWithDefaultPageSize() throws IOException {
        // jammdb default page size is 4096; read enough bytes to parse meta
        int defaultPageSize = 4096;
        ByteBuffer page0 = readPageRaw(0, defaultPageSize);
        Meta meta0 = parseMeta(page0);

        ByteBuffer page1 = readPageRaw(1, defaultPageSize);
        Meta meta1 = parseMeta(page1);

        boolean valid0 = meta0 != null;
        boolean valid1 = meta1 != null;

        if (valid0 && valid1) {
            return meta0.txId >= meta1.txId ? meta0 : meta1;
        } else if (valid0) {
            return meta0;
        } else if (valid1) {
            return meta1;
        } else {
            throw new IOException("No valid meta page found (not a jammdb database?)");
        }
    }

    private ByteBuffer readPageRaw(long pageId, int pageSize) throws IOException {
        long position = pageId * pageSize;
        ByteBuffer buf = ByteBuffer.allocate(pageSize);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        channel.read(buf, position);
        buf.flip();
        return buf;
    }

    private Meta parseMeta(ByteBuffer pageBuf) {
        // Meta starts at offset 32 (the ptr field position)
        int pos = pageBuf.position();
        pageBuf.position(pos + META_OFFSET_IN_PAGE);

        // Read meta fields (all in little-endian)
        long metaPage = pageBuf.getInt() & 0xFFFFFFFFL;
        long magic = pageBuf.getInt() & 0xFFFFFFFFL;
        long version = pageBuf.getInt() & 0xFFFFFFFFL;
        // 4 bytes padding
        pageBuf.getInt();
        long pageSize = pageBuf.getLong();
        long rootPage = pageBuf.getLong();
        long nextInt = pageBuf.getLong();
        long numPages = pageBuf.getLong();
        long freelistPage = pageBuf.getLong();
        long txId = pageBuf.getLong();
        long hash = pageBuf.getLong();

        // Validate
        if (magic != JAMMDB_MAGIC) {
            return null;
        }
        if (version != JAMMDB_VERSION) {
            return null;
        }

        Meta meta = new Meta();
        meta.metaPage = metaPage;
        meta.magic = magic;
        meta.version = version;
        meta.pageSize = pageSize;
        meta.rootBucketPage = rootPage;
        meta.nextInt = nextInt;
        meta.numPages = numPages;
        meta.freelistPage = freelistPage;
        meta.txId = txId;
        meta.hash = hash;
        return meta;
    }

    private ByteBuffer readPage(long pageId) throws IOException {
        long position = pageId * pageSize;
        // First read the header to check for overflow pages
        ByteBuffer headerBuf = ByteBuffer.allocate(PAGE_HEADER_SIZE);
        headerBuf.order(ByteOrder.LITTLE_ENDIAN);
        channel.read(headerBuf, position);
        headerBuf.flip();
        // overflow field is at offset 24 in the page header
        long overflow = headerBuf.getLong(24);
        int totalPages = (int) (overflow + 1);
        int totalSize = totalPages * (int) pageSize;

        ByteBuffer buf = ByteBuffer.allocate(totalSize);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        channel.read(buf, position);
        buf.flip();
        return buf;
    }

    /**
     * Lists all top-level buckets in the database.
     * @return list of bucket names (as byte arrays)
     */
    public List<byte[]> listRootBuckets() throws IOException {
        List<byte[]> buckets = new ArrayList<>();
        collectBuckets(rootBucketPage, buckets);
        return buckets;
    }

    /**
     * Recursively collects bucket names from a page (handling B+tree branch pages).
     */
    private void collectBuckets(long pageId, List<byte[]> buckets) throws IOException {
        ByteBuffer page = readPage(pageId);
        byte pageType = page.get(8); // page_type at offset 8

        if (pageType == TYPE_LEAF) {
            long count = page.getLong(16);
            for (int i = 0; i < count; i++) {
                LeafElement elem = readLeafElement(page, i);
                if (elem.nodeType == NODE_TYPE_BUCKET) {
                    buckets.add(elem.key);
                }
            }
        } else if (pageType == TYPE_BRANCH) {
            long count = page.getLong(16);
            for (int i = 0; i < count; i++) {
                BranchElement elem = readBranchElement(page, i);
                collectBuckets(elem.childPage, buckets);
            }
        }
    }

    /**
     * Gets the root page ID of a bucket by name.
     * @param bucketName the bucket name (as bytes)
     * @return the root page ID, or -1 if not found
     */
    public long getBucketRootPage(byte[] bucketName) throws IOException {
        return findBucketRootPage(rootBucketPage, bucketName);
    }

    private long findBucketRootPage(long pageId, byte[] bucketName) throws IOException {
        ByteBuffer page = readPage(pageId);
        byte pageType = page.get(8);

        if (pageType == TYPE_LEAF) {
            long count = page.getLong(16);
            for (int i = 0; i < count; i++) {
                LeafElement elem = readLeafElement(page, i);
                if (elem.nodeType == NODE_TYPE_BUCKET && java.util.Arrays.equals(elem.key, bucketName)) {
                    // value is BucketMeta: root_page(8) + next_int(8)
                    ByteBuffer metaBuf = ByteBuffer.wrap(elem.value);
                    metaBuf.order(ByteOrder.LITTLE_ENDIAN);
                    return metaBuf.getLong();
                }
            }
        } else if (pageType == TYPE_BRANCH) {
            long count = page.getLong(16);
            for (int i = 0; i < count; i++) {
                BranchElement elem = readBranchElement(page, i);
                long result = findBucketRootPage(elem.childPage, bucketName);
                if (result != -1) {
                    return result;
                }
            }
        }
        return -1;
    }

    /**
     * Lists all entries (key-value pairs and sub-buckets) in a bucket.
     * <p><b>Warning:</b> this loads all entries into memory. For large buckets,
     * use {@link #listBucketEntries(List, long, int)} instead.</p>
     *
     * @param bucketPath list of bucket names from root to the target bucket (empty list = root)
     * @return list of entries
     */
    public List<BucketEntry> listBucketEntries(List<byte[]> bucketPath) throws IOException {
        List<BucketEntry> entries = new ArrayList<>();
        long pageId = navigateToBucket(bucketPath);
        collectEntries(pageId, entries);
        return entries;
    }

    /**
     * Lists a page of entries from a bucket, supporting pagination to avoid loading
     * the entire bucket into memory.
     *
     * @param bucketPath list of bucket names from root to the target bucket (empty list = root)
     * @param offset     zero-based index of the first entry to return (skips earlier entries)
     * @param limit      maximum number of entries to return (use {@code <= 0} for {@link #DEFAULT_PAGE_SIZE})
     * @return a page of entries; never {@code null}
     * @throws IOException if the bucket cannot be read
     */
    public BucketEntryPage listBucketEntries(List<byte[]> bucketPath, long offset, int limit) throws IOException {
        if (limit <= 0) limit = DEFAULT_PAGE_SIZE;
        long pageId = navigateToBucket(bucketPath);
        List<BucketEntry> entries = new ArrayList<>(Math.min(limit, 256));
        long[] totalHolder = new long[]{0};
        collectEntriesPaged(pageId, entries, offset, limit, totalHolder);
        return new BucketEntryPage(entries, totalHolder[0], offset, limit);
    }

    /**
     * Counts the total number of entries (KV pairs + sub-buckets) in a bucket.
     *
     * @param bucketPath list of bucket names from root to the target bucket
     * @return the total entry count
     * @throws IOException if the bucket cannot be read
     */
    public long countBucketEntries(List<byte[]> bucketPath) throws IOException {
        long pageId = navigateToBucket(bucketPath);
        return countEntries(pageId);
    }

    /**
     * Fetches the full value of a specific key within a bucket. Use this for lazy
     * loading of large values that were truncated in preview mode.
     *
     * @param bucketPath list of bucket names from root to the target bucket
     * @param key        the key whose value to fetch
     * @return the full value bytes, or {@code null} if the key is not found / is a sub-bucket
     * @throws IOException if the bucket cannot be read
     */
    public byte[] getValue(List<byte[]> bucketPath, byte[] key) throws IOException {
        long pageId = navigateToBucket(bucketPath);
        return findValue(pageId, key);
    }

    private long navigateToBucket(List<byte[]> bucketPath) throws IOException {
        long pageId = rootBucketPage;
        for (byte[] name : bucketPath) {
            pageId = findBucketRootPage(pageId, name);
            if (pageId == -1) {
                throw new IOException("Bucket not found: " + new String(name));
            }
        }
        return pageId;
    }

    private void collectEntries(long pageId, List<BucketEntry> entries) throws IOException {
        ByteBuffer page = readPage(pageId);
        byte pageType = page.get(8);

        if (pageType == TYPE_LEAF) {
            long count = page.getLong(16);
            for (int i = 0; i < count; i++) {
                LeafElement elem = readLeafElement(page, i);
                entries.add(toBucketEntry(elem));
            }
        } else if (pageType == TYPE_BRANCH) {
            long count = page.getLong(16);
            for (int i = 0; i < count; i++) {
                BranchElement elem = readBranchElement(page, i);
                collectEntries(elem.childPage, entries);
            }
        }
    }

    /**
     * Collects entries with pagination support. Traverses the B+tree in order,
     * skipping {@code offset} entries and collecting at most {@code limit} entries.
     * The total entry count is stored in {@code totalHolder[0]}.
     */
    private void collectEntriesPaged(long pageId, List<BucketEntry> entries,
                                     long offset, int limit, long[] totalHolder) throws IOException {
        // Use a mutable holder to track skip/collect state across recursive calls
        long[] state = new long[]{offset, limit, 0}; // [remainingSkip, remainingLimit, collected]
        collectEntriesPagedImpl(pageId, entries, state, totalHolder);
    }

    private void collectEntriesPagedImpl(long pageId, List<BucketEntry> entries,
                                         long[] state, long[] totalHolder) throws IOException {
        ByteBuffer page = readPage(pageId);
        byte pageType = page.get(8);

        if (pageType == TYPE_LEAF) {
            long count = page.getLong(16);
            for (int i = 0; i < count; i++) {
                totalHolder[0]++;
                if (state[1] <= 0) continue; // already collected enough, just count
                if (state[0] > 0) { state[0]--; continue; } // still skipping
                LeafElement elem = readLeafElement(page, i);
                entries.add(toBucketEntry(elem));
                state[1]--;
            }
        } else if (pageType == TYPE_BRANCH) {
            long count = page.getLong(16);
            for (int i = 0; i < count; i++) {
                if (state[1] <= 0) {
                    // Already collected enough; still need to count remaining entries
                    // in this subtree. We can read the count from the child page header
                    // without reading all elements.
                    BranchElement elem = readBranchElement(page, i);
                    totalHolder[0] += countSubtreeEntries(elem.childPage);
                } else {
                    BranchElement elem = readBranchElement(page, i);
                    collectEntriesPagedImpl(elem.childPage, entries, state, totalHolder);
                }
            }
        }
    }

    /**
     * Counts entries in a subtree by reading page headers only (no element data).
     * For leaf pages, reads the count field. For branch pages, sums children.
     */
    private long countSubtreeEntries(long pageId) throws IOException {
        ByteBuffer page = readPage(pageId);
        byte pageType = page.get(8);
        if (pageType == TYPE_LEAF) {
            return page.getLong(16);
        } else if (pageType == TYPE_BRANCH) {
            long count = page.getLong(16);
            long total = 0;
            for (int i = 0; i < count; i++) {
                BranchElement elem = readBranchElement(page, i);
                total += countSubtreeEntries(elem.childPage);
            }
            return total;
        }
        return 0;
    }

    private long countEntries(long pageId) throws IOException {
        return countSubtreeEntries(pageId);
    }

    /**
     * Finds the full value for a specific key by traversing the B+tree.
     * Returns null for sub-bucket keys or if the key is not found.
     */
    private byte[] findValue(long pageId, byte[] key) throws IOException {
        ByteBuffer page = readPage(pageId);
        byte pageType = page.get(8);

        if (pageType == TYPE_LEAF) {
            long count = page.getLong(16);
            for (int i = 0; i < count; i++) {
                LeafElement elem = readLeafElement(page, i);
                if (java.util.Arrays.equals(elem.key, key) && elem.nodeType == NODE_TYPE_DATA) {
                    return elem.value;
                }
            }
        } else if (pageType == TYPE_BRANCH) {
            long count = page.getLong(16);
            for (int i = 0; i < count; i++) {
                BranchElement elem = readBranchElement(page, i);
                byte[] result = findValue(elem.childPage, key);
                if (result != null) return result;
            }
        }
        return null;
    }

    /**
     * Converts a LeafElement to a BucketEntry, truncating large values for preview.
     */
    private BucketEntry toBucketEntry(LeafElement elem) {
        BucketEntry entry = new BucketEntry();
        entry.key = elem.key;
        entry.isBucket = (elem.nodeType == NODE_TYPE_BUCKET);
        if (entry.isBucket) {
            ByteBuffer metaBuf = ByteBuffer.wrap(elem.value);
            metaBuf.order(ByteOrder.LITTLE_ENDIAN);
            entry.bucketRootPage = metaBuf.getLong();
            entry.value = null;
            entry.valueTruncated = false;
        } else {
            entry.value = elem.value;
            entry.bucketRootPage = -1;
            // Mark truncation: if the full value exceeds the preview limit, truncate
            if (elem.value.length > VALUE_PREVIEW_LIMIT) {
                entry.valueTruncated = true;
                entry.fullValueSize = elem.value.length;
                entry.value = new byte[VALUE_PREVIEW_LIMIT];
                System.arraycopy(elem.value, 0, entry.value, 0, VALUE_PREVIEW_LIMIT);
            } else {
                entry.valueTruncated = false;
                entry.fullValueSize = elem.value.length;
            }
        }
        return entry;
    }

    private LeafElement readLeafElement(ByteBuffer page, int index) {
        int baseOffset = PAGE_HEADER_SIZE + index * LEAF_ELEMENT_SIZE;
        int pos = page.position();

        page.position(baseOffset);
        byte nodeType = page.get();
        // 7 bytes padding
        page.position(baseOffset + 8);
        long dataPos = page.getLong();
        long keySize = page.getLong();
        long valueSize = page.getLong();

        // Calculate key and value positions
        // key address = element address + dataPos
        // element address = page_start + baseOffset
        // key position in page = baseOffset + dataPos
        int keyOffset = (int) (baseOffset + dataPos);
        int valueOffset = keyOffset + (int) keySize;
        int cap = page.capacity();

        // Bounds check: ensure key and value fit within the page buffer
        if (keyOffset < 0 || keyOffset + keySize > cap) {
            page.position(pos);
            LeafElement elem = new LeafElement();
            elem.nodeType = nodeType;
            elem.pos = dataPos;
            elem.key = new byte[0];
            elem.value = new byte[0];
            return elem;
        }
        if (valueOffset < 0 || valueOffset + valueSize > cap) {
            // Value extends beyond buffer (should not happen with overflow page support)
            page.position(pos);
            LeafElement elem = new LeafElement();
            elem.nodeType = nodeType;
            elem.pos = dataPos;
            // Read key only
            byte[] key = new byte[(int) keySize];
            page.position(keyOffset);
            page.get(key);
            elem.key = key;
            // Read as much value as available
            int availValue = Math.max(0, cap - valueOffset);
            byte[] value = new byte[availValue];
            if (availValue > 0) {
                page.position(valueOffset);
                page.get(value);
            }
            elem.value = value;
            return elem;
        }

        byte[] key = new byte[(int) keySize];
        page.position(keyOffset);
        page.get(key);

        byte[] value = new byte[(int) valueSize];
        page.position(valueOffset);
        page.get(value);

        // Restore position
        page.position(pos);

        LeafElement elem = new LeafElement();
        elem.nodeType = nodeType;
        elem.pos = dataPos;
        elem.key = key;
        elem.value = value;
        return elem;
    }

    private BranchElement readBranchElement(ByteBuffer page, int index) {
        int baseOffset = PAGE_HEADER_SIZE + index * BRANCH_ELEMENT_SIZE;
        int pos = page.position();

        page.position(baseOffset);
        long childPage = page.getLong();
        long keySize = page.getLong();
        long dataPos = page.getLong();

        // key address = element address + dataPos
        int keyOffset = (int) (baseOffset + dataPos);
        int cap = page.capacity();

        BranchElement elem = new BranchElement();
        elem.childPage = childPage;

        // Bounds check
        if (keyOffset < 0 || keyOffset + keySize > cap) {
            elem.key = new byte[0];
            page.position(pos);
            return elem;
        }

        byte[] key = new byte[(int) keySize];
        page.position(keyOffset);
        page.get(key);
        elem.key = key;

        // Restore position
        page.position(pos);

        return elem;
    }

    @Override
    public void close() throws IOException {
        if (channel != null) {
            channel.close();
        }
    }

    // Inner classes for data structures

    private static class Meta {
        long metaPage;
        long magic;
        long version;
        long pageSize;
        long rootBucketPage;
        long nextInt;
        long numPages;
        long freelistPage;
        long txId;
        long hash;
    }

    private static class LeafElement {
        byte nodeType;
        long pos;
        byte[] key;
        byte[] value;
    }

    private static class BranchElement {
        long childPage;
        byte[] key;
    }

    /**
     * Represents an entry in a bucket (either a key-value pair or a sub-bucket).
     */
    public static class BucketEntry {
        public byte[] key;
        public byte[] value;
        public boolean isBucket;
        public long bucketRootPage;
        /** True if the value was truncated for preview (exceeds {@link #VALUE_PREVIEW_LIMIT}). */
        public boolean valueTruncated;
        /** The full size of the value in bytes (before truncation). */
        public int fullValueSize;

        public String getKeyString() {
            return new String(key, java.nio.charset.StandardCharsets.UTF_8);
        }

        public String getValueString() {
            return value != null ? new String(value, java.nio.charset.StandardCharsets.UTF_8) : "";
        }
    }

    /**
     * A page of bucket entries returned by {@link #listBucketEntries(List, long, int)}.
     */
    public static class BucketEntryPage {
        private final List<BucketEntry> entries;
        private final long total;
        private final long offset;
        private final int limit;

        public BucketEntryPage(List<BucketEntry> entries, long total, long offset, int limit) {
            this.entries = entries;
            this.total = total;
            this.offset = offset;
            this.limit = limit;
        }

        public List<BucketEntry> getEntries() { return entries; }
        public long getTotal() { return total; }
        public long getOffset() { return offset; }
        public int getLimit() { return limit; }

        public int getPageCount() {
            return (int) Math.ceil((double) total / limit);
        }

        public int getCurrentPage() {
            return (int) (offset / limit) + 1;
        }

        public boolean hasNextPage() {
            return offset + entries.size() < total;
        }

        public boolean hasPrevPage() {
            return offset > 0;
        }
    }

    /**
     * Checks if a file is a jammdb database by reading the magic number.
     * @param dbPath the database file path
     * @return true if the file is a jammdb database
     */
    public static boolean isJammdbDatabase(Path dbPath) {
        try (FileChannel ch = FileChannel.open(dbPath, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(48);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            ch.read(buf, 0);
            buf.flip();
            // Skip to meta offset (32) + meta_page (4) = offset 36
            buf.position(36);
            int magic = buf.getInt();
            return magic == JAMMDB_MAGIC;
        } catch (IOException e) {
            return false;
        }
    }
}
