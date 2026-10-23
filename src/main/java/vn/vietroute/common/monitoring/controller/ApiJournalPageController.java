package vn.vietroute.common.monitoring.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang tra lỗi/chuẩn bị debug, không thay thế API gateway hoặc cơ chế ghi audit bền vững. */
@Controller
@AccessFunction(group = "SYSTEM")
public class ApiJournalPageController {

    @GetMapping(Routes.API_JOURNAL_PAGE)
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('SYSTEM_READ')")
    public String journal(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, Http.NO_STORE);
        return "operations/api-journal";
    }
}
