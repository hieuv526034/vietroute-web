const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');

// Mô phỏng Swagger/fetch để kiểm tra CSRF xoay vòng, không gọi server hoặc DB.
const source = fs.readFileSync(path.resolve(__dirname, '../../../main/webapp/assets/js/swagger.js'), 'utf8');

function client(fetch) {
    let configuration;
    vm.runInNewContext(source, {
        document: {
            querySelector: (selector) => ({ content: selector.includes('app-base') ? '/vietroute' : 'vi' }),
        },
        fetch,
        SwaggerUIBundle: (options) => { configuration = options; },
    });
    return configuration;
}

test('Swagger lấy CSRF mới cho mỗi mutation và giữ context path', async () => {
    let issued = 0;
    const swagger = client(async (url, options) => {
        assert.equal(url, '/vietroute/api/v1/auth/csrf');
        assert.equal(options.credentials, 'same-origin');
        issued += 1;
        return { ok: true, json: async () => ({ data: { headerName: 'X-XSRF-TOKEN', token: `csrf-${issued}` } }) };
    });
    await swagger.requestInterceptor({});
    assert.equal(issued, 0);
    const login = await swagger.requestInterceptor({ method: 'POST', headers: {} });
    const logout = await swagger.requestInterceptor({ method: 'POST', headers: {} });
    assert.equal(login.headers['X-XSRF-TOKEN'], 'csrf-1');
    assert.equal(logout.headers['X-XSRF-TOKEN'], 'csrf-2');
    assert.equal(logout.headers['X-Locale'], 'vi');
    assert.equal(swagger.persistAuthorization, false);
});

test('Swagger dừng mutation nếu không lấy được CSRF', async () => {
    const swagger = client(async () => ({ ok: false }));
    await assert.rejects(swagger.requestInterceptor({ method: 'DELETE' }), /CSRF/);
});
