package vn.vietroute.auth.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** ACCESS_READ cho xem ma trận; API ghi và danh sách tài khoản tiếp tục có ràng buộc riêng. */
@Controller
@AccessFunction(group = Groups.ACCESS)
public class AccessPageController {

    @GetMapping(Routes.ACCESS_PAGE)
    @PreAuthorize("hasAuthority('ACCESS_READ')")
    public String access() {
        return "operations/access";
    }
}
