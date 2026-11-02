package vn.vietroute.business.carrier.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.carrier.dto.request.VehicleRequest;
import vn.vietroute.business.carrier.dto.response.CarrierChoiceResponse;
import vn.vietroute.business.carrier.dto.response.VehicleResponse;
import vn.vietroute.business.carrier.dto.search.CarrierSearch;
import vn.vietroute.business.carrier.entity.Carrier;
import vn.vietroute.business.carrier.entity.Vehicle;
import vn.vietroute.business.carrier.repo.CarrierRepo;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Xe thuộc một nhà xe cố định; plate chuẩn hóa UNIQUE, số chỗ và loại xe được kiểm ở cả DB/service. */
@Service
@RequiredArgsConstructor
public class VehicleService {

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

    public PageResponse<VehicleResponse> page(CarrierSearch page) {
        PageResponse<Vehicle> result = carriers.vehicles(page);
        return new PageResponse<>(
            result.getContent().stream().map(VehicleResponse::from).toList(),
            result.getPage(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.isHasNext(),
            result.isHasPrevious()
        );
    }

    public VehicleResponse find(long id) {
        return transactions.execute(connection -> VehicleResponse.from(carriers.vehicle(connection, id, false)));
    }

    /** Khóa cha active và loại xe trước insert; scope suy ra từ carrier đã được guard trên cùng connection. */
    public VehicleResponse create(VehicleRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            Carrier parent = carriers.carrier(connection, body.getCarrierId(), true);
            BusinessRules.require(parent.isActive(), ErrorCode.RESOURCE_IN_USE);
            carriers.requireVehicleType(connection, body.getVehicleTypeId());
            long id = carriers.insertVehicle(connection, parent, body, actor.id());
            return VehicleResponse.from(carriers.vehicle(connection, id, false));
        });
    }

    /** Giữ carrier/scope/code; cho ngừng sử dụng cả khi nhà xe đã khóa, không xóa lịch sử. */
    public VehicleResponse update(long id, VehicleRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            Vehicle snapshot = carriers.vehicle(connection, id, false);
            Carrier parent = carriers.carrier(connection, snapshot.getCarrierId(), true);
            Vehicle current = carriers.vehicle(connection, id, true);
            BusinessRules.require(!body.isActive() || parent.isActive(), ErrorCode.RESOURCE_IN_USE);
            if (body.isActive() || body.getVehicleTypeId() != current.getVehicleTypeId()) carriers.requireVehicleType(
                connection,
                body.getVehicleTypeId()
            );
            carriers.updateVehicle(connection, current, body, actor.id());
            return VehicleResponse.from(carriers.vehicle(connection, id, false));
        });
    }
}
