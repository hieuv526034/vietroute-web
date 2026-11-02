const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const tables = require('../../../main/webapp/assets/js/table.js');
const requests = require('../../../main/webapp/assets/js/request-state.js');

// Chạy client thật: đổi danh mục chỉ tải lựa chọn phụ thuộc, chưa gửi truy vấn chuyến.
function fixture(access = null) {
    const nodes = new Map(), state = { calls: [], errors: [] };
    const node = (id) => {
        if (nodes.has(id)) return nodes.get(id);
        const item = {
            value: '', dataset: {}, children: [], handlers: {}, attrs: {},
            addEventListener(type, handler) { this.handlers[type] = handler; },
            setAttribute(key, value) { this.attrs[key] = value; },
            replaceChildren(...children) { this.children = children; this.value = ''; },
            append(...children) { this.children.push(...children); },
            focus() {},
        };
        nodes.set(id, item);
        return item;
    };
    const root = node('journey-workspace');
    root.dataset = { authenticated: access ? 'true' : 'false', loading: 'Loading', choose: 'Choose', all: 'All', empty: 'Empty' };
    const form = node('journey-form');
    form.elements = Object.fromEntries(['date', 'seats', 'carrierId', 'vehicleTypeId', 'minPrice', 'maxPrice'].map((name) => [name, node(name)]));
    form.elements.seats.value = '1';
    const pickers = ['from', 'to'].map((side) => {
        const item = node(side);
        item.dataset.side = side;
        item.querySelector = (selector) => node(side + selector);
        return item;
    });
    const filters = ['CARRIER', 'VEHICLE_TYPE'].map((kind) => {
        const item = node(kind);
        item.dataset.kind = kind;
        item.querySelector = () => form.elements[kind === 'CARRIER' ? 'carrierId' : 'vehicleTypeId'];
        return item;
    });
    form.querySelectorAll = (selector) => selector === '.journey-picker' ? pickers : filters;
    const api = {
        async get(path, { params }) {
            state.calls.push({ path, params });
            if (path === '/api/v1/trips/1') return { data: { data: { id: 1, code: 'TRIP01', status: 'OPEN', stops: [] } } };
            if (path === '/api/v1/locations' && state.failLocation) throw new Error('Unavailable');
            const content = path === '/api/v1/provinces'
                ? [{ id: params.page + 1, name: 'Province ' + (params.page + 1) }]
                : path === '/api/v1/locations'
                    ? [{ id: params.provinceId * 10, name: 'Location ' + params.provinceId }]
                    : path === '/api/v1/trips' ? [] : [{ id: 1, name: 'Choice' }];
            return { data: { data: { content, hasNext: path === '/api/v1/provinces' && params.page === 0, page: params.page, totalPages: 1, totalElements: content.length } } };
        },
    };
    vm.runInNewContext(fs.readFileSync('src/main/webapp/assets/js/journeys.js', 'utf8'), {
        URLSearchParams, Intl, Date, location: { search: access ? '?tripId=1' : '' },
        requestAnimationFrame: (callback) => callback(),
        document: { documentElement: { lang: 'vi' }, getElementById: node, querySelector: () => ({ content: '' }), createElement: (tag) => node(Symbol(tag)) },
        window: {
            VietRoute: { api, feedback() {}, failure(error) { state.errors.push(error); } },
            VietRouteTable: { ...tables, pager: () => ({ query: { page: 0, size: 20 }, update() {} }) },
            VietRouteRequests: requests,
            VietRouteUI: { modal: () => ({ isOpen: false, open() {} }), tabs: () => ({ select() {} }) },
            VietRouteAccess: { load: async () => access },
            VietRouteForms: { validate: () => true, clear() {} },
        },
    });
    const idle = async () => {
        for (let tries = 0; tries < 50 && root.attrs['aria-busy'] !== 'false'; tries++) await new Promise(setImmediate);
        assert.equal(root.attrs['aria-busy'], 'false');
    };
    const province = (side) => node(side + '[data-place-province]');
    const place = (side) => node(side + '[data-place-select]');
    const change = async (side, value) => { province(side).value = value; province(side).handlers.change(); await idle(); };
    return { state, node, form, province, place, change, idle };
}

test('Chọn tỉnh tải đúng địa điểm, đổi tỉnh xóa địa điểm cũ; chỉ submit mới tìm chuyến', async () => {
    const ui = fixture();
    await ui.idle();
    assert.equal(ui.province('from').children.length, 3); // placeholder và đủ hai trang tỉnh
    assert.equal(ui.place('from').disabled, true);
    assert.equal(ui.state.calls.some((call) => call.path === '/api/v1/locations'), false);
    await ui.change('from', '1');
    assert.equal(ui.place('from').children[1].value, '10');
    ui.place('from').value = '10';
    await ui.change('from', '2');
    assert.equal(ui.place('from').value, '');
    assert.equal(ui.place('from').children[1].value, '20');
    await ui.change('to', '1');
    ui.place('to').value = '10';
    assert.equal(ui.state.calls.some((call) => call.path === '/api/v1/trips'), false);
    ui.form.handlers.submit({ preventDefault() {} });
    await ui.idle();
    const search = ui.state.calls.find((call) => call.path === '/api/v1/trips').params;
    assert.equal(search.fromProvinceId, '2');
    assert.equal(search.toLocationId, '10');
    assert.equal(search.fromLocationId, undefined);
    assert.equal(ui.state.errors.length, 0);
});

test('Trang tìm chuyến công khai cho đặt khi có cả quyền trang đơn và API tạo, không cần grant trang công khai', async () => {
    const ui = fixture({ view: false, targets: ['GET /my-bookings', 'POST /api/v1/my-bookings'] });
    await ui.idle();
    assert.equal(ui.state.errors.length, 0);
    assert.equal(ui.node('journey-book').hidden, false);
    assert.match(ui.node('journey-book').href, /\/my-bookings\?.*tripId=1/);
});

test('Thiếu quyền trang đơn hoặc API tạo thì vẫn ẩn đặt chỗ dù đọc được chuyến', async () => {
    for (const targets of [['GET /my-bookings'], ['POST /api/v1/my-bookings']]) {
        const ui = fixture({ view: true, targets });
        await ui.idle();
        assert.equal(ui.state.errors.length, 0);
        assert.equal(ui.node('journey-book').hidden, true);
    }
});

test('Lỗi tải địa điểm giữ tỉnh, xóa lựa chọn lỗi thời và không tự gửi lại request', async () => {
    const ui = fixture();
    await ui.idle();
    await ui.change('from', '1');
    ui.place('from').value = '10';
    ui.state.failLocation = true;
    await ui.change('from', '2');
    assert.equal(ui.province('from').value, '2');
    assert.equal(ui.place('from').value, '');
    assert.equal(ui.place('from').disabled, true);
    assert.equal(ui.state.errors.length, 1);
    assert.equal(ui.state.calls.filter((call) => call.path === '/api/v1/locations').length, 2);
    ui.state.failLocation = false;
    await ui.change('from', '1');
    assert.equal(ui.place('from').disabled, false);
});
