package vn.vietroute.common.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.routing.WebRoutePolicy;

/** Giới hạn body và BCrypt đồng thời; không có quota IP, Redis hoặc trạng thái lịch sử request. */
@RequiredArgsConstructor
public final class RequestGuardFilter extends OncePerRequestFilter {

    private final ApiResponses responses;

    private final Semaphore credentials = new Semaphore(Http.CREDENTIAL_CONCURRENCY);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean credential = WebRoutePolicy.credentialMutation(path, request.getMethod());
        if (credential && !credentials.tryAcquire()) {
            responses.write(request, response, ErrorCode.SERVICE_BUSY);
            return;
        }
        try {
            if (request.getContentLengthLong() > Http.MAX_JSON_BYTES) {
                responses.write(request, response, ErrorCode.PAYLOAD_TOO_LARGE);
                return;
            }
            String contentType = request.getContentType();
            String mime = contentType == null ? "" : contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (mime.equals("application/json") || (mime.startsWith("application/") && mime.endsWith("+json"))) {
                byte[] bytes = request.getInputStream().readNBytes(Http.MAX_JSON_BYTES + 1);
                if (bytes.length > Http.MAX_JSON_BYTES) {
                    responses.write(request, response, ErrorCode.PAYLOAD_TOO_LARGE);
                    return;
                }
                chain.doFilter(new JsonRequest(request, bytes), response);
            } else chain.doFilter(request, response);
        } finally {
            if (credential) credentials.release();
        }
    }

    /** MVC đọc lại body đã chặn kích thước, kể cả khi client gửi Transfer-Encoding: chunked. */
    private static final class JsonRequest extends HttpServletRequestWrapper {

        private final byte[] bytes;

        private JsonRequest(HttpServletRequest request, byte[] bytes) {
            super(request);
            this.bytes = bytes;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(bytes);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public int read(byte[] target, int offset, int length) {
                    return input.read(target, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new IllegalStateException("Synchronous MVC");
                }
            };
        }
    }
}
