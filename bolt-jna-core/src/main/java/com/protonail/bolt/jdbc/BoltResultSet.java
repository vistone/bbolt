package com.protonail.bolt.jdbc;

import com.protonail.bolt.jna.BoltKeyValue;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

public class BoltResultSet extends AbstractResultSet {
    private final List<BoltKeyValue> keyValues;
    private int currentIndex = -1;
    private boolean wasNull = false;
    private boolean closed = false;

    public BoltResultSet(List<BoltKeyValue> keyValues) {
        this.keyValues = keyValues;
    }

    @Override
    public boolean next() throws SQLException {
        checkClosed();
        currentIndex++;
        return currentIndex < keyValues.size();
    }

    @Override
    public void close() throws SQLException {
        closed = true;
    }

    @Override
    public boolean wasNull() throws SQLException {
        return wasNull;
    }

    @Override
    public String getString(int columnIndex) throws SQLException {
        byte[] data = getBytes(columnIndex);
        return data != null ? new String(data, StandardCharsets.UTF_8) : null;
    }

    @Override
    public byte[] getBytes(int columnIndex) throws SQLException {
        checkClosed();
        checkRow();
        BoltKeyValue kv = keyValues.get(currentIndex);
        if (columnIndex == 1) {
            byte[] key = kv.getKey();
            wasNull = key == null;
            return key;
        } else if (columnIndex == 2) {
            byte[] value = kv.getValue();
            wasNull = value == null;
            return value;
        }
        throw new SQLException("Invalid column index: " + columnIndex);
    }

    @Override
    public String getString(String columnLabel) throws SQLException {
        return getString(findColumn(columnLabel));
    }

    @Override
    public byte[] getBytes(String columnLabel) throws SQLException {
        return getBytes(findColumn(columnLabel));
    }

    @Override
    public int findColumn(String columnLabel) throws SQLException {
        if ("key".equalsIgnoreCase(columnLabel)) {
            return 1;
        } else if ("value".equalsIgnoreCase(columnLabel)) {
            return 2;
        }
        throw new SQLException("Column not found: " + columnLabel);
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        checkClosed();
        return new BoltResultSetMetaData();
    }

    @Override
    public Object getObject(int columnIndex) throws SQLException {
        return getBytes(columnIndex);
    }

    @Override
    public Object getObject(String columnLabel) throws SQLException {
        return getObject(findColumn(columnLabel));
    }

    @Override
    public int getInt(int columnIndex) throws SQLException {
        String str = getString(columnIndex);
        if (str == null) return 0;
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            throw new SQLException("Cannot parse value as int: " + str, e);
        }
    }

    @Override
    public long getLong(int columnIndex) throws SQLException {
        String str = getString(columnIndex);
        if (str == null) return 0;
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            throw new SQLException("Cannot parse value as long: " + str, e);
        }
    }

    @Override
    public double getDouble(int columnIndex) throws SQLException {
        String str = getString(columnIndex);
        if (str == null) return 0;
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            throw new SQLException("Cannot parse value as double: " + str, e);
        }
    }

    @Override
    public boolean isBeforeFirst() throws SQLException {
        return currentIndex < 0;
    }

    @Override
    public boolean isAfterLast() throws SQLException {
        return currentIndex >= keyValues.size();
    }

    @Override
    public boolean isFirst() throws SQLException {
        return currentIndex == 0;
    }

    @Override
    public boolean isLast() throws SQLException {
        return currentIndex == keyValues.size() - 1;
    }

    @Override
    public void beforeFirst() throws SQLException {
        currentIndex = -1;
    }

    @Override
    public void afterLast() throws SQLException {
        currentIndex = keyValues.size();
    }

    @Override
    public boolean first() throws SQLException {
        currentIndex = 0;
        return currentIndex < keyValues.size();
    }

    @Override
    public boolean last() throws SQLException {
        currentIndex = keyValues.size() - 1;
        return currentIndex >= 0;
    }

    @Override
    public int getRow() throws SQLException {
        return currentIndex + 1;
    }

    @Override
    public boolean absolute(int row) throws SQLException {
        currentIndex = row - 1;
        return currentIndex >= 0 && currentIndex < keyValues.size();
    }

    @Override
    public boolean relative(int rows) throws SQLException {
        currentIndex += rows;
        return currentIndex >= 0 && currentIndex < keyValues.size();
    }

    @Override
    public boolean previous() throws SQLException {
        currentIndex--;
        return currentIndex >= 0;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Unwrap not supported for " + iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this);
    }

    private void checkRow() throws SQLException {
        if (currentIndex < 0 || currentIndex >= keyValues.size()) {
            throw new SQLException("Invalid row position");
        }
    }

    private void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("ResultSet is closed");
        }
    }
}
