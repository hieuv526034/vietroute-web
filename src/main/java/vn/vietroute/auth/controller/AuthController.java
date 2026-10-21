package vn.vietroute.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import vn.vietroute.auth.dto.request.*;
import vn.vietroute.auth.dto.response.*;
import vn.vietroute.auth.security.AuthCookies;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.AuthService;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.web.ApiResponses;

/** Controller không tự xác minh JWT/password hoặc thực hiện SQL. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Đăng nhập JWT, tài khoản hiện tại, CSRF, đổi mật khẩu và logout")
public class AuthController {

    private final AuthService auth;

    private final AuthCookies cookies;

    private final ApiResponses responses;

    @GetMapping("/csrf")
    @Operation(
        summary = "Lấy token CSRF",
        description = "Gọi trước login hoặc POST bằng cookie. Gửi token qua header trả về; giữ cookie XSRF-TOKEN tương ứng."
    )
    public vn.vietroute.common.web.ApiResponse<CsrfResponse> csrf(
        CsrfToken token,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        response.setHeader("Cache-Control", "no-store");
        return responses.success(SuccessCode.OK, new CsrfResponse(token.getHeaderName(), token.getToken()), request);
    }

    @PostMapping("/login")
    @Operation(
        summary = "Đăng nhập",
        description = "JWT có hạn 30 phút. Website nhận cookie HttpOnly; API client dùng accessToken với Bearer. Sai 5 lần sẽ khóa 15 phút."
    )
    public vn.vietroute.common.web.ApiResponse<AuthResponse> login(
        @Valid @RequestBody LoginRequest body,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        AuthResponse result = auth.login(body);
        cookies.set(request, response, result.accessToken());
        response.setHeader("Cache-Control", "no-store");
        return responses.success(SuccessCode.LOGIN, result, request);
    }

    @GetMapping("/me")
    @Operation(
        summary = "Xem tài khoản đang đăng nhập",
        description = "Yêu cầu Bearer JWT hoặc cookie auth hợp lệ; response không chứa password hash."
    )
    public vn.vietroute.common.web.ApiResponse<UserResponse> me(
        @AuthenticationPrincipal UserPrincipal user,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        response.setHeader("Cache-Control", "no-store");
        return responses.success(SuccessCode.OK, auth.me(user), request);
    }

    @PostMapping("/change-password")
    @Operation(
        summary = "Đổi mật khẩu",
        description = "Xác minh mật khẩu hiện tại, thu hồi mọi token cũ và phát token mới cho phiên hiện tại."
    )
    public vn.vietroute.common.web.ApiResponse<AuthResponse> changePassword(
        @AuthenticationPrincipal UserPrincipal user,
        @Valid @RequestBody ChangePasswordRequest body,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        AuthResponse result = auth.changePassword(user, body);
        cookies.set(request, response, result.accessToken());
        response.setHeader("Cache-Control", "no-store");
        return responses.success(SuccessCode.PASSWORD_CHANGED, result, request);
    }

    @PostMapping("/logout")
    @Operation(
        summary = "Đăng xuất",
        description = "Xóa cookie và tăng tokenVersion để thu hồi mọi token của tài khoản. Cookie mode yêu cầu CSRF."
    )
    public vn.vietroute.common.web.ApiResponse<Void> logout(
        @AuthenticationPrincipal UserPrincipal user,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        auth.logout(user);
        cookies.clear(request, response);
        response.setHeader("Cache-Control", "no-store");
        return responses.success(SuccessCode.LOGOUT, null, request);
    }
}
