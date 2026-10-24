package vn.vietroute.business.location.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import org.springframework.stereotype.Repository;
import vn.vietroute.business.location.dto.request.LocationCreateRequest;
import vn.vietroute.business.location.dto.request.LocationLookupRequest;
import vn.vietroute.business.location.dto.request.LocationUpdateRequest;
import vn.vietroute.business.location.dto.request.LocationWriteRequest;
import vn.vietroute.business.location.dto.request.ProvinceCreateRequest;
import vn.vietroute.business.location.dto.request.ProvinceLookupRequest;
import vn.vietroute.business.location.dto.request.ProvinceUpdateRequest;
import vn.vietroute.business.location.entity.Location;
import vn.vietroute.business.location.entity.Province;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** JDBC danh mục dùng chung; projection/filter/count tại DB, không nhận tên bảng/cột từ HTTP. */
@Repository
public class LocationRepository extends BaseRepository {

    private static final String PROVINCE_COLUMNS =
        "id,code,name_vi,name_en,sort_order,active,created_at,updated_at,created_by,updated_by,version";

    private static final String LOCATION_COLUMNS =
        "l.id,l.province_id,l.code,l.name_vi,l.name_en,l.address,l.latitude,l.longitude,l.sort_order,l.active,l.created_at,l.updated_at,l.created_by,l.updated_by,l.version,p.name_vi AS province_name_vi,p.name_en AS province_name_en,p.active AS province_active";

    private static final String LOCATION_FROM =
        " FROM location_places l JOIN location_provinces p ON p.id=l.province_id";

    public LocationRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    /** Cùng predicate cho count/data; tra công khai truyền active=true, quản trị được đọc trạng thái tắt. */
    public PageResponse<Province> provinces(ProvinceLookupRequest page, Boolean active) {
        return read(connection -> {
            String from =
                " FROM location_provinces WHERE (?::boolean IS NULL OR active=?) AND concat_ws(' ',code,name_vi,name_en) ILIKE ? ESCAPE '!'";
            long total;
            try (var query = connection.prepareStatement("SELECT count(*)" + from)) {
                bindProvinceSearch(query, page, active);
                try (var rows = query.executeQuery()) {
                    rows.next();
                    total = rows.getLong(1);
                }
            }
            var content = new ArrayList<Province>();
            try (
                var query = connection.prepareStatement(
                    "SELECT " + PROVINCE_COLUMNS + from + " ORDER BY sort_order,id LIMIT ? OFFSET ?"
                )
            ) {
                bindProvinceSearch(query, page, active);
                query.setInt(4, page.getSize());
                query.setLong(5, page.offset());
                try (var rows = query.executeQuery()) {
                    while (rows.next()) content.add(province(rows));
                }
            }
            return PageResponse.of(content, page, total);
        });
    }

    /** publicOnly là lựa chọn nội bộ của service, không lấy từ query HTTP; tỉnh tắt luôn ẩn địa điểm công khai. */
    public PageResponse<Location> locations(LocationLookupRequest page, Boolean active, boolean publicOnly) {
        return read(connection -> {
            String from =
                LOCATION_FROM +
                " WHERE (?::bigint IS NULL OR l.province_id=?) AND (?::boolean IS NULL OR l.active=?) AND (NOT ? OR p.active) AND concat_ws(' ',l.code,l.name_vi,l.name_en,l.address,p.name_vi,p.name_en) ILIKE ? ESCAPE '!'";
            long total;
            try (var query = connection.prepareStatement("SELECT count(*)" + from)) {
                bindLocationSearch(query, page, active, publicOnly);
                try (var rows = query.executeQuery()) {
                    rows.next();
                    total = rows.getLong(1);
                }
            }
            var content = new ArrayList<Location>();
            try (
                var query = connection.prepareStatement(
                    "SELECT " + LOCATION_COLUMNS + from + " ORDER BY l.sort_order,l.id LIMIT ? OFFSET ?"
                )
            ) {
                bindLocationSearch(query, page, active, publicOnly);
                query.setInt(7, page.getSize());
                query.setLong(8, page.offset());
                try (var rows = query.executeQuery()) {
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
        try (
            var query = connection.prepareStatement(
                "SELECT " + PROVINCE_COLUMNS + " FROM location_provinces WHERE id=?"
            )
        ) {
            query.setLong(1, id);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                return province(row);
            }
        }
    }

    public Location location(Connection connection, long id) throws SQLException {
        try (var query = connection.prepareStatement("SELECT " + LOCATION_COLUMNS + LOCATION_FROM + " WHERE l.id=?")) {
            query.setLong(1, id);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                return location(row);
            }
        }
    }

    /** Khóa tỉnh trước khi tạo/bật địa điểm để không chạy lọt cập nhật trạng thái tỉnh đồng thời. Caller sở hữu transaction. */
    public void requireActiveProvince(Connection connection, long id) throws SQLException {
        try (var query = connection.prepareStatement("SELECT active FROM location_provinces WHERE id=? FOR SHARE")) {
            query.setLong(1, id);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                if (!row.getBoolean(1)) throw new BusinessException(ErrorCode.LOCATION_PROVINCE_INACTIVE);
            }
        }
    }

    /** INSERT lấy audit từ principal; code trùng chốt bằng UNIQUE và mapper COM005. */
    public Province createProvince(Connection connection, ProvinceCreateRequest body, long actor) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "INSERT INTO location_provinces(code,name_vi,name_en,sort_order,active,created_by,updated_by) VALUES (?,?,?,?,?,?,?) RETURNING " +
                    PROVINCE_COLUMNS
            )
        ) {
            query.setString(1, body.getCode());
            query.setString(2, body.getNameVi().strip());
            query.setString(3, body.getNameEn().strip());
            query.setInt(4, body.getSortOrder());
            query.setBoolean(5, body.isActive());
            query.setLong(6, actor);
            query.setLong(7, actor);
            try (var row = query.executeQuery()) {
                row.next();
                return province(row);
            }
        }
    }

    /** Tắt chỉ sửa cờ/version của tỉnh, không xóa/chuyển trạng thái riêng của địa điểm con hoặc lịch sử. */
    public Province updateProvince(Connection connection, long id, ProvinceUpdateRequest body, long actor)
        throws SQLException {
        try (
            var query = connection.prepareStatement(
                "UPDATE location_provinces SET name_vi=?,name_en=?,sort_order=?,active=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND version=? RETURNING " +
                    PROVINCE_COLUMNS
            )
        ) {
            query.setString(1, body.getNameVi().strip());
            query.setString(2, body.getNameEn().strip());
            query.setInt(3, body.getSortOrder());
            query.setBoolean(4, body.isActive());
            query.setLong(5, actor);
            query.setLong(6, id);
            query.setLong(7, body.getVersion());
            try (var row = query.executeQuery()) {
                if (row.next()) return province(row);
            }
        }
        province(connection, id);
        throw new BusinessException(ErrorCode.STALE_VERSION);
    }

    public Location createLocation(Connection connection, LocationCreateRequest body, long actor) throws SQLException {
        long id;
        try (
            var query = connection.prepareStatement(
                "INSERT INTO location_places(name_vi,name_en,address,latitude,longitude,sort_order,active,created_by,updated_by,province_id,code) VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING id"
            )
        ) {
            bindLocationWrite(query, body, actor);
            query.setLong(10, body.getProvinceId());
            query.setString(11, body.getCode());
            try (var row = query.executeQuery()) {
                row.next();
                id = row.getLong(1);
            }
        }
        return location(connection, id);
    }

    /** Compare-and-set version trong SQL; không cho đổi code/province_id, không tự commit connection của service. */
    public Location updateLocation(Connection connection, long id, LocationUpdateRequest body, long actor)
        throws SQLException {
        try (
            var query = connection.prepareStatement(
                "UPDATE location_places SET name_vi=?,name_en=?,address=?,latitude=?,longitude=?,sort_order=?,active=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND version=? RETURNING id"
            )
        ) {
            bindLocationFields(query, body);
            query.setLong(8, actor);
            query.setLong(9, id);
            query.setLong(10, body.getVersion());
            try (var row = query.executeQuery()) {
                if (row.next()) return location(connection, id);
            }
        }
        location(connection, id);
        throw new BusinessException(ErrorCode.STALE_VERSION);
    }

    private static void bindLocationWrite(PreparedStatement query, LocationWriteRequest body, long actor)
        throws SQLException {
        bindLocationFields(query, body);
        query.setLong(8, actor);
        query.setLong(9, actor);
    }

    private static void bindLocationFields(PreparedStatement query, LocationWriteRequest body) throws SQLException {
        query.setString(1, body.getNameVi().strip());
        query.setString(2, body.getNameEn().strip());
        query.setString(3, body.getAddress().strip());
        query.setBigDecimal(4, body.getLatitude());
        query.setBigDecimal(5, body.getLongitude());
        query.setInt(6, body.getSortOrder());
        query.setBoolean(7, body.isActive());
    }

    private static void bindProvinceSearch(PreparedStatement query, ProvinceLookupRequest page, Boolean active)
        throws SQLException {
        query.setObject(1, active, Types.BOOLEAN);
        query.setObject(2, active, Types.BOOLEAN);
        query.setString(3, pattern(page.getQuery()));
    }

    private static void bindLocationSearch(
        PreparedStatement query,
        LocationLookupRequest page,
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
}
