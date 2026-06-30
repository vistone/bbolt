package com.protonail.bolt.intellij.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConnectionCreationTest {

    @Test
    void createDatabase_createsOpenableBboltDatabase() throws Exception {
        Path dbPath = tempMissingPath("new-bbolt", ".db");

        DatabaseConnection.createDatabase(dbPath.toString(), DatabaseConnection.DatabaseFormat.BBOLT);

        assertTrue(Files.exists(dbPath));
        try (DatabaseConnection connection = DatabaseConnection.open(dbPath.toString())) {
            assertEquals("bbolt", connection.getFormatName());
            assertTrue(connection.supportsEditing());
            assertTrue(connection.listRootBuckets().isEmpty());
        } finally {
            Files.deleteIfExists(dbPath);
        }
    }

    @Test
    void createDatabase_createsOpenableJammdbDatabase() throws Exception {
        Path dbPath = tempMissingPath("new-jammdb", ".db");

        DatabaseConnection.createDatabase(dbPath.toString(), DatabaseConnection.DatabaseFormat.JAMMDB);

        assertTrue(Files.exists(dbPath));
        try (DatabaseConnection connection = DatabaseConnection.open(dbPath.toString())) {
            assertEquals("jammdb", connection.getFormatName());
            assertTrue(connection.supportsEditing());
            assertTrue(connection.listRootBuckets().isEmpty());
        } finally {
            Files.deleteIfExists(dbPath);
        }
    }

    @Test
    void createDatabase_refusesToOverwriteExistingFiles() throws Exception {
        Path dbPath = Files.createTempFile("existing-db", ".db");
        try {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> DatabaseConnection.createDatabase(dbPath.toString(), DatabaseConnection.DatabaseFormat.JAMMDB));
            assertTrue(ex.getMessage().contains("already exists"));
        } finally {
            Files.deleteIfExists(dbPath);
        }
    }

    private static Path tempMissingPath(String prefix, String suffix) throws Exception {
        Path path = Files.createTempFile(prefix, suffix);
        Files.delete(path);
        return path;
    }
}
