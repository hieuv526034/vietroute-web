package vn.vietroute.common.base;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Kết quả phân trang không phụ thuộc Spring Data. */
@Schema(description = "Kết quả phân trang và tổng số bản ghi")
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class PageResponse<T> {

    @Schema(description = "Nội dung trang")
    private final List<T> content;

    @Schema(description = "Trang hiện tại, bắt đầu từ 0")
    private final int page;

    @Schema(description = "Kích thước trang")
    private final int size;

    @Schema(description = "Tổng số bản ghi")
    private final long totalElements;

    @Schema(description = "Tổng số trang")
    private final long totalPages;

    @Schema(description = "Còn trang sau")
    private final boolean hasNext;

    @Schema(description = "Có trang trước")
    private final boolean hasPrevious;

    public static <T> PageResponse<T> of(List<T> content, PageRequest request, long total) {
        long pages = total == 0 ? 0 : 1 + (total - 1) / request.getSize();
        return new PageResponse<>(
            List.copyOf(content),
            request.getPage(),
            request.getSize(),
            total,
            pages,
            request.getPage() + 1L < pages,
            request.getPage() > 0
        );
    }
}
