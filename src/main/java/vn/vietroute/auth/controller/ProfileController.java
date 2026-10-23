package vn.vietroute.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.dto.request.ProfileUpdateRequest;
import vn.vietroute.auth.dto.response.UserResponse;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.service.ProfileService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.media.dto.request.MediaUploadRequest;
import vn.vietroute.common.media.dto.response.MediaResponse;
import vn.vietroute.common.media.service.MediaService;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** API tự phục vụ; dùng MediaService chung, không triển khai upload/Cloudinary lần thứ hai trong auth. */
@RestController
@RequestMapping(Routes.AUTH)
@AccessFunction(group = "PROFILE")
public class ProfileController extends BaseController {

    private final ProfileService profiles;

    private final MediaService images;

    public ProfileController(
        ProfileService profiles,
        MediaService images,
        ApiResponses responses,
        ApiExceptionMapper errors
    ) {
        super(responses, errors);
        this.profiles = profiles;
        this.images = images;
    }

    @InitBinder("mediaUploadRequest")
    public void uploadFields(WebDataBinder binder) {
        binder.setAllowedFields("file");
    }

    @PutMapping(Routes.AUTH_PROFILE)
    @Operation(
        summary = "Sửa hồ sơ cá nhân",
        description = "Chỉ chính principal, version bắt buộc; ảnh READY thuộc chính mình, null để bỏ ảnh"
    )
    public ApiResult<UserResponse> update(
        @Valid @RequestBody ProfileUpdateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> profiles.update(body, actor));
    }

    @PostMapping(value = Routes.AUTH_AVATAR, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload ảnh đại diện của chính mình",
        description = "JPEG/PNG thật, quota và giới hạn media dùng chung; sau upload gắn ID qua profile/version"
    )
    public ApiResult<MediaResponse> upload(
        @Valid @ModelAttribute MediaUploadRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.CREATED, request, () -> images.uploadAvatar(body.getFile(), actor));
    }
}
