package vn.vietroute.business.carrier.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.repo.AccessChangeRepo;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.carrier.dto.request.CarrierCreateRequest;
import vn.vietroute.business.carrier.dto.request.CarrierSearchRequest;
import vn.vietroute.business.carrier.dto.request.CarrierUpdateRequest;
import vn.vietroute.business.carrier.dto.response.CarrierResponse;
import vn.vietroute.business.carrier.entity.Carrier;
import vn.vietroute.business.carrier.repository.CarrierRepository;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.media.service.MediaService;

/** Nhà xe sở hữu scope duy nhất; scope active khác trạng thái nhận chuyến/đặt của doanh nghiệp. */
@Service
@RequiredArgsConstructor
public class CarrierService {

    private final CarrierRepository carriers;
    private final JdbcTransactions transactions;
    private final MediaService media;

    private final AccessChangeRepo changes;

    /** Count/trang đều lọc quyền scoped trong SQL, không union quyền từ nhà xe khác. */
    public PageResponse<CarrierResponse> page(CarrierSearchRequest page) {
        PageResponse<Carrier> result = carriers.carriers(page);
        return new PageResponse<>(
            result.content().stream().map(CarrierResponse::from).toList(),
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages(),
            result.hasNext(),
            result.hasPrevious()
        );
    }

    public CarrierResponse find(long id) {
        return transactions.execute(connection -> CarrierResponse.from(carriers.carrier(connection, id, false)));
    }

    /** ADMIN tạo scope + nhà xe cùng transaction, không tự cấp mapping role cho người dùng thường. */
    public CarrierResponse create(CarrierCreateRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            UserRoleRepo.requireGlobal(connection, actor);
            if (body.getLogoMediaId() != null) media.requireReference(connection, body.getLogoMediaId(), actor);
            long id = carriers.insertCarrier(connection, body, actor.id());
            Carrier value = carriers.carrier(connection, id, false);
            changes.append(
                connection,
                "SCOPE_CREATED",
                "SCOPE",
                Long.toString(value.getScopeId()),
                actor.id(),
                Map.of(),
                Map.of("active", true, "nameVi", body.getName(), "nameEn", body.getName(), "version", 0)
            );
            return CarrierResponse.from(value);
        });
    }

    /** Khóa ảnh trước entity, version/audit đồng thời; chỉ ADMIN được đổi trạng thái nhận nghiệp vụ. */
    public CarrierResponse update(long id, CarrierUpdateRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            if (body.getLogoMediaId() != null) media.requireReference(connection, body.getLogoMediaId(), actor);
            Carrier current = carriers.carrier(connection, id, true);
            if (current.isActive() != body.isActive()) UserRoleRepo.requireGlobal(connection, actor);
            carriers.updateCarrier(connection, current, body, actor.id());
            return CarrierResponse.from(carriers.carrier(connection, id, false));
        });
    }
}
