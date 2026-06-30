package com.protonail.bolt.intellij.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConnectionEditingTest {

    @Test
    void queryBucketEntries_filtersJammdbEntriesByKeyOrValue() throws Exception {
        try (DatabaseConnection connection = DatabaseConnection.open(
                Paths.get("src/test/resources/jammdb/sample.db").toString())) {
            DatabaseConnection.EntryPage byKey = connection.queryBucketEntries(
                    Arrays.asList(b("settings"), b("security")), "ssl", 0, 500);
            assertEquals(1, byKey.total);
            assertEquals("ssl_enabled", byKey.entries.get(0).getKeyString());

            DatabaseConnection.EntryPage byValue = connection.queryBucketEntries(
                    Collections.singletonList(b("users")), "alice", 0, 500);
            assertEquals(1, byValue.total);
            assertTrue(byValue.entries.get(0).getValueString().contains("alice"));
        }
    }

    @Test
    void jammdbConnectionsSupportPutDeleteAndBucketEdits() throws Exception {
        Path dbCopy = Files.createTempFile("bbolt-jammdb-editing", ".db");
        Files.copy(Paths.get("src/test/resources/jammdb/sample.db"), dbCopy,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        try (DatabaseConnection connection = DatabaseConnection.open(dbCopy.toString())) {
            assertTrue(connection.supportsEditing());

            connection.putValue(Collections.singletonList(b("users")), b("new_user"), b("alice-updated"));
            assertArrayEquals(b("alice-updated"),
                    connection.getValue(Collections.singletonList(b("users")), b("new_user")));

            connection.deleteValue(Collections.singletonList(b("users")), b("new_user"));
            assertNull(connection.getValue(Collections.singletonList(b("users")), b("new_user")));

            connection.createBucket(Collections.emptyList(), b("codex_bucket"));
            assertTrue(connection.listRootBuckets().stream().anyMatch(name -> Arrays.equals(name, b("codex_bucket"))));

            connection.deleteBucket(Collections.emptyList(), b("codex_bucket"));
            assertFalse(connection.listRootBuckets().stream().anyMatch(name -> Arrays.equals(name, b("codex_bucket"))));
        } finally {
            Files.deleteIfExists(dbCopy);
        }
    }

    private static byte[] b(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
