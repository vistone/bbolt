package com.protonail.bolt.intellij.driver;

import com.intellij.openapi.diagnostic.Logger;
import com.protonail.bolt.intellij.BoltNativeLoader;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Registers the bbolt JDBC driver and provides connections.
 * Uses reflection to avoid hard dependency on Database plugin APIs.
 */
public class BoltDriverProvider {
    private static final Logger LOG = Logger.getInstance(BoltDriverProvider.class);

    public static final String DRIVER_ID = "bbolt";
    public static final String DRIVER_CLASS = "com.protonail.bolt.jdbc.BoltDriver";
    public static final String URL_PREFIX = "jdbc:bbolt:";

    private static volatile boolean driverRegistered = false;

    /**
     * Ensures the bbolt JDBC driver is registered with DriverManager.
     */
    public static synchronized void ensureDriverRegistered() {
        if (driverRegistered) return;
        BoltNativeLoader.ensureLoaded();
        try {
            Class<?> driverClass = Class.forName(DRIVER_CLASS);
            Driver driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(driver);
            driverRegistered = true;
            LOG.info("bbolt JDBC driver registered");
        } catch (Exception e) {
            LOG.error("Failed to register bbolt JDBC driver", e);
            throw new RuntimeException("Failed to register bbolt JDBC driver", e);
        }
    }

    /**
     * Creates a connection to a bbolt database file.
     */
    @NotNull
    public static Connection createConnection(@NotNull String dbPath) throws SQLException {
        ensureDriverRegistered();
        String url = URL_PREFIX + dbPath;
        Properties props = new Properties();
        props.setProperty("path", dbPath);
        return DriverManager.getConnection(url, props);
    }
}
