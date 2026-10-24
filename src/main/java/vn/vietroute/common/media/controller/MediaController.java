package vn.vietroute.common.media.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.AccessConstants.Permissions;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.media.dto.request.MediaDeleteRequest;
import vn.vietroute.common.media.dto.request.MediaUpdateRequest;
import vn.vietroute.common.media.dto.request.MediaUploadRequest;
import vn.vietroute.common.media.dto.response.MediaResponse;
import vn.vietroute.common.media.service.MediaService;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** API chỉ nhận file/version; owner và audit lấy từ principal đã được xác minh. */
@RestController
@RequestMapping(Routes.MEDIA)
@Tag(name = "Ảnh công khai", description = "Upload/xem/xóa ảnh xe, logo, avatar; không dùng cho giấy tờ nhạy cảm")
@AccessFunction(group = Groups.MEDIA, function = Functions.MEDIA, permissions = { Permissions.MEDIA_READ })
public class MediaController extends BaseController {

    private final MediaService media;

    public MediaController(MediaService media, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.media = media;
    }

    /** Whitelist binding multipart, không để client thêm owner/audit vào DTO. */
    @InitBinder("mediaUploadRequest")
    public void uploadFields(WebDataBinder binder) {
        binder.setAllowedFields("file");
    }

    @GetMapping
    @Operation(
        summary = "Đọc thư viện ảnh",
        description = "MEDIA_READ; chỉ ảnh của mình, ADMIN xem tất cả. Sắp mới nhất trước."
    )
    @AccessFunction(group = Groups.MEDIA, permissions = { Permissions.MEDIA_READ })
    public ApiResult<PageResponse<MediaResponse>> page(
        @Valid PageRequest page,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> media.page(page, actor));
    }

    @GetMapping(Routes.BY_ID)
    @Operation(
        summary = "Đọc metadata ảnh",
        description = "MEDIA_READ và kiểm owner/ADMIN. URL là ảnh công khai trên CDN."
    )
    @AccessFunction(group = Groups.MEDIA, permissions = { Permissions.MEDIA_READ })
    public ApiResult<MediaResponse> find(
        @PathVariable long id,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> media.find(id, actor));
    }

    @PutMapping(Routes.BY_ID)
    @Operation(
        summary = "Sửa tên và mô tả ảnh",
        description = "MEDIA_UPDATE + owner/ADMIN; chỉ READY, kiểm version, không thay URL/provider ID"
    )
    @AccessFunction(group = Groups.MEDIA, permissions = { Permissions.MEDIA_UPDATE })
    public ApiResult<MediaResponse> update(
        @PathVariable long id,
        @Valid @RequestBody MediaUpdateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> media.update(id, body, actor));
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Upload một ảnh công khai",
        description = "MEDIA_CREATE; JPEG/PNG tối đa 5 MiB, 4096 px mỗi chiều, 6 triệu pixel; chuẩn hóa bỏ metadata. Cookie cần CSRF header."
    )
    @AccessFunction(group = Groups.MEDIA, permissions = { Permissions.MEDIA_CREATE })
    public ApiResult<MediaResponse> upload(
        @Valid @ModelAttribute MediaUploadRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () -> media.upload(body.getFile(), actor));
    }

    @DeleteMapping(Routes.BY_ID)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Yêu cầu xóa ảnh",
        description = "MEDIA_DELETE và owner/ADMIN, version hiện hành; worker xóa CDN rồi xác nhận DELETED. Cache có thể còn trong thời gian invalidation."
    )
    @AccessFunction(group = Groups.MEDIA, permissions = { Permissions.MEDIA_DELETE })
    public ApiResult<MediaResponse> delete(
        @PathVariable long id,
        @Valid @RequestBody MediaDeleteRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.ACCEPTED, SuccessCode.ACCEPTED, request, () ->
            media.delete(id, body.getVersion(), actor)
        );
    }
}
