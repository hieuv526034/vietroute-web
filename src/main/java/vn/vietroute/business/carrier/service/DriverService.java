package vn.vietroute.business.carrier.service;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.auth.security.annotation.AccessResource;
import vn.vietroute.business.carrier.dto.request.CarrierSearchRequest;
import vn.vietroute.business.carrier.dto.request.DriverCreateRequest;
import vn.vietroute.business.carrier.dto.request.DriverUpdateRequest;
import vn.vietroute.business.carrier.dto.response.CarrierChoiceResponse;
import vn.vietroute.business.carrier.dto.response.DriverResponse;
import vn.vietroute.business.carrier.repository.CarrierRepository;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Hồ sơ tài xế độc lập credentials; tài khoản liên kết phải thuộc đúng scope và có role DRIVER hiện hành. */
@Service
@RequiredArgsConstructor
@AccessResource(Groups.DRIVER)
public class DriverService {

    /** Picker cần đúng quyền tạo và scope con, chỉ trả trường phục vụ lựa chọn nhà xe active. */
    @AccessOperation(action = PermissionAction.CREATE)
    public PageResponse<CarrierChoiceResponse> carriers(CarrierSearchRequest page) {
        page.setActive(true);
        var result = carriers.choices(page, Groups.DRIVER);
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

    @AccessOperation(action = PermissionAction.READ)
    public PageResponse<DriverResponse> page(CarrierSearchRequest page) {
        var result = carriers.drivers(page);
        return new PageResponse<>(
            result.content().stream().map(DriverResponse::from).toList(),
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages(),
            result.hasNext(),
            result.hasPrevious()
        );
    }

    @AccessOperation(action = PermissionAction.READ)
    public DriverResponse find(long id) {
        return transactions.execute(connection ->
            DriverResponse.from(carriers.driver(connection, id, PermissionAction.READ, false))
        );
    }

    /** Tạo hồ sơ chưa liên kết không cấp account hay bỏ bước xác minh email; scope lấy từ nhà xe server đã kiểm. */
    @AccessOperation(action = PermissionAction.CREATE)
    public DriverResponse create(DriverCreateRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> {
            var parent = carriers.carrier(
                connection,
                body.getCarrierId(),
                Groups.DRIVER,
                PermissionAction.CREATE,
                true
            );
            BusinessRules.require(parent.isActive(), ErrorCode.RESOURCE_IN_USE);
            if (body.getUserId() != null) carriers.requireDriverUser(connection, body.getUserId(), parent.getScopeId());
            long id = carriers.insertDriver(connection, parent, body, actor.id());
            return DriverResponse.from(carriers.driver(connection, id, PermissionAction.CREATE, false));
        });
    }

    /** Khóa cha trước con; không đổi carrier và không cấp role/membership qua form hồ sơ. */
    @AccessOperation(action = PermissionAction.UPDATE)
    public DriverResponse update(long id, DriverUpdateRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> {
            var snapshot = carriers.driver(connection, id, PermissionAction.UPDATE, false);
            var parent = carriers.carrier(
                connection,
                snapshot.getCarrierId(),
                Groups.DRIVER,
                PermissionAction.UPDATE,
                true
            );
            var current = carriers.driver(connection, id, PermissionAction.UPDATE, true);
            BusinessRules.require(!body.isActive() || parent.isActive(), ErrorCode.RESOURCE_IN_USE);
            if (
                body.getUserId() != null && (body.isActive() || !Objects.equals(body.getUserId(), current.getUserId()))
            ) carriers.requireDriverUser(connection, body.getUserId(), current.getScopeId());
            carriers.updateDriver(connection, current, body, actor.id());
            return DriverResponse.from(carriers.driver(connection, id, PermissionAction.UPDATE, false));
        });
    }
}
