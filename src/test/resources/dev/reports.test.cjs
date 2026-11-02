const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const { readPage, readOptions } = require('../../../main/webapp/assets/js/table.js');

// Chạy client thật với API chỉ đọc giả; select nhà xe không được tự tính lại báo cáo.
function fixture({ allowed = true, failPage = null } = {}) {
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
                if (page === failPage) throw { response: { status: 503 } };
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
            readOptions,
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
test('Select nhà xe đọc đủ trang 100 dòng, không cắt danh mục; báo cáo chỉ tải một lần', async () => {
    const ui = fixture();
    await settle();
    const choices = ui.state.requests.filter((x) => x.path.endsWith('/carriers'));
    assert.deepEqual(choices.map((x) => x.params.page), [0, 1, 2]);
    assert.ok(choices.every((x) => x.params.size === 100));
    assert.equal(ui.node('report-carrier').children.length, 4);
    assert.equal(ui.state.requests.filter((x) => x.path === '/api/v1/admin/reports').length, 1);
});
test('Đổi select chỉ ẩn snapshot cũ; submit mới gọi báo cáo với ID đã chọn', async () => {
    const ui = fixture();
    await settle();
    const count = ui.state.requests.length;
    ui.node('report-carrier').value = '2';
    ui.node('report-carrier').handlers.change();
    assert.equal(ui.node('report-results').hidden, true);
    assert.equal(ui.state.requests.length, count);
    ui.submit('report-form');
    ui.submit('report-form');
    await settle();
    assert.equal(ui.state.requests.at(-1).params.carrierId, '2');
    assert.equal(ui.state.requests.length, count + 1);
});
test('Lỗi giữa các trang không công bố select thiếu dữ liệu hoặc gọi báo cáo', async () => {
    const ui = fixture({ failPage: 1 });
    await settle();
    assert.equal(ui.node('report-carrier').children.length, 0);
    assert.equal(ui.state.errors.length, 1);
    assert.equal(ui.state.errors[0].id, 'report-feedback');
    assert.equal(ui.state.requests.filter((x) => x.path === '/api/v1/admin/reports').length, 0);
    assert.equal(ui.node('report-workspace').attrs['aria-busy'], 'false');
});
test('Thiếu quyền không tải danh mục hoặc báo cáo', async () => {
    const ui = fixture({ allowed: false });
    await settle();
    assert.equal(ui.state.requests.length, 0);
    assert.equal(ui.node('report-controls').disabled, true);
});
