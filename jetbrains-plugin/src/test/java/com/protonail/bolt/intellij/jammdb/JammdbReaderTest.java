package com.protonail.bolt.intellij.jammdb;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JammdbReader} focusing on large-value (overflow pages) scenarios.
 *
 * <p>These tests guard against regressions of the BufferUnderflowException that occurred
 * when reading leaf elements whose key+value data spans multiple pages (overflow pages).
 * The jammdb format stores an {@code overflow} field in the page header indicating how
 * many additional pages follow the first one; the reader must allocate a buffer covering
 * all of them before parsing leaf elements.</p>
 *
 * <p>Test databases are pre-generated with the official jammdb Rust crate and packaged
 * under {@code src/test/resources/jammdb/}:</p>
 * <ul>
 *   <li>{@code large-values.db} - contains values of 5000 and 10000 bytes that force
 *       overflow pages (page size = 4096).</li>
 *   <li>{@code sample.db} - a smaller database with nested buckets and regular-sized
 *       values, used for baseline verification.</li>
 * </ul>
 */
class JammdbReaderTest {

    private static final Path LARGE_VALUES_DB = Paths.get("src/test/resources/jammdb/large-values.db");
    private static final Path SAMPLE_DB = Paths.get("src/test/resources/jammdb/sample.db");

    private JammdbReader largeValuesReader;
    private JammdbReader sampleReader;

    @BeforeEach
    void setUp() throws IOException {
        largeValuesReader = new JammdbReader(LARGE_VALUES_DB);
        sampleReader = new JammdbReader(SAMPLE_DB);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (largeValuesReader != null) largeValuesReader.close();
        if (sampleReader != null) sampleReader.close();
    }

    // =========================================================================
    // Format detection
    // =========================================================================

    @Test
    void isJammdbDatabase_recognizesLargeValuesDatabase() {
        assertTrue(JammdbReader.isJammdbDatabase(LARGE_VALUES_DB),
                "large-values.db should be detected as a jammdb database");
    }

    @Test
    void isJammdbDatabase_recognizesSampleDatabase() {
        assertTrue(JammdbReader.isJammdbDatabase(SAMPLE_DB),
                "sample.db should be detected as a jammdb database");
    }

    // =========================================================================
    // Root bucket listing
    // =========================================================================

    @Test
    void listRootBuckets_largeValuesDatabaseReturnsExpectedBuckets() throws IOException {
        List<byte[]> buckets = largeValuesReader.listRootBuckets();
        assertNotNull(buckets);
        assertEquals(2, buckets.size(), "large-values.db should have 2 top-level buckets");

        List<String> names = bucketsToStrings(buckets);
        assertTrue(names.contains("large_bucket"), "Should contain 'large_bucket' bucket");
        assertTrue(names.contains("nested"), "Should contain 'nested' bucket");
    }

    @Test
    void listRootBuckets_sampleDatabaseReturnsExpectedBuckets() throws IOException {
        List<byte[]> buckets = sampleReader.listRootBuckets();
        assertNotNull(buckets);
        assertEquals(6, buckets.size(), "sample.db should have 6 top-level buckets");

        List<String> names = bucketsToStrings(buckets);
        assertTrue(names.contains("app_config"));
        assertTrue(names.contains("logs"));
        assertTrue(names.contains("metrics"));
        assertTrue(names.contains("products"));
        assertTrue(names.contains("settings"));
        assertTrue(names.contains("users"));
    }

    // =========================================================================
    // Overflow pages: large value reading (the core regression test)
    // =========================================================================

    /**
     * Core regression test: reading a bucket that contains values larger than a single
     * page must not throw BufferUnderflowException. Before the overflow-page fix, the
     * reader allocated only {@code pageSize} bytes and crashed when reading a value
     * whose end offset exceeded the buffer capacity.
     */
    @Test
    void listBucketEntries_largeValueDoesNotThrowBufferUnderflow() {
        List<byte[]> path = Collections.singletonList(b("large_bucket"));
        // This call used to throw java.nio.BufferUnderflowException
        assertDoesNotThrow(() -> largeValuesReader.listBucketEntries(path));
    }

    @Test
    void listBucketEntries_largeBucketContainsAllExpectedEntries() throws IOException {
        List<byte[]> path = Collections.singletonList(b("large_bucket"));
        List<JammdbReader.BucketEntry> entries = largeValuesReader.listBucketEntries(path);

        assertNotNull(entries);
        assertEquals(4, entries.size(), "large_bucket should contain 4 entries");

        JammdbReader.BucketEntry bigKey = findEntry(entries, "big_key");
        assertNotNull(bigKey, "Should find 'big_key' entry");
        assertFalse(bigKey.isBucket, "big_key should be a KV pair, not a bucket");
        assertEquals(5000, bigKey.value.length, "big_key value should be 5000 bytes");
        // Verify content integrity: value is 5000 'A' characters
        assertArrayEquals(repeat('A', 5000), bigKey.value, "big_key value content mismatch");
    }

    @Test
    void listBucketEntries_hugeValueOf10000BytesIsReadCorrectly() throws IOException {
        List<byte[]> path = Collections.singletonList(b("large_bucket"));
        List<JammdbReader.BucketEntry> entries = largeValuesReader.listBucketEntries(path);

        JammdbReader.BucketEntry hugeKey = findEntry(entries, "huge_key");
        assertNotNull(hugeKey, "Should find 'huge_key' entry");
        assertEquals(10000, hugeKey.value.length, "huge_key value should be 10000 bytes");
        assertArrayEquals(repeat('B', 10000), hugeKey.value, "huge_key value content mismatch");
    }

    @Test
    void listBucketEntries_smallValueCoexistingWithLargeValuesIsIntact() throws IOException {
        List<byte[]> path = Collections.singletonList(b("large_bucket"));
        List<JammdbReader.BucketEntry> entries = largeValuesReader.listBucketEntries(path);

        JammdbReader.BucketEntry smallKey = findEntry(entries, "small_key");
        assertNotNull(smallKey, "Should find 'small_key' entry");
        assertEquals("small_value", smallKey.getValueString(),
                "small_key value should be preserved when coexisting with large values");
    }

    @Test
    void listBucketEntries_largeValueKeyStringIsCorrect() throws IOException {
        List<byte[]> path = Collections.singletonList(b("large_bucket"));
        List<JammdbReader.BucketEntry> entries = largeValuesReader.listBucketEntries(path);

        List<String> keys = entries.stream()
                .map(JammdbReader.BucketEntry::getKeyString)
                .toList();
        assertTrue(keys.contains("big_key"));
        assertTrue(keys.contains("huge_key"));
        assertTrue(keys.contains("massive_key"));
        assertTrue(keys.contains("small_key"));
    }

    // =========================================================================
    // Overflow pages inside nested buckets
    // =========================================================================

    /**
     * Verifies overflow page handling works for nested buckets, not just top-level ones.
     * The nested bucket 'nested/sub1' contains a 5000-byte value that requires overflow.
     */
    @Test
    void listBucketEntries_largeValueInNestedBucketDoesNotThrow() {
        List<byte[]> path = Arrays.asList(b("nested"), b("sub1"));
        assertDoesNotThrow(() -> largeValuesReader.listBucketEntries(path));
    }

    @Test
    void listBucketEntries_largeValueInNestedBucketIsReadCorrectly() throws IOException {
        List<byte[]> path = Arrays.asList(b("nested"), b("sub1"));
        List<JammdbReader.BucketEntry> entries = largeValuesReader.listBucketEntries(path);

        assertNotNull(entries);
        assertEquals(3, entries.size(), "nested/sub1 should contain 3 entries");

        JammdbReader.BucketEntry big = findEntry(entries, "big");
        assertNotNull(big);
        assertEquals(5000, big.value.length, "nested 'big' value should be 5000 bytes");
        assertArrayEquals(repeat('X', 5000), big.value, "nested 'big' value content mismatch");
    }

    @Test
    void listBucketEntries_smallValuesInNestedBucketAreIntact() throws IOException {
        List<byte[]> path = Arrays.asList(b("nested"), b("sub1"));
        List<JammdbReader.BucketEntry> entries = largeValuesReader.listBucketEntries(path);

        JammdbReader.BucketEntry k1 = findEntry(entries, "k1");
        assertNotNull(k1);
        assertEquals("v1", k1.getValueString());

        JammdbReader.BucketEntry k2 = findEntry(entries, "k2");
        assertNotNull(k2);
        assertEquals("v2", k2.getValueString());
    }

    // =========================================================================
    // Bucket navigation
    // =========================================================================

    @Test
    void getBucketRootPage_returnsValidPageForExistingBucket() throws IOException {
        long pageId = largeValuesReader.getBucketRootPage(b("large_bucket"));
        assertTrue(pageId > 0, "Root page ID should be positive for an existing bucket");
    }

    @Test
    void getBucketRootPage_returnsMinusOneForMissingBucket() throws IOException {
        long pageId = largeValuesReader.getBucketRootPage(b("does_not_exist"));
        assertEquals(-1, pageId, "Should return -1 for a non-existent bucket");
    }

    @Test
    void listBucketEntries_emptyPathReturnsRootEntries() throws IOException {
        List<JammdbReader.BucketEntry> entries = largeValuesReader.listBucketEntries(Collections.emptyList());
        assertNotNull(entries);
        assertEquals(2, entries.size(), "Empty path should return root bucket entries");
        assertTrue(entries.stream().allMatch(e -> e.isBucket),
                "Root entries should all be buckets");
    }

    @Test
    void listBucketEntries_throwsForMissingBucketInPath() {
        List<byte[]> path = Collections.singletonList(b("missing_bucket"));
        IOException ex = assertThrows(IOException.class,
                () -> largeValuesReader.listBucketEntries(path));
        assertTrue(ex.getMessage().contains("Bucket not found"),
                "Exception message should mention 'Bucket not found'");
    }

    // =========================================================================
    // Sample database baseline (no overflow, ensures basic logic still works)
    // =========================================================================

    @Test
    void sampleDatabase_settingsBucketContainsNestedSubBuckets() throws IOException {
        List<byte[]> path = Collections.singletonList(b("settings"));
        List<JammdbReader.BucketEntry> entries = sampleReader.listBucketEntries(path);

        assertNotNull(entries);
        assertEquals(3, entries.size(), "settings bucket should have 3 sub-buckets");
        assertTrue(entries.stream().allMatch(e -> e.isBucket),
                "All settings entries should be sub-buckets");

        List<String> names = entries.stream()
                .map(JammdbReader.BucketEntry::getKeyString)
                .toList();
        assertTrue(names.contains("backup"));
        assertTrue(names.contains("general"));
        assertTrue(names.contains("security"));
    }

    @Test
    void sampleDatabase_nestedBucketEntriesAreReadable() throws IOException {
        List<byte[]> path = Arrays.asList(b("settings"), b("security"));
        List<JammdbReader.BucketEntry> entries = sampleReader.listBucketEntries(path);

        assertNotNull(entries);
        assertEquals(4, entries.size());

        JammdbReader.BucketEntry sslEnabled = findEntry(entries, "ssl_enabled");
        assertNotNull(sslEnabled);
        assertEquals("true", sslEnabled.getValueString());
    }

    @Test
    void sampleDatabase_kvPairsHaveCorrectSizes() throws IOException {
        List<byte[]> path = Collections.singletonList(b("users"));
        List<JammdbReader.BucketEntry> entries = sampleReader.listBucketEntries(path);

        assertNotNull(entries);
        assertEquals(5, entries.size());
        for (JammdbReader.BucketEntry e : entries) {
            assertFalse(e.isBucket, "users entries should be KV pairs");
            assertNotNull(e.value, "KV value should not be null");
            assertTrue(e.value.length > 0, "KV value should not be empty");
        }
    }

    // =========================================================================
    // Pagination API (offset/limit)
    // =========================================================================

    @Test
    void countBucketEntries_returnsCorrectCountForLargeBucket() throws IOException {
        List<byte[]> path = Collections.singletonList(b("large_bucket"));
        long count = largeValuesReader.countBucketEntries(path);
        assertEquals(4, count, "large_bucket should have 4 entries");
    }

    @Test
    void countBucketEntries_returnsCorrectCountForNestedBucket() throws IOException {
        List<byte[]> path = Arrays.asList(b("nested"), b("sub1"));
        long count = largeValuesReader.countBucketEntries(path);
        assertEquals(3, count, "nested/sub1 should have 3 entries");
    }

    @Test
    void listBucketEntriesPaged_firstPageReturnsAllEntriesForSmallBucket() throws IOException {
        List<byte[]> path = Collections.singletonList(b("large_bucket"));
        JammdbReader.BucketEntryPage page = largeValuesReader.listBucketEntries(path, 0, 500);

        assertNotNull(page);
        assertEquals(4, page.getTotal(), "Total should be 4");
        assertEquals(4, page.getEntries().size(), "Should return all 4 entries");
        assertEquals(0, page.getOffset());
        assertEquals(1, page.getCurrentPage());
        assertEquals(1, page.getPageCount());
        assertFalse(page.hasNextPage());
        assertFalse(page.hasPrevPage());
    }

    @Test
    void listBucketEntriesPaged_offsetSkipsEntries() throws IOException {
        List<byte[]> path = Collections.singletonList(b("large_bucket"));
        // Skip first entry, limit to 1
        JammdbReader.BucketEntryPage page = largeValuesReader.listBucketEntries(path, 1, 1);

        assertEquals(4, page.getTotal());
        assertEquals(1, page.getEntries().size(), "Should return 1 entry");
        assertEquals(1, page.getOffset());
        assertTrue(page.hasNextPage(), "Should have next page");
        assertTrue(page.hasPrevPage(), "Should have prev page");
    }

    @Test
    void listBucketEntriesPaged_offsetBeyondTotalReturnsEmptyPage() throws IOException {
        List<byte[]> path = Collections.singletonList(b("large_bucket"));
        JammdbReader.BucketEntryPage page = largeValuesReader.listBucketEntries(path, 100, 10);

        assertEquals(4, page.getTotal());
        assertTrue(page.getEntries().isEmpty(), "Should return no entries beyond total");
    }

    @Test
    void listBucketEntriesPaged_limitZeroUsesDefaultPageSize() throws IOException {
        List<byte[]> path = Collections.singletonList(b("large_bucket"));
        JammdbReader.BucketEntryPage page = largeValuesReader.listBucketEntries(path, 0, 0);

        assertEquals(4, page.getTotal());
        assertEquals(4, page.getEntries().size(), "limit=0 should use default page size (500)");
    }

    @Test
    void listBucketEntriesPaged_smallLimitPaginatesCorrectly() throws IOException {
        List<byte[]> path = Collections.singletonList(b("large_bucket"));
        // Page size = 2: first page has 2 entries, second page has 2 entries
        JammdbReader.BucketEntryPage page1 = largeValuesReader.listBucketEntries(path, 0, 2);
        assertEquals(4, page1.getTotal());
        assertEquals(2, page1.getEntries().size());
        assertEquals(1, page1.getCurrentPage());
        assertEquals(2, page1.getPageCount());
        assertTrue(page1.hasNextPage());
        assertFalse(page1.hasPrevPage());

        JammdbReader.BucketEntryPage page2 = largeValuesReader.listBucketEntries(path, 2, 2);
        assertEquals(4, page2.getTotal());
        assertEquals(2, page2.getEntries().size());
        assertEquals(2, page2.getCurrentPage());
        assertFalse(page2.hasNextPage());
        assertTrue(page2.hasPrevPage());
    }

    @Test
    void listBucketEntriesPaged_entriesAreConsistentWithFullLoad() throws IOException {
        List<byte[]> path = Collections.singletonList(b("large_bucket"));
        List<JammdbReader.BucketEntry> all = largeValuesReader.listBucketEntries(path);
        JammdbReader.BucketEntryPage page = largeValuesReader.listBucketEntries(path, 0, 500);

        assertEquals(all.size(), page.getEntries().size());
        for (int i = 0; i < all.size(); i++) {
            assertArrayEquals(all.get(i).key, page.getEntries().get(i).key,
                    "Key at index " + i + " should match");
        }
    }

    @Test
    void listBucketEntriesPaged_worksWithNestedBucketPath() throws IOException {
        List<byte[]> path = Arrays.asList(b("nested"), b("sub1"));
        JammdbReader.BucketEntryPage page = largeValuesReader.listBucketEntries(path, 0, 2);

        assertEquals(3, page.getTotal());
        assertEquals(2, page.getEntries().size());
        assertTrue(page.hasNextPage());
    }

    // =========================================================================
    // Large value truncation (preview mode)
    // =========================================================================

    @Test
    void listBucketEntriesPaged_largeValueIsTruncatedInPreview() throws IOException {
        List<byte[]> path = Collections.singletonList(b("large_bucket"));
        JammdbReader.BucketEntryPage page = largeValuesReader.listBucketEntries(path, 0, 500);

        JammdbReader.BucketEntry massiveKey = page.getEntries().stream()
                .filter(e -> "massive_key".equals(e.getKeyString()))
                .findFirst()
                .orElse(null);
        assertNotNull(massiveKey);
        assertTrue(massiveKey.valueTruncated, "100000-byte value should be truncated in preview");
        assertEquals(100000, massiveKey.fullValueSize, "fullValueSize should report original size");
        assertTrue(massiveKey.value.length <= 64 * 1024, "Preview value should be within preview limit");
    }

    @Test
    void listBucketEntriesPaged_smallValueIsNotTruncated() throws IOException {
        List<byte[]> path = Collections.singletonList(b("large_bucket"));
        JammdbReader.BucketEntryPage page = largeValuesReader.listBucketEntries(path, 0, 500);

        JammdbReader.BucketEntry hugeKey = page.getEntries().stream()
                .filter(e -> "huge_key".equals(e.getKeyString()))
                .findFirst()
                .orElse(null);
        assertNotNull(hugeKey);
        assertFalse(hugeKey.valueTruncated, "10000-byte value should NOT be truncated (below 64KB limit)");
        assertEquals(10000, hugeKey.value.length, "Value should be full 10000 bytes");
    }

    @Test
    void getValue_returnsFullValueForMassiveKey() throws IOException {
        List<byte[]> path = Collections.singletonList(b("large_bucket"));
        byte[] fullValue = largeValuesReader.getValue(path, b("massive_key"));

        assertNotNull(fullValue);
        assertEquals(100000, fullValue.length, "getValue should return full 100000-byte value");
        assertArrayEquals(repeat('C', 100000), fullValue);
    }

    @Test
    void getValue_returnsFullValueForLargeKey() throws IOException {
        List<byte[]> path = Collections.singletonList(b("large_bucket"));
        byte[] fullValue = largeValuesReader.getValue(path, b("huge_key"));

        assertNotNull(fullValue);
        assertEquals(10000, fullValue.length, "getValue should return full 10000-byte value");
        assertArrayEquals(repeat('B', 10000), fullValue);
    }

    @Test
    void getValue_returnsNullForSubBucketKey() throws IOException {
        List<byte[]> path = Collections.singletonList(b("nested"));
        // sub1 is a sub-bucket, not a KV pair
        byte[] value = largeValuesReader.getValue(path, b("sub1"));
        assertNull(value, "getValue should return null for sub-bucket keys");
    }

    @Test
    void getValue_returnsNullForMissingKey() throws IOException {
        List<byte[]> path = Collections.singletonList(b("large_bucket"));
        byte[] value = largeValuesReader.getValue(path, b("nonexistent_key"));
        assertNull(value, "getValue should return null for missing keys");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] repeat(char c, int count) {
        byte[] result = new byte[count];
        Arrays.fill(result, (byte) c);
        return result;
    }

    private static List<String> bucketsToStrings(List<byte[]> buckets) {
        return buckets.stream()
                .map(bs -> new String(bs, StandardCharsets.UTF_8))
                .toList();
    }

    private static JammdbReader.BucketEntry findEntry(List<JammdbReader.BucketEntry> entries, String key) {
        return entries.stream()
                .filter(e -> key.equals(e.getKeyString()))
                .findFirst()
                .orElse(null);
    }
}
