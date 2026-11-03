package vn.vietroute.business.carrier.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import vn.vietroute.common.validation.BusinessRules;

/** Xe thuộc một nhà xe cố định; plate chuẩn hóa UNIQUE, số chỗ và loại xe được kiểm ở cả DB/service. */
@Service
@Transactional
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
        return VehicleResponse.from(carriers.vehicle(id, false));
    }

    /** Khóa cha active và loại xe trước insert; scope suy ra từ carrier đã được guard trên cùng connection. */
    public VehicleResponse create(VehicleRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        Carrier parent = carriers.carrier(body.getCarrierId(), true);
        BusinessRules.require(parent.isActive(), ErrorCode.RESOURCE_IN_USE);
        carriers.requireVehicleType(body.getVehicleTypeId());
        long id = carriers.insertVehicle(parent, body, actor.id());
        return VehicleResponse.from(carriers.vehicle(id, false));
    }

    /** Giữ carrier/scope/code; cho ngừng sử dụng cả khi nhà xe đã khóa, không xóa lịch sử. */
    public VehicleResponse update(long id, VehicleRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        Vehicle snapshot = carriers.vehicle(id, false);
        Carrier parent = carriers.carrier(snapshot.getCarrierId(), true);
        Vehicle current = carriers.vehicle(id, true);
        BusinessRules.require(!body.isActive() || parent.isActive(), ErrorCode.RESOURCE_IN_USE);
        if (body.isActive() || body.getVehicleTypeId() != current.getVehicleTypeId()) carriers.requireVehicleType(
            body.getVehicleTypeId()
        );
        carriers.updateVehicle(current, body, actor.id());
        return VehicleResponse.from(carriers.vehicle(id, false));
    }
}
