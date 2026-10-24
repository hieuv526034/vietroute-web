package vn.vietroute.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.dto.request.ActivationRequest;
import vn.vietroute.auth.dto.request.BindEmailRequest;
import vn.vietroute.auth.dto.request.EmailRequest;
import vn.vietroute.auth.dto.request.RegisterRequest;
import vn.vietroute.auth.dto.request.ResetPasswordRequest;
import vn.vietroute.auth.dto.response.AccountOptionsResponse;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.service.AccountLifecycleService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** Public chỉ cho luồng chứng minh quyền sở hữu email; không nhận role/user ID hoặc tự phát JWT sau xác minh. */
@RestController
@RequestMapping(Routes.AUTH)
@Tag(name = "Vòng đời tài khoản", description = "Đăng ký, xác minh email và đặt lại mật khẩu qua mail/worker")
@AccessFunction(group = Groups.AUTH, authenticated = true)
public class AccountLifecycleController extends BaseController {

    private final AccountLifecycleService accounts;

    public AccountLifecycleController(
        AccountLifecycleService accounts,
        ApiResponses responses,
        ApiExceptionMapper errors
    ) {
        super(responses, errors);
        this.accounts = accounts;
    }

    @GetMapping(Routes.AUTH_OPTIONS)
    @Operation(
        summary = "Xem khả năng đăng ký/khôi phục email",
        description = "Không DB/credential; enabled là cấu hình, không chứng minh provider online"
    )
    public ApiResult<AccountOptionsResponse> options(HttpServletRequest request) {
        return execute(SuccessCode.OK, request, accounts::options);
    }

    @PostMapping(Routes.AUTH_REGISTER)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Đăng ký CUSTOMER chờ kích hoạt email",
        description = "202 chung kể cả duplicate/cap; không trả token/email tồn tại; có CSRF/quota"
    )
    public ApiResult<Void> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        return execute(HttpStatus.ACCEPTED, SuccessCode.ACCOUNT_REQUESTED, request, () -> {
            accounts.register(body, locale(request));
            return null;
        });
    }

    @PostMapping(Routes.AUTH_RESEND)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Yêu cầu gửi lại mã kích hoạt",
        description = "202 chung, cooldown DB 60 giây; mã mới thay mã cũ"
    )
    public ApiResult<Void> resend(@Valid @RequestBody EmailRequest body, HttpServletRequest request) {
        return requestCode(body, false, request);
    }

    @PostMapping(Routes.AUTH_FORGOT)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Yêu cầu mã đặt lại qua email đã xác minh",
        description = "202 chung; không tiết lộ email tồn tại; mã 15 phút, không ép reset vì nhập sai"
    )
    public ApiResult<Void> forgot(@Valid @RequestBody EmailRequest body, HttpServletRequest request) {
        return requestCode(body, true, request);
    }

    @PostMapping(Routes.AUTH_ACTIVATE)
    @Operation(
        summary = "Xác minh email bằng mã một lần",
        description = "Không tự login; không kích hoạt lại user bị admin vô hiệu hóa"
    )
    public ApiResult<Void> activate(@Valid @RequestBody ActivationRequest body, HttpServletRequest request) {
        return execute(SuccessCode.ACCOUNT_VERIFIED, request, () -> {
            accounts.activate(body);
            return null;
        });
    }

    @PostMapping(Routes.AUTH_RESET)
    @Operation(
        summary = "Đặt lại mật khẩu qua mã email",
        description = "Chặn hiện tại + hai mật khẩu cũ, tiêu thụ mã một lần và thu hồi JWT; phải đăng nhập lại"
    )
    public ApiResult<Void> reset(@Valid @RequestBody ResetPasswordRequest body, HttpServletRequest request) {
        return execute(SuccessCode.PASSWORD_CHANGED, request, () -> {
            accounts.reset(body);
            return null;
        });
    }

    @PostMapping(Routes.AUTH_EMAIL)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Gắn email cho tài khoản đang đăng nhập",
        description = "Cần JWT và mật khẩu hiện tại; email mới chưa dùng reset trước khi được xác minh"
    )
    public ApiResult<Void> email(
        @AuthenticationPrincipal UserPrincipal user,
        @Valid @RequestBody BindEmailRequest body,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.ACCEPTED, SuccessCode.ACCOUNT_REQUESTED, request, () -> {
            accounts.bindEmail(user, body, locale(request));
            return null;
        });
    }

    private ApiResult<Void> requestCode(EmailRequest body, boolean reset, HttpServletRequest request) {
        return execute(HttpStatus.ACCEPTED, SuccessCode.ACCOUNT_REQUESTED, request, () -> {
            accounts.requestCode(body.getEmail(), reset, locale(request));
            return null;
        });
    }
}
