package vn.vietroute.business.carrier.service;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.business.carrier.dto.request.PublicCarrierSearchRequest;
import vn.vietroute.business.carrier.dto.response.PublicCarrierResponse;
import vn.vietroute.business.carrier.repository.PublicCarrierRepository;
import vn.vietroute.business.carrier.repository.PublicCarrierRepository.Published;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.media.service.MediaService;

/** Chỉ đọc nội dung nhà xe đã công bố; không gọi service staff hoặc bỏ qua guard quản trị. */
@Service
@RequiredArgsConstructor
public class PublicCarrierService {

    private final PublicCarrierRepository carriers;
    private final JdbcTransactions transactions;
    private final MediaService media;

    /** Đọc trang và logo READY theo lô trên cùng connection; không tạo N+1 hoặc gọi CDN khi truy vấn. */
    public PageResponse<PublicCarrierResponse> page(PublicCarrierSearchRequest body) {
        return transactions.execute(connection -> {
            var page = carriers.page(connection, body);
            var ids = page
                .content()
                .stream()
                .map(Published::logoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            var urls = media.publishedUrls(connection, ids);
            return new PageResponse<>(
                page
                    .content()
                    .stream()
                    .map(row -> response(row, row.logoId() == null ? null : urls.get(row.logoId())))
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
            var row = carriers.find(connection, id);
            var urls = media.publishedUrls(connection, row.logoId() == null ? Set.of() : Set.of(row.logoId()));
            return response(row, row.logoId() == null ? null : urls.get(row.logoId()));
        });
    }

    private PublicCarrierResponse response(Published row, String logo) {
        return new PublicCarrierResponse(row.id(), row.code(), row.name(), row.phone(), row.description(), logo);
    }
}
