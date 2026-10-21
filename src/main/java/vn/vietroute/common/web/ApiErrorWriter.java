package vn.vietroute.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vietroute.common.constant.ErrorCode;

/** Lỗi ở filter không đi qua ControllerAdvice; dùng cùng factory để giữ nguyên hợp đồng. */
@Component
@RequiredArgsConstructor
public class ApiErrorWriter {

    private final ObjectMapper mapper;

    private final ApiResponses responses;

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        RequestTraceFilter.ensure(request, response);
        response.setStatus(code.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Content-Language", responses.locale(request).getLanguage());
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), responses.error(code, new Object[0], List.of(), request));
    }
}
