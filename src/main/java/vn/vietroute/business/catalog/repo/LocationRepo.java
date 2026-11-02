package vn.vietroute.business.catalog.repo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import org.springframework.stereotype.Repository;
import vn.vietroute.business.catalog.dto.request.LocationRequest;
import vn.vietroute.business.catalog.dto.request.ProvinceRequest;
import vn.vietroute.business.catalog.dto.search.LocationLookupSearch;
import vn.vietroute.business.catalog.dto.search.ProvinceLookupSearch;
import vn.vietroute.business.catalog.entity.Location;
import vn.vietroute.business.catalog.entity.Province;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.base.JpaRepositorySupport;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** JDBC danh mục dùng chung; projection/filter/count tại DB, không nhận tên bảng/cột từ HTTP. */
@Repository
public class LocationRepo extends JpaRepositorySupport<Location, Long> {

    private static final String PROVINCE_COLUMNS =
        "id,code,name_vi,name_en,sort_order,active,created_at,updated_at,created_by,updated_by,version";

    private static final String LOCATION_COLUMNS = """
    l.id, l.province_id, l.code, l.name_vi, l.name_en, l.address, l.latitude, l.longitude, l.sort_order,
        l.active, l.created_at, l.updated_at, l.created_by, l.updated_by, l.version, p.name_vi AS province_name_vi,
        p.name_en AS province_name_en, p.active AS province_active
    """;

    private static final String LOCATION_FROM = """
    FROM location_places l
    JOIN location_provinces p ON p.id = l.province_id
    """;

    public LocationRepo(EntityManager manager, JdbcTransactions transactions) {
        super(Location.class, manager, transactions);
    }

    /** Cùng predicate cho count/data; tra công khai truyền active=true, quản trị được đọc trạng thái tắt. */
    public PageResponse<Province> provinces(ProvinceLookupSearch page, Boolean active) {
        return read(connection -> {
            String from = """
            FROM location_provinces
            WHERE (?::boolean IS NULL OR active = ?)
                AND concat_ws(' ', code, name_vi, name_en) ILIKE ? ESCAPE '!'
            """;
            long total;
            try (PreparedStatement query = connection.prepareStatement("SELECT count(*)" + from)) {
                bindProvinceSearch(query, page, active);
                try (ResultSet rows = query.executeQuery()) {
                    rows.next();
                    total = rows.getLong(1);
                }
            }
            ArrayList<Province> content = new ArrayList<Province>();
            try (
                PreparedStatement query = connection.prepareStatement(
                    """
                    SELECT %s
                    %s
                    ORDER BY sort_order, id
                    LIMIT ? OFFSET ?
                    """.formatted(PROVINCE_COLUMNS, from)
                )
            ) {
                bindProvinceSearch(query, page, active);
                query.setInt(4, page.getSize());
                query.setLong(5, page.offset());
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) content.add(province(rows));
                }
            }
            return PageResponse.of(content, page, total);
        });
    }

    /** publicOnly là lựa chọn nội bộ của service, không lấy từ query HTTP; tỉnh tắt luôn ẩn địa điểm công khai. */
    public PageResponse<Location> locations(LocationLookupSearch page, Boolean active, boolean publicOnly) {
        return read(connection -> {
            String from = """
            %s
            WHERE (?::bigint IS NULL OR l.province_id = ?)
                AND (?::boolean IS NULL OR l.active = ?)
                AND (NOT ? OR p.active)
                AND concat_ws(' ', l.code, l.name_vi, l.name_en, l.address, p.name_vi, p.name_en) ILIKE ? ESCAPE '!'
            """.formatted(LOCATION_FROM);
            long total;
            try (PreparedStatement query = connection.prepareStatement("SELECT count(*)" + from)) {
                bindLocationSearch(query, page, active, publicOnly);
                try (ResultSet rows = query.executeQuery()) {
                    rows.next();
                    total = rows.getLong(1);
                }
            }
            ArrayList<Location> content = new ArrayList<Location>();
            try (
                PreparedStatement query = connection.prepareStatement(
                    """
                    SELECT %s
                    %s
                    ORDER BY l.sort_order, l.id
                    LIMIT ? OFFSET ?
                    """.formatted(LOCATION_COLUMNS, from)
                )
            ) {
                bindLocationSearch(query, page, active, publicOnly);
                query.setInt(7, page.getSize());
                query.setLong(8, page.offset());
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) content.add(location(rows));
                }
            }
            return PageResponse.of(content, page, total);
        });
    }

    public Province province(long id) {
        return read(connection -> province(connection, id));
    }

    public Location location(long id) {
        return read(connection -> location(connection, id));
    }

    public Province province(Connection connection, long id) throws SQLException {
        return BusinessRules.requireFound(entityManager.find(Province.class, id));
    }

    public Location location(Connection connection, long id) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT " + LOCATION_COLUMNS + LOCATION_FROM + " WHERE l.id=?"
            )
        ) {
            query.setLong(1, id);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                return location(row);
            }
        }
    }

    /** Khóa tỉnh trước khi tạo/bật địa điểm để không chạy lọt cập nhật trạng thái tỉnh đồng thời. Caller sở hữu transaction. */
    public void requireActiveProvince(Connection connection, long id) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT active FROM location_provinces WHERE id=? FOR SHARE"
            )
        ) {
            query.setLong(1, id);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                if (!row.getBoolean(1)) throw new BusinessException(ErrorCode.LOCATION_PROVINCE_INACTIVE);
            }
        }
    }

    /** INSERT lấy audit từ principal; code trùng chốt bằng UNIQUE và mapper COM005. */
    public Province createProvince(Connection connection, ProvinceRequest body, long actor) {
        return created(
            Province.builder()
                .code(body.getCode())
                .nameVi(body.getNameVi().strip())
                .nameEn(body.getNameEn().strip())
                .sortOrder(body.getSortOrder())
                .active(body.isActive())
                .build(),
            actor
        );
    }

    /** Tắt chỉ sửa cờ/version của tỉnh, không xóa/chuyển trạng thái riêng của địa điểm con hoặc lịch sử. */
    public Province updateProvince(Connection connection, long id, ProvinceRequest body, long actor) {
        Province value = BusinessRules.requireFound(
            entityManager.find(Province.class, id, LockModeType.PESSIMISTIC_WRITE)
        );
        BusinessRules.require(
            body.getCode() == null || value.getCode().equals(body.getCode()),
            ErrorCode.INVALID_REQUEST
        );
        BusinessRules.requireVersion(body.getVersion(), value.getVersion());
        value.setNameVi(body.getNameVi().strip());
        value.setNameEn(body.getNameEn().strip());
        value.setSortOrder(body.getSortOrder());
        value.setActive(body.isActive());
        changed(value, actor);
        return value;
    }

    public Location createLocation(Connection connection, LocationRequest body, long actor) throws SQLException {
        Location value = Location.builder().code(body.getCode()).provinceId(body.getProvinceId()).build();
        locationFields(value, body);
        created(value, actor);
        return location(connection, value.getId());
    }

    /** Compare-and-set version trong SQL; không cho đổi code/province_id, không tự commit connection của service. */
    public Location updateLocation(Connection connection, long id, LocationRequest body, long actor)
        throws SQLException {
        Location value = BusinessRules.requireFound(
            entityManager.find(Location.class, id, LockModeType.PESSIMISTIC_WRITE)
        );
        BusinessRules.require(
            (body.getCode() == null || value.getCode().equals(body.getCode())) &&
                (body.getProvinceId() == null || value.getProvinceId().equals(body.getProvinceId())),
            ErrorCode.INVALID_REQUEST
        );
        BusinessRules.requireVersion(body.getVersion(), value.getVersion());
        locationFields(value, body);
        changed(value, actor);
        return location(connection, id);
    }

    private static void bindProvinceSearch(PreparedStatement query, ProvinceLookupSearch page, Boolean active)
        throws SQLException {
        query.setObject(1, active, Types.BOOLEAN);
        query.setObject(2, active, Types.BOOLEAN);
        query.setString(3, pattern(page.getQuery()));
    }

    private static void bindLocationSearch(
        PreparedStatement query,
        LocationLookupSearch page,
        Boolean active,
        boolean publicOnly
    ) throws SQLException {
        query.setObject(1, page.getProvinceId(), Types.BIGINT);
        query.setObject(2, page.getProvinceId(), Types.BIGINT);
        query.setObject(3, active, Types.BOOLEAN);
        query.setObject(4, active, Types.BOOLEAN);
        query.setBoolean(5, publicOnly);
        query.setString(6, pattern(page.getQuery()));
    }

    private static String pattern(String query) {
        return "%" + query.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static Province province(ResultSet row) throws SQLException {
        return Province.builder()
            .id(row.getLong("id"))
            .code(row.getString("code"))
            .nameVi(row.getString("name_vi"))
            .nameEn(row.getString("name_en"))
            .sortOrder(row.getInt("sort_order"))
            .active(row.getBoolean("active"))
            .createdAt(row.getTimestamp("created_at").toInstant())
            .updatedAt(row.getTimestamp("updated_at").toInstant())
            .createdBy(row.getLong("created_by"))
            .updatedBy(row.getLong("updated_by"))
            .version(row.getLong("version"))
            .build();
    }

    private static Location location(ResultSet row) throws SQLException {
        return Location.builder()
            .id(row.getLong("id"))
            .code(row.getString("code"))
            .nameVi(row.getString("name_vi"))
            .nameEn(row.getString("name_en"))
            .provinceId(row.getLong("province_id"))
            .provinceNameVi(row.getString("province_name_vi"))
            .provinceNameEn(row.getString("province_name_en"))
            .provinceActive(row.getBoolean("province_active"))
            .address(row.getString("address"))
            .latitude(row.getBigDecimal("latitude"))
            .longitude(row.getBigDecimal("longitude"))
            .sortOrder(row.getInt("sort_order"))
            .active(row.getBoolean("active"))
            .createdAt(row.getTimestamp("created_at").toInstant())
            .updatedAt(row.getTimestamp("updated_at").toInstant())
            .createdBy(row.getLong("created_by"))
            .updatedBy(row.getLong("updated_by"))
            .version(row.getLong("version"))
            .build();
    }

    /** Timestamp/actor chỉ lấy từ server, không từ request; caller đang sở hữu transaction JPA/JDBC. */
    private <E extends BaseEntity> E created(E value, long actor) {
        Instant now = Instant.now();
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        value.setCreatedBy(actor);
        value.setUpdatedBy(actor);
        entityManager.persist(value);
        entityManager.flush();
        return value;
    }

    private void changed(BaseEntity value, long actor) {
        value.setUpdatedAt(Instant.now());
        value.setUpdatedBy(actor);
        entityManager.flush();
    }

    private static void locationFields(Location value, LocationRequest body) {
        value.setNameVi(body.getNameVi().strip());
        value.setNameEn(body.getNameEn().strip());
        value.setAddress(body.getAddress().strip());
        value.setLatitude(body.getLatitude());
        value.setLongitude(body.getLongitude());
        value.setSortOrder(body.getSortOrder());
        value.setActive(body.isActive());
    }
}
