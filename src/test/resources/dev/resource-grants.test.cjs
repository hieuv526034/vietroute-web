const { test } = require('node:test');
const assert = require('node:assert/strict');
const { model } = require('../../../main/webapp/assets/js/resource-grants.js');

test('menu and same-action APIs remain independent; future APIs are never selected implicitly', () => {
    const menu = 'GET /operations/vehicles', list = 'GET /api/v1/admin/vehicles', detail = list + '/{id}';
    const value = model([menu], [menu, list]);
    value.toggle(list, true);
    value.toggle(detail, true);
    assert.deepEqual(value.values(), [list, menu]);
    assert.deepEqual(value.changes(), { added: [list], removed: [] });
    assert.deepEqual(value.state([menu, list, detail]), { checked: false, partial: true });
    value.toggle(menu, false);
    assert.deepEqual(value.values(), [list]);
    assert.deepEqual(value.changes(), { added: [list], removed: [menu] });
});

test('filtering never discards hidden grants and retired targets can only be removed', () => {
    const value = model(['GET /retired', 'GET /visible'], ['GET /visible']);
    value.toggle('GET /visible', false);
    assert.deepEqual(value.values(), ['GET /retired']);
    value.toggle('GET /retired', false);
    value.toggle('GET /retired', true);
    assert.deepEqual(value.values(), []);
});
