package vn.vietroute.business.carrier.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.carrier.dto.request.CarrierSearchRequest;
import vn.vietroute.business.carrier.dto.request.VehicleCreateRequest;
import vn.vietroute.business.carrier.dto.request.VehicleUpdateRequest;
import vn.vietroute.business.carrier.dto.response.CarrierChoiceResponse;
import vn.vietroute.business.carrier.dto.response.VehicleResponse;
import vn.vietroute.business.carrier.entity.Carrier;
import vn.vietroute.business.carrier.entity.Vehicle;
import vn.vietroute.business.carrier.repository.CarrierRepository;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.media.service.MediaService;
import vn.vietroute.common.validation.BusinessRules;

/** Xe thuộc một nhà xe cố định; plate chuẩn hóa UNIQUE, số chỗ và loại xe được kiểm ở cả DB/service. */
@Service
@RequiredArgsConstructor
public class VehicleService {

    /** Picker cần đúng quyền tạo và scope con, chỉ trả trường phục vụ lựa chọn nhà xe active. */
    public PageResponse<CarrierChoiceResponse> carriers(CarrierSearchRequest page) {
        page.setActive(true);
        PageResponse<Carrier> result = carriers.choices(page);
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

    public PageResponse<VehicleResponse> page(CarrierSearchRequest page) {
        PageResponse<Vehicle> result = carriers.vehicles(page);
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

    public VehicleResponse find(long id) {
        return transactions.execute(connection -> VehicleResponse.from(carriers.vehicle(connection, id, false)));
    }

    /** Khóa cha active và loại xe trước insert; scope suy ra từ carrier đã được guard trên cùng connection. */
    public VehicleResponse create(VehicleCreateRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            if (body.getPhotoMediaId() != null) media.requireReference(connection, body.getPhotoMediaId(), actor);
            Carrier parent = carriers.carrier(connection, body.getCarrierId(), true);
            BusinessRules.require(parent.isActive(), ErrorCode.RESOURCE_IN_USE);
            carriers.requireVehicleType(connection, body.getVehicleTypeId());
            long id = carriers.insertVehicle(connection, parent, body, actor.id());
            return VehicleResponse.from(carriers.vehicle(connection, id, false));
        });
    }

    /** Giữ carrier/scope/code; cho ngừng sử dụng cả khi nhà xe đã khóa, không xóa lịch sử. */
    public VehicleResponse update(long id, VehicleUpdateRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            if (body.getPhotoMediaId() != null) media.requireReference(connection, body.getPhotoMediaId(), actor);
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
