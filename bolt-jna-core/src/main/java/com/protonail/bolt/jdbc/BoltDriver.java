package com.protonail.bolt.jdbc;

import com.protonail.bolt.jna.Bolt;
import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

public class BoltDriver implements Driver {
    private static final String URL_PREFIX = "jdbc:bolt:";

    static {
        try {
            DriverManager.registerDriver(new BoltDriver());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register Bolt JDBC Driver", e);
        }
        // Initialize Go runtime
        Bolt.init();
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        String dbPath = url.substring(URL_PREFIX.length());
        return new BoltConnection(dbPath, info);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return url != null && url.startsWith(URL_PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false; // Not fully JDBC compliant, supports basic operations
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
}
