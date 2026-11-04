package vn.vietroute.business.report.repo;

import static vn.vietroute.common.base.BaseRepository.decimalValue;
import static vn.vietroute.common.base.BaseRepository.instantValue;
import static vn.vietroute.common.base.BaseRepository.longValue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.booking.constant.BookingStatus;
import vn.vietroute.business.carrier.dto.projection.CarrierLookupProjection;
import vn.vietroute.business.carrier.entity.Carrier;
import vn.vietroute.business.report.dto.response.ReportCarrierResponse;
import vn.vietroute.business.report.dto.response.ReportStatusResponse;
import vn.vietroute.business.report.dto.response.ReportSummaryResponse;
import vn.vietroute.business.report.dto.search.ReportCarrierSearch;
import vn.vietroute.business.report.dto.search.ReportSearch;
import vn.vietroute.business.trip.constant.TripStatus;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Business;

/**
 * SQL native và ánh xạ của ReportRepo; transaction ghi thuộc Service.
 */
public interface ReportRepo extends Repository<Carrier, Long> {
    String FILTER =
        "FROM {h-schema}carriers owner WHERE " +
        UserRoleRepo.VISIBLE +
        "AND LOWER(owner.code || ' ' || owner.name) LIKE :keyword ESCAPE '!' ";

    @Query(value = "SELECT COUNT(*) " + FILTER, nativeQuery = true)
    long carrierCount(long viewerId, long viewerVersion, String viewTarget, String keyword);

    @Query(
        value = "SELECT owner.id, owner.code, owner.name, owner.active " +
            FILTER +
            "ORDER BY owner.name, owner.id LIMIT :pageSize OFFSET :offset ",
        nativeQuery = true
    )
    List<CarrierLookupProjection> carrierRows(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        String keyword,
        int pageSize,
        long offset
    );

    @Transactional(readOnly = true)
    default PageResponse<ReportCarrierResponse> carriers(ReportCarrierSearch body) {
        UserPrincipal actor = UserPrincipal.current();
        String text = body.getQuery() == null ? "" : body.getQuery().strip().toLowerCase(Locale.ROOT);
        String keyword = "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        long total = carrierCount(actor.id(), actor.tokenVersion(), actor.getTarget(), keyword);
        List<ReportCarrierResponse> rows = carrierRows(
            actor.id(),
            actor.tokenVersion(),
            actor.getTarget(),
            keyword,
            body.getSize(),
            body.offset()
        )
            .stream()
            .map(row -> new ReportCarrierResponse(row.getId(), row.getCode(), row.getName(), row.getActive()))
            .toList();
        return PageResponse.of(rows, body, total);
    }

    @Query(
        value = """
        WITH selected AS MATERIALIZED (
            SELECT t.id, t.carrier_id, t.scope_id, t.status
            FROM {h-schema}carrier_trips t
            JOIN {h-schema}carriers owner ON owner.id = t.carrier_id AND owner.scope_id = t.scope_id
            WHERE
        """ +
            UserRoleRepo.VISIBLE +
            """
                AND t.departure_at >= :fromAt AND t.departure_at < :toAt
                AND (CAST(:carrierId AS BIGINT) IS NULL OR owner.id = :carrierId)
            ), trip_totals AS (
            SELECT COUNT(*) AS trip_count
            , COUNT(*) FILTER (WHERE status = 'DRAFT') AS trip_DRAFT,
                COUNT(*) FILTER (WHERE status = 'OPEN') AS trip_OPEN,
                COUNT(*) FILTER (WHERE status = 'IN_PROGRESS') AS trip_IN_PROGRESS,
                COUNT(*) FILTER (WHERE status = 'COMPLETED') AS trip_COMPLETED,
                COUNT(*) FILTER (WHERE status = 'CANCELLED') AS trip_CANCELLED
            FROM selected
            ), booking_totals AS (
            SELECT COUNT(*) AS booking_count
            , COUNT(*) FILTER (WHERE b.status = 'PENDING') AS booking_PENDING,
                COUNT(*) FILTER (WHERE b.status = 'CONFIRMED') AS booking_CONFIRMED,
                COUNT(*) FILTER (WHERE b.status = 'REJECTED') AS booking_REJECTED,
                COUNT(*) FILTER (WHERE b.status = 'CANCELLED') AS booking_CANCELLED,
                COUNT(*) FILTER (WHERE b.status = 'COMPLETED') AS booking_COMPLETED
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
            """,
        nativeQuery = true
    )
    Map<String, Object> summaryRow(
        long viewerId,
        long viewerVersion,
        String viewTarget,
        OffsetDateTime fromAt,
        OffsetDateTime toAt,
        Long carrierId
    );

    @Transactional(readOnly = true)
    default ReportSummaryResponse summary(ReportSearch body) {
        UserPrincipal actor = UserPrincipal.current();
        ZoneId zone = ZoneId.of(Business.TIME_ZONE);
        Map<String, Object> row = summaryRow(
            actor.id(),
            actor.tokenVersion(),
            actor.getTarget(),
            body.getFromDate().atStartOfDay(zone).toOffsetDateTime(),
            body.getToDate().plusDays(1).atStartOfDay(zone).toOffsetDateTime(),
            body.getCarrierId()
        );
        ArrayList<ReportStatusResponse> trips = new ArrayList<>();
        ArrayList<ReportStatusResponse> bookings = new ArrayList<>();
        for (TripStatus status : TripStatus.values())
            trips.add(
                new ReportStatusResponse(
                    status.name(),
                    longValue(row.get("trip_" + status.name().toLowerCase(Locale.ROOT)))
                )
            );
        for (BookingStatus status : BookingStatus.values())
            bookings.add(
                new ReportStatusResponse(
                    status.name(),
                    longValue(row.get("booking_" + status.name().toLowerCase(Locale.ROOT)))
                )
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
