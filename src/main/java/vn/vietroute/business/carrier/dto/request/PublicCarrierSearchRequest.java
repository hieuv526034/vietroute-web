package vn.vietroute.business.carrier.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.PageRequest;

/** Tra cứu công khai, không nhận active/scope/actor để mở hồ sơ quản trị. */
@Getter
@Setter
@Schema(description = "Tìm nhà xe đang công bố theo mã, tên hoặc điện thoại")
public class PublicCarrierSearchRequest extends PageRequest {

    @Size(max = 160, message = "{validation.length}")
    @Schema(description = "Từ khóa tối đa 160 ký tự, tìm nguyên văn không phân biệt hoa/thường")
    private String query;
}
