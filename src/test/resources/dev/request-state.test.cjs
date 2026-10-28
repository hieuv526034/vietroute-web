const { test } = require('node:test');
const assert = require('node:assert/strict');
const { singleFlight, allReads } = require('../../../main/webapp/assets/js/request-state.js');

test('Nhóm metadata lỗi sớm vẫn đợi request còn lại trước khi cho mở khóa controls', async () => {
    let release, finished = false;
    const slow = new Promise((resolve) => { release = resolve; });
    const failure = Error('503');
    const result = allReads([Promise.reject(failure), slow]);
    const checked = assert.rejects(result, (error) => error === failure).then(() => { finished = true; });
    await new Promise(setImmediate);
    assert.equal(finished, false);
    release('remaining response');
    await checked;
    assert.equal(finished, true);
    assert.deepEqual(await allReads([Promise.resolve('first'), Promise.resolve('second')]), ['first', 'second']);
});

test('Bấm lưu lặp trong lúc HTTP đang chạy chỉ thực hiện một mutation', async () => {
    const run = singleFlight();
    let calls = 0, release;
    const action = () => { calls++; return new Promise((resolve) => { release = resolve; }); };
    const first = run('POST:/profile', action), second = run('POST:/profile', action);
    await Promise.resolve();
    assert.equal(calls, 1);
    assert.equal(first, second);
    release('saved');
    assert.deepEqual(await Promise.all([first, second]), ['saved', 'saved']);
    assert.equal(await run('POST:/profile', () => 'manual-new-save'), 'manual-new-save');
});

test('Lỗi không tự retry; mở lại khóa cho lần người dùng chủ động lưu sau', async () => {
    const run = singleFlight(); let calls = 0;
    await assert.rejects(run('PUT:/profile', () => { calls++; throw Error('409'); }), /409/);
    assert.equal(calls, 1);
    assert.equal(await run('PUT:/profile', () => { calls++; return 'ok'; }), 'ok');
    assert.equal(calls, 2);
});
