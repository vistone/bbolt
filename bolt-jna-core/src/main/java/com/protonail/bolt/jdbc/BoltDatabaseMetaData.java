package com.protonail.bolt.jdbc;

import com.protonail.bolt.jna.BoltBucket;
import com.protonail.bolt.jna.BoltCursor;
import com.protonail.bolt.jna.BoltKeyValue;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class BoltDatabaseMetaData implements DatabaseMetaData {
    private final BoltConnection connection;

    public BoltDatabaseMetaData(BoltConnection connection) {
        this.connection = connection;
    }

    @Override
    public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types) throws SQLException {
        List<String> buckets = new ArrayList<>();
        connection.getBolt().view(tx -> {
            try (BoltCursor cursor = tx.createCursor()) {
                BoltKeyValue kv = cursor.first();
                while (kv != null) {
                    if (kv.getValue() == null) { // Buckets have null values at root level
                        buckets.add(new String(kv.getKey(), StandardCharsets.UTF_8));
                    }
                    kv = cursor.next();
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to list buckets", e);
            }
        });

        // Create result set with bucket names as table names
        List<Object[]> tableRows = new ArrayList<>();
        for (String bucket : buckets) {
            if (tableNamePattern == null || matchesTablePattern(bucket, tableNamePattern)) {
                tableRows.add(new Object[]{bucket, "TABLE"});
            }
        }
        
        // Standard JDBC TABLES result set columns: TABLE_CAT, TABLE_SCHEM, TABLE_NAME, TABLE_TYPE, REMARKS, TYPE_CAT, TYPE_SCHEM, TYPE_NAME, SELF_REFERENCING_COL_NAME, REF_GENERATION
        return new AbstractResultSet() {
            private int currentIndex = -1;
            private boolean closed = false;

            @Override
            public boolean next() throws SQLException {
                currentIndex++;
                return currentIndex < tableRows.size();
            }

            @Override
            public void close() throws SQLException {
                closed = true;
            }

            @Override
            public boolean wasNull() throws SQLException {
                return false;
            }

            @Override
            public String getString(int columnIndex) throws SQLException {
                if (currentIndex < 0 || currentIndex >= tableRows.size()) {
                    throw new SQLException("Invalid row position");
                }
                Object[] row = tableRows.get(currentIndex);
                switch (columnIndex) {
                    case 1: return null; // TABLE_CAT
                    case 2: return null; // TABLE_SCHEM
                    case 3: return (String) row[0]; // TABLE_NAME
                    case 4: return (String) row[1]; // TABLE_TYPE
                    case 5: return ""; // REMARKS
                    default: return null;
                }
            }

            @Override
            public String getString(String columnLabel) throws SQLException {
                return getString(findColumn(columnLabel));
            }

            @Override
            public int findColumn(String columnLabel) throws SQLException {
                switch (columnLabel.toUpperCase()) {
                    case "TABLE_CAT": return 1;
                    case "TABLE_SCHEM": return 2;
                    case "TABLE_NAME": return 3;
                    case "TABLE_TYPE": return 4;
                    case "REMARKS": return 5;
                    default: throw new SQLException("Column not found: " + columnLabel);
                }
            }

            @Override
            public ResultSetMetaData getMetaData() throws SQLException {
                return new ResultSetMetaData() {
                    @Override
                    public int getColumnCount() throws SQLException {
                        return 5;
                    }

                    @Override
                    public String getColumnName(int column) throws SQLException {
                        switch (column) {
                            case 1: return "TABLE_CAT";
                            case 2: return "TABLE_SCHEM";
                            case 3: return "TABLE_NAME";
                            case 4: return "TABLE_TYPE";
                            case 5: return "REMARKS";
                            default: throw new SQLException("Invalid column index: " + column);
                        }
                    }

                    @Override public String getColumnLabel(int column) throws SQLException { return getColumnName(column); }
                    @Override public int getColumnType(int column) throws SQLException { return Types.VARCHAR; }
                    @Override public String getColumnTypeName(int column) throws SQLException { return "VARCHAR"; }
                    @Override public int isNullable(int column) throws SQLException { return columnNoNulls; }
                    @Override public boolean isAutoIncrement(int column) throws SQLException { return false; }
                    @Override public boolean isCaseSensitive(int column) throws SQLException { return true; }
                    @Override public boolean isSearchable(int column) throws SQLException { return true; }
                    @Override public boolean isCurrency(int column) throws SQLException { return false; }
                    @Override public boolean isSigned(int column) throws SQLException { return false; }
                    @Override public int getColumnDisplaySize(int column) throws SQLException { return 255; }
                    @Override public String getSchemaName(int column) throws SQLException { return ""; }
                    @Override public int getPrecision(int column) throws SQLException { return 255; }
                    @Override public int getScale(int column) throws SQLException { return 0; }
                    @Override public String getTableName(int column) throws SQLException { return ""; }
                    @Override public String getCatalogName(int column) throws SQLException { return ""; }
                    @Override public boolean isReadOnly(int column) throws SQLException { return true; }
                    @Override public boolean isWritable(int column) throws SQLException { return false; }
                    @Override public boolean isDefinitelyWritable(int column) throws SQLException { return false; }
                    @Override public String getColumnClassName(int column) throws SQLException { return String.class.getName(); }
                      @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLFeatureNotSupportedException(); }
                      @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return false; }
                  };
            }

            // Other required methods
            @Override public boolean isBeforeFirst() throws SQLException { return currentIndex < 0; }
            @Override public boolean isAfterLast() throws SQLException { return currentIndex >= tableRows.size(); }
            @Override public boolean isFirst() throws SQLException { return currentIndex == 0; }
            @Override public boolean isLast() throws SQLException { return currentIndex == tableRows.size() - 1; }
            @Override public void beforeFirst() throws SQLException { currentIndex = -1; }
            @Override public void afterLast() throws SQLException { currentIndex = tableRows.size(); }
            @Override public boolean first() throws SQLException { currentIndex = 0; return currentIndex < tableRows.size(); }
            @Override public boolean last() throws SQLException { currentIndex = tableRows.size() - 1; return currentIndex >= 0; }
            @Override public int getRow() throws SQLException { return currentIndex + 1; }
            @Override public boolean absolute(int row) throws SQLException { currentIndex = row - 1; return currentIndex >= 0 && currentIndex < tableRows.size(); }
            @Override public boolean relative(int rows) throws SQLException { currentIndex += rows; return currentIndex >= 0 && currentIndex < tableRows.size(); }
            @Override public boolean previous() throws SQLException { currentIndex--; return currentIndex >= 0; }
            @Override public int getFetchDirection() throws SQLException { return FETCH_FORWARD; }
            @Override public void setFetchDirection(int direction) throws SQLException {}
            @Override public int getFetchSize() throws SQLException { return 0; }
            @Override public void setFetchSize(int rows) throws SQLException {}
            @Override public int getType() throws SQLException { return TYPE_FORWARD_ONLY; }
            @Override public int getConcurrency() throws SQLException { return CONCUR_READ_ONLY; }
            @Override public int getHoldability() throws SQLException { return CLOSE_CURSORS_AT_COMMIT; }
            @Override public boolean isClosed() throws SQLException { return closed; }
            @Override public Object getObject(int columnIndex) throws SQLException { return getString(columnIndex); }
            @Override public Object getObject(String columnLabel) throws SQLException { return getString(columnLabel); }
        };
    }

    /**
     * Matches a table name against a JDBC table name pattern.
     * Supports '_' (single char) and '%' (any sequence) wildcards.
     * Regex special characters in the pattern are escaped.
     */
    private static boolean matchesTablePattern(String tableName, String pattern) {
        StringBuilder regex = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            switch (c) {
                case '_':
                    regex.append('.');
                    break;
                case '%':
                    regex.append(".*");
                    break;
                default:
                    regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.matches(regex.toString(), tableName);
    }

    @Override
    public String getDatabaseProductName() throws SQLException {
        return "bbolt";
    }

    @Override
    public boolean dataDefinitionCausesTransactionCommit() throws SQLException { return false; }
    @Override
    public boolean dataDefinitionIgnoredInTransactions() throws SQLException { return true; }
    @Override
    public boolean deletesAreDetected(int type) throws SQLException { return false; }
    @Override
    public boolean insertsAreDetected(int type) throws SQLException { return false; }
    @Override
    public boolean updatesAreDetected(int type) throws SQLException { return false; }
    @Override
    public boolean othersDeletesAreVisible(int type) throws SQLException { return false; }
    @Override
    public boolean othersInsertsAreVisible(int type) throws SQLException { return false; }
    @Override
    public boolean othersUpdatesAreVisible(int type) throws SQLException { return false; }
    @Override
    public boolean ownDeletesAreVisible(int type) throws SQLException { return false; }
    @Override
    public boolean ownInsertsAreVisible(int type) throws SQLException { return false; }
    @Override
    public boolean ownUpdatesAreVisible(int type) throws SQLException { return false; }
    @Override
    public boolean supportsBatchUpdates() throws SQLException { return false; }
    @Override
    public boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException { return false; }
    @Override
    public boolean supportsDataManipulationTransactionsOnly() throws SQLException { return true; }
    @Override
    public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public boolean generatedKeyAlwaysReturned() throws SQLException { return false; }
    @Override
    public ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException { throw new SQLFeatureNotSupportedException(); }

    @Override
    public String getDatabaseProductVersion() throws SQLException {
        return "1.0";
    }

    @Override
    public String getDriverName() throws SQLException {
        return "bbolt JDBC Driver";
    }

    @Override
    public String getDriverVersion() throws SQLException {
        return "1.0";
    }

    @Override
    public int getDriverMajorVersion() {
        return 1;
    }

    @Override
    public int getDriverMinorVersion() {
        return 0;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return connection;
    }

    // Basic required implementations
    @Override
    public boolean supportsResultSetType(int type) throws SQLException {
        return type == ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public boolean supportsResultSetConcurrency(int type, int concurrency) throws SQLException {
        return concurrency == ResultSet.CONCUR_READ_ONLY;
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return false;
    }

    // All other unsupported methods throw SQLFeatureNotSupportedException
    @Override
    public boolean allProceduresAreCallable() throws SQLException { return false; }
    @Override
    public boolean allTablesAreSelectable() throws SQLException { return true; }
    @Override
    public String getURL() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override
    public String getUserName() throws SQLException { return ""; }
    @Override
    public boolean nullsAreSortedHigh() throws SQLException { return false; }
    @Override
    public boolean nullsAreSortedLow() throws SQLException { return true; }
    @Override
    public boolean nullsAreSortedAtStart() throws SQLException { return false; }
    @Override
    public boolean nullsAreSortedAtEnd() throws SQLException { return true; }
    @Override
    public int getDatabaseMajorVersion() throws SQLException { return 1; }
    @Override
    public int getDatabaseMinorVersion() throws SQLException { return 0; }
    @Override
    public int getJDBCMajorVersion() throws SQLException { return 4; }
    @Override
    public int getJDBCMinorVersion() throws SQLException { return 0; }
    @Override
    public int getDefaultTransactionIsolation() throws SQLException { return Connection.TRANSACTION_SERIALIZABLE; }
    @Override
    public boolean supportsTransactions() throws SQLException { return true; }
    @Override
    public boolean supportsTransactionIsolationLevel(int level) throws SQLException { return level == Connection.TRANSACTION_SERIALIZABLE; }

    // The rest of the methods are not implemented for brevity, you can add them as needed
    @Override public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getSchemas() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getCatalogs() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getTableTypes() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getVersionColumns(String catalog, String schema, String table) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getCrossReference(String parentCatalog, String parentSchema, String parentTable, String foreignCatalog, String foreignSchema, String foreignTable) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getTypeInfo() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public boolean usesLocalFiles() throws SQLException { return true; }
    @Override public boolean usesLocalFilePerTable() throws SQLException { return false; }
    @Override public boolean supportsMixedCaseIdentifiers() throws SQLException { return true; }
    @Override public boolean storesUpperCaseIdentifiers() throws SQLException { return false; }
    @Override public boolean storesLowerCaseIdentifiers() throws SQLException { return false; }
    @Override public boolean storesMixedCaseIdentifiers() throws SQLException { return true; }
    @Override public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException { return true; }
    @Override public boolean storesUpperCaseQuotedIdentifiers() throws SQLException { return false; }
    @Override public boolean storesLowerCaseQuotedIdentifiers() throws SQLException { return false; }
    @Override public boolean storesMixedCaseQuotedIdentifiers() throws SQLException { return true; }
    @Override public String getIdentifierQuoteString() throws SQLException { return "\""; }
    @Override public String getSQLKeywords() throws SQLException { return ""; }
    @Override public String getNumericFunctions() throws SQLException { return ""; }
    @Override public String getStringFunctions() throws SQLException { return ""; }
    @Override public String getSystemFunctions() throws SQLException { return ""; }
    @Override public String getTimeDateFunctions() throws SQLException { return ""; }
    @Override public String getSearchStringEscape() throws SQLException { return "\\"; }
    @Override public String getExtraNameCharacters() throws SQLException { return ""; }
    @Override public boolean supportsAlterTableWithAddColumn() throws SQLException { return false; }
    @Override public boolean supportsAlterTableWithDropColumn() throws SQLException { return false; }
    @Override public boolean supportsColumnAliasing() throws SQLException { return true; }
    @Override public boolean nullPlusNonNullIsNull() throws SQLException { return true; }
    @Override public boolean supportsConvert() throws SQLException { return false; }
    @Override public boolean supportsConvert(int fromType, int toType) throws SQLException { return false; }
    @Override public boolean supportsTableCorrelationNames() throws SQLException { return true; }
    @Override public boolean supportsDifferentTableCorrelationNames() throws SQLException { return false; }
    @Override public boolean supportsExpressionsInOrderBy() throws SQLException { return true; }
    @Override public boolean supportsOrderByUnrelated() throws SQLException { return true; }
    @Override public boolean supportsGroupBy() throws SQLException { return true; }
    @Override public boolean supportsGroupByUnrelated() throws SQLException { return true; }
    @Override public boolean supportsGroupByBeyondSelect() throws SQLException { return true; }
    @Override public boolean supportsLikeEscapeClause() throws SQLException { return true; }
    @Override public boolean supportsMultipleResultSets() throws SQLException { return false; }
    @Override public boolean supportsMultipleTransactions() throws SQLException { return false; }
    @Override public boolean supportsNonNullableColumns() throws SQLException { return true; }
    @Override public boolean supportsMinimumSQLGrammar() throws SQLException { return false; }
    @Override public boolean supportsCoreSQLGrammar() throws SQLException { return false; }
    @Override public boolean supportsExtendedSQLGrammar() throws SQLException { return false; }
    @Override public boolean supportsANSI92EntryLevelSQL() throws SQLException { return false; }
    @Override public boolean supportsANSI92IntermediateSQL() throws SQLException { return false; }
    @Override public boolean supportsANSI92FullSQL() throws SQLException { return false; }
    @Override public boolean supportsIntegrityEnhancementFacility() throws SQLException { return false; }
    @Override public boolean supportsOuterJoins() throws SQLException { return false; }
    @Override public boolean supportsFullOuterJoins() throws SQLException { return false; }
    @Override public boolean supportsLimitedOuterJoins() throws SQLException { return false; }
    @Override public String getSchemaTerm() throws SQLException { return ""; }
    @Override public String getProcedureTerm() throws SQLException { return ""; }
    @Override public String getCatalogTerm() throws SQLException { return ""; }
    @Override public boolean isCatalogAtStart() throws SQLException { return false; }
    @Override public String getCatalogSeparator() throws SQLException { return ""; }
    @Override public boolean supportsSchemasInDataManipulation() throws SQLException { return false; }
    @Override public boolean supportsSchemasInProcedureCalls() throws SQLException { return false; }
    @Override public boolean supportsSchemasInTableDefinitions() throws SQLException { return false; }
    @Override public boolean supportsSchemasInIndexDefinitions() throws SQLException { return false; }
    @Override public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException { return false; }
    @Override public boolean supportsCatalogsInDataManipulation() throws SQLException { return false; }
    @Override public boolean supportsCatalogsInProcedureCalls() throws SQLException { return false; }
    @Override public boolean supportsCatalogsInTableDefinitions() throws SQLException { return false; }
    @Override public boolean supportsCatalogsInIndexDefinitions() throws SQLException { return false; }
    @Override public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException { return false; }
    @Override public boolean supportsPositionedDelete() throws SQLException { return false; }
    @Override public boolean supportsPositionedUpdate() throws SQLException { return false; }
    @Override public boolean supportsSelectForUpdate() throws SQLException { return false; }
    @Override public boolean supportsStoredProcedures() throws SQLException { return false; }
    @Override public boolean supportsSubqueriesInComparisons() throws SQLException { return false; }
    @Override public boolean supportsSubqueriesInExists() throws SQLException { return false; }
    @Override public boolean supportsSubqueriesInIns() throws SQLException { return false; }
    @Override public boolean supportsSubqueriesInQuantifieds() throws SQLException { return false; }
    @Override public boolean supportsCorrelatedSubqueries() throws SQLException { return false; }
    @Override public boolean supportsUnion() throws SQLException { return false; }
    @Override public boolean supportsUnionAll() throws SQLException { return false; }
    @Override public boolean supportsOpenCursorsAcrossCommit() throws SQLException { return false; }
    @Override public boolean supportsOpenCursorsAcrossRollback() throws SQLException { return false; }
    @Override public boolean supportsOpenStatementsAcrossCommit() throws SQLException { return false; }
    @Override public boolean supportsOpenStatementsAcrossRollback() throws SQLException { return false; }
    @Override public int getMaxBinaryLiteralLength() throws SQLException { return 0; }
    @Override public int getMaxCharLiteralLength() throws SQLException { return 0; }
    @Override public int getMaxColumnNameLength() throws SQLException { return 0; }
    @Override public int getMaxColumnsInGroupBy() throws SQLException { return 0; }
    @Override public int getMaxColumnsInIndex() throws SQLException { return 0; }
    @Override public int getMaxColumnsInOrderBy() throws SQLException { return 0; }
    @Override public int getMaxColumnsInSelect() throws SQLException { return 0; }
    @Override public int getMaxColumnsInTable() throws SQLException { return 0; }
    @Override public int getMaxConnections() throws SQLException { return 1; }
    @Override public int getMaxCursorNameLength() throws SQLException { return 0; }
    @Override public int getMaxIndexLength() throws SQLException { return 0; }
    @Override public int getMaxSchemaNameLength() throws SQLException { return 0; }
    @Override public int getMaxProcedureNameLength() throws SQLException { return 0; }
    @Override public int getMaxCatalogNameLength() throws SQLException { return 0; }
    @Override public int getMaxRowSize() throws SQLException { return 0; }
    @Override public boolean doesMaxRowSizeIncludeBlobs() throws SQLException { return false; }
    @Override public int getMaxStatementLength() throws SQLException { return 0; }
    @Override public int getMaxStatements() throws SQLException { return 0; }
    @Override public int getMaxTableNameLength() throws SQLException { return 0; }
    @Override public int getMaxTablesInSelect() throws SQLException { return 0; }
    @Override public int getMaxUserNameLength() throws SQLException { return 0; }
    @Override public boolean supportsSavepoints() throws SQLException { return false; }
    @Override public boolean supportsNamedParameters() throws SQLException { return false; }
    @Override public boolean supportsMultipleOpenResults() throws SQLException { return false; }
    @Override public boolean supportsGetGeneratedKeys() throws SQLException { return false; }
    @Override public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getAttributes(String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public boolean supportsResultSetHoldability(int holdability) throws SQLException { return false; }
    @Override public int getResultSetHoldability() throws SQLException { return ResultSet.CLOSE_CURSORS_AT_COMMIT; }
    @Override public int getSQLStateType() throws SQLException { return sqlStateSQL; }
    @Override public boolean locatorsUpdateCopy() throws SQLException { return false; }
    @Override public boolean supportsStatementPooling() throws SQLException { return false; }
    @Override public RowIdLifetime getRowIdLifetime() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException { return false; }
    @Override public boolean autoCommitFailureClosesAllResultSets() throws SQLException { return false; }
    @Override public ResultSet getClientInfoProperties() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getFunctionColumns(String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return false; }
}
