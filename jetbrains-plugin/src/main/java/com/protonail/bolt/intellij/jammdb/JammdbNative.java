package com.protonail.bolt.intellij.jammdb;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

public interface JammdbNative extends Library {
    JammdbNative INSTANCE = JammdbNativeLoader.load();

    Pointer Jammdb_CreateDatabase(String dbPath);

    Pointer Jammdb_PutValue(String dbPath, String bucketPath, byte[] key, int keyLen, byte[] value, int valueLen);

    Pointer Jammdb_DeleteValue(String dbPath, String bucketPath, byte[] key, int keyLen);

    Pointer Jammdb_CreateBucket(String dbPath, String parentPath, byte[] bucketName, int bucketNameLen);

    Pointer Jammdb_DeleteBucket(String dbPath, String parentPath, byte[] bucketName, int bucketNameLen);

    void Jammdb_FreeString(Pointer message);

    static void check(Pointer error) {
        if (error == null) return;
        String message = error.getString(0);
        INSTANCE.Jammdb_FreeString(error);
        throw new IllegalStateException(message);
    }
}
