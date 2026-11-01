package vn.vietroute.auth.dto.response.registry;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.base.PageResponse;

/** Trang registry và quota toàn loại; allocated không thay đổi theo bộ lọc tìm kiếm. */
@Schema(description = "Trang mã role/quyền và giới hạn dung lượng cấu hình")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class AccessRegistryPageResponse {

    @Schema(description = "Trang đã lọc tại PostgreSQL")
    private PageResponse<AccessRegistryResponse> page;

    @Schema(description = "Số mã tối đa cấu hình cho loại này, không phải quyền toàn cục")
    private int capacity;

    @Schema(description = "Số mã chưa xóa gồm cả mã tắt, không tính tombstone role")
    private long allocated;

    public AccessRegistryPageResponse(PageResponse<AccessRegistryResponse> page, int capacity, long allocated) {
        this.page = page;
        this.capacity = capacity;
        this.allocated = allocated;
    }

    public PageResponse<AccessRegistryResponse> page() {
        return page;
    }

    public int capacity() {
        return capacity;
    }

    public long allocated() {
        return allocated;
    }
}
