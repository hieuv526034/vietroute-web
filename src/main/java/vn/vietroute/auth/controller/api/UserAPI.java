package vn.vietroute.auth.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.dto.FunctionDTO;
import vn.vietroute.auth.dto.UserDTO;
import vn.vietroute.auth.dto.request.UserRequest;
import vn.vietroute.auth.dto.search.UserSearch;
import vn.vietroute.auth.security.AuthCookies;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.FunctionService;
import vn.vietroute.auth.service.UserService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** API theo bảng gốc; quyền endpoint do interceptor, actor/audit chỉ lấy server. */
@RestController
@Tag(name = "UserAPI")
public class UserAPI extends BaseController {

    private final UserService users;
    private final FunctionService functions;
    private final AuthCookies cookies;

    public UserAPI(
        UserService users,
        FunctionService functions,
        AuthCookies cookies,
        ApiResponses responses,
        ApiExceptionMapper errors
    ) {
        super(responses, errors);
        this.users = users;
        this.functions = functions;
        this.cookies = cookies;
    }

    @GetMapping(Routes.AUTH_CSRF)
    @Operation(summary = "Token CSRF cho mutation dùng cookie")
    public ApiResult<Map<String, String>> csrf(HttpServletRequest request) {
        return execute(SuccessCode.OK, request, () -> {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
        });
    }

    @PostMapping(Routes.AUTH_LOGIN)
    @Operation(
        summary = "Đăng nhập",
        description = "Chỉ đăng nhập bằng username/password, không dùng email; 5 lần sai khóa 15 phút, browser nhận cookie HttpOnly"
    )
    public ApiResult<UserDTO> login(
        @Valid @RequestBody UserRequest body,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        return execute(SuccessCode.LOGIN, request, () -> {
            long started = System.nanoTime();
            UserDTO value = users.login(body);
            response.setHeader(Http.SERVER_TIMING, "auth;dur=" + Math.max(0, System.nanoTime() - started) / 1_000_000);
            response.setHeader(HttpHeaders.CACHE_CONTROL, Http.NO_STORE);
            request.setAttribute(Http.VERIFIED_USER_ATTRIBUTE, value.getUser().getId());
            request.setAttribute(Http.JOURNAL_USER_ATTRIBUTE, value.getUser().getId());
            cookies.set(request, response, value.getAccessToken());
            return value;
        });
    }

    @GetMapping(Routes.AUTH + Routes.ME_ACTION)
    @Operation(summary = "Tài khoản hiện tại")
    public ApiResult<UserDTO> me(@AuthenticationPrincipal UserPrincipal actor, HttpServletRequest request) {
        return execute(SuccessCode.OK, request, () -> users.me(actor));
    }

    @PostMapping(Routes.AUTH + Routes.PASSWORD_ACTION)
    @Operation(
        summary = "Đổi mật khẩu",
        description = "Bắt buộc currentPassword/newPassword; không trùng mật khẩu hiện tại và hai lần trước; thu hồi phiên sau commit"
    )
    public ApiResult<Void> password(
        @Valid @RequestBody UserRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        return execute(SuccessCode.OK, request, () -> {
            users.password(body, actor);
            cookies.clear(request, response);
            return null;
        });
    }

    @PostMapping(Routes.AUTH_LOGOUT)
    @Operation(summary = "Đăng xuất và thu hồi phiên")
    public ApiResult<Void> logout(
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        return execute(SuccessCode.OK, request, () -> {
            if (actor != null) users.logout(actor);
            cookies.clear(request, response);
            return null;
        });
    }

    @PutMapping(Routes.AUTH + Routes.AUTH_PROFILE)
    @Operation(
        summary = "Sửa hồ sơ của chính mình",
        description = "displayName/version; không đổi email, role hoặc trạng thái"
    )
    public ApiResult<UserDTO> profile(
        @Valid @RequestBody UserRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> users.profile(body, actor));
    }

    @GetMapping(Routes.AUTH + Routes.AUTH_CAPABILITIES)
    @Operation(summary = "Các chức năng/endpoint của chính tài khoản")
    public ApiResult<List<FunctionDTO>> capabilities(
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> functions.capabilities(actor.id()));
    }

    @GetMapping(Routes.ACCOUNTS)
    @Operation(summary = "Tìm và phân trang tài khoản")
    public ApiResult<PageResponse<UserDTO>> search(
        @Valid @ModelAttribute UserSearch search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> users.search(search));
    }

    @PostMapping(Routes.ACCOUNTS)
    @Operation(
        summary = "Tạo tài khoản",
        description = "username/displayName/password/roles bắt buộc; danh tính do quản trị xác minh, bắt đổi mật khẩu khi đăng nhập"
    )
    public ApiResult<UserDTO> create(
        @Valid @RequestBody UserRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () -> users.create(body, actor));
    }

    @GetMapping(Routes.ACCOUNTS + Routes.BY_ID)
    @Operation(summary = "Chi tiết tài khoản")
    public ApiResult<UserDTO> get(@PathVariable long id, HttpServletRequest request) {
        return execute(SuccessCode.OK, request, () -> users.get(id));
    }

    @GetMapping(Routes.ACCOUNTS + "/{id}/access")
    @Operation(summary = "Xem endpoint của tài khoản đích")
    public ApiResult<UserDTO> preview(@PathVariable long id, HttpServletRequest request) {
        return execute(SuccessCode.OK, request, () -> {
            UserDTO dto = users.get(id);
            UserDTO result = new UserDTO();
            result.setUser(dto);
            result.setCapabilities(functions.capabilities(id));
            return result;
        });
    }

    @PutMapping(Routes.ACCOUNTS + Routes.BY_ID)
    @Operation(summary = "Sửa tên tài khoản", description = "displayName/version bắt buộc")
    public ApiResult<UserDTO> update(
        @PathVariable long id,
        @Valid @RequestBody UserRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> users.update(id, body, actor));
    }

    @PutMapping(Routes.ACCOUNTS + "/{id}/state")
    @Operation(
        summary = "Khóa/mở tài khoản",
        description = "active/version bắt buộc, thu hồi token/challenge nguyên tử"
    )
    public ApiResult<UserDTO> state(
        @PathVariable long id,
        @Valid @RequestBody UserRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> users.state(id, body, actor));
    }

    @DeleteMapping(Routes.ACCOUNTS + Routes.BY_ID)
    @Operation(summary = "Xóa mềm tài khoản và mapping")
    public ApiResult<Void> delete(
        @PathVariable long id,
        @Parameter(description = "Version hiện hành") @RequestParam long version,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> {
            users.delete(id, version, actor);
            return null;
        });
    }
}
