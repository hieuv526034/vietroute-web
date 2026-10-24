const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const vm = require('node:vm');
const context = { window: {} };
vm.runInNewContext(fs.readFileSync('src/main/webapp/assets/js/role-picker.js', 'utf8'), context);
const picker = context.window.VietRouteRolePicker;

test('Tìm role theo tên vi/en bỏ dấu hoặc mã, không làm mất lựa chọn ngoài kết quả', () => {
    assert.equal(picker.matches('OPERATOR', 'Điều hành nhà xe', 'dieu hanh'), true);
    assert.equal(picker.matches('OPERATOR', 'Điều hành nhà xe', 'operator'), true);
    assert.equal(picker.matches('DRIVER', 'Driver', 'khach'), false);
    const choices = [
        { dataset: { roleChoice: 'DRIVER' }, textContent: 'Tài xế DRIVER', checked: true },
        { dataset: { roleChoice: 'CUSTOMER' }, textContent: 'Khách CUSTOMER', checked: false },
    ];
    const handlers = {};
    const input = { value: 'khach', addEventListener: (event, callback) => (handlers[event] = callback) };
    const status = {};
    const control = picker.bind(input, { querySelectorAll: () => choices }, status);
    handlers.input();
    assert.equal(choices[0].hidden, true);
    assert.equal(choices[0].checked, true);
    assert.equal(choices[1].hidden, false);
    assert.equal(status.textContent, '1 / 2');
    control.reset();
    assert.equal(choices[0].hidden, false);
    assert.equal(choices[0].checked, true);
});
