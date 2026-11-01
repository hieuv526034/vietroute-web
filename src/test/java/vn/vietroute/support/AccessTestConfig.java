package vn.vietroute.support;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.dto.response.FunctionDTO;
import vn.vietroute.auth.dto.response.UserDTO;
import vn.vietroute.auth.dto.search.UserSearch;
import vn.vietroute.auth.entity.User;
import vn.vietroute.auth.repo.FunctionRepo;
import vn.vietroute.auth.repo.UserRepo;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Thay DB read port, giữ nguyên EndpointAccess/Security/DTO/controller production. */
@Configuration
public class AccessTestConfig {

    @Bean
    @Primary
    public AuthorizationFixture isolatedAuthorizationRepository() {
        return new AuthorizationFixture();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public static class AuthorizationFixture extends UserRepo {

        public final Map<Long, User> states = new HashMap<>();
        public boolean strict;
        public int reads;
        public int failAt = Integer.MAX_VALUE;

        public AuthorizationFixture() {
            super(TestJpa.MANAGER, null);
        }

        @Override
        public User load(Long id) {
            if (++reads >= failAt) throw new BusinessException(ErrorCode.DATABASE_UNAVAILABLE);
            if (strict || states.containsKey(id)) return states.get(id);
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal actor) || actor.id() != id) return null;
            Set<String> targets = LegacyBusinessGrants.targets(
                auth.getAuthorities().stream().map(Object::toString).collect(Collectors.toSet())
            );
            return NativeSecurityFixture.snapshot(
                id,
                actor.username(),
                actor.displayName(),
                null,
                true,
                false,
                false,
                true,
                actor.tokenVersion(),
                actor.roles(),
                targets,
                Set.of()
            );
        }

        @Override
        public PageResponse<UserDTO> search(UserSearch search) {
            return PageResponse.of(List.of(), search, 0);
        }
    }

    public static List<SimpleGrantedAuthority> upgradedAuthorities(String... codes) {
        return LegacyBusinessGrants.targets(Set.of(codes)).stream().map(SimpleGrantedAuthority::new).toList();
    }

    @Bean
    @Primary
    public FunctionRepo isolatedFunctionMenus(AuthorizationFixture state) {
        return new FunctionRepo(TestJpa.MANAGER, null) {
            @Override
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            public List<FunctionDTO> capabilities(long id) {
                User user = state.load(id);
                if (user == null || user.isMustChangePassword() || user.getEmailVerifiedAt() == null) return List.of();
                FunctionDTO dto = new FunctionDTO();
                dto.setFunction("TEST_FUNCTION");
                dto.setTargets(user.getTargets());
                dto.setView(true);
                return List.of(dto);
            }

            @Override
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            public List<FunctionDTO> navigation(long id, String current, Locale locale) {
                User user = state.load(id);
                if (user == null || user.isMustChangePassword() || user.getEmailVerifiedAt() == null) return List.of();
                return user
                    .getTargets()
                    .stream()
                    .filter(
                        target -> target.startsWith("GET /") && !target.startsWith("GET /api/") && !target.contains("{")
                    )
                    .sorted()
                    .map(target -> {
                        FunctionDTO dto = new FunctionDTO();
                        dto.setPath(target.substring(4));
                        dto.setName(target.substring(4));
                        dto.setActive(current.equals(target.substring(4)));
                        return dto;
                    })
                    .toList();
            }
        };
    }
}
