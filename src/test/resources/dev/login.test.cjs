const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

// Client thật, response có thể chậm và document chưa chuyển: không gọi mạng/DB hoặc dùng tài khoản thật.
function fixture({ navigationFails = false } = {}) {
    const state = { requests: [], navigation: [], events: {} };
    const element = () => ({
        dataset: {},
        attrs: {},
        handlers: {},
        disabled: false,
        hidden: true,
        textContent: '',
        classList: { toggle() {} },
        addEventListener(type, action) {
            this.handlers[type] = action;
        },
        setAttribute(name, value) {
            this.attrs[name] = value;
        },
        removeAttribute(name) {
            delete this.attrs[name];
        },
        closest() {
            return null;
        },
    });
    const form = element(),
        button = element(),
        feedback = element();
    button.textContent = 'Sign in';
    button.dataset = { loading: 'Signing in', redirecting: 'Opening workspace' };
    form.dataset.redirecting = 'Signed in. Opening workspace';
    form.fields = { username: 'FixtureUser', password: 'Fixture-Only!' };
    form.querySelector = () => button;
    form.reset = () => {
        form.fields = { username: '', password: '' };
    };
    const nodes = new Map([
        ['login-form', form],
        ['form-feedback', feedback],
    ]);
    const api = {
        async get() {
            return { data: { data: { headerName: 'X-CSRF', token: 'fixture-only' } } };
        },
        request(config) {
            state.requests.push(config);
            return new Promise((resolve, reject) => {
                state.resolve = resolve;
                state.reject = reject;
            });
        },
    };
    const document = {
        getElementById: (id) => nodes.get(id),
        querySelector(selector) {
            const key = selector.match(/^meta\[name="([^"]+)"\]$/)?.[1];
            return key
                ? {
                      content:
                          { 'app-base': '/demo', 'app-locale': 'en', 'app-network-error': 'Network error' }[key] || '',
                  }
                : null;
        },
        querySelectorAll: () => [],
        addEventListener() {},
        dispatchEvent() {},
    };
    const window = {
        location: {
            search: '',
            assign(url) {
                if (navigationFails) throw new Error('Navigation failed');
                state.navigation.push(url);
            },
        },
        addEventListener(type, action) {
            state.events[type] = action;
        },
        VietRouteRequests: { attach: () => () => null, singleFlight: () => (key, action) => action() },
        VietRouteForms: { validate: () => true, serverErrors() {} },
    };
    vm.runInNewContext(fs.readFileSync('src/main/webapp/assets/js/app.js', 'utf8'), {
        window,
        document,
        axios: { create: () => api },
        URLSearchParams,
        Event: class {},
        FormData: class {
            constructor(source) {
                this.source = source;
            }
            *[Symbol.iterator]() {
                yield* Object.entries(this.source.fields);
            }
        },
    });
    return {
        state,
        form,
        button,
        feedback,
        route: window.VietRoute.route,
        submit: () => form.handlers.submit({ preventDefault() {} }),
    };
}
const settle = () => new Promise((resolve) => setImmediate(resolve));
const response = (extra = {}) => ({
    data: {
        data: { user: { roles: ['ADMIN'], mustChangePassword: false, emailVerificationRequired: false, ...extra } },
    },
});

test('Login chậm giữ trạng thái gửi, CSRF và chặn submit lặp; thành công vẫn bận tới khi document chuyển', async () => {
    const ui = fixture(),
        pending = ui.submit();
    await settle();
    assert.equal(ui.button.disabled, true);
    assert.equal(ui.button.textContent, 'Signing in');
    await ui.submit();
    assert.equal(ui.state.requests.length, 1);
    assert.equal(ui.state.requests[0].headers['X-CSRF'], 'fixture-only');
    ui.state.resolve(response());
    await pending;
    assert.deepEqual(ui.state.navigation, ['/demo/dashboard?lang=en']);
    assert.equal(ui.route('/my-bookings'), '/demo/my-bookings?lang=en');
    assert.equal(ui.button.disabled, true);
    assert.equal(ui.button.textContent, 'Opening workspace');
    assert.equal(ui.form.dataset.submitting, 'true');
    assert.equal(ui.form.attrs['aria-busy'], 'true');
    assert.equal(ui.feedback.textContent, 'Signed in. Opening workspace');
    assert.equal(ui.form.fields.username, 'FixtureUser');
    assert.equal(ui.form.fields.password, 'Fixture-Only!');
    ui.state.events.pagehide();
    assert.equal(ui.form.fields.password, '');
    await ui.submit();
    assert.equal(ui.state.requests.length, 1);
});

test('Điểm đến giữ rào thiết lập; các role sẵn sàng đều vào dashboard có locale/context', async () => {
    for (const user of [
        { mustChangePassword: true },
        { emailVerificationRequired: true },
        { roles: ['CUSTOMER'] },
        { roles: ['DRIVER'] },
        { roles: ['OPERATOR'] },
    ]) {
        const ui = fixture(),
            pending = ui.submit();
        await settle();
        ui.state.resolve(response(user));
        await pending;
        assert.deepEqual(ui.state.navigation, [
            user.mustChangePassword || user.emailVerificationRequired
                ? '/demo/account?lang=en'
                : '/demo/dashboard?lang=en',
        ]);
    }
});

test('Login lỗi giữ dữ liệu để sửa và mở lại nút, không tự replay hoặc chuyển trang', async () => {
    for (const status of [401, 429, 503]) {
        const ui = fixture(),
            pending = ui.submit();
        await settle();
        ui.state.reject({ response: { status, data: { message: 'Try again' } } });
        await pending;
        assert.equal(ui.button.disabled, false);
        assert.equal(ui.button.textContent, 'Sign in');
        assert.equal(ui.form.fields.password, 'Fixture-Only!');
        assert.equal(ui.form.dataset.submitting, 'false');
        assert.deepEqual(ui.state.navigation, []);
        assert.equal(ui.state.requests.length, 1);
    }
});

test('Quay lại bằng bfcache xóa dữ liệu và giải phóng trạng thái chuyển trang', async () => {
    const ui = fixture(),
        pending = ui.submit();
    await settle();
    ui.state.resolve(response());
    await pending;
    ui.state.events.pageshow({ persisted: true });
    assert.equal(ui.button.disabled, false);
    assert.equal(ui.button.textContent, 'Sign in');
    assert.equal(ui.form.attrs['aria-busy'], undefined);
    assert.equal(ui.button.attrs['data-request-loading'], undefined);
    assert.equal(ui.feedback.hidden, true);
    assert.equal(ui.form.fields.password, '');
});

test('Lỗi bắt đầu navigation giải phóng spinner và cho phép thao tác lại', async () => {
    const ui = fixture({ navigationFails: true }),
        pending = ui.submit();
    await settle();
    ui.state.resolve(response());
    await pending;
    assert.equal(ui.button.disabled, false);
    assert.equal(ui.form.attrs['aria-busy'], undefined);
    assert.equal(ui.button.attrs['data-request-loading'], undefined);
    assert.deepEqual(ui.state.navigation, []);
});
