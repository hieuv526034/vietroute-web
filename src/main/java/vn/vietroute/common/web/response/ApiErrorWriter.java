package vn.vietroute.common.web.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.web.filter.RequestTraceFilter;

/** Lỗi ở filter không đi qua ControllerAdvice; dùng cùng factory để giữ nguyên hợp đồng. */
@Component
@RequiredArgsConstructor
public class ApiErrorWriter {

    private final ObjectMapper mapper;

    private final ApiResponses responses;

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        RequestTraceFilter.ensure(request, response);
        response.setStatus(code.getStatus().value());
        response.setContentType(Http.JSON_UTF8);
        response.setHeader(HttpHeaders.CONTENT_LANGUAGE, responses.locale(request).getLanguage());
        response.setHeader(HttpHeaders.CACHE_CONTROL, Http.NO_STORE);
        mapper.writeValue(response.getOutputStream(), responses.error(code, new Object[0], List.of(), request));
    }
}
