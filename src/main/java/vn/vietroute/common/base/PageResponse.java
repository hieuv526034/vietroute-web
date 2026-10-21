package vn.vietroute.common.base;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Kết quả phân trang không phụ thuộc Spring Data. */
@Schema(description = "Kết quả phân trang và tổng số bản ghi")
public record PageResponse<T>(
        @Schema(description = "Nội dung trang") List<T> content,
        @Schema(description = "Trang hiện tại, bắt đầu từ 0") int page,
        @Schema(description = "Kích thước trang") int size,
        @Schema(description = "Tổng số bản ghi") long totalElements,
        @Schema(description = "Tổng số trang") long totalPages,
        @Schema(description = "Còn trang sau") boolean hasNext,
        @Schema(description = "Có trang trước") boolean hasPrevious) {
    public static <T> PageResponse<T> of(List<T> content, PageRequest request, long total) {
        long pages = total == 0 ? 0 : 1 + (total - 1) / request.getSize();
        return new PageResponse<>(List.copyOf(content), request.getPage(), request.getSize(), total,
                pages, request.getPage() + 1L < pages, request.getPage() > 0);
    }
}
