package vn.vietroute.business.carrier.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.DataScopeGuard;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.auth.security.annotation.AccessResource;
import vn.vietroute.business.carrier.dto.request.CarrierCreateRequest;
import vn.vietroute.business.carrier.dto.request.CarrierSearchRequest;
import vn.vietroute.business.carrier.dto.request.CarrierUpdateRequest;
import vn.vietroute.business.carrier.dto.response.CarrierResponse;
import vn.vietroute.business.carrier.repository.CarrierRepository;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.media.service.MediaService;

/** Nhà xe sở hữu scope duy nhất; scope active khác trạng thái nhận chuyến/đặt của doanh nghiệp. */
@Service
@RequiredArgsConstructor
@AccessResource(Groups.CARRIER)
public class CarrierService {

    private final CarrierRepository carriers;
    private final JdbcTransactions transactions;
    private final MediaService media;

    /** Count/trang đều lọc quyền scoped trong SQL, không union quyền từ nhà xe khác. */
    @AccessOperation(action = PermissionAction.READ)
    public PageResponse<CarrierResponse> page(CarrierSearchRequest page) {
        var result = carriers.carriers(page);
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

    @AccessOperation(action = PermissionAction.READ)
    public CarrierResponse find(long id) {
        return transactions.execute(connection ->
            CarrierResponse.from(carriers.carrier(connection, id, Groups.CARRIER, PermissionAction.READ, false))
        );
    }

    /** ADMIN tạo scope + nhà xe cùng transaction, không tự cấp mapping role cho người dùng thường. */
    @AccessOperation(action = PermissionAction.CREATE, adminOnly = true)
    public CarrierResponse create(CarrierCreateRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> {
            DataScopeGuard.requireAdmin(connection, actor);
            if (body.getLogoMediaId() != null) media.requireReference(connection, body.getLogoMediaId(), actor);
            long id = carriers.insertCarrier(connection, body, actor.id());
            return CarrierResponse.from(
                carriers.carrier(connection, id, Groups.CARRIER, PermissionAction.CREATE, false)
            );
        });
    }

    /** Khóa ảnh trước entity, version/audit đồng thời; chỉ ADMIN được đổi trạng thái nhận nghiệp vụ. */
    @AccessOperation(action = PermissionAction.UPDATE)
    public CarrierResponse update(long id, CarrierUpdateRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> {
            if (body.getLogoMediaId() != null) media.requireReference(connection, body.getLogoMediaId(), actor);
            var current = carriers.carrier(connection, id, Groups.CARRIER, PermissionAction.UPDATE, true);
            if (current.isActive() != body.isActive()) DataScopeGuard.requireAdmin(connection, actor);
            carriers.updateCarrier(connection, current, body, actor.id());
            return CarrierResponse.from(
                carriers.carrier(connection, id, Groups.CARRIER, PermissionAction.UPDATE, false)
            );
        });
    }
}
