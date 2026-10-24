import java.sql.Connection;
import java.sql.SQLException;
import org.postgresql.ds.PGSimpleDataSource;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcSchema;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Chỉ PostgreSQL/PgBouncer Docker CI; kiểm schema và commit/rollback thật, không dùng .env/Neon. */
public final class TransactionPoolerInspector {

    private TransactionPoolerInspector() {}

    public static void main(String[] args) throws Exception {
        String url = System.getenv("CI_POOLER_DB_URL");
        if (!"jdbc:postgresql://vietroute-db-ci:6432/vietroute_ci".equals(url)) throw new AssertionError("Chỉ DB CI");
        var source = new PGSimpleDataSource();
        source.setURL(url);
        source.setUser(System.getenv("CI_GUARD_DB_USER"));
        source.setPassword(System.getenv("CI_GUARD_DB_PASSWORD"));
        var factory = new JdbcConnectionFactory(source, new JdbcSchema("vietroute"));
        var transactions = new JdbcTransactions(factory);
        transactions.execute(connection -> {
            try (var query = connection.createStatement()) {
                query.execute("CREATE TABLE ci_transaction_probe(id INTEGER PRIMARY KEY)");
            }
            return null;
        });
        try {
            // Pooler cố tình DISCARD ALL sau mỗi transaction: tái hiện set schema kiểu cũ bị mất.
            try (var connection = source.getConnection(); var query = connection.createStatement()) {
                query.execute("SELECT pg_catalog.set_config('search_path','\"vietroute\", pg_temp',false)");
                try (var row = query.executeQuery("SELECT to_regclass('ci_transaction_probe')")) {
                    row.next();
                    if (row.getObject(1) != null) throw new AssertionError("Pooler CI phải reset session state");
                }
            }
            var probe = new Probe(factory);
            probe.add(1);
            if (probe.count() != 1) throw new AssertionError("Callback repository chưa commit");
            try {
                transactions.execute(connection -> {
                    insert(connection, 2);
                    throw new BusinessException(ErrorCode.CONFLICT);
                });
                throw new AssertionError("Phải giữ lỗi nghiệp vụ");
            } catch (BusinessException error) {
                if (error.getErrorCode() != ErrorCode.CONFLICT) throw error;
            }
            try {
                probe.fail();
                throw new AssertionError("Phải báo lỗi callback");
            } catch (BusinessException error) {
                if (error.getErrorCode() != ErrorCode.DATABASE_UNAVAILABLE) throw error;
            }
            if (probe.count() != 1) throw new AssertionError("Callback lỗi phải rollback toàn bộ");
            for (int index = 0; index < 8; index++) {
                if (probe.count() != 1) throw new AssertionError("Schema phải đúng qua mọi lượt mượn");
            }
            transactions.execute(connection -> {
                try (var query = connection.createStatement()) {
                    query.execute("CREATE TEMP TABLE ci_transaction_probe(id INTEGER) ON COMMIT DROP");
                    try (var row = query.executeQuery("SELECT count(*) FROM ci_transaction_probe")) {
                        row.next();
                        if (row.getInt(1) != 1) throw new AssertionError("pg_temp không được che bảng ứng dụng");
                    }
                }
                return null;
            });
        } finally {
            transactions.execute(connection -> {
                try (var query = connection.createStatement()) {
                    query.execute("DROP TABLE ci_transaction_probe");
                }
                return null;
            });
        }
        System.out.println("Pooler reset reproduced; LOCAL schema, commit, rollback and temp shadow checks passed");
    }

    private static void insert(Connection connection, int id) throws SQLException {
        try (var query = connection.prepareStatement("INSERT INTO ci_transaction_probe(id) VALUES (?)")) {
            query.setInt(1, id);
            query.executeUpdate();
        }
    }

    /** Dùng đúng BaseRepository production để phát hiện callback thành công mà quên commit sau đổi factory. */
    static final class Probe extends BaseRepository {

        Probe(JdbcConnectionFactory connections) {
            super(connections);
        }

        void add(int id) {
            read(connection -> {
                insert(connection, id);
                return null;
            });
        }

        int count() {
            return read(connection -> {
                try (
                    var query = connection.createStatement();
                    var row = query.executeQuery("SELECT count(*) FROM ci_transaction_probe")
                ) {
                    row.next();
                    return row.getInt(1);
                }
            });
        }

        void fail() {
            read(connection -> {
                insert(connection, 3);
                throw new SQLException("Fixture rollback only", "40001");
            });
        }
    }
}
