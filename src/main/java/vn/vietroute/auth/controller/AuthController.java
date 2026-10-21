package vn.vietroute.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.dto.request.ChangePasswordRequest;
import vn.vietroute.auth.dto.request.LoginRequest;
import vn.vietroute.auth.dto.response.AuthResponse;
import vn.vietroute.auth.dto.response.CsrfResponse;
import vn.vietroute.auth.dto.response.UserResponse;
import vn.vietroute.auth.security.AuthCookies;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.AuthService;
import vn.vietroute.common.base.BaseApiController;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.ApiResponse;
import vn.vietroute.common.web.ApiResponses;

/** Controller không tự xác minh JWT/password hoặc thực hiện SQL. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Đăng nhập JWT, tài khoản hiện tại, CSRF, đổi mật khẩu và logout")
public class AuthController extends BaseApiController {

    private final AuthService auth;

    private final AuthCookies cookies;

    public AuthController(
        AuthService auth,
        AuthCookies cookies,
        ApiResponses responses,
        ApiExceptionMapper exceptions
    ) {
        super(responses, exceptions);
        this.auth = auth;
        this.cookies = cookies;
    }

    @GetMapping("/csrf")
    @Operation(
        summary = "Lấy token CSRF",
        description = "Gọi trước login hoặc POST bằng cookie. Gửi token qua header trả về; giữ cookie XSRF-TOKEN tương ứng."
    )
    public ResponseEntity<ApiResponse<CsrfResponse>> csrf(
        CsrfToken token,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        // Truy cập token kích hoạt phát cookie CSRF; base bọc lỗi nếu việc tạo token không hoàn tất.
        return execute(SuccessCode.OK, request, () -> {
            response.setHeader("Cache-Control", "no-store");
            return new CsrfResponse(token.getHeaderName(), token.getToken());
        });
    }

    @PostMapping("/login")
    @Operation(
        summary = "Đăng nhập",
        description = "JWT có hạn 30 phút. Website nhận cookie HttpOnly; API client dùng accessToken với Bearer. Sai 5 lần sẽ khóa 15 phút."
    )
    public ResponseEntity<ApiResponse<AuthResponse>> login(
        @Valid @RequestBody LoginRequest body,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        // Chỉ phát cookie sau khi service xác minh mật khẩu và cập nhật số lần đăng nhập sai.
        return execute(SuccessCode.LOGIN, request, () -> {
            AuthResponse result = auth.login(body);
            cookies.set(request, response, result.accessToken());
            response.setHeader("Cache-Control", "no-store");
            return result;
        });
    }

    @GetMapping("/me")
    @Operation(
        summary = "Xem tài khoản đang đăng nhập",
        description = "Yêu cầu Bearer JWT hoặc cookie auth hợp lệ; response không chứa password hash."
    )
    public ResponseEntity<ApiResponse<UserResponse>> me(
        @AuthenticationPrincipal UserPrincipal user,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        response.setHeader("Cache-Control", "no-store");
        // Principal đã qua filter; service đọc lại dữ liệu và chỉ trả các field được công bố.
        return execute(SuccessCode.OK, request, () -> auth.me(user));
    }

    @PostMapping("/change-password")
    @Operation(
        summary = "Đổi mật khẩu",
        description = "Xác minh mật khẩu hiện tại, thu hồi mọi token cũ và phát token mới cho phiên hiện tại."
    )
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(
        @AuthenticationPrincipal UserPrincipal user,
        @Valid @RequestBody ChangePasswordRequest body,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        // Service đổi hash và tăng tokenVersion trong transaction; thành công mới thay cookie phiên hiện tại.
        return execute(SuccessCode.PASSWORD_CHANGED, request, () -> {
            AuthResponse result = auth.changePassword(user, body);
            cookies.set(request, response, result.accessToken());
            response.setHeader("Cache-Control", "no-store");
            return result;
        });
    }

    @PostMapping("/logout")
    @Operation(
        summary = "Đăng xuất",
        description = "Xóa cookie và tăng tokenVersion để thu hồi mọi token của tài khoản. Cookie mode yêu cầu CSRF."
    )
    public ResponseEntity<ApiResponse<Void>> logout(
        @AuthenticationPrincipal UserPrincipal user,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        // Thu hồi token ở DB trước; nếu DB lỗi, base trả lỗi thay vì báo đăng xuất thành công sai thực tế.
        return execute(SuccessCode.LOGOUT, request, () -> {
            auth.logout(user);
            cookies.clear(request, response);
            response.setHeader("Cache-Control", "no-store");
            return null;
        });
    }
}
