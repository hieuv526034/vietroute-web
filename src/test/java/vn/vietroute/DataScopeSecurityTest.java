package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.common.exception.BusinessException;

/** Lọc SQL/role scoped thật được giữ ở BusinessIntegrationTest; kiểm sớm invariant quan hệ/server context tại đây. */
class DataScopeSecurityTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingAuthenticationAndMismatchedServerParentScopesFailClosed() {
        assertThrows(BusinessException.class, () -> UserRoleRepo.bindVisible(null, 1));
        assertThrows(BusinessException.class, () -> UserRoleRepo.requireSameScope(1, 2));
        assertThrows(BusinessException.class, () -> UserRoleRepo.requireSameScope(0, 0));
        UserRoleRepo.requireSameScope(7, 7);
    }
}
