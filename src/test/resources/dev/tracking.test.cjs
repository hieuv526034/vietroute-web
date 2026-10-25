const { test } = require('node:test');
const assert = require('node:assert/strict');
const { sharing } = require('../../../main/webapp/assets/js/tracking.js');

const fixture = (overrides = {}) => {
    let success, failure, time = 1000000;
    const sent = [], stopped = [], cleared = [], states = [], errors = [];
    const controller = sharing({
        start: async () => ({ sessionKey: 'consent-a', minSampleSeconds: 10, path: '/trip-a' }),
        stop: async (session) => { stopped.push(session); },
        sample: async (body, session) => { sent.push({ body, session }); },
        geolocation: {
            watchPosition(ok, error, options) {
                success = ok; failure = error;
                assert.equal(options.maximumAge, 0);
                assert.equal(options.timeout, 15000);
                return 7;
            },
            clearWatch(id) { cleared.push(id); },
        },
        now: () => time,
        onState: (state) => { states.push(state); },
        onError: (error) => { errors.push(error); },
        ...overrides,
    });
    const point = (timestamp = time) => ({ coords: { latitude: 10.12345678, longitude: 106.12345678, accuracy: 12.345 }, timestamp });
    return { controller, sent, stopped, cleared, states, errors, point, push: (value) => success(value), fail: (code) => failure({ code }), advance: (ms) => { time += ms; } };
};

test('Chỉ bắt đầu watch khi có consent và gửi mẫu thiết bị đã chuẩn hóa', async () => {
    const f = fixture();
    await f.controller.begin();
    await f.push(f.point());
    assert.equal(f.sent.length, 1);
    assert.deepEqual(f.sent[0].body, { sessionKey: 'consent-a', latitude: 10.123457, longitude: 106.123457, accuracy: 12.35, measuredAt: new Date(1000000).toISOString() });
    assert.equal(f.sent[0].session.path, '/trip-a');
    assert.equal(f.states.at(-1), 'SHARING');
    await f.controller.halt();
    assert.deepEqual(f.cleared, [7]);
    assert.equal(f.stopped.length, 1);
});

test('Từ chối vị trí hoặc timeout dừng watch và thu hồi consent, không tự bật lại', async () => {
    for (const [code, status] of [[1, 'DENIED'], [3, 'TIMEOUT'], [2, 'UNAVAILABLE']]) {
        const f = fixture();
        await f.controller.begin();
        await f.fail(code);
        assert.equal(f.controller.isActive(), false);
        assert.equal(f.states.at(-1), status);
        assert.deepEqual(f.cleared, [7]);
        assert.equal(f.stopped.length, 1);
        await f.push(f.point());
        assert.equal(f.sent.length, 0);
    }
});

test('Không gửi mẫu cũ, future hoặc tọa độ không hữu hạn; không chế timestamp mới', async () => {
    const f = fixture();
    await f.controller.begin();
    await f.push(f.point(1000000 - 120001));
    await f.push(f.point(1000000 + 15001));
    const invalid = f.point(); invalid.coords.latitude = NaN;
    await f.push(invalid);
    assert.equal(f.sent.length, 0);
    assert.equal(f.states.at(-1), 'OLD');
    await f.controller.halt();
});

test('Mẫu mới cách nhau tối thiểu 10 giây và không xếp hàng tọa độ trong RAM', async () => {
    const f = fixture();
    await f.controller.begin();
    await f.push(f.point());
    f.advance(9999); await f.push(f.point());
    assert.equal(f.sent.length, 1);
    f.advance(1); await f.push(f.point());
    assert.equal(f.sent.length, 2);
    await f.controller.halt();
});

test('Lỗi HTTP dừng chia sẻ, xóa watch và không tự retry mutation', async () => {
    let attempts = 0;
    const f = fixture({ sample: async () => { attempts++; throw new Error('network unavailable'); } });
    await f.controller.begin();
    await f.push(f.point());
    f.advance(20000); await f.push(f.point());
    assert.equal(attempts, 1);
    assert.equal(f.controller.isActive(), false);
    assert.equal(f.stopped.length, 1);
    assert.equal(f.errors.length, 1);
});

test('Đóng màn hình trong lúc POST bật chưa xong vẫn thu hồi đúng consent, không chạy watch', async () => {
    let release;
    const f = fixture({ start: () => new Promise((resolve) => { release = resolve; }) });
    const pending = f.controller.begin();
    await f.controller.halt();
    release({ sessionKey: 'late-session', minSampleSeconds: 10, path: '/old-trip' });
    await pending;
    assert.equal(f.controller.isActive(), false);
    assert.equal(f.sent.length, 0);
    assert.deepEqual(f.stopped, [{ sessionKey: 'late-session', minSampleSeconds: 10, path: '/old-trip' }]);
    assert.equal(f.cleared.length, 0);
});

test('Bấm bật lặp chỉ tạo một consent; dừng lặp không phát thêm request', async () => {
    let starts = 0;
    const f = fixture({ start: async () => { starts++; return { sessionKey: 'one', minSampleSeconds: 10 }; } });
    await Promise.all([f.controller.begin(), f.controller.begin()]);
    await f.controller.begin();
    assert.equal(starts, 1);
    await f.controller.halt(); await f.controller.halt();
    assert.equal(f.stopped.length, 1);
});

test('Dừng xóa watch ngay cả khi backend dừng bị lỗi, không giữ tọa độ để gửi lại', async () => {
    const f = fixture({ stop: async () => { throw new Error('offline'); } });
    await f.controller.begin();
    await f.controller.halt();
    assert.equal(f.controller.isActive(), false);
    assert.deepEqual(f.cleared, [7]);
    await f.push(f.point());
    assert.equal(f.sent.length, 0);
    assert.equal(f.errors.length, 1);
});
const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const trackingSource = readFileSync(require.resolve('../../../main/webapp/assets/js/tracking.js'), 'utf8');
const settle = () => new Promise(setImmediate);
const deferred = () => {
    let resolve, reject;
    const promise = new Promise((ok, error) => { resolve = ok; reject = error; });
    return { promise, resolve, reject };
};
const panelFixture = (overrides = {}) => {
    const node = () => ({
        disabled: false, hidden: false, textContent: '', children: [], handlers: {},
        addEventListener(name, handler) { this.handlers[name] = handler; },
        replaceChildren() { this.children = []; },
        append(...children) { this.children.push(...children); },
        async click() { if (!this.disabled) await this.handlers.click?.(); },
    });
    const nodes = Object.fromEntries(['status', 'summary', 'trail', 'start', 'stop', 'refresh', 'feedback'].map(key => [key, node()]));
    nodes.feedback.id = 'tracking-feedback';
    const dialog = { ...node(), open: true };
    const reads = [], mutations = [], watches = [], cleared = [];
    let poll;
    const feedback = { value: '' };
    const root = {
        dataset: { loading: 'Đang tải…', off: 'OFF', waiting: 'WAITING', stopped: 'STOPPED', offline: 'OFFLINE' },
        hidden: false,
        querySelector(selector) { return nodes[selector.slice('[data-tracking-'.length, -1)]; },
        closest() { return dialog; },
    };
    const window = {
        isSecureContext: true, addEventListener() {},
        VietRoute: {
            api: { get(path) { const result = deferred(); reads.push({ ...result, path }); return result.promise; } },
            async mutate(path, body) {
                mutations.push({ path, body });
                if (path.endsWith('/start')) {
                    if (overrides.start) return overrides.start();
                    return { data: { data: { sessionKey: 'fixture-consent', minSampleSeconds: 10 } } };
                }
                if (overrides.stop) return overrides.stop();
                return {};
            },
            feedback(message) { feedback.value = message; },
            failure(error) { feedback.value = error.message; },
        },
    };
    const document = { hidden: false, documentElement: { lang: 'vi' }, addEventListener() {}, createElement: node };
    const navigator = { geolocation: {
        watchPosition(success, failure) { watches.push({ success, failure }); return watches.length; },
        clearWatch(id) { cleared.push(id); },
    } };
    const sandbox = { module: { exports: {} }, window, document, navigator,
        setInterval(callback) { poll = callback; return 1; }, clearInterval() {} };
    vm.runInNewContext(trackingSource, sandbox);
    const panel = sandbox.module.exports.attach(root, { driver: true });
    const context = { path: '/driver/trip-a/tracking', version: 1, canWrite: true, canStart: true };
    const reply = (index, status = 'OFF', extra = {}) => reads[index].resolve({ data: { data: { status, latest: null, trail: [], ...extra } } });
    return { panel, context, nodes, reads, mutations, watches, cleared, feedback, reply, poll: () => poll() };
};

test('Panel dùng loading khi mở và GET cũ OFF không thu hồi consent vừa bật', async () => {
    const f = panelFixture();
    f.panel.open(f.context);
    assert.equal(f.nodes.status.textContent, 'Đang tải…');
    await f.nodes.start.click();
    assert.equal(f.watches.length, 1);
    f.reply(0, 'OFF');
    await settle();
    assert.equal(f.nodes.status.textContent, 'WAITING');
    assert.equal(f.cleared.length, 0);
    assert.equal(f.mutations.filter(row => row.path.endsWith('/stop')).length, 0);
});

test('Poll không đọc snapshot cũ trong khi POST bật còn chờ', async () => {
    const start = deferred();
    const f = panelFixture({ start: () => start.promise });
    f.panel.open(f.context); f.reply(0); await settle();
    const beginning = f.nodes.start.click();
    f.poll();
    assert.equal(f.reads.length, 1);
    assert.equal(f.nodes.refresh.disabled, true);
    start.resolve({ data: { data: { sessionKey: 'fixture-consent', minSampleSeconds: 10 } } });
    await beginning;
    assert.equal(f.reads.length, 2);
    f.reply(1, 'WAITING'); await settle();
    assert.equal(f.watches.length, 1);
});

test('Dừng xóa watch và tọa độ ngay; GET LIVE cũ không khôi phục panel', async () => {
    const stop = deferred();
    const f = panelFixture({ stop: () => stop.promise });
    f.panel.open(f.context); f.reply(0); await settle();
    await f.nodes.start.click();
    const stopping = f.nodes.stop.click();
    assert.deepEqual(f.cleared, [1]);
    assert.equal(f.nodes.start.disabled, true);
    f.reply(1, 'LIVE', { latest: { latitude: 10, longitude: 106, accuracy: 10, measuredAt: '2026-10-25T00:00:00Z' } });
    await settle();
    assert.equal(f.nodes.summary.children.length, 0);
    assert.equal(f.nodes.status.textContent, 'STOPPED');
    stop.resolve({}); await stopping;
    assert.equal(f.nodes.start.disabled, false);
});

test('GET phục hồi xóa lỗi đọc cũ mà không gửi mutation', async () => {
    const f = panelFixture();
    f.panel.open(f.context); f.reads[0].reject(new Error('COM013')); await settle();
    assert.equal(f.feedback.value, 'COM013');
    const refreshing = f.nodes.refresh.click();
    f.reply(1); await refreshing; await settle();
    assert.equal(f.feedback.value, '');
    assert.equal(f.nodes.status.textContent, 'OFF');
    assert.equal(f.mutations.length, 0);
});

test('GET thành công không xóa lỗi POST bật phát sinh sau lỗi đọc', async () => {
    const f = panelFixture({ start: async () => { throw new Error('START_FAILED'); } });
    f.panel.open(f.context); f.reads[0].reject(new Error('COM013')); await settle();
    await f.nodes.start.click();
    f.reply(1); await settle();
    assert.equal(f.feedback.value, 'START_FAILED');
    assert.equal(f.watches.length, 0);
});

test('Lỗi GET cũ đang thu hồi consent không ghi vào chuyến vừa mở', async () => {
    const stop = deferred();
    const f = panelFixture({ stop: () => stop.promise });
    f.panel.open(f.context); f.reply(0); await settle();
    await f.nodes.start.click();
    f.reads[1].reject(new Error('OLD_READ_FAILED')); await settle();
    f.panel.open({ ...f.context, path: '/driver/trip-b/tracking' });
    stop.resolve({}); await settle();
    assert.equal(f.feedback.value, '');
    assert.equal(f.nodes.status.textContent, 'Đang tải…');
    f.poll();
    assert.equal(f.reads.at(-1).path, '/driver/trip-b/tracking');
});
