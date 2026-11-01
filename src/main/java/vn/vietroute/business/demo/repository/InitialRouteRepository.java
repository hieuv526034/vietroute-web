package vn.vietroute.business.demo.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.springframework.stereotype.Repository;

/** Hai tuyến nền có chặng/giá lũy kế đầy đủ; không tự công bố chuyến chạy hoặc tạo booking lịch sử giả. */
@Repository
public class InitialRouteRepository {

    /** Caller giữ khóa seed/transaction. ON CONFLICT giữ tuyến và điểm dừng đã được người quản trị sửa. */
    public void seed(Connection connection) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                INSERT INTO carrier_routes(carrier_id,scope_id,code,name_vi,name_en,description)
                SELECT c.id,c.scope_id,x.code,x.name_vi,x.name_en,'Tuyến nền VietRoute; thời gian và giá chặng do dự án tạo để nghiệm thu, cần xác nhận trước khi công bố chuyến.'
                FROM (VALUES('VRMIENNAM','HCMHANOI','Thành phố Hồ Chí Minh — Đà Nẵng — Hà Nội','Ho Chi Minh City — Da Nang — Hanoi'),
                    ('VRBACNAM','HANOIHCM','Hà Nội — Đà Nẵng — Thành phố Hồ Chí Minh','Hanoi — Da Nang — Ho Chi Minh City')) x(carrier_code,code,name_vi,name_en)
                JOIN carriers c ON c.code=x.carrier_code ON CONFLICT(carrier_id,code) DO NOTHING
                """
            )
        ) {
            query.executeUpdate();
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                INSERT INTO carrier_route_stops(route_id,carrier_id,scope_id,location_id,stop_order,arrival_offset_minutes,departure_offset_minutes,cumulative_fare,pickup_allowed,dropoff_allowed)
                SELECT r.id,r.carrier_id,r.scope_id,l.id,x.position,x.arrival,x.departure,x.fare,x.pickup,x.dropoff
                FROM (VALUES('HCMHANOI','VRTHUDUC',1,0,0,0,true,false),('HCMHANOI','VRDANANG',2,1080,1110,450000,true,true),('HCMHANOI','VRHANOI',3,2040,2040,900000,false,true),
                    ('HANOIHCM','VRHANOI',1,0,0,0,true,false),('HANOIHCM','VRDANANG',2,960,990,450000,true,true),('HANOIHCM','VRTHUDUC',3,2040,2040,900000,false,true)) x(route_code,location_code,position,arrival,departure,fare,pickup,dropoff)
                JOIN carrier_routes r ON r.code=x.route_code JOIN carriers c ON c.id=r.carrier_id AND c.code IN('VRMIENNAM','VRBACNAM')
                JOIN location_places l ON l.code=x.location_code WHERE r.created_by=0
                ON CONFLICT(route_id,stop_order) DO NOTHING
                """
            )
        ) {
            query.executeUpdate();
        }
    }
}
