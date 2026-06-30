package com.protonail.bolt.intellij.ui;

import org.junit.jupiter.api.Test;

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
    void jammdbConnectionsReportReadOnlyAndRejectEdits() throws Exception {
        try (DatabaseConnection connection = DatabaseConnection.open(
                Paths.get("src/test/resources/jammdb/sample.db").toString())) {
            assertFalse(connection.supportsEditing());

            UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                    () -> connection.putValue(Collections.singletonList(b("users")), b("new"), b("value")));
            assertTrue(ex.getMessage().contains("does not support editing"));
        }
    }

    private static byte[] b(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
