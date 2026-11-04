package vn.vietroute.business.catalog.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.catalog.dto.request.CategoryRequest;
import vn.vietroute.business.catalog.dto.response.CategoryLookupResponse;
import vn.vietroute.business.catalog.dto.response.CategoryResponse;
import vn.vietroute.business.catalog.dto.search.CategorySearch;
import vn.vietroute.business.catalog.service.CategoryService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.validation.RequestGroups;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** API danh mục chung; kiểm quyền theo endpoint và tải con theo cha. */
@RestController
@Tag(name = "Category", description = "Danh mục chung, cây tải con theo cha và CRUD có audit")
public class CategoryAPI extends BaseController {

    private final CategoryService categories;

    public CategoryAPI(CategoryService categories, ApiResponses responses, ApiExceptionMapper exceptions) {
        super(responses, exceptions);
        this.categories = categories;
    }

    @GetMapping(Routes.CATEGORIES)
    @Operation(
        summary = "Lookup danh mục theo nhóm/cha",
        description = "Chỉ nhóm công khai, size tối đa 100. WARD/LOCATION phải chọn parentId; không trả cả cây hoặc audit."
    )
    public ApiResult<PageResponse<CategoryLookupResponse>> lookupCategories(
        @Valid @ModelAttribute CategorySearch search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> categories.lookup(search.getRefType(), search, locale(request)));
    }

    @GetMapping(Routes.ADMIN_CATEGORIES)
    @Operation(
        summary = "Nhánh danh mục và tìm kiếm cây",
        description = "tree=true đọc một cấp theo refType/parentId hoặc tìm mục khớp cùng tổ tiên theo query. " +
            "Kết quả cây không chia trang. Yêu cầu thường vẫn phân trang theo cha."
    )
    public ApiResult<PageResponse<CategoryResponse>> searchCategories(
        @Valid @ModelAttribute CategorySearch search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> categories.search(search, locale(request)));
    }

    @GetMapping(Routes.ADMIN_CATEGORY_GROUPS)
    @Operation(
        summary = "Nhóm danh mục",
        description = "Đọc metadata cấu trúc field, không kèm các mục con; cần grant GET chính xác."
    )
    public ApiResult<PageResponse<CategoryResponse>> categoryGroups(
        @Valid @ModelAttribute CategorySearch search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> categories.groups(search, locale(request)));
    }

    @GetMapping(Routes.ADMIN_CATEGORIES + Routes.BY_ID)
    @Operation(
        summary = "Chi tiết Category",
        description = "Grant riêng, gồm props/audit/version, không tự cấp quyền từ menu."
    )
    public ApiResult<CategoryResponse> category(@PathVariable @Min(1) long id, HttpServletRequest request) {
        return execute(SuccessCode.OK, request, () -> categories.findCategory(id, locale(request)));
    }

    @PostMapping(Routes.ADMIN_CATEGORIES)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Tạo Category",
        description = "refType bắt buộc; GROUP có refType bằng code, VALUE kiểm props và cha theo nhóm; audit từ JWT."
    )
    public ApiResult<CategoryResponse> createCategory(
        @Validated(RequestGroups.Create.class) @RequestBody CategoryRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () ->
            categories.createCategory(body, actor, locale(request))
        );
    }

    @PutMapping(Routes.ADMIN_CATEGORIES + Routes.BY_ID)
    @Operation(
        summary = "Sửa Category",
        description = "Giữ identity/cha, kiểm version; sửa metadata không được làm dữ liệu cũ sai hợp đồng."
    )
    public ApiResult<CategoryResponse> updateCategory(
        @PathVariable @Min(1) long id,
        @Validated(RequestGroups.Update.class) @RequestBody CategoryRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> categories.updateCategory(id, body, actor, locale(request)));
    }

    @DeleteMapping(Routes.ADMIN_CATEGORIES + Routes.BY_ID)
    @Operation(
        summary = "Ngừng dùng Category",
        description = "Xóa mềm bằng version; giữ dữ liệu giao dịch và khóa ngoại, không xóa cả cây."
    )
    public ApiResult<CategoryResponse> deleteCategory(
        @PathVariable @Min(1) long id,
        @Validated(RequestGroups.Delete.class) @RequestBody CategoryRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () ->
            categories.deleteCategory(id, body.getVersion(), actor, locale(request))
        );
    }
}
