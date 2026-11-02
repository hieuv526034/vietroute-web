const { test } = require('node:test');
const assert = require('node:assert/strict');
const { pageWindow, boundedPage, lookupSummary, readPage, readOptions } = require('../../../main/webapp/assets/js/table.js');

test('Select danh mục tải đủ trang sau, gộp ID trùng và không tự thử lại khi lỗi', async () => {
    const calls = [];
    const items = await readOptions(async (query) => {
        calls.push(query);
        return query.page === 0
            ? { content: [{ id: 1 }, { id: 2 }], hasNext: true }
            : { content: [{ id: 2 }, { id: 3 }], hasNext: false };
    });
    assert.deepEqual(calls, [{ page: 0, size: 100 }, { page: 1, size: 100 }]);
    assert.deepEqual(items.map((item) => item.id), [1, 2, 3]);
    let attempts = 0;
    await assert.rejects(readOptions(async () => {
        if (++attempts === 2) throw new Error('offline');
        return { content: [{ id: 1 }], hasNext: true };
    }));
    assert.equal(attempts, 2);
});

test('Nhãn bộ chọn dùng tổng từ server, dịch đúng trang và không hiển thị trang 0/0 khi rỗng', () => {
    const vi = '{total} kết quả · Trang {page}/{pages}';
    assert.equal(lookupSummary({ totalElements: 24, page: 1, totalPages: 3 }, vi, 'Chưa có dữ liệu.'), '24 kết quả · Trang 2/3');
    assert.equal(lookupSummary({ totalElements: 6, page: 0, totalPages: 1 }, vi, 'Chưa có dữ liệu.'), '6 kết quả · Trang 1/1');
    assert.equal(lookupSummary({ totalElements: 0, page: 0, totalPages: 0 }, vi, 'Chưa có dữ liệu.'), 'Chưa có dữ liệu.');
    assert.equal(lookupSummary({ totalElements: 24, page: 2, totalPages: 3 }, '{total} results · Page {page}/{pages}', 'No data'), '24 results · Page 3/3');
});

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
