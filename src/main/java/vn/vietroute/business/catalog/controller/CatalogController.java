package vn.vietroute.business.catalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.catalog.dto.request.CatalogCreateRequest;
import vn.vietroute.business.catalog.dto.response.CatalogResponse;
import vn.vietroute.business.catalog.service.CatalogService;
import vn.vietroute.common.base.*;
import vn.vietroute.common.constant.Constants.CatalogType;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.web.ApiResponses;

/** Controller danh mục làm ví dụ hoàn chỉnh để các thành viên triển khai module sau theo cùng chuẩn. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Catalog", description = "Danh mục vi/en, phân trang và audit")
public class CatalogController {

    private final CatalogService catalogs;

    private final ApiResponses responses;

    @GetMapping("/api/v1/catalogs/{type}")
    @Operation(
        summary = "Tra cứu danh mục",
        description = "Công khai, chỉ trả mục đang hoạt động. page bắt đầu từ 0, size tối đa 100. Tên trả theo locale."
    )
    public vn.vietroute.common.web.ApiResponse<PageResponse<CatalogResponse>> page(
        @PathVariable CatalogType type,
        @Valid @ModelAttribute PageRequest page,
        HttpServletRequest request
    ) {
        return responses.success(SuccessCode.OK, catalogs.page(type, page, responses.locale(request)), request);
    }

    @PostMapping("/api/v1/admin/catalogs")
    @Operation(
        summary = "Tạo mục danh mục",
        description = "Chỉ ADMIN. Server tự sinh mã nếu null; tên vi/en bắt buộc; code trùng trả HTTP 409; audit lấy từ principal."
    )
    @ApiResponse(responseCode = "201", description = "Tạo thành công")
    public ResponseEntity<vn.vietroute.common.web.ApiResponse<CatalogResponse>> create(
        @Valid @RequestBody CatalogCreateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(201).body(
            responses.success(SuccessCode.CREATED, catalogs.create(body, actor, responses.locale(request)), request)
        );
    }
}
