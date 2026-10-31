package vn.vietroute.auth.dto.request.access;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.constant.AccessAuditConstants.Event;

/** Điều kiện lọc metadata, không nhận hoặc xuất payload thô của request. */
@Getter
@Setter
@Schema(description = "Lọc lịch sử quyền, có phân trang PostgreSQL")
public class AccessChangeSearchRequest extends PageRequest {

    @Schema(description = "Loại sự kiện; bỏ trống là mọi loại")
    private Event event;

    @Size(max = 120, message = "{validation.length}")
    @Schema(description = "Tiền tố mã/ID đối tượng, tìm literal", maxLength = 120)
    private String target = "";

    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "ID người thực hiện; system là 0", minimum = "0")
    private Long actorId;
}
