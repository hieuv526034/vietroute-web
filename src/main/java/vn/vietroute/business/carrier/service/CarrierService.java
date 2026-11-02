package vn.vietroute.business.carrier.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.repo.AccessChangeRepo;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.carrier.dto.request.CarrierRequest;
import vn.vietroute.business.carrier.dto.response.CarrierResponse;
import vn.vietroute.business.carrier.dto.search.CarrierSearch;
import vn.vietroute.business.carrier.entity.Carrier;
import vn.vietroute.business.carrier.repo.CarrierRepo;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Nhà xe sở hữu scope duy nhất; scope active khác trạng thái nhận chuyến/đặt của doanh nghiệp. */
@Service
@RequiredArgsConstructor
public class CarrierService {

    private final CarrierRepo carriers;
    private final JdbcTransactions transactions;

    private final AccessChangeRepo changes;

    /** Count/trang đều lọc quyền scoped trong SQL, không union quyền từ nhà xe khác. */
    public PageResponse<CarrierResponse> page(CarrierSearch page) {
        PageResponse<Carrier> result = carriers.carriers(page);
        return new PageResponse<>(
            result.getContent().stream().map(CarrierResponse::from).toList(),
            result.getPage(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.isHasNext(),
            result.isHasPrevious()
        );
    }

    public CarrierResponse find(long id) {
        return transactions.execute(connection -> CarrierResponse.from(carriers.carrier(connection, id, false)));
    }

    /** Actor có global target tạo scope + nhà xe cùng transaction, không tự cấp mapping role cho người dùng thường. */
    public CarrierResponse create(CarrierRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            UserRoleRepo.requireGlobal(connection, actor);
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

    /** Khóa ảnh trước entity, version/audit đồng thời; đổi trạng thái nhận nghiệp vụ cần global target tương ứng. */
    public CarrierResponse update(long id, CarrierRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            Carrier current = carriers.carrier(connection, id, true);
            if (current.isActive() != body.isActive()) UserRoleRepo.requireGlobal(connection, actor);
            carriers.updateCarrier(connection, current, body, actor.id());
            return CarrierResponse.from(carriers.carrier(connection, id, false));
        });
    }
}
