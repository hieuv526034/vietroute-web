package vn.vietroute.business.report.repo;

import static vn.vietroute.common.base.BaseRepository.booleanValue;
import static vn.vietroute.common.base.BaseRepository.decimalValue;
import static vn.vietroute.common.base.BaseRepository.instantValue;
import static vn.vietroute.common.base.BaseRepository.longValue;
import static vn.vietroute.common.base.BaseRepository.stringValue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.business.booking.constant.BookingStatus;
import vn.vietroute.business.report.dto.response.ReportCarrierResponse;
import vn.vietroute.business.report.dto.response.ReportStatusResponse;
import vn.vietroute.business.report.dto.response.ReportSummaryResponse;
import vn.vietroute.business.report.dto.search.ReportCarrierSearch;
import vn.vietroute.business.report.dto.search.ReportSearch;
import vn.vietroute.business.trip.constant.TripStatus;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Business;

/** Tổng hợp bằng một statement/snapshot; không join nhân chéo tổng chuyến với nhiều đơn. */
@Repository
@RequiredArgsConstructor
public class ReportRepo {

    private final EntityManager entityManager;

    /** Count/trang dùng cùng REPORT_READ và alias owner, vẫn thấy nhà xe ngừng kinh doanh để tra lịch sử. */
    public PageResponse<ReportCarrierResponse> carriers(ReportCarrierSearch body) {
        String filter = """
        FROM {h-schema}carriers owner
        WHERE %s
            AND LOWER(owner.code || ' ' || owner.name) LIKE :keyword ESCAPE '!'
        """.formatted(UserRoleRepo.visibleSql());
        String text = body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT);
        String pattern = "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        long total;
        {
            Query query = entityManager.createNativeQuery("SELECT COUNT(*)" + filter, Tuple.class);

            UserRoleRepo.bindVisible(query);
            query.setParameter("keyword", pattern);
            {
                Tuple row = (Tuple) query.getSingleResult();
                total = longValue(row.get(0));
            }
        }
        ArrayList<ReportCarrierResponse> content = new ArrayList<ReportCarrierResponse>();
        {
            Query query = entityManager.createNativeQuery(
                """
                SELECT owner.id, owner.code, owner.name, owner.active
                %s
                ORDER BY owner.name, owner.id
                LIMIT :pageSize OFFSET :offset
                """.formatted(filter),
                Tuple.class
            );

            UserRoleRepo.bindVisible(query);
            query.setParameter("keyword", pattern);
            query.setParameter("pageSize", body.getSize());
            query.setParameter("offset", body.offset());
            {
                for (Object rowValue : query.getResultList()) {
                    Tuple row = (Tuple) rowValue;
                    content.add(
                        new ReportCarrierResponse(
                            longValue(row.get(0)),
                            stringValue(row.get(1)),
                            stringValue(row.get(2)),
                            booleanValue(row.get(3))
                        )
                    );
                }
            }
        }
        return PageResponse.of(content, body, total);
    }

    /** Nhóm theo departure_at [đầu ngày, đầu ngày kế tiếp); tiền gồm cả đơn hủy để không mất khoản phải hoàn. */
    public ReportSummaryResponse summary(ReportSearch body) {
        StringBuilder sql = new StringBuilder(
            """
            WITH selected AS MATERIALIZED (
                SELECT t.id, t.carrier_id, t.scope_id, t.status
                FROM {h-schema}carrier_trips t
                JOIN {h-schema}carriers owner ON owner.id = t.carrier_id AND owner.scope_id = t.scope_id
                WHERE
            """
        )
            .append(UserRoleRepo.visibleSql())
            .append(" AND t.departure_at>=:fromAt AND t.departure_at<:toAt");
        if (body.getCarrierId() != null) sql.append(" AND owner.id=:carrierId");
        sql.append(
            """
            ), trip_totals AS (
                SELECT COUNT(*) AS trip_count
            """
        );
        for (TripStatus status : TripStatus.values())
            sql.append(",COUNT(*) FILTER(WHERE status='")
                .append(status.name())
                .append("') AS trip_")
                .append(status.name());
        sql.append(
            """
                FROM selected
            ), booking_totals AS (
                SELECT COUNT(*) AS booking_count
            """
        );
        for (BookingStatus status : BookingStatus.values())
            sql.append(",COUNT(*) FILTER(WHERE b.status='")
                .append(status.name())
                .append("') AS booking_")
                .append(status.name());
        sql.append(
            """
                , COALESCE(SUM(b.seats)
                    FILTER (WHERE b.status IN ('CONFIRMED', 'COMPLETED')), 0) AS confirmed_seats
                , COALESCE(SUM(b.total_price)
                    FILTER (WHERE b.status IN ('CONFIRMED', 'COMPLETED')), 0) AS confirmed_value
                , COALESCE(SUM(b.paid_amount), 0) AS paid_amount
                , COALESCE(SUM(b.refunded_amount), 0) AS refunded_amount
                , COALESCE(SUM(b.paid_amount - b.refunded_amount)
                    FILTER (WHERE b.status = 'CANCELLED'), 0) AS refund_outstanding
                FROM {h-schema}bookings b
                JOIN selected t ON t.id = b.trip_id AND t.carrier_id = b.carrier_id
                    AND t.scope_id = b.scope_id
            )
            SELECT CURRENT_TIMESTAMP AS as_of, trip_totals.*, booking_totals.*
            FROM trip_totals
            CROSS JOIN booking_totals
            """
        );
        {
            Query query = entityManager.createNativeQuery(sql.toString(), Tuple.class);

            UserRoleRepo.bindVisible(query);
            ZoneId zone = ZoneId.of(Business.TIME_ZONE);
            query.setParameter("fromAt", body.getFromDate().atStartOfDay(zone).toOffsetDateTime());
            query.setParameter("toAt", body.getToDate().plusDays(1).atStartOfDay(zone).toOffsetDateTime());
            if (body.getCarrierId() != null) query.setParameter("carrierId", body.getCarrierId());
            {
                Tuple row = (Tuple) query.getSingleResult();
                ArrayList<ReportStatusResponse> trips = new ArrayList<ReportStatusResponse>();
                ArrayList<ReportStatusResponse> bookings = new ArrayList<ReportStatusResponse>();
                for (TripStatus status : TripStatus.values())
                    trips.add(new ReportStatusResponse(status.name(), longValue(row.get("trip_" + status.name()))));
                for (BookingStatus status : BookingStatus.values())
                    bookings.add(
                        new ReportStatusResponse(status.name(), longValue(row.get("booking_" + status.name())))
                    );
                BigDecimal paid = decimalValue(row.get("paid_amount"));
                BigDecimal refunded = decimalValue(row.get("refunded_amount"));
                return new ReportSummaryResponse(
                    body.getFromDate(),
                    body.getToDate(),
                    body.getCarrierId(),
                    instantValue(row.get("as_of")),
                    longValue(row.get("trip_count")),
                    List.copyOf(trips),
                    longValue(row.get("booking_count")),
                    List.copyOf(bookings),
                    longValue(row.get("confirmed_seats")),
                    decimalValue(row.get("confirmed_value")),
                    paid,
                    refunded,
                    paid.subtract(refunded),
                    decimalValue(row.get("refund_outstanding"))
                );
            }
        }
    }
}
