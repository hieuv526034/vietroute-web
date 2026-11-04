package vn.vietroute.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import vn.vietroute.common.base.BaseRepository.SqlWork;
import vn.vietroute.common.config.database.SchemaDataSource;

/** Chỉ test loopback: lấy SQL/bind từ DAO thật, replay EXPLAIN SELECT; không sửa SQL nghiệp vụ hoặc in payload. */
public final class SqlPlanProbe {

    private SqlPlanProbe() {}

    private record Binding(Method method, Object[] arguments) {}

    public record Select(String sql, List<Binding> bindings) {
        public JsonNode explain(Connection connection) throws Exception {
            if (!sql.stripLeading().matches("(?is)^(SELECT|WITH)\\b.*")) throw new IllegalArgumentException(
                "Chỉ đo SELECT"
            );
            try (var query = connection.prepareStatement("EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON) " + sql)) {
                for (var binding : bindings) binding.method().invoke(query, binding.arguments());
                try (var row = query.executeQuery()) {
                    row.next();
                    return new ObjectMapper().readTree(row.getString(1)).get(0);
                }
            }
        }
    }

    private static final ThreadLocal<List<Select>> CAPTURE = new ThreadLocal<>();

    /** Chỉ fixture test: quan sát datasource thật bên dưới cả Hibernate và JDBC, không đổi transaction key. */
    public static void install(SchemaDataSource source) {
        if (source.getTargetDataSource() instanceof ObservedDataSource) return;
        source.setTargetDataSource(new ObservedDataSource(source.getTargetDataSource()));
    }

    /** Callback vẫn chạy DAO thật; lấy SQL/bind từ connection mà Hibernate thực sự sử dụng. */
    public static List<Select> capture(Connection connection, SqlWork<?> work) throws SQLException {
        if (CAPTURE.get() != null) throw new IllegalStateException("Không lồng hai vùng đo SQL");
        var selects = new ArrayList<Select>();
        CAPTURE.set(selects);
        try {
            work.apply(connection);
            return List.copyOf(selects);
        } finally {
            CAPTURE.remove();
        }
    }

    private static final class ObservedDataSource extends DelegatingDataSource {

        ObservedDataSource(DataSource target) {
            super(target);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return observe(super.getConnection());
        }

        @Override
        public Connection getConnection(String user, String password) throws SQLException {
            return observe(super.getConnection(user, password));
        }
    }

    private static Connection observe(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] { Connection.class },
            (proxy, method, args) -> {
                try {
                    var result = method.invoke(connection, args);
                    if (!method.getName().equals("prepareStatement") || CAPTURE.get() == null) return result;
                    String sql = (String) args[0];
                    if (!sql.stripLeading().matches("(?is)^(SELECT|WITH)\\b.*")) return result;
                    var statement = (PreparedStatement) result;
                    Map<Integer, Binding> bindings = new LinkedHashMap<>();
                    return Proxy.newProxyInstance(
                        PreparedStatement.class.getClassLoader(),
                        new Class<?>[] { PreparedStatement.class },
                        (target, call, values) -> {
                            try {
                                if (call.getName().equals("clearParameters")) bindings.clear();
                                if (
                                    call.getName().startsWith("set") &&
                                    values != null &&
                                    values.length >= 2 &&
                                    values[0] instanceof Integer at
                                ) {
                                    bindings.put(at, new Binding(call, values.clone()));
                                }
                                if (call.getName().equals("executeQuery") && CAPTURE.get() != null) {
                                    CAPTURE.get().add(new Select(sql, List.copyOf(bindings.values())));
                                }
                                return call.invoke(statement, values);
                            } catch (InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                        }
                    );
                } catch (InvocationTargetException failure) {
                    throw failure.getCause();
                }
            }
        );
    }

    public static Set<String> indexes(JsonNode plan) {
        var result = new LinkedHashSet<String>();
        if (plan.has("Index Name")) result.add(plan.path("Index Name").asText());
        for (var item : plan) if (item.isContainerNode()) result.addAll(indexes(item));
        return Set.copyOf(result);
    }

    public static Map<String, Object> summary(JsonNode plan) {
        return Map.of(
            "executionMs",
            plan.path("Execution Time").asDouble(),
            "sharedBlocks",
            plan.path("Plan").path("Shared Hit Blocks").asLong() +
                plan.path("Plan").path("Shared Read Blocks").asLong(),
            "indexes",
            indexes(plan)
        );
    }
}
