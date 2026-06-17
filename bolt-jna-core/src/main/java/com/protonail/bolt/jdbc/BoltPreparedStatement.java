package com.protonail.bolt.jdbc;

import java.sql.*;
import java.util.Calendar;

public class BoltPreparedStatement extends BoltStatement implements PreparedStatement {
    private final String sql;

    public BoltPreparedStatement(BoltConnection connection, String sql) {
        super(connection);
        this.sql = sql;
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        return executeQuery(sql);
    }

    @Override
    public int executeUpdate() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean execute() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void addBatch() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    // All setter methods are not implemented yet
    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setShort(int parameterIndex, short x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setInt(int parameterIndex, int x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setLong(int parameterIndex, long x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setBigDecimal(int parameterIndex, java.math.BigDecimal x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setString(int parameterIndex, String x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setDate(int parameterIndex, Date x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setTime(int parameterIndex, Time x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setAsciiStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setUnicodeStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setBinaryStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void clearParameters() throws SQLException {}
    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setCharacterStream(int parameterIndex, java.io.Reader reader, int length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setBlob(int parameterIndex, Blob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setClob(int parameterIndex, Clob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public ResultSetMetaData getMetaData() throws SQLException { return new BoltResultSetMetaData(); }
    @Override
    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setURL(int parameterIndex, java.net.URL x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setNString(int parameterIndex, String value) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setNCharacterStream(int parameterIndex, java.io.Reader value, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setClob(int parameterIndex, java.io.Reader reader, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setBlob(int parameterIndex, java.io.InputStream inputStream, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setNClob(int parameterIndex, java.io.Reader reader, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setAsciiStream(int parameterIndex, java.io.InputStream x, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setBinaryStream(int parameterIndex, java.io.InputStream x, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setCharacterStream(int parameterIndex, java.io.Reader reader, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setAsciiStream(int parameterIndex, java.io.InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setBinaryStream(int parameterIndex, java.io.InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setCharacterStream(int parameterIndex, java.io.Reader reader) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setNCharacterStream(int parameterIndex, java.io.Reader value) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setClob(int parameterIndex, java.io.Reader reader) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setBlob(int parameterIndex, java.io.InputStream inputStream) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public void setNClob(int parameterIndex, java.io.Reader reader) throws SQLException { throw new SQLFeatureNotSupportedException(); }
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
}
