package vn.vietroute.business.media.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Trạng thái ảnh độc lập với nhà cung cấp; publicId chỉ dùng nội bộ để phục hồi/xóa. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JdbcTable(
    name = "media_assets",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "media_assets_public_id_key", columns = { "public_id" }, unique = true),
        @JdbcIndex(name = "idx_media_owner_created", columns = { "created_by", "created_at", "id" }),
        @JdbcIndex(name = "idx_media_status_created", columns = { "status", "created_at", "id" }),
        @JdbcIndex(name = "idx_media_updated_by", columns = { "updated_by" }),
    }
)
public class MediaAsset extends BaseEntity {

    private String publicId;

    private String status;

    private String url;

    private String thumbnailUrl;

    private String format;

    private Long bytes;

    private Integer width;

    private Integer height;
}
