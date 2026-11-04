package vn.vietroute.business.carrier.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.repo.AccessChangeRepo;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.carrier.dto.request.CarrierRequest;
import vn.vietroute.business.carrier.dto.response.CarrierResponse;
import vn.vietroute.business.carrier.dto.search.CarrierSearch;
import vn.vietroute.business.carrier.entity.Carrier;
import vn.vietroute.business.carrier.repo.CarrierRepo;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcSchema;
import vn.vietroute.common.validation.BusinessRules;

/** Nhà xe sở hữu scope duy nhất; scope active khác trạng thái nhận chuyến/đặt của doanh nghiệp. */
@Service
@Transactional
@RequiredArgsConstructor
public class CarrierService {

    private final CarrierRepo carriers;
    private final AppSettings settings;
    private final JdbcSchema schema;

    private final UserRoleRepo mappings;

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
        return CarrierResponse.from(carriers.carrier(id, false));
    }

    /** Actor có global target tạo scope + nhà xe cùng transaction, không tự cấp mapping role cho người dùng thường. */
    public CarrierResponse create(CarrierRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        mappings.requireGlobal(actor);
        mappings.lockRoleAssignments();
        BusinessRules.require(
            mappings.scopeCount() < settings.getScopeCapacity(),
            ErrorCode.ACCESS_CAPACITY_REACHED,
            settings.getScopeCapacity()
        );
        long scope = mappings.nextScopeId(schema.quoted() + ".carrier_scope_ids");
        long id = carriers.insertCarrier(scope, body, actor.id());
        Carrier value = carriers.carrier(id, false);
        changes.append(
            "SCOPE_CREATED",
            "SCOPE",
            Long.toString(value.getScopeId()),
            actor.id(),
            Map.of(),
            Map.of("active", true, "nameVi", body.getName(), "nameEn", body.getName(), "version", 0)
        );
        return CarrierResponse.from(value);
    }

    /** Khóa ảnh trước entity, version/audit đồng thời; đổi trạng thái nhận nghiệp vụ cần global target tương ứng. */
    public CarrierResponse update(long id, CarrierRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        Carrier current = carriers.carrier(id, true);
        if (current.isActive() != body.isActive()) mappings.requireGlobal(actor);
        carriers.updateCarrier(current, body, actor.id());
        return CarrierResponse.from(carriers.carrier(id, false));
    }
}
