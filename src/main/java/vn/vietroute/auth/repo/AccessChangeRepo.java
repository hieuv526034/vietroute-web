package vn.vietroute.auth.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Repository;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.vietroute.auth.entity.AccessChange;
import vn.vietroute.common.base.JpaRepositorySupport;
import vn.vietroute.common.constant.ApplicationConstants.Http;

/** Repository bảng AccessChange; CRUD JPA và SQL batch/query phức tạp cùng transaction server. */
@Repository
public class AccessChangeRepo extends JpaRepositorySupport<AccessChange, Long> {

    private static final ObjectMapper JSON = new ObjectMapper();

    public AccessChangeRepo(EntityManager manager) {
        super(AccessChange.class, manager);
    }

    public void append(
        String event,
        String kind,
        String key,
        long actor,
        Map<String, Object> before,
        Map<String, Object> after
    ) {
        AccessChange change = new AccessChange();
        change.setEventType(event);
        change.setTargetKind(kind);
        change.setTargetKey(key);
        change.setActorId(actor);
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            Object trace = attributes.getRequest().getAttribute(Http.TRACE_ATTRIBUTE);
            if (trace instanceof String text) change.setTraceId(text);
        }
        change.setBeforeState(JSON.valueToTree(before));
        change.setAfterState(JSON.valueToTree(after));
        Instant now = Instant.now();
        change.setCreatedAt(now);
        change.setUpdatedAt(now);
        change.setCreatedBy(actor);
        change.setUpdatedBy(actor);
        entityManager.persist(change);
        entityManager.flush();
    }
}
