/* Tạo tài liệu debug từ dữ liệu đã lọc. Không thực thi HTTP, không lấy token/cookie hiện tại. */
(() => {
    'use strict';
    const prepare = (detail) => {
        const { entry } = detail;
        const path = detail.replayPath.startsWith('/api/')
            ? detail.replayPath.replace(/\{([^{}]+)\}/g, '{{$1}}')
            : '/api/REPLACE_ENDPOINT';
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
        return { url, method: entry.method === 'OTHER' ? 'REPLACE_METHOD' : entry.method, body };
    };
    const quote = (value) => `'${String(value).replaceAll("'", "'\\''")}'`;
    const curl = (detail) => {
        const request = prepare(detail);
        const parts = [
            `curl --request ${request.method} ${quote(request.url)}`,
            "--header 'Authorization: Bearer {{accessToken}}'",
            "--header 'Content-Type: application/json'",
            "--header 'X-Locale: vi'",
        ];
        if (request.body) parts.push(`--data-raw ${quote(JSON.stringify(request.body, null, 2))}`);
        return parts.join(' \\\n  ');
    };
    const collection = (detail, description) => {
        const value = prepare(detail);
        return {
            info: {
                name: `VietRoute ${detail.entry.method} ${detail.entry.endpoint}`,
                schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
                description,
            },
            variable: [
                { key: 'baseUrl', value: '' },
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
                            { key: 'X-Locale', value: 'vi' },
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
    window.VietRouteJournalExport = Object.freeze({ curl, collection });
})();
