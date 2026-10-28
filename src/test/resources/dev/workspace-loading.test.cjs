/* Chạy JS trang thật: metadata chậm/lỗi không chặn bảng; modal chưa đủ snapshot không được lưu. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const grants = require('../../../main/webapp/assets/js/resource-grants.js');
const requestsState = require('../../../main/webapp/assets/js/request-state.js');

const settle = async () => {
    for (let index = 0; index < 5; index++) await new Promise(setImmediate);
};
const deferred = () => {
    let resolve, reject;
    const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
    return { promise, resolve, reject };
};

function fixture(page) {
    const nodes = new Map(), requests = [], errors = [], buttons = [], modals = new Map();
    const element = (tag = 'div') => ({
        tag, children: [], handlers: {}, dataset: {}, value: '', textContent: '', hidden: false, disabled: false,
        addEventListener(type, handler) { this.handlers[type] = handler; },
        setAttribute() {}, removeAttribute() {}, reset() {},
        focus() { this.focused = true; if (this.handlers.focus) void this.handlers.focus(); },
        append(...items) { for (const item of items) { item.parent = this; this.children.push(item); } },
        replaceChildren(...items) { this.children = []; this.append(...items); },
        remove() { this.parent.children = this.parent.children.filter((item) => item !== this); },
        querySelector() { return node('fieldset'); },
        querySelectorAll(selector) {
            if (selector === 'option[data-role-choice]') return this.children.filter((item) => item.dataset.roleChoice);
            if (selector === 'label') return this.children.filter((item) => item.tag === 'label');
            return [];
        },
    });
    const node = (id) => {
        if (!nodes.has(id)) { const value = element(); value.id = id; nodes.set(id, value); }
        return nodes.get(id);
    };
    const root = node('root');
    node('role-form').elements = { code: node('code'), nameVi: node('vi'), nameEn: node('en'), active: node('active') };
    const allow = [
        'GET /api/v1/admin/access/registry/{kind}/page', 'GET /api/v1/admin/access/tree',
        'GET /api/v1/admin/access/resource-catalog', 'GET /api/v1/admin/access/roles/{role}/resources',
        'PUT /api/v1/admin/access/roles/{role}/resources', 'POST /api/v1/admin/accounts',
        'PUT /api/v1/admin/access/users/{id}/roles', 'GET /api/v1/admin/accounts',
    ];
    const user = { id: 2, username: 'target', displayName: 'Target', roles: ['CUSTOMER'], active: true, version: 3 };
    const role = { code: 'CUSTOMER', role: 'CUSTOMER', nameVi: 'Khách', nameEn: 'Customer', active: true, version: 2 };
    const content = page === 'accounts' ? [user] : [role];
    const state = { override: undefined, mutations: 0 };
    const read = async (path) => {
        if (path === '/api/v1/auth/me') return { id: 1, permissions: ['ACCESS_ASSIGN'] };
        if (path === '/api/v1/auth/capabilities') return [{ targets: allow }];
        if (path === '/api/v1/admin/accounts') return { content, totalElements: 1 };
        if (path.includes('/registry/roles/page?')) return { page: { content, totalElements: 1 } };
        if (path.endsWith('/roles') || path.endsWith('/registry/roles')) return [role];
        if (path.endsWith('/resources')) return { targets: [], version: 2 };
        if (path.endsWith('/access')) return { user, capabilities: [] };
        return [];
    };
    const app = {
        api: { async get(path) {
            requests.push(path);
            const result = state.override ? await state.override(path, read) : await read(path);
            return { data: { data: result } };
        } },
        feedback() {}, failure(error, target) { errors.push({ error, target }); },
        async mutate() { state.mutations++; },
    };
    const window = {
        VietRoute: app,
        VietRouteRequests: requestsState,
        VietRouteForms: { validate: () => true, clear() {} },
        VietRouteRolePicker: { bind: () => ({ reset() {} }) },
        VietRouteTable: {
            pager: () => ({ query: { page: 0, size: 10 }, update() {} }),
            readPage: (fetch, query) => fetch(query),
            actionButton(label, icon, click) { const button = element('button'); button.icon = icon; button.click = click; buttons.push(button); return button; },
        },
        VietRouteUI: { modal(id, options) {
            modals.set(id, options);
            return { open() { node(id).open = true; }, focus() {} };
        } },
        VietRouteResourceGrants: { model: grants.model, render() {} },
    };
    const document = { querySelector: () => root, getElementById: node, createElement: element, documentElement: { lang: 'vi' } };
    vm.runInNewContext(fs.readFileSync(`src/main/webapp/assets/js/${page}.js`, 'utf8'), { window, document, Date, FormData, URLSearchParams });
    return { node, requests, errors, buttons, modals, state, user };
}

test('Role tải bảng trước metadata; modal lỗi giữ mở và chặn lưu, mở lại tải snapshot mới', async () => {
    const ui = fixture('roles');
    await settle();
    assert.equal(ui.requests.length, 2);
    assert.ok(ui.requests[1].includes('/registry/roles/page?'));
    const wait = deferred();
    ui.state.override = (path, read) => path.endsWith('/tree') ? wait.promise : read(path);
    ui.buttons.find((item) => item.icon === 'assign').click();
    assert.equal(ui.node('grants-dialog').open, true);
    assert.equal(ui.modals.get('grants-dialog').busy(), true);
    assert.equal(ui.node('grants-save').disabled, true);
    wait.reject({ response: { status: 503 } });
    await settle();
    assert.equal(ui.errors[0].target, 'grants-feedback');
    assert.equal(ui.node('grants-dialog').open, true);
    assert.equal(ui.node('grants-save').disabled, true);
    ui.node('grants-form').handlers.submit({ preventDefault() {} });
    assert.equal(ui.state.mutations, 0);
    ui.modals.get('grants-dialog').onClose();
    ui.state.override = undefined;
    ui.buttons.find((item) => item.icon === 'assign').click();
    await settle();
    assert.equal(ui.node('grants-save').disabled, false);
    assert.equal(ui.requests.filter((path) => path.endsWith('/tree')).length, 2);
});

test('Tài khoản chỉ tải me/capabilities/bảng; tạo chỉ tải role khi mở và lỗi không được gửi', async () => {
    const ui = fixture('accounts');
    await settle();
    assert.deepEqual(ui.requests.sort(), ['/api/v1/auth/me', '/api/v1/auth/capabilities', '/api/v1/admin/accounts'].sort());
    const wait = deferred();
    ui.state.override = (path, read) => path.endsWith('/registry/roles') ? wait.promise : read(path);
    ui.node('accounts-create').handlers.click();
    assert.equal(ui.node('account-edit-dialog').open, true);
    assert.equal(ui.modals.get('account-edit-dialog').busy(), true);
    wait.reject({ response: { status: 503 } });
    await settle();
    assert.equal(ui.errors[0].target, 'account-edit-feedback');
    assert.equal(ui.node('account-save').disabled, true);
    ui.node('account-edit-form').handlers.submit({ preventDefault() {} });
    assert.equal(ui.state.mutations, 0);
    ui.modals.get('account-edit-dialog').onClose();
    ui.state.override = undefined;
    ui.node('accounts-create').handlers.click();
    await settle();
    assert.equal(ui.node('account-save').disabled, false);
    assert.equal(ui.requests.filter((path) => path.endsWith('/registry/roles')).length, 2);
    assert.equal(ui.requests.some((path) => path.endsWith('/tree')), false);
});

test('Chi tiết tài khoản tải tree trong modal và preview đúng target; lỗi metadata không xóa bảng', async () => {
    const ui = fixture('accounts');
    await settle();
    const rows = ui.node('accounts-body').children.length, wait = deferred();
    ui.state.override = (path, read) => path.endsWith('/tree') ? wait.promise : read(path);
    ui.buttons.find((item) => item.icon === 'view').click();
    assert.equal(ui.node('account-detail-dialog').open, true);
    assert.ok(ui.requests.includes('/api/v1/admin/accounts/2/access'));
    wait.reject({ response: { status: 403 } });
    await settle();
    assert.equal(ui.errors[0].target, 'account-detail-feedback');
    assert.equal(ui.node('account-state-open').hidden, true);
    assert.equal(ui.node('accounts-body').children.length, rows);
});

test('Bộ lọc role trả focus sau metadata lỗi, không tự retry và cho thử lại khi người dùng quay lại', async () => {
    const ui = fixture('accounts');
    await settle();
    ui.state.override = (path, read) => path.endsWith('/registry/roles') ? Promise.reject({response:{status:503}}) : read(path);
    ui.node('account-role-filter').focus();
    await settle();
    assert.equal(ui.node('account-role-filter').focused,true);
    assert.equal(ui.requests.filter((path) => path.endsWith('/registry/roles')).length,1);
    ui.state.override = undefined;
    ui.node('account-role-filter').focus();
    await settle();
    assert.equal(ui.requests.filter((path) => path.endsWith('/registry/roles')).length,2);
    assert.equal(ui.node('account-role-filter').children.length,1);
});
