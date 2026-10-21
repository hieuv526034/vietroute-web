package vn.vietroute.business.catalog.repository;

import java.sql.*;
import java.util.ArrayList;
import org.springframework.stereotype.Repository;
import vn.vietroute.business.catalog.dto.request.CatalogCreateRequest;
import vn.vietroute.business.catalog.entity.CatalogItem;
import vn.vietroute.common.base.*;
import vn.vietroute.common.constant.Constants.CatalogType;
import vn.vietroute.common.jdbc.*;

/** Repository mẫu cho module nghiệp vụ; mọi giá trị SQL đều được bind bằng PreparedStatement. */
@Repository
public class CatalogRepository extends BaseRepository {

    public CatalogRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    public PageResponse<CatalogItem> page(CatalogType type, PageRequest page) {
        return read(connection -> {
            long total;
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT COUNT(*) FROM catalog_items WHERE type=? AND active=true"
                )
            ) {
                query.setString(1, type.name());
                try (ResultSet count = query.executeQuery()) {
                    count.next();
                    total = count.getLong(1);
                }
            }
            var content = new ArrayList<CatalogItem>();
            // ORDER BY cố định: không nối trực tiếp sort từ request vào SQL.
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT * FROM catalog_items WHERE type=? AND active=true ORDER BY sort_order,id LIMIT ? OFFSET ?"
                )
            ) {
                query.setString(1, type.name());
                query.setInt(2, page.getSize());
                query.setLong(3, page.offset());
                try (ResultSet result = query.executeQuery()) {
                    while (result.next()) {
                        content.add(map(result));
                    }
                }
            }
            return PageResponse.of(content, page, total);
        });
    }

    public CatalogItem insert(Connection connection, CatalogCreateRequest request, String code, long actor)
        throws SQLException {
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO catalog_items(type,code,name_vi,name_en,sort_order,created_by,updated_by) VALUES (?,?,?,?,?,?,?) RETURNING *"
            )
        ) {
            insert.setString(1, request.getType().name());
            insert.setString(2, code);
            insert.setString(3, request.getNameVi().trim());
            insert.setString(4, request.getNameEn().trim());
            insert.setInt(5, request.getSortOrder());
            insert.setLong(6, actor);
            insert.setLong(7, actor);
            try (ResultSet result = insert.executeQuery()) {
                result.next();
                return map(result);
            }
        }
    }

    private CatalogItem map(ResultSet result) throws SQLException {
        return CatalogItem.builder()
            .id(result.getLong("id"))
            .type(CatalogType.valueOf(result.getString("type")))
            .code(result.getString("code"))
            .nameVi(result.getString("name_vi"))
            .nameEn(result.getString("name_en"))
            .sortOrder(result.getInt("sort_order"))
            .active(result.getBoolean("active"))
            .version(result.getLong("version"))
            .createdAt(result.getTimestamp("created_at").toInstant())
            .updatedAt(result.getTimestamp("updated_at").toInstant())
            .createdBy(result.getObject("created_by", Long.class))
            .updatedBy(result.getObject("updated_by", Long.class))
            .build();
    }
}
