package vn.vietroute.business.carrier.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.auth.security.annotation.AccessResource;
import vn.vietroute.business.carrier.dto.request.CarrierSearchRequest;
import vn.vietroute.business.carrier.dto.request.VehicleCreateRequest;
import vn.vietroute.business.carrier.dto.request.VehicleUpdateRequest;
import vn.vietroute.business.carrier.dto.response.CarrierChoiceResponse;
import vn.vietroute.business.carrier.dto.response.VehicleResponse;
import vn.vietroute.business.carrier.repository.CarrierRepository;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.media.service.MediaService;
import vn.vietroute.common.validation.BusinessRules;

/** Xe thuộc một nhà xe cố định; plate chuẩn hóa UNIQUE, số chỗ và loại xe được kiểm ở cả DB/service. */
@Service
@RequiredArgsConstructor
@AccessResource(Groups.VEHICLE)
public class VehicleService {

    /** Picker cần đúng quyền tạo và scope con, chỉ trả trường phục vụ lựa chọn nhà xe active. */
    @AccessOperation(action = PermissionAction.CREATE)
    public PageResponse<CarrierChoiceResponse> carriers(CarrierSearchRequest page) {
        page.setActive(true);
        var result = carriers.choices(page, Groups.VEHICLE);
        return new PageResponse<>(
            result.content().stream().map(CarrierChoiceResponse::from).toList(),
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages(),
            result.hasNext(),
            result.hasPrevious()
        );
    }

    private final CarrierRepository carriers;
    private final JdbcTransactions transactions;
    private final MediaService media;

    @AccessOperation(action = PermissionAction.READ)
    public PageResponse<VehicleResponse> page(CarrierSearchRequest page) {
        var result = carriers.vehicles(page);
        return new PageResponse<>(
            result.content().stream().map(VehicleResponse::from).toList(),
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages(),
            result.hasNext(),
            result.hasPrevious()
        );
    }

    @AccessOperation(action = PermissionAction.READ)
    public VehicleResponse find(long id) {
        return transactions.execute(connection ->
            VehicleResponse.from(carriers.vehicle(connection, id, PermissionAction.READ, false))
        );
    }

    /** Khóa cha active và loại xe trước insert; scope suy ra từ carrier đã được guard trên cùng connection. */
    @AccessOperation(action = PermissionAction.CREATE)
    public VehicleResponse create(VehicleCreateRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> {
            if (body.getPhotoMediaId() != null) media.requireReference(connection, body.getPhotoMediaId(), actor);
            var parent = carriers.carrier(
                connection,
                body.getCarrierId(),
                Groups.VEHICLE,
                PermissionAction.CREATE,
                true
            );
            BusinessRules.require(parent.isActive(), ErrorCode.RESOURCE_IN_USE);
            carriers.requireVehicleType(connection, body.getVehicleTypeId());
            long id = carriers.insertVehicle(connection, parent, body, actor.id());
            return VehicleResponse.from(carriers.vehicle(connection, id, PermissionAction.CREATE, false));
        });
    }

    /** Giữ carrier/scope/code; cho ngừng sử dụng cả khi nhà xe đã khóa, không xóa lịch sử. */
    @AccessOperation(action = PermissionAction.UPDATE)
    public VehicleResponse update(long id, VehicleUpdateRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> {
            if (body.getPhotoMediaId() != null) media.requireReference(connection, body.getPhotoMediaId(), actor);
            var snapshot = carriers.vehicle(connection, id, PermissionAction.UPDATE, false);
            var parent = carriers.carrier(
                connection,
                snapshot.getCarrierId(),
                Groups.VEHICLE,
                PermissionAction.UPDATE,
                true
            );
            var current = carriers.vehicle(connection, id, PermissionAction.UPDATE, true);
            BusinessRules.require(!body.isActive() || parent.isActive(), ErrorCode.RESOURCE_IN_USE);
            if (body.isActive() || body.getVehicleTypeId() != current.getVehicleTypeId()) carriers.requireVehicleType(
                connection,
                body.getVehicleTypeId()
            );
            carriers.updateVehicle(connection, current, body, actor.id());
            return VehicleResponse.from(carriers.vehicle(connection, id, PermissionAction.UPDATE, false));
        });
    }
}
