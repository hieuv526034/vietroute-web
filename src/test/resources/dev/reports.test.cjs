const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const { readPage } = require('../../../main/webapp/assets/js/table.js');

// Chạy client thật với API chỉ đọc giả; chọn/phân trang nhà xe không được tự tính lại báo cáo.
function fixture({ allowed = true } = {}) {
    const nodes = new Map(),
        state = { requests: [], errors: [], modalOpen: false };
    const node = (id) => {
        if (nodes.has(id)) return nodes.get(id);
        const item = {
            id,
            value: '',
            hidden: false,
            dataset: {},
            children: [],
            handlers: {},
            attrs: {},
            textContent: '',
            addEventListener(type, fn) {
                this.handlers[type] = fn;
            },
            setAttribute(key, value) {
                this.attrs[key] = value;
            },
            append(...items) {
                this.children.push(...items);
            },
            replaceChildren(...items) {
                this.children = items;
            },
            querySelectorAll() {
                return [];
            },
        };
        nodes.set(id, item);
        return item;
    };
    const root = node('report-workspace');
    root.dataset = {
        loading: 'Loading',
        all: 'All carriers',
        select: 'Select',
        empty: 'Empty',
        asOf: 'Updated',
        active: 'Active',
        inactive: 'Inactive',
        noRead: 'No permission',
    };
    const form = node('report-form');
    form.elements = { fromDate: node('from'), toDate: node('to'), carrierId: node('report-carrier') };
    form.elements.fromDate.value = '2026-10-01';
    form.elements.toDate.value = '2026-10-31';
    const nodesIds = [
        'report-results',
        'report-controls',
        'report-picker-controls',
        'report-status',
        'report-snapshot',
        'report-carriers-body',
        'report-carrier-name',
        'report-carrier-open',
        'report-carrier-all',
        'report-carrier-search',
        'report-query',
        'report-trips',
        'report-bookings',
    ];
    nodesIds.forEach(node);
    const api = {
        async get(path, config) {
            state.requests.push({ path, params: config.params });
            if (state.failNext && path.endsWith('/carriers')) {
                state.failNext = false;
                throw { response: { status: 503 } };
            }
            if (path.endsWith('/carriers')) {
                const page = config.params.page;
                return {
                    data: {
                        data: {
                            page,
                            size: 10,
                            totalElements: 24,
                            totalPages: 3,
                            hasNext: page < 2,
                            content: [
                                { id: page + 1, code: 'C' + (page + 1), name: 'Carrier ' + (page + 1), active: true },
                            ],
                        },
                    },
                };
            }
            return {
                data: {
                    data: {
                        fromDate: config.params.fromDate,
                        toDate: config.params.toDate,
                        asOf: '2026-10-27T07:00:00Z',
                        trips: [],
                        bookings: [],
                    },
                },
            };
        },
    };
    const window = {
        VietRoute: {
            api,
            feedback() {},
            failure(error, id) {
                state.errors.push({ error, id });
            },
        },
        VietRouteForms: { validate: () => true, clear() {} },
        VietRouteAccess: {
            load: async () => ({
                targets: allowed ? ['GET /api/v1/admin/reports', 'GET /api/v1/admin/reports/carriers'] : [],
            }),
        },
        VietRouteUI: {
            tabs: () => ({ select() {} }),
            modal: (id, options) => ({
                get isOpen() {
                    return state.modalOpen;
                },
                open() {
                    state.modalOpen = true;
                },
                close() {
                    if (!options.busy()) state.modalOpen = false;
                },
            }),
        },
        VietRouteTable: {
            readPage,
            pager: (prefix, options) => {
                state.pager = {
                    query: { page: 0, size: 10 },
                    update(result) {
                        this.query = { page: result.page, size: result.size };
                    },
                    change: options.onChange,
                };
                return state.pager;
            },
            actionButton: (label, icon, handler) => ({ label, handler }),
        },
    };
    const document = {
        getElementById: node,
        documentElement: { lang: 'vi' },
        createElement: (tag) => ({
            children: [],
            textContent: '',
            append(...x) {
                this.children.push(...x);
            },
        }),
    };
    vm.runInNewContext(fs.readFileSync('src/main/webapp/assets/js/reports.js', 'utf8'), {
        window,
        document,
        Intl,
        Date,
    });
    return {
        state,
        node,
        form,
        submit(id) {
            node(id).handlers.submit({ preventDefault() {}, currentTarget: node(id) });
        },
        open() {
            node('report-carrier-open').handlers.click({ currentTarget: node('report-carrier-open') });
        },
    };
}
const settle = async () => {
    for (let i = 0; i < 4; i++) await new Promise((resolve) => setImmediate(resolve));
};
test('Mở trang chỉ tải báo cáo, nhà xe chỉ tải khi mở modal', async () => {
    const ui = fixture();
    await settle();
    assert.equal(ui.state.requests.length, 1);
    assert.equal(ui.state.requests[0].path, '/api/v1/admin/reports');
    ui.open();
    await settle();
    assert.equal(ui.state.modalOpen, true);
    assert.equal(ui.state.requests.length, 2);
    assert.equal(ui.state.requests[1].path, '/api/v1/admin/reports/carriers');
});
test('Tìm/phân trang chỉ đổi danh sách nhà xe và dùng bộ lọc đã xác nhận', async () => {
    const ui = fixture();
    await settle();
    ui.open();
    await settle();
    ui.node('report-query').value = '  Carrier  ';
    ui.submit('report-carrier-search');
    await settle();
    assert.equal(ui.state.requests.at(-1).params.query, 'Carrier');
    assert.equal(ui.state.requests.at(-1).params.page, 0);
    ui.node('report-query').value = 'Draft not submitted';
    await ui.state.pager.change({ page: 1, size: 10 });
    assert.equal(ui.state.requests.at(-1).params.query, 'Carrier');
    assert.equal(ui.state.requests.at(-1).params.page, 1);
    assert.equal(ui.state.requests.filter((x) => x.path === '/api/v1/admin/reports').length, 1);
});
test('Chọn nhà xe giữ lựa chọn khi tìm tiếp, chỉ Xem báo cáo mới lấy số liệu theo ID đã chọn', async () => {
    const ui = fixture();
    await settle();
    ui.open();
    await settle();
    const row = ui.node('report-carriers-body').children[0];
    row.children[3].children[0].handler();
    assert.equal(ui.node('report-carrier').value, '1');
    assert.equal(ui.state.modalOpen, false);
    assert.equal(ui.node('report-results').hidden, true);
    ui.open();
    await settle();
    await ui.state.pager.change({ page: 1, size: 10 });
    assert.equal(ui.node('report-carrier').value, '1');
    ui.node('report-carrier-all').handlers.click();
    assert.equal(ui.node('report-carrier').value, '');
    ui.node('report-carrier').value = '1';
    ui.submit('report-form');
    await settle();
    assert.equal(ui.state.requests.at(-1).params.carrierId, '1');
});
test('Lỗi picker giữ modal/lựa chọn, báo ngay trong modal; thiếu quyền không gọi API', async () => {
    const ui = fixture();
    await settle();
    ui.node('report-carrier').value = '7';
    ui.state.failNext = true;
    ui.open();
    await settle();
    assert.equal(ui.state.modalOpen, true);
    assert.equal(ui.node('report-carrier').value, '7');
    assert.equal(ui.state.errors[0].id, 'report-picker-feedback');
    assert.equal(ui.node('report-picker-controls').disabled, false);
    const denied = fixture({ allowed: false });
    await settle();
    assert.equal(denied.state.requests.length, 0);
    assert.equal(denied.node('report-controls').disabled, true);
});
