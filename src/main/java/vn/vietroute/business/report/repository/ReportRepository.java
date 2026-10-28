package vn.vietroute.business.report.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.DataScopeGuard;
import vn.vietroute.business.booking.entity.BookingStatus;
import vn.vietroute.business.report.dto.request.ReportCarrierSearchRequest;
import vn.vietroute.business.report.dto.request.ReportSearchRequest;
import vn.vietroute.business.report.dto.response.ReportCarrierResponse;
import vn.vietroute.business.report.dto.response.ReportStatusResponse;
import vn.vietroute.business.report.dto.response.ReportSummaryResponse;
import vn.vietroute.business.trip.entity.TripStatus;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Business;

/** Tổng hợp bằng một statement/snapshot; không join nhân chéo tổng chuyến với nhiều đơn. */
@Repository
public class ReportRepository {

    /** Count/trang dùng cùng REPORT_READ và alias owner, vẫn thấy nhà xe ngừng kinh doanh để tra lịch sử. */
    public PageResponse<ReportCarrierResponse> carriers(Connection connection, ReportCarrierSearchRequest body)
        throws SQLException {
        String filter =
            " FROM carriers owner WHERE " +
            DataScopeGuard.visibleSql() +
            " AND lower(owner.code || ' ' || owner.name) LIKE ? ESCAPE '!'";
        String text = body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT);
        String pattern = "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        long total;
        try (var query = connection.prepareStatement("SELECT count(*)" + filter)) {
            int at = DataScopeGuard.bindVisible(query, 1, Groups.REPORT, PermissionAction.READ);
            query.setString(at, pattern);
            try (var row = query.executeQuery()) {
                row.next();
                total = row.getLong(1);
            }
        }
        var content = new ArrayList<ReportCarrierResponse>();
        try (
            var query = connection.prepareStatement(
                "SELECT owner.id,owner.code,owner.name,owner.active" +
                    filter +
                    " ORDER BY owner.name,owner.id LIMIT ? OFFSET ?"
            )
        ) {
            int at = DataScopeGuard.bindVisible(query, 1, Groups.REPORT, PermissionAction.READ);
            query.setString(at++, pattern);
            query.setInt(at++, body.getSize());
            query.setLong(at, body.offset());
            try (var row = query.executeQuery()) {
                while (row.next())
                    content.add(
                        new ReportCarrierResponse(row.getLong(1), row.getString(2), row.getString(3), row.getBoolean(4))
                    );
            }
        }
        return PageResponse.of(content, body, total);
    }

    /** Nhóm theo departure_at [đầu ngày, đầu ngày kế tiếp); tiền gồm cả đơn hủy để không mất khoản phải hoàn. */
    public ReportSummaryResponse summary(Connection connection, ReportSearchRequest body) throws SQLException {
        StringBuilder sql = new StringBuilder(
            "WITH selected AS MATERIALIZED (SELECT t.id,t.carrier_id,t.scope_id,t.status FROM carrier_trips t JOIN carriers owner ON owner.id=t.carrier_id AND owner.scope_id=t.scope_id WHERE "
        )
            .append(DataScopeGuard.visibleSql())
            .append(" AND t.departure_at>=? AND t.departure_at<?");
        if (body.getCarrierId() != null) sql.append(" AND owner.id=?");
        sql.append("), trip_totals AS (SELECT count(*) AS trip_count");
        for (var status : TripStatus.values())
            sql.append(",count(*) FILTER(WHERE status='")
                .append(status.name())
                .append("') AS trip_")
                .append(status.name());
        sql.append(" FROM selected), booking_totals AS (SELECT count(*) AS booking_count");
        for (var status : BookingStatus.values())
            sql.append(",count(*) FILTER(WHERE b.status='")
                .append(status.name())
                .append("') AS booking_")
                .append(status.name());
        sql.append(
            """
            ,coalesce(sum(b.seats) FILTER(WHERE b.status IN('CONFIRMED','COMPLETED')),0) AS confirmed_seats
            ,coalesce(sum(b.total_price) FILTER(WHERE b.status IN('CONFIRMED','COMPLETED')),0) AS confirmed_value
            ,coalesce(sum(b.paid_amount),0) AS paid_amount,coalesce(sum(b.refunded_amount),0) AS refunded_amount
            ,coalesce(sum(b.paid_amount-b.refunded_amount) FILTER(WHERE b.status='CANCELLED'),0) AS refund_outstanding
            FROM bookings b JOIN selected t ON t.id=b.trip_id AND t.carrier_id=b.carrier_id AND t.scope_id=b.scope_id)
            SELECT CURRENT_TIMESTAMP AS as_of,trip_totals.*,booking_totals.* FROM trip_totals CROSS JOIN booking_totals
            """
        );
        try (var query = connection.prepareStatement(sql.toString())) {
            int at = DataScopeGuard.bindVisible(query, 1, Groups.REPORT, PermissionAction.READ);
            var zone = ZoneId.of(Business.TIME_ZONE);
            query.setObject(at++, body.getFromDate().atStartOfDay(zone).toOffsetDateTime());
            query.setObject(at++, body.getToDate().plusDays(1).atStartOfDay(zone).toOffsetDateTime());
            if (body.getCarrierId() != null) query.setLong(at, body.getCarrierId());
            try (var row = query.executeQuery()) {
                row.next();
                var trips = new ArrayList<ReportStatusResponse>();
                var bookings = new ArrayList<ReportStatusResponse>();
                for (var status : TripStatus.values())
                    trips.add(new ReportStatusResponse(status.name(), row.getLong("trip_" + status.name())));
                for (var status : BookingStatus.values())
                    bookings.add(new ReportStatusResponse(status.name(), row.getLong("booking_" + status.name())));
                var paid = row.getBigDecimal("paid_amount");
                var refunded = row.getBigDecimal("refunded_amount");
                return new ReportSummaryResponse(
                    body.getFromDate(),
                    body.getToDate(),
                    body.getCarrierId(),
                    row.getTimestamp("as_of").toInstant(),
                    row.getLong("trip_count"),
                    List.copyOf(trips),
                    row.getLong("booking_count"),
                    List.copyOf(bookings),
                    row.getLong("confirmed_seats"),
                    row.getBigDecimal("confirmed_value"),
                    paid,
                    refunded,
                    paid.subtract(refunded),
                    row.getBigDecimal("refund_outstanding")
                );
            }
        }
    }
}
