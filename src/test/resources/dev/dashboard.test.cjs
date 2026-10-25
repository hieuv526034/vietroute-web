const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const { readFileSync } = require('node:fs');
const { plan, dateRange } = require('../../../main/webapp/assets/js/dashboard.js');
const source = readFileSync(require.resolve('../../../main/webapp/assets/js/dashboard.js'), 'utf8');
const cap = (feature, target, view = true) => ({ function: feature, view, actions: ['READ'], targets: [target] });

test('Dashboard chỉ lập kế hoạch từ endpoint hiệu lực, VIEW hoặc tên role không tự cấp READ', () => {
    assert.deepEqual(
        plan([{ function: 'REPORT', view: true, actions: ['READ'], targets: ['GET /operations/reports'] }]),
        [],
    );
    assert.deepEqual(
        plan(
            [cap('DRIVER_TRIP', 'GET /api/v1/driver/trips'), cap('BOOKING', 'GET /api/v1/admin/bookings')],
            ['DRIVER'],
        ),
        ['driver', 'staff'],
    );
    assert.deepEqual(plan([cap('MY_BOOKING', 'GET /api/v1/my-bookings', false)], ['CUSTOMER']), ['customer']);
    assert.deepEqual(
        plan([cap('MY_BOOKING', 'GET /api/v1/my-bookings'), cap('DRIVER_TRIP', 'GET /api/v1/driver/trips')], ['ADMIN']),
        [],
    );
});

test('Khoảng ngày dashboard dùng lịch Việt Nam qua ranh giới tháng và năm', () => {
    assert.deepEqual(dateRange(new Date('2026-12-31T18:00:00Z')), {
        today: '2027-01-01',
        fromDate: '2027-01-01',
        toDate: '2027-02-28',
    });
    assert.deepEqual(dateRange(new Date('2028-01-30T12:00:00Z')), {
        today: '2028-01-30',
        fromDate: '2028-01-01',
        toDate: '2028-02-29',
    });
});

const fixture = (capabilities, getOverride) => {
    const ids = new Map();
    const node = (tag = 'div') => ({
        tag,
        children: [],
        dataset: {},
        attrs: {},
        listeners: {},
        disabled: false,
        textContent: '',
        append(...values) {
            this.children.push(...values);
        },
        replaceChildren() {
            this.children = [];
        },
        setAttribute(key, value) {
            this.attrs[key] = value;
        },
        addEventListener(key, handler) {
            this.listeners[key] = handler;
        },
    });
    for (const id of [
        'business-dashboard',
        'dashboard-refresh',
        'dashboard-metrics',
        'dashboard-tasks',
        'dashboard-feedback',
        'dashboard-status',
        'dashboard-range',
        'dashboard-updated',
    ])
        ids.set(id, node());
    const root = ids.get('business-dashboard');
    root.dataset = {
        loading: 'loading',
        empty: 'empty',
        noData: 'no-data',
        updated: 'updated',
        range: 'range',
        myOrders: 'my-orders',
        pending: 'pending',
        confirmed: 'confirmed',
        partial: 'partial',
        recent: 'recent',
    };
    const calls = [],
        failures = [];
    const api = {
        async get(path, options) {
            calls.push({ path, options });
            let value;
            if (path === '/api/v1/auth/me')
                value = { mustChangePassword: false, emailVerificationRequired: false, roles: ['CUSTOMER', 'DRIVER'] };
            else if (path === '/api/v1/auth/capabilities') value = capabilities;
            else if (getOverride) value = await getOverride(path, options);
            else value = { totalElements: 101, content: [] };
            return { data: { data: value } };
        },
    };
    const sandbox = {
        module: { exports: {} },
        document: { documentElement: { lang: 'en' }, getElementById: (id) => ids.get(id), createElement: node },
        window: {
            location: {
                assign() {
                    throw Error('unexpected redirect');
                },
            },
            VietRoute: {
                api,
                route: (path) => path + '?lang=en',
                feedback() {},
                failure(error, id) {
                    failures.push({ error, id });
                },
            },
        },
    };
    vm.runInNewContext(source, sandbox);
    sandbox.module.exports.mount();
    return { ids, calls, failures };
};
const settle = () => new Promise(setImmediate);

test('VIEW-only không gọi dữ liệu; READ-only dùng totalElements và không thêm link trang private', async () => {
    const view = fixture([{ function: 'MY_BOOKING', view: true, actions: [], targets: [] }]);
    await settle();
    assert.deepEqual(
        view.calls.map((row) => row.path),
        ['/api/v1/auth/me', '/api/v1/auth/capabilities'],
    );
    const read = fixture([cap('MY_BOOKING', 'GET /api/v1/my-bookings', false)]);
    await settle();
    const metrics = read.ids.get('dashboard-metrics').children;
    assert.equal(metrics[0].children[1].textContent, '101');
    assert.equal(
        read.ids.get('dashboard-tasks').children[0].children.some((row) => row.tag === 'a'),
        false,
    );
});

test('Lỗi một khối không chặn khối được phép tiếp theo và không tải endpoint ngoài kế hoạch', async () => {
    const f = fixture(
        [cap('REPORT', 'GET /api/v1/admin/reports'), cap('BOOKING', 'GET /api/v1/admin/bookings')],
        async (path) => {
            if (path.includes('reports')) throw Error('503');
            return { totalElements: 3, content: [] };
        },
    );
    await settle();
    assert.equal(f.failures.length, 1);
    assert.equal(f.failures[0].id, 'dashboard-report-error');
    assert.equal(f.ids.get('dashboard-metrics').children[0].children[1].textContent, '3');
    assert.equal(
        f.calls.some((row) => row.path.includes('driver/trips')),
        false,
    );
    assert.equal(
        f.calls.some((row) => row.path.includes('my-bookings')),
        false,
    );
});

test('Bấm làm mới liên tiếp không nhân đôi lượt đọc đang chạy', async () => {
    let release;
    const f = fixture(
        [cap('MY_BOOKING', 'GET /api/v1/my-bookings')],
        () =>
            new Promise((resolve) => {
                release = resolve;
            }),
    );
    await settle();
    f.ids.get('dashboard-refresh').listeners.click();
    f.ids.get('dashboard-refresh').listeners.click();
    assert.equal(f.calls.filter((row) => row.path === '/api/v1/auth/me').length, 1);
    assert.equal(f.ids.get('dashboard-refresh').disabled, true);
    release({ totalElements: 0, content: [] });
    await settle();
});

test('Danh sách có dữ liệu dựng đúng mã, chặng, giờ và link có locale', async () => {
    const f = fixture([cap('MY_BOOKING', 'GET /api/v1/my-bookings')], async () => ({
        totalElements: 1,
        content: [
            {
                code: 'VR8',
                tripCode: 'VR20261105HN',
                status: 'PENDING',
                pickupNameVi: 'Điểm đón',
                pickupNameEn: 'Pickup',
                dropoffNameVi: 'Điểm trả',
                dropoffNameEn: 'Dropoff',
                pickupAt: '2026-11-05T01:00:00Z',
            },
        ],
    }));
    await settle();
    assert.equal(f.failures.length, 0);
    const card = f.ids.get('dashboard-tasks').children[0];
    assert.equal(card.children.find((row) => row.tag === 'a').href, '/my-bookings?lang=en');
    const row = card.children.find((row) => row.tag === 'ul').children[0];
    assert.equal(row.children[0].textContent, 'VR8 · VR20261105HN');
    assert.match(row.children[2].textContent, /Pickup → Dropoff/);
    assert.equal(f.ids.get('dashboard-range').textContent, '');
});
