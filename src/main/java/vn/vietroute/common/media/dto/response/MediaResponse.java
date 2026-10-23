package vn.vietroute.common.media.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseResponse;
import vn.vietroute.common.media.entity.MediaAsset;

/** Chỉ công bố metadata cần cho UI; không trả secret hoặc định danh quản trị của Cloudinary. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Schema(description = "Ảnh công khai cùng audit; URL chỉ có ý nghĩa khi trạng thái READY")
public class MediaResponse extends BaseResponse {

    @Schema(description = "PENDING, READY, DELETING hoặc DELETED", example = "READY")
    private String status;

    @Schema(description = "Tên ảnh")
    private String title;

    @Schema(description = "Mô tả thay thế")
    private String altText;

    @Schema(description = "URL HTTPS ảnh công khai trên CDN")
    private String url;

    @Schema(description = "URL ảnh xem trước đã giới hạn kích thước")
    private String thumbnailUrl;

    @Schema(description = "Định dạng sau khi chuẩn hóa", example = "png")
    private String format;

    @Schema(description = "Dung lượng ảnh đã chuẩn hóa, byte", example = "152000")
    private Long bytes;

    @Schema(description = "Chiều rộng, pixel", example = "1200")
    private Integer width;

    @Schema(description = "Chiều cao, pixel", example = "800")
    private Integer height;

    public static MediaResponse from(MediaAsset image) {
        boolean visible = "READY".equals(image.getStatus());
        return MediaResponse.builder()
            .id(image.getId())
            .createdAt(image.getCreatedAt())
            .updatedAt(image.getUpdatedAt())
            .createdBy(image.getCreatedBy())
            .updatedBy(image.getUpdatedBy())
            .version(image.getVersion())
            .status(image.getStatus())
            .title(image.getTitle())
            .altText(image.getAltText())
            .url(visible ? image.getUrl() : null)
            .thumbnailUrl(visible ? image.getThumbnailUrl() : null)
            .format(image.getFormat())
            .bytes(image.getBytes())
            .width(image.getWidth())
            .height(image.getHeight())
            .build();
    }
}
