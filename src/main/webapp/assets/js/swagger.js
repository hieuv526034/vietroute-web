/* Swagger cùng origin, tự gửi CSRF khi thử API bằng cookie; không lưu Authorization qua reload. */
(() => {
    'use strict';
    const base = document.querySelector('meta[name="app-base"]').content;
    const locale = document.querySelector('meta[name="app-locale"]').content;
    SwaggerUIBundle({
        url: `${base}/v3/api-docs`,
        dom_id: '#swagger-ui',
        deepLinking: true,
        persistAuthorization: false,
        validatorUrl: null,
        async requestInterceptor(request) {
            request.credentials = 'same-origin';
            request.headers ||= {};
            request.headers['X-Locale'] = locale;
            // Request tải đặc tả có thể không chứa method; Swagger mặc định dùng GET.
            const method = (request.method || 'GET').toUpperCase();
            if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
                // Login/logout có thể xoay CSRF cookie; luôn lấy token hiện hành trước mutation.
                const response = await fetch(`${base}/api/v1/auth/csrf`, {
                    credentials: 'same-origin',
                    headers: { 'X-Locale': locale },
                });
                if (!response.ok) {
                    throw new Error('Cannot refresh CSRF token');
                }
                const csrf = (await response.json()).data;
                request.headers[csrf.headerName] = csrf.token;
            }
            return request;
        },
    });
})();
