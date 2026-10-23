const { test } = require('node:test');
const assert = require('node:assert/strict');
const { pageWindow, boundedPage, readPage } = require('../../../main/webapp/assets/js/table.js');

test('Phân trang rỗng và danh sách rất lớn vẫn chỉ tạo tối đa năm số trang', () => {
    assert.deepEqual(pageWindow(0, 0), []);
    assert.deepEqual(pageWindow(0, 2), [0, 1]);
    assert.deepEqual(pageWindow(0, 1000001), [0, 1, 2, 3, 4]);
    assert.deepEqual(pageWindow(50, 1000001), [48, 49, 50, 51, 52]);
    assert.deepEqual(pageWindow(1000000, 1000001), [999996, 999997, 999998, 999999, 1000000]);
    assert.equal(boundedPage(-1, 20), 0);
    assert.equal(boundedPage(100, 3), 2);
    assert.equal(boundedPage(1, 0), 0);
    assert.equal(boundedPage(2000000, 3000000), 1000000);
});

test('Xóa hết trang cuối chỉ đọc lại một trang hợp lệ, giữ nguyên size đã chọn', async () => {
    const calls = [];
    const result = await readPage(async (query) => {
        calls.push(query);
        return { ...query, totalPages: 3, totalElements: 55, content: query.page === 2 ? ['last'] : [] };
    }, { page: 3, size: 20 });
    assert.deepEqual(calls, [{ page: 3, size: 20 }, { page: 2, size: 20 }]);
    assert.deepEqual(result.content, ['last']);
});

test('Lỗi HTTP không tự retry hoặc giả định trang đã được tải thành công', async () => {
    let attempts = 0;
    await assert.rejects(readPage(async () => { attempts++; throw new Error('offline'); }, { page: 2, size: 50 }));
    assert.equal(attempts, 1);
});
