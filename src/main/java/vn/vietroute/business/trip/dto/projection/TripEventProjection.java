package vn.vietroute.business.trip.dto.projection;

import java.time.Instant;

/** Lịch sử chuyến tối thiểu; Repo kiểm quyền trước khi ánh xạ sang Response DTO. */
public interface TripEventProjection {
    long getId();

    String getEventType();

    Integer getStopOrder();

    String getNote();

    Instant getCreatedAt();
}
