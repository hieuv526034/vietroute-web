/* Xuất URL môi trường hiện tại và snapshot đã lọc; token luôn là placeholder, không đọc cookie hoặc tự replay. */
(() => {
    'use strict';
    const normalizeBase = (value) => {
        if (!value) return '';
        const url = new URL(value);
        if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
            throw new Error('Invalid application base URL');
        }
        return url.href.replace(/\/$/, '');
    };
    // Origin lấy từ trình duyệt, context từ meta server; tuyệt đối không lấy host/header trong lịch sử request.
    const applicationBase = (origin, contextPath = '') => {
        const host = new URL(origin);
        if (host.origin !== origin || (contextPath && !/^(?:\/[A-Za-z0-9_-]+)+$/.test(contextPath))) {
            throw new Error('Invalid application location');
        }
        return normalizeBase(`${origin}${contextPath}`);
    };
    const prepare = (detail, context = {}) => {
        const { entry } = detail;
        const validPath =
            /^\/api\/[A-Za-z0-9_{}./-]+$/.test(detail.replayPath) &&
            !detail.replayPath.split('/').some((part, index) => index > 0 && (!part || part === '.' || part === '..'));
        const path = validPath ? detail.replayPath.replace(/\{([^{}]+)\}/g, '{{$1}}') : '/api/REPLACE_ENDPOINT';
        const query = new URLSearchParams(detail.query).toString();
        const url = `{{baseUrl}}${path}${query ? `?${query}` : ''}`;
        let body;
        try {
            const parsed = JSON.parse(detail.requestBody);
            if (!parsed.capture) body = parsed;
        } catch {
            /* Snapshot bị bỏ không được giả làm payload thật. */
        }
        if (entry.endpoint === '/api/v1/auth/login') body = { username: '{{username}}', password: '{{password}}' };
        if (entry.endpoint === '/api/v1/auth/change-password')
            body = { currentPassword: '{{currentPassword}}', newPassword: '{{newPassword}}' };
        return {
            url,
            baseUrl: normalizeBase(context.baseUrl),
            locale: context.locale === 'en' ? 'en' : 'vi',
            method: /^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)$/.test(entry.method) ? entry.method : 'REPLACE_METHOD',
            body,
        };
    };
    const quote = (value) => `'${String(value).replaceAll("'", "'\\''")}'`;
    const curl = (detail, context) => {
        const request = prepare(detail, context);
        const parts = [
            `curl --request ${request.method} ${quote(request.baseUrl ? request.url.replace('{{baseUrl}}', request.baseUrl) : request.url)}`,
            "--header 'Authorization: Bearer {{accessToken}}'",
            "--header 'Content-Type: application/json'",
            `--header 'X-Locale: ${request.locale}'`,
        ];
        if (request.body) parts.push(`--data-raw ${quote(JSON.stringify(request.body, null, 2))}`);
        return parts.join(' \\\n  ');
    };
    const collection = (detail, description, context) => {
        const value = prepare(detail, context);
        return {
            info: {
                name: `VietRoute ${detail.entry.method} ${detail.entry.endpoint}`,
                schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
                description,
            },
            variable: [
                { key: 'baseUrl', value: value.baseUrl },
                { key: 'accessToken', value: '' },
                { key: 'username', value: '' },
            ],
            item: [
                {
                    name: detail.entry.endpoint,
                    response: [
                        {
                            name: 'Filtered captured response',
                            code: detail.entry.status,
                            status: 'Captured projection',
                            header: [{ key: 'Content-Type', value: 'application/json' }],
                            body: detail.responseBody,
                        },
                    ],
                    request: {
                        method: value.method,
                        description,
                        auth: { type: 'bearer', bearer: [{ key: 'token', value: '{{accessToken}}', type: 'string' }] },
                        header: [
                            { key: 'Content-Type', value: 'application/json' },
                            { key: 'X-Locale', value: value.locale },
                        ],
                        url: value.url,
                        ...(value.body
                            ? {
                                  body: {
                                      mode: 'raw',
                                      raw: JSON.stringify(value.body, null, 2),
                                      options: { raw: { language: 'json' } },
                                  },
                              }
                            : {}),
                    },
                },
            ],
        };
    };
    window.VietRouteJournalExport = Object.freeze({ applicationBase, curl, collection });
})();
