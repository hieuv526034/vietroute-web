package vn.vietroute.business.carrier.service;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.business.carrier.dto.response.PublicCarrierResponse;
import vn.vietroute.business.carrier.dto.search.PublicCarrierSearch;
import vn.vietroute.business.carrier.repo.CarrierRepo;
import vn.vietroute.business.carrier.repo.CarrierRepo.Published;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.media.service.MediaService;

/** Chỉ đọc nội dung nhà xe đã công bố; không gọi service staff hoặc bỏ qua guard quản trị. */
@Service
@RequiredArgsConstructor
public class PublicCarrierService {

    private final CarrierRepo carriers;
    private final JdbcTransactions transactions;
    private final MediaService media;

    /** Đọc trang và logo READY theo lô trên cùng connection; không tạo N+1 hoặc gọi CDN khi truy vấn. */
    public PageResponse<PublicCarrierResponse> page(PublicCarrierSearch body) {
        return transactions.execute(connection -> {
            PageResponse<Published> page = carriers.page(connection, body);
            Set<Long> ids = page
                .content()
                .stream()
                .map(Published::getLogoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            Map<Long, String> urls = media.publishedUrls(connection, ids);
            return new PageResponse<>(
                page
                    .content()
                    .stream()
                    .map(row -> response(row, row.getLogoId() == null ? null : urls.get(row.getLogoId())))
                    .toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.hasNext(),
                page.hasPrevious()
            );
        });
    }

    /** ID đã kiểm publication trước khi đọc URL; API không cho anonymous tra thư viện bằng media ID. */
    public PublicCarrierResponse find(long id) {
        return transactions.execute(connection -> {
            Published row = carriers.find(connection, id);
            Map<Long, String> urls = media.publishedUrls(
                connection,
                row.getLogoId() == null ? Set.of() : Set.of(row.getLogoId())
            );
            return response(row, row.getLogoId() == null ? null : urls.get(row.getLogoId()));
        });
    }

    private PublicCarrierResponse response(Published row, String logo) {
        return new PublicCarrierResponse(
            row.getId(),
            row.getCode(),
            row.getName(),
            row.getPhone(),
            row.getDescription(),
            logo
        );
    }
}
