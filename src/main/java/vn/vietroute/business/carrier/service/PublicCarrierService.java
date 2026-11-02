package vn.vietroute.business.carrier.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.business.carrier.dto.response.PublicCarrierResponse;
import vn.vietroute.business.carrier.dto.search.PublicCarrierSearch;
import vn.vietroute.business.carrier.repo.CarrierRepo;
import vn.vietroute.business.carrier.repo.CarrierRepo.Published;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Chỉ đọc nội dung nhà xe đã công bố; không gọi service staff hoặc bỏ qua guard quản trị. */
@Service
@RequiredArgsConstructor
public class PublicCarrierService {

    private final CarrierRepo carriers;
    private final JdbcTransactions transactions;

    /** Đọc trang nhà xe công bố trên cùng connection; không tạo N+1 hoặc gọi CDN khi truy vấn. */
    public PageResponse<PublicCarrierResponse> page(PublicCarrierSearch body) {
        return transactions.execute(connection -> {
            PageResponse<Published> page = carriers.page(connection, body);
            return new PageResponse<>(
                page.getContent().stream().map(this::response).toList(),
                page.getPage(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isHasNext(),
                page.isHasPrevious()
            );
        });
    }

    /** Chỉ trả nhà xe đang công bố; dữ liệu nội bộ không nằm trong projection này. */
    public PublicCarrierResponse find(long id) {
        return transactions.execute(connection -> {
            Published row = carriers.find(connection, id);
            return response(row);
        });
    }

    private PublicCarrierResponse response(Published row) {
        return new PublicCarrierResponse(
            row.getId(),
            row.getCode(),
            row.getName(),
            row.getPhone(),
            row.getDescription()
        );
    }
}
