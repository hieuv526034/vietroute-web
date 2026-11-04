package vn.vietroute.business.carrier.service;

import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.dto.UserDTO;
import vn.vietroute.auth.dto.request.UserRequest;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.UserService;
import vn.vietroute.business.carrier.dto.request.DriverRequest;
import vn.vietroute.business.carrier.dto.response.CarrierChoiceResponse;
import vn.vietroute.business.carrier.dto.response.DriverResponse;
import vn.vietroute.business.carrier.dto.search.CarrierSearch;
import vn.vietroute.business.carrier.entity.Carrier;
import vn.vietroute.business.carrier.entity.Driver;
import vn.vietroute.business.carrier.repo.CarrierRepo;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Hồ sơ tài xế độc lập credentials; tài khoản liên kết phải thuộc đúng scope và có role DRIVER hiện hành. */
@Service
@RequiredArgsConstructor
public class DriverService {

    /** Picker cần đúng quyền tạo và scope con, chỉ trả trường phục vụ lựa chọn nhà xe active. */
    public PageResponse<CarrierChoiceResponse> carriers(CarrierSearch page) {
        page.setActive(true);
        PageResponse<Carrier> result = carriers.choices(page);
        return new PageResponse<>(
            result.getContent().stream().map(CarrierChoiceResponse::from).toList(),
            result.getPage(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.isHasNext(),
            result.isHasPrevious()
        );
    }

    private final CarrierRepo carriers;
    private final JdbcTransactions transactions;
    private final UserService provisioner;

    /** Cấp mới chỉ cho hồ sơ chưa liên kết, không có chuyến đang chờ/chạy; credential/membership do auth quản lý. */
    public UserDTO provision(long id, UserRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        String hash = provisioner.passwordHash(body.getPassword());
        return transactions.execute(connection -> {
            Driver snapshot = carriers.driver(id, false);
            Carrier parent = carriers.carrier(snapshot.getCarrierId(), true);
            Driver current = carriers.driver(id, true);
            BusinessRules.require(current.getVersion() == body.getVersion(), ErrorCode.STALE_VERSION);
            BusinessRules.require(parent.isActive() && current.getUserId() == null, ErrorCode.RESOURCE_IN_USE);
            carriers.requireDriverUnassigned(id);
            long user = provisioner.provisionDriver(
                connection,
                parent.getScopeId(),
                current.getName(),
                body,
                hash,
                actor
            );
            carriers.linkProvisionedDriver(current, user, actor.id());
            UserDTO result = new UserDTO();
            result.setId(user);
            result.setUsername(body.getUsername().toLowerCase(Locale.ROOT));
            result.setMustChangePassword(true);
            result.setVersion(current.getVersion() + 1);
            return result;
        });
    }

    public PageResponse<DriverResponse> page(CarrierSearch page) {
        PageResponse<Driver> result = carriers.drivers(page);
        return new PageResponse<>(
            result.getContent().stream().map(DriverResponse::from).toList(),
            result.getPage(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.isHasNext(),
            result.isHasPrevious()
        );
    }

    @Transactional(readOnly = true)
    public DriverResponse find(long id) {
        return DriverResponse.from(carriers.driver(id, false));
    }

    /** Tạo hồ sơ chưa liên kết không cấp account hay bỏ bước xác minh email; scope lấy từ nhà xe server đã kiểm. */
    @Transactional
    public DriverResponse create(DriverRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        Carrier parent = carriers.carrier(body.getCarrierId(), true);
        BusinessRules.require(parent.isActive(), ErrorCode.RESOURCE_IN_USE);
        if (body.getUserId() != null) carriers.requireDriverUser(body.getUserId(), parent.getScopeId());
        long id = carriers.insertDriver(parent, body, actor.id());
        return DriverResponse.from(carriers.driver(id, false));
    }

    /** Khóa cha trước con; không đổi carrier và không cấp role/membership qua form hồ sơ. */
    @Transactional
    public DriverResponse update(long id, DriverRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        Driver snapshot = carriers.driver(id, false);
        Carrier parent = carriers.carrier(snapshot.getCarrierId(), true);
        Driver current = carriers.driver(id, true);
        if (!Objects.equals(current.getUserId(), body.getUserId())) carriers.requireDriverUnassigned(id);
        BusinessRules.require(!body.isActive() || parent.isActive(), ErrorCode.RESOURCE_IN_USE);
        if (
            body.getUserId() != null && (body.isActive() || !Objects.equals(body.getUserId(), current.getUserId()))
        ) carriers.requireDriverUser(body.getUserId(), current.getScopeId());
        carriers.updateDriver(current, body, actor.id());
        return DriverResponse.from(carriers.driver(id, false));
    }
}
