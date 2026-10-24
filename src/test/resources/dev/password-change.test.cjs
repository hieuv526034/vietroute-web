const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

// Chạy client thật với DOM/Axios giả; không mạng, cookie, DB hoặc mật khẩu tài khoản thật.
function fixture({ base = '/demo', locale = 'en', changed = false, temporary = true } = {}) {
    const state = { requests: [], navigation: [], valid: true, serverErrors: [] };
    const nodes = new Map();
    const node = (id) => {
        const value = {
            hidden: false,
            disabled: false,
            dataset: {},
            textContent: '',
            handlers: {},
            classList: { toggle() {} },
            addEventListener(type, action) {
                this.handlers[type] = action;
            },
            closest() {
                return null;
            },
        };
        nodes.set(id, value);
        return value;
    };
    for (const id of [
        'account-name',
        'account-username',
        'account-roles',
        'account-email',
        'account-email-status',
        'account-created',
        'initial-password',
        'form-feedback',
        'password-feedback',
        'password-open',
    ])
        node(id);
    nodes.get('form-feedback').dataset.passwordChanged = 'Password updated';
    const form = node('password-form');
    const controls = node('controls');
    const button = node('submit');
    button.textContent = 'Save';
    form.fields = { currentPassword: 'Fixture-Old-Only!', newPassword: 'Fixture-New-Only!' };
    form.querySelector = () => button;
    form.closest = () => controls;
    form.reset = () => {
        form.fields = { currentPassword: '', newPassword: '' };
    };
    const dialog = node('password-dialog');
    dialog.close = () => {
        dialog.open = false;
    };
    const user = {
        displayName: 'Fixture',
        username: 'fixture',
        roles: ['ADMIN'],
        createdAt: '2026-10-24T00:00:00Z',
        mustChangePassword: temporary,
    };
    const api = {
        async get(url) {
            if (url.endsWith('/csrf')) return { data: { data: { headerName: 'X-XSRF-TOKEN', token: 'fixture-only' } } };
            return { data: { data: user } };
        },
        request(options) {
            state.requests.push(options);
            return new Promise((resolve, reject) => {
                state.resolve = resolve;
                state.reject = reject;
            });
        },
    };
    const document = {
        getElementById: (id) => nodes.get(id),
        querySelector(selector) {
            const name = selector.match(/^meta\[name="([^"]+)"\]$/)?.[1];
            return name
                ? {
                      content:
                          {
                              'app-base': base,
                              'app-locale': locale,
                              'app-network-error': 'Network error',
                              'app-request-label': 'Request',
                              'app-login-action': 'Sign in again',
                          }[name] || '',
                  }
                : null;
        },
        querySelectorAll: () => [],
        addEventListener() {},
        dispatchEvent() {},
        createElement: () => ({ append() {} }),
    };
    nodes.get('password-feedback').closest = () => dialog;
    nodes.get('password-feedback').append = () => {};
    const window = {
        location: {
            search: changed ? '?lang=en&passwordChanged=true' : '?lang=en',
            assign: (url) => state.navigation.push(url),
        },
        VietRouteRequests: { attach: () => () => null, singleFlight: () => (key, action) => action() },
        VietRouteForms: {
            validate: () => state.valid,
            serverErrors: (scope, errors) => state.serverErrors.push(errors),
        },
        VietRouteUI: {
            modal(id, options) {
                state.modal = options;
                return {
                    open() {
                        dialog.open = true;
                    },
                };
            },
        },
    };
    vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../../../main/webapp/assets/js/app.js'), 'utf8'), {
        window,
        document,
        axios: { create: () => api },
        Intl,
        Date,
        URLSearchParams,
        Event: class {},
        FormData: class {
            constructor(source) {
                this.source = source;
            }
            *[Symbol.iterator]() {
                // FormData thật bỏ control bị disable bởi fieldset cha.
                if (!controls.disabled) yield* Object.entries(this.source.fields);
            }
        },
    });
    return {
        state,
        form,
        controls,
        button,
        dialog,
        nodes,
        user,
        submit: () => form.handlers.submit({ preventDefault() {} }),
    };
}

const settle = () => new Promise((resolve) => setImmediate(resolve));

test('password success rebuilds server navigation with context and locale after clearing fields', async () => {
    for (const [base, locale] of [
        ['', 'vi'],
        ['/demo', 'en'],
    ]) {
        const ui = fixture({ base, locale });
        await settle();
        ui.nodes.get('password-open').handlers.click();
        const pending = ui.submit();
        await settle();
        await ui.submit();
        assert.equal(ui.state.requests.length, 1);
        const request = ui.state.requests[0];
        assert.equal(request.url, '/api/v1/auth/change-password');
        assert.equal(request.data.currentPassword, 'Fixture-Old-Only!');
        assert.equal(request.data.newPassword, 'Fixture-New-Only!');
        assert.equal(request.headers['X-XSRF-TOKEN'], 'fixture-only');
        assert.equal(ui.controls.disabled, true);
        assert.equal(ui.state.modal.busy(), true);
        ui.state.resolve({ data: { message: 'Password updated', data: { user: { mustChangePassword: false } } } });
        await pending;
        assert.equal(ui.form.fields.newPassword, '');
        assert.equal(ui.form.fields.currentPassword, '');
        assert.equal(ui.dialog.open, false);
        assert.equal(ui.controls.disabled, false);
        assert.deepEqual(ui.state.navigation, [`${base}/account?lang=${locale}&passwordChanged=true`]);
    }
});

test('failed password save retains the draft and modal without retry or navigation', async () => {
    for (const status of [401, 409, 503]) {
        const ui = fixture();
        await settle();
        ui.nodes.get('password-open').handlers.click();
        const pending = ui.submit();
        await settle();
        ui.state.reject({
            response: { status, data: { code: 'FIXTURE', message: 'Cannot save', traceId: 'fixture-trace' } },
        });
        await pending;
        assert.equal(ui.dialog.open, true);
        assert.equal(ui.form.fields.newPassword, 'Fixture-New-Only!');
        assert.equal(ui.controls.disabled, false);
        assert.equal(ui.state.requests.length, 1);
        assert.equal(ui.state.navigation.length, 0);
        assert.match(ui.nodes.get('password-feedback').textContent, /Cannot save.*\n.*FIXTURE[\s\S]*fixture-trace/);
    }
});

test('password success marker is display only and still requires the current server account state', async () => {
    for (const temporary of [true, false]) {
        const ui = fixture({ changed: true, temporary });
        await settle();
        assert.equal(ui.nodes.get('initial-password').hidden, !temporary);
        assert.equal(ui.nodes.get('form-feedback').textContent, temporary ? '' : 'Password updated');
        assert.equal(ui.state.requests.length, 0);
        assert.equal(ui.state.navigation.length, 0);
    }
    const invalid = fixture();
    invalid.state.valid = false;
    await invalid.submit();
    assert.equal(invalid.state.requests.length, 0);
    assert.equal(invalid.controls.disabled, false);
});
