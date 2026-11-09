const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

// Chạy client thật để kiểm chi tiết, link gọi và xóa liên hệ khi mở đơn khác.
function fixture() {
    const nodes = new Map();
    const errors = [];
    const node = (id) => {
        if (id === 'booking-money-form') return null;
        if (nodes.has(id)) return nodes.get(id);
        const item = {
            children: [], handlers: {}, attrs: {}, value: '',
            addEventListener(type, action) { this.handlers[type] = action; },
            setAttribute(key, value) { this.attrs[key] = value; },
            replaceChildren(...children) { this.children = children; },
            append(...children) { this.children.push(...children); },
            reset() {}, focus() {}, contains() { return true; },
        };
        nodes.set(id, item);
        return item;
    };
    const root = node('booking-workspace');
    root.dataset = { customer: 'true', driverContact: 'Tài xế', callDriver: 'Gọi tài xế', plate: 'Biển số' };
    for (const id of ['booking-create-form', 'booking-state-form', 'booking-search']) {
        node(id).elements = Object.fromEntries(['tripId', 'pickupStopId', 'dropoffStopId', 'seats', 'query', 'status', 'note'].map((key) => [key, node(id + key)]));
    }
    const booking = {
        id: 1, code: 'VR1', tripId: 2, status: 'CONFIRMED', pickupAt: '2026-11-10T02:30:00Z',
        dropoffAt: '2026-11-10T04:30:00Z', totalPrice: 180000, paidAmount: 0, refundedAmount: 0,
        events: [], driverName: '<Tên tài xế>', driverPhone: '+84 (901) 234-567', plateNumber: 'TEST-01',
    };
    const api = {
        async get(path) {
            return { data: { data: path.endsWith('/1') ? { ...booking } : { content: [{ ...booking }], totalElements: 1 } } };
        },
    };
    vm.runInNewContext(fs.readFileSync('src/main/webapp/assets/js/bookings.js', 'utf8'), {
        URLSearchParams, Intl, Date, crypto: { randomUUID: () => 'test-only' }, location: { search: '' },
        requestAnimationFrame: (action) => action(),
        document: { documentElement: { lang: 'vi' }, getElementById: node, querySelector: () => ({ content: '' }), createElement: (tag) => node(Symbol(tag)) },
        window: {
            VietRoute: { api, feedback() {}, failure(error) { errors.push(error); } },
            VietRouteTable: {
                pager: () => ({ query: { page: 0, size: 10 }, update() {} }),
                readPage: (read, query) => read(query),
                actionButton(label, icon, action) { const item = node(Symbol(label)); item.click = () => action(item); return item; },
            },
            VietRouteUI: { modal: () => ({ isOpen: false, open() {}, close() {} }), tabs: () => ({ select() {} }) },
            VietRouteAccess: { load: async () => ({ targets: ['GET /api/v1/my-bookings', 'GET /api/v1/my-bookings/{id}'] }) },
            VietRouteForms: { validate: () => true },
        },
    });
    const idle = async () => {
        for (let i = 0; i < 30 && root.attrs['aria-busy'] !== 'false'; i++) await new Promise(setImmediate);
        assert.equal(root.attrs['aria-busy'], 'false');
        assert.deepEqual(errors, []);
    };
    const open = async () => {
        await node('booking-body').children[0].children.at(-1).children[0].click();
        await idle();
    };
    const links = () => node('booking-summary').children.flatMap((item) => item.children).filter((item) => item.href);
    return { node, booking, idle, open, links };
}

test('Chi tiết đơn đã xác nhận có link gọi tài xế, biển số và hiển thị tên bằng text', async () => {
    const ui = fixture();
    await ui.idle();
    await ui.open();
    assert.equal(ui.links()[0].href, 'tel:+84901234567');
    assert.equal(ui.links()[0].textContent, 'Gọi tài xế: +84 (901) 234-567');
    assert.ok(ui.node('booking-summary').children.some((item) => item.textContent === 'Tài xế: <Tên tài xế>'));
    assert.ok(ui.node('booking-summary').children.some((item) => item.textContent === 'Biển số: TEST-01'));
    ui.booking.status = 'PENDING';
    await ui.open();
    assert.equal(ui.links().length, 0);
    assert.equal(ui.node('booking-summary').children.some((item) => item.textContent?.startsWith('Tài xế:')), false);
});

test('Đơn không có liên hệ không hiện dữ liệu cũ, số điện thoại không hợp lệ không tạo link gọi', async () => {
    const ui = fixture();
    await ui.idle();
    ui.booking.driverPhone = 'javascript:alert(1)';
    await ui.open();
    assert.equal(ui.links().length, 0);
    ui.booking.driverName = null;
    await ui.open();
    assert.equal(ui.node('booking-summary').children.some((item) => item.textContent?.startsWith('Tài xế:')), false);
});
