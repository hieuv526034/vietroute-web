package vn.vietroute.auth.security;

import java.util.Objects;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Chặn caller nội bộ truyền actor giả để bỏ kiểm phạm vi ủy quyền; HTTP lấy actor từ principal đã xác minh. */
public final class AccessActors {

    private AccessActors() {}

    public static void requireCurrent(UserPrincipal actor) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (
            auth == null ||
            !(auth.getPrincipal() instanceof UserPrincipal current) ||
            actor == null ||
            actor.id() == null ||
            actor.id() == SystemActor.ID ||
            !Objects.equals(actor.id(), current.id()) ||
            actor.tokenVersion() != current.tokenVersion() ||
            !actor.roles().equals(current.roles())
        ) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
