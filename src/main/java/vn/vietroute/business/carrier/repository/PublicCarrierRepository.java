package vn.vietroute.business.carrier.repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.stereotype.Repository;
import vn.vietroute.business.carrier.dto.request.PublicCarrierSearchRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Projection công bố độc lập staff DAO; count/trang/ID luôn kiểm nhà xe và scope còn active. */
@Repository
public class PublicCarrierRepository {

    public record Published(long id, String code, String name, String phone, String description, Long logoId) {}

    private static final String FROM =
        " FROM carriers c JOIN auth_data_scopes s ON s.id=c.scope_id WHERE c.active AND s.active";
    private static final String COLUMNS = "c.id,c.code,c.name,c.phone,c.description,c.logo_media_id";

    /** Từ khóa nguyên văn và phân trang ổn định name/id được xử lý hoàn toàn trong PostgreSQL. */
    public PageResponse<Published> page(Connection connection, PublicCarrierSearchRequest body) throws SQLException {
        String where = FROM + " AND strpos(lower(concat_ws(' ',c.code,c.name,c.phone)),?)>0";
        String text = body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT);
        long total;
        try (var query = connection.prepareStatement("SELECT count(*)" + where)) {
            query.setString(1, text);
            try (var row = query.executeQuery()) {
                row.next();
                total = row.getLong(1);
            }
        }
        var result = new ArrayList<Published>();
        try (
            var query = connection.prepareStatement(
                "SELECT " + COLUMNS + where + " ORDER BY c.name,c.id LIMIT ? OFFSET ?"
            )
        ) {
            query.setString(1, text);
            query.setInt(2, body.getSize());
            query.setLong(3, body.offset());
            try (var row = query.executeQuery()) {
                while (row.next()) result.add(map(row));
            }
        }
        return PageResponse.of(result, body, total);
    }

    /** Nhà xe/scope ngừng công bố trả 404; không dùng membership để lọc mất lựa chọn công khai. */
    public Published find(Connection connection, long id) throws SQLException {
        try (var query = connection.prepareStatement("SELECT " + COLUMNS + FROM + " AND c.id=?")) {
            query.setLong(1, id);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                return map(row);
            }
        }
    }

    private Published map(ResultSet row) throws SQLException {
        return new Published(
            row.getLong(1),
            row.getString(2),
            row.getString(3),
            row.getString(4),
            row.getString(5),
            row.getObject(6, Long.class)
        );
    }
}
