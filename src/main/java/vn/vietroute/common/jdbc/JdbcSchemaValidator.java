package vn.vietroute.common.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Đối chiếu metadata entity với DB sau migration, trước seed; sai cấu trúc thì dừng startup để rollback. */
public final class JdbcSchemaValidator {

    private JdbcSchemaValidator() {}

    /** Kiểm tra schema, primary key và tên/thứ tự cột/tính unique của từng index được khai báo. */
    public static void verify(Connection connection, Class<?>... entities) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String schema = connection.getSchema();
        JdbcSchema.validate(schema);
        for (Class<?> entity : entities) {
            JdbcTable table = entity.getAnnotation(JdbcTable.class);
            if (table == null) {
                throw new IllegalStateException("Missing application table metadata: " + entity.getSimpleName());
            }
            verifyPrimaryKey(metadata, schema, table);
            verifyIndexes(metadata, schema, table);
        }
    }

    private static void verifyPrimaryKey(DatabaseMetaData metadata, String schema, JdbcTable table)
        throws SQLException {
        Map<Short, String> columns = new TreeMap<>();
        try (ResultSet keys = metadata.getPrimaryKeys(null, schema, table.name())) {
            while (keys.next()) {
                columns.put(keys.getShort("KEY_SEQ"), keys.getString("COLUMN_NAME"));
            }
        }
        if (!List.copyOf(columns.values()).equals(List.of(table.primaryKey()))) {
            throw new IllegalStateException("Primary key mismatch: " + schema + "." + table.name());
        }
    }

    private static void verifyIndexes(DatabaseMetaData metadata, String schema, JdbcTable table) throws SQLException {
        Map<String, Map<Short, String>> columns = new HashMap<>();
        Map<String, Boolean> unique = new HashMap<>();
        try (ResultSet indexes = metadata.getIndexInfo(null, schema, table.name(), false, false)) {
            while (indexes.next()) {
                String name = indexes.getString("INDEX_NAME");
                String column = indexes.getString("COLUMN_NAME");
                if (name != null && column != null) {
                    columns
                        .computeIfAbsent(name, ignored -> new TreeMap<>())
                        .put(indexes.getShort("ORDINAL_POSITION"), column);
                    unique.put(name, !indexes.getBoolean("NON_UNIQUE"));
                }
            }
        }
        for (JdbcIndex index : table.indexes()) {
            Map<Short, String> actual = columns.get(index.name());
            if (
                actual == null ||
                !List.copyOf(actual.values()).equals(List.of(index.columns())) ||
                !Boolean.valueOf(index.unique()).equals(unique.get(index.name()))
            ) {
                throw new IllegalStateException("Index mismatch: " + schema + "." + index.name());
            }
        }
    }
}
