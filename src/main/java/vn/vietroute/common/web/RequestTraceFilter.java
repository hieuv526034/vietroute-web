package vn.vietroute.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.vietroute.common.constant.Constants;

/** Gắn ID tra cứu lỗi, chỉ chấp nhận header ngắn có định dạng an toàn cho log. */
public class RequestTraceFilter extends OncePerRequestFilter {
    public static String ensure(HttpServletRequest request, HttpServletResponse response) {
        String trace = (String) request.getAttribute(Constants.TRACE_ATTRIBUTE);
        if (trace == null) {
            String supplied = request.getHeader(Constants.REQUEST_ID);
            trace = supplied != null && supplied.matches("[A-Za-z0-9-]{8,64}") ? supplied : UUID.randomUUID().toString();
            request.setAttribute(Constants.TRACE_ATTRIBUTE, trace);
        }
        if (response != null) { response.setHeader(Constants.REQUEST_ID, trace); }
        return trace;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ensure(request, response);
        chain.doFilter(request, response);
    }
}
