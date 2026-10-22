package vn.vietroute.business.media.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang thư viện ảnh; API phía dưới luôn kiểm quyền chức năng và owner riêng. */
@Controller
public class MediaPageController {

    @GetMapping(Routes.MEDIA_PAGE)
    public String library() {
        return "media/library";
    }
}
