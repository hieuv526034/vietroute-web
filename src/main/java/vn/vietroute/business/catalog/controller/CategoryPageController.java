package vn.vietroute.business.catalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Một màn hình bảng cây quản lý các nhóm Category. */
@Controller
public class CategoryPageController {

    @GetMapping(Routes.CATEGORY_PAGE)
    @Operation(summary = "Danh mục chung", description = "Bảng cây quản trị; cần grant GET đúng endpoint trong DB")
    public String categories() {
        return "catalog/category";
    }
}
