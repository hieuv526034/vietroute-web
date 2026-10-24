/* Cây quyền: kiểm thao tác của người dùng, quyền dùng chung và draft; không cần browser/token/DB thật. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const context = { window: {} };
vm.runInNewContext(fs.readFileSync('src/main/webapp/assets/js/access-tree.js', 'utf8'), context);
const features = [{ viewPermission: 'CATALOG_VIEW', actions: [{ permission: 'CATALOG_CREATE' }, { permission: 'CATALOG_DELETE' }] }];
const model = (values = []) => context.window.VietRouteAccessTree.model(values, features);
test('Chọn CREATE gợi ý VIEW nhưng không cấp UPDATE/DELETE; preview ghi rõ cả hai quyền thêm', () => {
    const state = model(); state.toggle('CATALOG_CREATE', true);
    assert.deepEqual(Array.from(state.values()), ['CATALOG_CREATE', 'CATALOG_VIEW']);
    assert.deepEqual(Array.from(state.changes().added), ['CATALOG_CREATE', 'CATALOG_VIEW']);
});
test('Tích một quyền làm cha chọn một phần, không tự chọn các quyền còn lại', () => {
    const state = model(); state.toggle('CATALOG_CREATE', true);
    assert.equal(state.state(['CATALOG_CREATE', 'CATALOG_DELETE']).partial, true);
    assert.equal(state.has('CATALOG_DELETE'), false);
});
test('Quyền dùng chung chỉ lưu một mã; bỏ ở một vị trí bỏ mọi bản hiển thị', () => {
    const state = model(); state.toggle('SYSTEM_READ', true); state.toggle('SYSTEM_READ', true);
    assert.equal(Array.from(state.values()).filter((code) => code === 'SYSTEM_READ').length, 1);
    state.toggle('SYSTEM_READ', false); assert.equal(state.has('SYSTEM_READ'), false);
});
test('Quyền READ dùng chung chỉ gợi VIEW của chức năng vừa chọn, không mở thêm menu khác', () => {
    const features = [
        { code: 'MONITORING', viewPermission: 'SYSTEM_VIEW', actions: [{ permission: 'SYSTEM_READ' }] },
        { code: 'CONFIGURATION', viewPermission: 'CONFIGURATION_VIEW', actions: [{ permission: 'SYSTEM_READ' }] },
    ];
    const state = context.window.VietRouteAccessTree.model([], features);
    state.toggle('SYSTEM_READ', true, 'MONITORING');
    assert.equal(state.has('SYSTEM_VIEW'), true);
    assert.equal(state.has('CONFIGURATION_VIEW'), false);
    state.toggle('SYSTEM_READ', false, 'CONFIGURATION');
    assert.equal(state.has('SYSTEM_READ'), false);
});
test('Chỉ đổi kết quả đang lọc vẫn giữ quyền ngoài kết quả và bản gốc để xem trước', () => {
    const state = model(['OTHER_READ', 'CATALOG_DELETE']);
    state.toggle('CATALOG_CREATE', true); state.toggle('CATALOG_DELETE', false);
    assert.equal(state.has('OTHER_READ'), true);
    assert.deepEqual(Array.from(state.changes().removed), ['CATALOG_DELETE']);
    const draft = Array.from(state.values()); assert.deepEqual(Array.from(state.values()), draft);
});

test('Bộ hệ thống chỉ chọn endpoint quản trị được khai báo, không gom CRUD nghiệp vụ, WRITE cũ hay ADMIN-only', () => {
    const definitions = [
        { code: 'ACCESS', systemBundle: true, actions: [
            { permission: 'ACCESS_ASSIGN', endpoints: [{ adminOnly: false }] },
            { permission: 'ACCESS_WRITE', endpoints: [{ adminOnly: false }] },
        ] },
        { code: 'JOURNAL', systemBundle: true, actions: [{ permission: 'JOURNAL_VIEW', endpoints: [{ adminOnly: true }] }] },
        { code: 'CATALOG', systemBundle: false, actions: [{ permission: 'CATALOG_DELETE', endpoints: [{ adminOnly: false }] }] },
    ];
    const selection = context.window.VietRouteAccessTree.systemSelection(definitions);
    assert.deepEqual(Array.from(selection, (item) => item.code), ['ACCESS_ASSIGN']);
    const state = context.window.VietRouteAccessTree.model(['CATALOG_READ'], definitions);
    for (const item of selection) state.toggle(item.code, true, item.feature);
    assert.deepEqual(Array.from(state.values()), ['ACCESS_ASSIGN', 'CATALOG_READ']);
    assert.deepEqual(Array.from(state.changes().added), ['ACCESS_ASSIGN']);
});
