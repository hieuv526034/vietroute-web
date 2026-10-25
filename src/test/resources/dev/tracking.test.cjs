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
