/* Swagger cùng origin, tự gửi CSRF khi thử API bằng cookie; không lưu Authorization qua reload. */
(async () => {
    'use strict';
    const base = document.querySelector('meta[name="app-base"]').content;
    const locale = document.querySelector('meta[name="app-locale"]').content;
    const response = await fetch(`${base}/api/v1/auth/csrf`, { credentials: 'same-origin', headers: { 'X-Locale': locale } });
    const csrf = (await response.json()).data;
    SwaggerUIBundle({
        url: `${base}/v3/api-docs`, dom_id: '#swagger-ui', deepLinking: true,
        persistAuthorization: false, validatorUrl: null,
        requestInterceptor(request) {
            request.credentials = 'same-origin';
            request.headers['X-Locale'] = locale;
            if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method.toUpperCase())) {
                request.headers[csrf.headerName] = csrf.token;
            }
            return request;
        }
    });
})();
