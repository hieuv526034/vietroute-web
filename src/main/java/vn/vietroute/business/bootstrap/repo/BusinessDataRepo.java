package vn.vietroute.business.bootstrap.repo;

import static vn.vietroute.common.base.BaseRepository.booleanValue;
import static vn.vietroute.common.base.BaseRepository.longValue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import vn.vietroute.business.bootstrap.dto.response.BusinessDataResponse;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Dữ liệu khởi tạo theo tập từ JSON phiên bản trong source; giữ dữ liệu có sẵn, không query/save theo từng dòng. */
@Repository
@RequiredArgsConstructor
public class BusinessDataRepo {

    private final EntityManager entityManager;

    private final ObjectMapper json;
    private final AppSettings settings;
    private final BusinessDataCustomRepo batch;

    /** Một transaction do caller sở hữu, advisory lock serialize mọi lần seed và kiểm quota phạm vi. */
    public BusinessDataResponse seed(Connection connection, long actor) throws SQLException {
        return seed(connection, actor, load("bootstrap/foundation.json"));
    }

    /** Đọc bộ dữ liệu đóng gói theo tên resource cố định của server, không nhận đường dẫn từ HTTP. */
    private JsonNode load(String resource) {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            return json.readTree(input);
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /** Bộ mở rộng chỉ dùng startup opt-in; API nền vẫn có phạm vi nhỏ, không tự cấp tài khoản. */
    public void seedAdditionalFoundation(Connection connection) throws SQLException {
        seed(connection, 0, load("bootstrap/operations.json"));
    }

    private BusinessDataResponse seed(Connection connection, long actor, JsonNode data) throws SQLException {
        {
            Query lock = entityManager.createNativeQuery(
                "SELECT CAST(PG_ADVISORY_XACT_LOCK(781205611) AS TEXT),CAST(PG_ADVISORY_XACT_LOCK(781205690) AS TEXT)"
            );

            lock.getSingleResult();
        }
        // Cùng mã nhưng đơn vị khác phải dừng thay vì ghép xe/tài xế vào nhầm nhà xe.
        String carriers = data.path("carriers").toString();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT (
                    SELECT COUNT(*)
                    FROM {h-schema}carriers
                ) + (
                    SELECT COUNT(*)
                    FROM JSONB_TO_RECORDSET(CAST(:carriers AS JSONB)) x(code TEXT, name TEXT)
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM {h-schema}carriers c
                        WHERE c.code = x.code
                    )
                ), EXISTS (
                    SELECT 1
                    FROM JSONB_TO_RECORDSET(CAST(:carriers AS JSONB)) x(code TEXT, name TEXT)
                    JOIN {h-schema}carriers c USING(code)
                    WHERE c.name <> x.name
                )
                """,
                Tuple.class
            );

            query.setParameter("carriers", carriers);
            {
                Tuple row = (Tuple) query.getSingleResult();
                if (booleanValue(row.get(1))) throw new BusinessException(ErrorCode.CONFLICT);
                if (longValue(row.get(0)) > settings.getScopeCapacity()) throw new BusinessException(
                    ErrorCode.ACCESS_CAPACITY_REACHED,
                    settings.getScopeCapacity()
                );
            }
        }
        return batch.seed(connection, actor, data);
    }

    public void seedRoutes(Connection connection) throws SQLException {
        batch.seedRoutes(connection);
    }

    public void seedOperations(Connection connection) throws SQLException {
        batch.seedOperations(connection);
    }
}
