package vn.vietroute.business.media.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.common.config.MediaSettings;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang thư viện ảnh; API phía dưới luôn kiểm quyền chức năng và owner riêng. */
@Controller
@RequiredArgsConstructor
public class MediaPageController {

    private final MediaSettings settings;

    @GetMapping(Routes.MEDIA_PAGE)
    public String library(Model model) {
        // Chỉ công bố cờ bật/tắt, không đưa Settings hoặc credential Cloudinary vào HTML.
        model.addAttribute("mediaEnabled", settings.isEnabled());
        return "media/library";
    }
}
