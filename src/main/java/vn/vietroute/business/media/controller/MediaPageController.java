package vn.vietroute.business.media.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Trang thư viện ảnh; API phía dưới luôn kiểm quyền chức năng và owner riêng. */
@Controller
public class MediaPageController {

    @GetMapping("/media")
    public String library() {
        return "media/library";
    }
}
