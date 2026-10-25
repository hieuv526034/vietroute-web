package vn.vietroute.auth.controller;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.constant.AccessAuditConstants.Event;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** ACCESS_READ cho xem ma trận; API ghi và danh sách tài khoản tiếp tục có ràng buộc riêng. */
@Controller
@AccessFunction(group = Groups.ACCESS, function = Functions.ACCESS, actions = { PermissionAction.VIEW })
public class AccessPageController {

    @GetMapping(Routes.ACCESS_PAGE)
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.VIEW)
    public String access(Model model) {
        model.addAttribute("accessHistoryEvents", List.of(Event.values()));
        return "operations/access";
    }

    @GetMapping(Routes.ACCESS_RESOURCES_PAGE)
    @AccessFunction(group = Groups.ACCESS, function = Functions.ACCESS_RESOURCES, actions = { PermissionAction.VIEW })
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.VIEW)
    public String resources() {
        return "operations/access-resources";
    }
}
