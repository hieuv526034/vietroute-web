package vn.vietroute.auth.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.vietroute.auth.entity.AccessChange;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.validation.BusinessRules;

/** SQL native của AccessChange; transaction ghi do Service/Initializer sở hữu. */
public interface AccessChangeRepo extends JpaRepository<AccessChange, Long> {
    ObjectMapper JSON = new ObjectMapper();

    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        INSERT INTO {h-schema}auth_access_changes(
            event_type, target_kind, target_key, actor_id, trace_id, before_state, after_state,
            created_at, updated_at, created_by, updated_by
        )
        VALUES (
            :event, :kind, :targetKey, :actor, :traceId, CAST(:beforeState AS JSONB), CAST(:afterState AS JSONB),
            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :actor, :actor
        )
        """,
        nativeQuery = true
    )
    int appendRow(
        @Param("event") String event,
        @Param("kind") String kind,
        @Param("targetKey") String key,
        @Param("actor") long actor,
        @Param("traceId") String traceId,
        @Param("beforeState") String before,
        @Param("afterState") String after
    );

    /** Lịch sử ghi trên cùng transaction với mutation; lỗi INSERT làm rollback toàn bộ. */
    default void append(
        String event,
        String kind,
        String key,
        long actor,
        Map<String, Object> before,
        Map<String, Object> after
    ) {
        String traceId = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            Object trace = attributes.getRequest().getAttribute(Http.TRACE_ATTRIBUTE);
            if (trace instanceof String text) traceId = text;
        }
        int inserted = appendRow(
            event,
            kind,
            key,
            actor,
            traceId,
            JSON.valueToTree(before).toString(),
            JSON.valueToTree(after).toString()
        );
        BusinessRules.require(inserted == 1, ErrorCode.DATABASE_UNAVAILABLE);
    }
}
