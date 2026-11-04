package vn.vietroute.business.booking.dto.projection;

import java.math.BigDecimal;
import java.time.Instant;

/** Sự kiện đơn và thu/hoàn; tên cột dùng chung cho lịch sử và kiểm requestKey. */
public interface BookingEventProjection {
    long getBookingId();

    long getActor();

    String getEventType();

    BigDecimal getAmount();

    String getNote();

    long getId();

    String getFromStatus();

    String getToStatus();

    Instant getCreatedAt();
}
