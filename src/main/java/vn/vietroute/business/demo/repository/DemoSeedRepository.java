package vn.vietroute.business.demo.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.config.AuthSettings;
import vn.vietroute.business.demo.dto.response.DemoSeedResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Dữ liệu khởi tạo theo tập từ JSON phiên bản trong source; giữ dữ liệu có sẵn, không query/save theo từng dòng. */
@Repository
@RequiredArgsConstructor
public class DemoSeedRepository {

    private final ObjectMapper json;
    private final AuthSettings settings;

    /** Một transaction do caller sở hữu, advisory lock serialize mọi lần seed và kiểm quota phạm vi. */
    public DemoSeedResponse seed(Connection connection, long actor) throws SQLException {
        try (
            var lock = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(781205611),pg_advisory_xact_lock(781205690)"
            )
        ) {
            lock.execute();
        }
        JsonNode data;
        try (var input = new ClassPathResource("demo/business.json").getInputStream()) {
            data = json.readTree(input);
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        // Cùng mã nhưng đơn vị khác phải dừng thay vì ghép xe/tài xế vào nhầm nhà xe.
        String carriers = data.path("carriers").toString();
        try (
            var query = connection.prepareStatement(
                "SELECT (SELECT count(*) FROM carriers)+(SELECT count(*) FROM jsonb_to_recordset(?::jsonb) x(code text,name text) WHERE NOT EXISTS(SELECT 1 FROM carriers c WHERE c.code=x.code)),EXISTS(SELECT 1 FROM jsonb_to_recordset(?::jsonb) x(code text,name text) JOIN carriers c USING(code) WHERE c.name<>x.name)"
            )
        ) {
            query.setString(1, carriers);
            query.setString(2, carriers);
            try (var row = query.executeQuery()) {
                row.next();
                if (row.getBoolean(2)) throw new BusinessException(ErrorCode.CONFLICT);
                if (row.getLong(1) > settings.getScopeCapacity()) throw new BusinessException(
                    ErrorCode.ACCESS_CAPACITY_REACHED,
                    settings.getScopeCapacity()
                );
            }
        }
        int created = 0;
        created += insert(
            connection,
            """
            INSERT INTO catalog_items(type,code,name_vi,name_en,sort_order,created_by,updated_by)
            SELECT type,code,"nameVi","nameEn","sortOrder",?,? FROM jsonb_to_recordset(?::jsonb)
                x(type text,code text,"nameVi" text,"nameEn" text,"sortOrder" integer)
            ON CONFLICT(type,code) DO NOTHING
            """,
            data.path("catalogs"),
            actor
        );
        created += insert(
            connection,
            """
            INSERT INTO location_provinces(code,name_vi,name_en,sort_order,active,created_by,updated_by)
            SELECT code,"nameVi","nameEn","sortOrder",active,?,? FROM jsonb_to_recordset(?::jsonb)
                x(code text,"nameVi" text,"nameEn" text,"sortOrder" integer,active boolean)
            ON CONFLICT(code) DO NOTHING
            """,
            data.path("provinces"),
            actor
        );
        created += insert(
            connection,
            """
            INSERT INTO location_places(code,province_id,name_vi,name_en,address,latitude,longitude,sort_order,active,created_by,updated_by)
            SELECT x.code,p.id,x."nameVi",x."nameEn",x.address,x.latitude,x.longitude,x."sortOrder",x.active,?,?
            FROM jsonb_to_recordset(?::jsonb) x(code text,"provinceCode" text,"nameVi" text,"nameEn" text,address text,latitude numeric,longitude numeric,"sortOrder" integer,active boolean)
            JOIN location_provinces p ON p.code=x."provinceCode" ON CONFLICT(code) DO NOTHING
            """,
            data.path("locations"),
            actor
        );
        created += insert(
            connection,
            """
            WITH created AS (INSERT INTO carriers(scope_id,scope_code,scope_name_vi,scope_name_en,code,name,phone,description,active,created_by,updated_by)
            SELECT nextval('carrier_scope_ids'),'CARRIER_'||x.code,x.name,x.name,x.code,x.name,x.phone,x.description,x.active,?,?
            FROM jsonb_to_recordset(?::jsonb) x(code text,name text,phone text,description text,active boolean)
            WHERE NOT EXISTS(SELECT 1 FROM carriers c WHERE c.code=x.code) ON CONFLICT(code) DO NOTHING
            RETURNING scope_id,scope_name_vi,scope_name_en,created_by), history AS (
                INSERT INTO auth_access_changes(event_type,target_kind,target_key,actor_id,before_state,after_state,created_by,updated_by)
                SELECT 'SCOPE_CREATED','SCOPE',scope_id::text,created_by,'{}'::jsonb,
                    jsonb_build_object('roles','[]'::jsonb,'permissions','[]'::jsonb,'resources','[]'::jsonb,'mappingCount',0,'delta',false,
                        'active',true,'deleted',false,'nameVi',scope_name_vi,'nameEn',scope_name_en,'version',0),created_by,created_by
                FROM created RETURNING id)
            SELECT count(*) FROM created
            """,
            data.path("carriers"),
            actor
        );
        created += insert(
            connection,
            """
            INSERT INTO carrier_vehicles(carrier_id,scope_id,code,plate_number,vehicle_type_id,capacity,active,created_by,updated_by)
            SELECT c.id,c.scope_id,x.code,x."plateNumber",t.id,x.capacity,x.active,?,?
            FROM jsonb_to_recordset(?::jsonb) x(code text,"carrierCode" text,"typeCode" text,"plateNumber" text,capacity integer,active boolean)
            JOIN carriers c ON c.code=x."carrierCode" JOIN catalog_items t ON t.type='VEHICLE_TYPE' AND t.code=x."typeCode"
            ON CONFLICT(carrier_id,code) DO NOTHING
            """,
            data.path("vehicles"),
            actor
        );
        created += insert(
            connection,
            """
            INSERT INTO carrier_drivers(carrier_id,scope_id,code,name,phone,active,created_by,updated_by)
            SELECT c.id,c.scope_id,x.code,x.name,x.phone,x.active,?,? FROM jsonb_to_recordset(?::jsonb)
                x(code text,"carrierCode" text,name text,phone text,active boolean)
            JOIN carriers c ON c.code=x."carrierCode" ON CONFLICT(carrier_id,code) DO NOTHING
            """,
            data.path("drivers"),
            actor
        );
        int catalogs = data.path("catalogs").size(),
            provinces = data.path("provinces").size(),
            locations = data.path("locations").size(),
            companies = data.path("carriers").size(),
            vehicles = data.path("vehicles").size(),
            drivers = data.path("drivers").size();
        return new DemoSeedResponse(
            created,
            catalogs + provinces + locations + companies + vehicles + drivers - created,
            catalogs,
            provinces,
            locations,
            companies,
            vehicles,
            drivers
        );
    }

    /** Mỗi loại dữ liệu đúng một INSERT SELECT, số round-trip không tăng theo số dòng. */
    private static int insert(Connection connection, String sql, JsonNode values, long actor) throws SQLException {
        try (var query = connection.prepareStatement(sql)) {
            query.setLong(1, actor);
            query.setLong(2, actor);
            query.setString(3, values.toString());
            if (!query.execute()) return query.getUpdateCount();
            try (var row = query.getResultSet()) {
                row.next();
                return row.getInt(1);
            }
        }
    }
}
