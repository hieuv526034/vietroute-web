package vn.vietroute.common.media.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.config.integration.MediaSettings;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang thư viện ảnh; API phía dưới luôn kiểm quyền chức năng và owner riêng. */
@Controller
@RequiredArgsConstructor
@AccessFunction(group = Groups.MEDIA, function = Functions.MEDIA, actions = { PermissionAction.VIEW })
public class MediaPageController {

    private final MediaSettings settings;

    @GetMapping(Routes.MEDIA_PAGE)
    @AccessOperation(resource = Groups.MEDIA, action = PermissionAction.VIEW)
    public String library(Model model) {
        // Chỉ công bố cờ bật/tắt, không đưa Settings hoặc credential Cloudinary vào HTML.
        model.addAttribute("mediaEnabled", settings.isEnabled());
        return "media/library";
    }
}
