package vn.vietroute.business.media.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.media.dto.request.MediaDeleteRequest;
import vn.vietroute.business.media.dto.request.MediaUploadRequest;
import vn.vietroute.business.media.dto.response.MediaResponse;
import vn.vietroute.business.media.service.MediaService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.ApiResponse;
import vn.vietroute.common.web.ApiResponses;

/** API chỉ nhận file/version; owner và audit lấy từ principal đã được xác minh. */
@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Ảnh công khai", description = "Upload/xem/xóa ảnh xe, logo, avatar; không dùng cho giấy tờ nhạy cảm")
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
    public ResponseEntity<ApiResponse<PageResponse<MediaResponse>>> page(
        @Valid PageRequest page,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> media.page(page, actor));
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Đọc metadata ảnh",
        description = "MEDIA_READ và kiểm owner/ADMIN. URL là ảnh công khai trên CDN."
    )
    public ResponseEntity<ApiResponse<MediaResponse>> find(
        @PathVariable long id,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> media.find(id, actor));
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Upload một ảnh công khai",
        description = "MEDIA_CREATE; JPEG/PNG tối đa 5 MiB, 4096 px mỗi chiều, 6 triệu pixel; chuẩn hóa bỏ metadata. Cookie cần CSRF header."
    )
    public ResponseEntity<ApiResponse<MediaResponse>> upload(
        @Valid @ModelAttribute MediaUploadRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () -> media.upload(body.getFile(), actor));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Yêu cầu xóa ảnh",
        description = "MEDIA_DELETE và owner/ADMIN, version hiện hành; worker xóa CDN rồi xác nhận DELETED. Cache có thể còn trong thời gian invalidation."
    )
    public ResponseEntity<ApiResponse<MediaResponse>> delete(
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
