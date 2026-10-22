package vn.vietroute.common.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Gắn ID tra cứu lỗi, chỉ chấp nhận header ngắn có định dạng an toàn cho log. */
public class RequestTraceFilter extends OncePerRequestFilter {

    public static String ensure(HttpServletRequest request, HttpServletResponse response) {
        String trace = (String) request.getAttribute(Http.TRACE_ATTRIBUTE);
        if (trace == null) {
            String supplied = request.getHeader(Http.REQUEST_ID);
            trace = supplied != null && supplied.matches(Regex.TRACE_ID) ? supplied : UUID.randomUUID().toString();
            request.setAttribute(Http.TRACE_ATTRIBUTE, trace);
        }
        if (response != null) {
            response.setHeader(Http.REQUEST_ID, trace);
        }
        return trace;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        ensure(request, response);
        chain.doFilter(request, response);
    }
}
