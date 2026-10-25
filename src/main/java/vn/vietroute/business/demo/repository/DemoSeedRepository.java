package vn.vietroute.business.demo.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.business.carrier.dto.request.CarrierCreateRequest;
import vn.vietroute.business.carrier.dto.request.DriverCreateRequest;
import vn.vietroute.business.carrier.dto.request.VehicleCreateRequest;
import vn.vietroute.business.carrier.repository.CarrierRepository;
import vn.vietroute.business.catalog.dto.request.CatalogCreateRequest;
import vn.vietroute.business.catalog.repository.CatalogRepository;
import vn.vietroute.business.demo.dto.response.DemoSeedResponse;
import vn.vietroute.business.location.dto.request.LocationCreateRequest;
import vn.vietroute.business.location.dto.request.ProvinceCreateRequest;
import vn.vietroute.business.location.repository.LocationRepository;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.validation.BusinessRules;

/** Bộ DEMO opt-in: chỉ INSERT bản thiếu, không đổi dữ liệu hiện có hoặc cấp account/role/verified email. */
@Repository
@RequiredArgsConstructor
public class DemoSeedRepository {

    private final ObjectMapper json;
    private final CatalogRepository catalogs;
    private final LocationRepository locations;
    private final CarrierRepository carriers;

    private enum Target {
        CATALOG("catalog_items", "name_vi"),
        PROVINCE("location_provinces", "name_vi"),
        LOCATION("location_places", "name_vi"),
        CARRIER("carriers", "name"),
        VEHICLE("carrier_vehicles", "code"),
        DRIVER("carrier_drivers", "name");

        private final String table;
        private final String label;

        Target(String table, String label) {
            this.table = table;
            this.label = label;
        }
    }

    /** Caller giữ một transaction; advisory lock ngăn hai admin cùng tạo bộ DEMO và scope trùng. */
    public DemoSeedResponse seed(Connection connection, long actor) throws SQLException {
        try (var lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(781205690)")) {
            lock.execute();
        }
        JsonNode data;
        try (var stream = new ClassPathResource("demo/business.json").getInputStream()) {
            data = json.readTree(stream);
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        int created = 0;
        Map<String, Long> types = new HashMap<>();
        Map<String, Long> provinces = new HashMap<>();
        Map<String, Long> companies = new HashMap<>();
        try {
            for (JsonNode item : data.get("catalogs")) {
                var body = json.treeToValue(item, CatalogCreateRequest.class);
                long id = existing(connection, Target.CATALOG, body.getCode(), null, body.getType().name());
                if (id == 0) {
                    id = catalogs.insert(connection, body, body.getCode(), actor).getId();
                    created++;
                }
                types.put(body.getCode(), id);
            }
            for (JsonNode item : data.get("provinces")) {
                var body = json.treeToValue(item, ProvinceCreateRequest.class);
                long id = existing(connection, Target.PROVINCE, body.getCode(), null, null);
                if (id == 0) {
                    id = locations.createProvince(connection, body, actor).getId();
                    created++;
                }
                provinces.put(body.getCode(), id);
            }
            for (JsonNode item : data.get("locations")) {
                ObjectNode fields = item.deepCopy();
                Long province = provinces.get(fields.remove("provinceCode").asText());
                BusinessRules.require(province != null, ErrorCode.INTERNAL_ERROR);
                fields.put("provinceId", province);
                var body = json.treeToValue(fields, LocationCreateRequest.class);
                if (existing(connection, Target.LOCATION, body.getCode(), null, null) == 0) {
                    locations.requireActiveProvince(connection, province);
                    locations.createLocation(connection, body, actor);
                    created++;
                }
            }
            for (JsonNode item : data.get("carriers")) {
                var body = json.treeToValue(item, CarrierCreateRequest.class);
                long id = existing(connection, Target.CARRIER, body.getCode(), null, null);
                if (id == 0) {
                    id = carriers.insertCarrier(connection, body, actor);
                    created++;
                }
                companies.put(body.getCode(), id);
            }
            for (JsonNode item : data.get("vehicles")) {
                ObjectNode fields = item.deepCopy();
                Long company = companies.get(fields.remove("carrierCode").asText());
                Long type = types.get(fields.remove("typeCode").asText());
                BusinessRules.require(company != null && type != null, ErrorCode.INTERNAL_ERROR);
                fields.put("carrierId", company);
                fields.put("vehicleTypeId", type);
                var body = json.treeToValue(fields, VehicleCreateRequest.class);
                if (existing(connection, Target.VEHICLE, body.getCode(), company, null) == 0) {
                    var parent = carriers.carrier(connection, company, Groups.DEMO, PermissionAction.CREATE, true);
                    BusinessRules.require(parent.isActive(), ErrorCode.RESOURCE_IN_USE);
                    carriers.requireVehicleType(connection, type);
                    carriers.insertVehicle(connection, parent, body, actor);
                    created++;
                }
            }
            for (JsonNode item : data.get("drivers")) {
                ObjectNode fields = item.deepCopy();
                Long company = companies.get(fields.remove("carrierCode").asText());
                BusinessRules.require(company != null, ErrorCode.INTERNAL_ERROR);
                fields.put("carrierId", company);
                var body = json.treeToValue(fields, DriverCreateRequest.class);
                if (existing(connection, Target.DRIVER, body.getCode(), company, null) == 0) {
                    var parent = carriers.carrier(connection, company, Groups.DEMO, PermissionAction.CREATE, true);
                    BusinessRules.require(parent.isActive(), ErrorCode.RESOURCE_IN_USE);
                    carriers.insertDriver(connection, parent, body, actor);
                    created++;
                }
            }
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        int total =
            data.get("catalogs").size() +
            data.get("provinces").size() +
            data.get("locations").size() +
            data.get("carriers").size() +
            data.get("vehicles").size() +
            data.get("drivers").size();
        return new DemoSeedResponse(
            created,
            total - created,
            data.get("catalogs").size(),
            data.get("provinces").size(),
            data.get("locations").size(),
            data.get("carriers").size(),
            data.get("vehicles").size(),
            data.get("drivers").size()
        );
    }

    /** Tên bảng/cột từ enum server; đụng mã không mang nhãn DEMO thì dừng, không ghi đè doanh nghiệp thật. */
    private long existing(Connection connection, Target target, String code, Long parent, String type)
        throws SQLException {
        BusinessRules.require(code != null && code.startsWith("DEMO"), ErrorCode.INTERNAL_ERROR);
        String sql =
            "SELECT id," +
            target.label +
            " AS label FROM " +
            target.table +
            " WHERE code=?" +
            (parent == null ? "" : " AND carrier_id=?") +
            (type == null ? "" : " AND type=?");
        try (var query = connection.prepareStatement(sql)) {
            int index = 1;
            query.setString(index++, code);
            if (parent != null) query.setLong(index++, parent);
            if (type != null) query.setString(index, type);
            try (var row = query.executeQuery()) {
                if (!row.next()) return 0;
                BusinessRules.require(row.getString("label").contains("DEMO"), ErrorCode.CONFLICT);
                return row.getLong("id");
            }
        }
    }
}
