const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

// Chạy script UI thật với DOM/API fixture, không mở browser hoặc đọc credential/mạng.
function fixture() {
    const nodes = new Map();
    const node = (id) => {
        const value = {
            hidden: false,
            disabled: false,
            dataset: {},
            handlers: {},
            attrs: {},
            textContent: 'Restore',
            addEventListener(type, action) {
                this.handlers[type] = action;
            },
            setAttribute(key, text) {
                this.attrs[key] = text;
            },
            removeAttribute(key) {
                delete this.attrs[key];
            },
            focus() {
                this.focused = true;
            },
        };
        nodes.set(id, value);
        return value;
    };
    const form = node('admin-recovery-form');
    const dialog = node('admin-recovery-dialog');
    const controls = node('admin-recovery-controls');
    const open = node('admin-recovery-open');
    const login = node('admin-recovery-login');
    login.hidden = true;
    const submit = node('submit');
    form.password = 'Fixture-Current-Only!';
    form.dataset.endpoint = '/api/v1/auth/admin-recovery';
    form.dataset.success = 'Restored; sign in again';
    form.querySelector = () => submit;
    form.reset = () => {
        form.password = '';
    };
    const state = { calls: 0, errors: [] };
    const window = {
        VietRoute: {
            mutate(url, body) {
                state.calls++;
                state.payload = { ...body };
                return new Promise((resolve, reject) => {
                    state.resolve = resolve;
                    state.reject = reject;
                });
            },
            feedback(message) {
                state.feedback = message;
            },
            failure(error) {
                state.errors.push(error);
            },
        },
        VietRouteForms: { validate: () => true, clear() {} },
        VietRouteUI: {
            modal(id, options) {
                state.modal = options;
                return {
                    open() {
                        dialog.open = true;
                    },
                };
            },
        },
    };
    vm.runInNewContext(
        fs.readFileSync(path.resolve(__dirname, '../../../main/webapp/assets/js/admin-recovery.js'), 'utf8'),
        {
            window,
            document: { getElementById: (id) => nodes.get(id) },
            FormData: class {
                constructor(source) {
                    this.source = source;
                }
                *[Symbol.iterator]() {
                    yield ['currentPassword', this.source.password];
                }
            },
        },
    );
    return { form, controls, dialog, open, login, state, submit: () => form.handlers.submit({ preventDefault() {} }) };
}

test('recovery modal blocks duplicate submit and stays open with draft after error', async () => {
    const ui = fixture();
    ui.open.handlers.click();
    const pending = ui.submit();
    await ui.submit();
    assert.equal(ui.state.calls, 1);
    assert.equal(ui.controls.disabled, true);
    assert.equal(ui.state.modal.busy(), true);
    ui.state.reject(new Error('503 fixture'));
    await pending;
    assert.equal(ui.dialog.open, true);
    assert.equal(ui.form.hidden, false);
    assert.equal(ui.form.password, 'Fixture-Current-Only!');
    assert.equal(ui.controls.disabled, false);
    assert.equal(ui.state.errors.length, 1);
    assert.equal(ui.state.calls, 1);
});

test('successful recovery clears password and requires explicit sign in without replay', async () => {
    const ui = fixture();
    const pending = ui.submit();
    ui.state.resolve({ data: { data: { loginRequired: true } } });
    await pending;
    assert.equal(ui.form.password, '');
    assert.equal(ui.form.hidden, true);
    assert.equal(ui.login.hidden, false);
    assert.equal(ui.login.focused, true);
    assert.equal(ui.open.disabled, true);
    assert.equal(ui.controls.disabled, false);
    assert.equal(ui.state.calls, 1);
});
