const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

// Header thật chạy với kích thước/DOM giả; không mạng, không lưu menu hay quyền vào storage.
function fixture({ width = 390 } = {}) {
    const handlers = {},
        events = {},
        nodes = new Map();
    const state = { dialogOpen: false };
    const document = {
        activeElement: null,
        getElementById: (id) => nodes.get(id),
        querySelector: (selector) =>
            selector === '.site-nav'
                ? header
                : selector.startsWith('meta')
                  ? { content: 'Loading page' }
                  : state.dialogOpen
                    ? {}
                    : null,
        addEventListener(type, fn) {
            handlers[type] = fn;
        },
        createElement: () => node('progress'),
        body: {
            append(item) {
                state.progress = item;
            },
        },
    };
    function node(id, parent) {
        const result = {
            id,
            parent,
            hidden: false,
            disabled: false,
            open: false,
            attrs: {},
            classList: { contains: (name) => id === 'account' && name === 'account-menu' },
            handlers: {},
            style: {
                values: {},
                setProperty(key, value) {
                    this.values[key] = value;
                },
            },
            addEventListener(type, fn) {
                this.handlers[type] = fn;
            },
            setAttribute(key, value) {
                this.attrs[key] = value;
            },
            hasAttribute(key) {
                return key in this.attrs;
            },
            contains(other) {
                for (let x = other; x; x = x.parent) if (x === this) return true;
                return false;
            },
            closest(selector) {
                return selector === 'a[href]' && this.href ? this : null;
            },
            focus() {
                document.activeElement = this;
                handlers.focusin?.({ target: this });
            },
            getBoundingClientRect() {
                return { left: width - 80, bottom: 64, width: 240 };
            },
        };
        nodes.set(id, result);
        return result;
    }
    const header = node('header');
    const primary = node('primary', header),
        account = node('account', header),
        outside = node('outside');
    const link = node('link', primary);
    link.href = 'https://fixture.test/operations/reports?lang=vi';
    const summary = node('summary', primary),
        popup = node('popup', primary),
        accountSummary = node('account-summary', account),
        accountPopup = node('account-popup', account);
    header.querySelectorAll = () => [primary, account];
    primary.querySelector = (selector) => (selector === 'summary' ? summary : popup);
    account.querySelector = (selector) => (selector === 'summary' ? accountSummary : accountPopup);
    const window = {
        innerWidth: width,
        innerHeight: 844,
        location: {
            href: 'https://fixture.test/operations/accounts?lang=vi',
            origin: 'https://fixture.test',
            pathname: '/operations/accounts',
            search: '?lang=vi',
        },
        addEventListener(type, fn) {
            events[type] = fn;
        },
    };
    vm.runInNewContext(fs.readFileSync('src/main/webapp/assets/js/navigation.js', 'utf8'), {
        document,
        window,
        URL,
    });
    return {
        primary,
        account,
        outside,
        summary,
        popup,
        accountPopup,
        link,
        state,
        document,
        events,
        open(menu) {
            menu.open = true;
            menu.handlers.toggle();
        },
        click(target, extra = {}) {
            handlers.click({ target, button: 0, ...extra });
        },
        key(key, target = summary) {
            const e = {
                key,
                target,
                preventDefault() {
                    this.defaultPrevented = true;
                },
            };
            handlers.keydown(e);
            return e;
        },
        escape() {
            const e = {
                key: 'Escape',
                preventDefault() {
                    this.defaultPrevented = true;
                },
            };
            handlers.keydown(e);
            return e;
        },
        resize() {
            events.resize();
        },
    };
}

test('Menu đóng lúc khởi tạo và trở về từ bfcache, không tự mở theo chiều rộng', () => {
    for (const width of [320, 390, 768, 1280, 1600]) {
        const ui = fixture({ width });
        assert.equal(ui.primary.open, false);
        ui.open(ui.primary);
        ui.resize();
        assert.equal(ui.primary.open, true);
        ui.events.pageshow({ persisted: false });
        assert.equal(ui.primary.open, true);
        ui.events.pageshow({ persisted: true });
        assert.equal(ui.primary.open, false);
    }
});
test('Popup tài khoản neo trong viewport và mỗi lần chỉ mở một menu', () => {
    const ui = fixture();
    ui.open(ui.primary);
    assert.deepEqual(ui.popup.style.values, {});
    ui.open(ui.account);
    assert.equal(ui.accountPopup.style.values['--menu-left'], '138px');
    assert.equal(ui.accountPopup.style.values['--menu-top'], '72px');
    assert.equal(ui.primary.open, false);
    ui.open(ui.primary);
    assert.equal(ui.account.open, false);
    ui.escape();
    assert.equal(ui.primary.open, false);
    assert.equal(ui.document.activeElement, ui.summary);
    ui.open(ui.primary);
    ui.click(ui.outside);
    assert.equal(ui.primary.open, false);
});
test('Tab ra ngoài đóng menu; Escape của modal và phím điều hướng link vẫn được giữ riêng', () => {
    const ui = fixture();
    ui.open(ui.primary);
    ui.outside.focus();
    assert.equal(ui.primary.open, false);
    ui.open(ui.primary);
    assert.equal(ui.key('ArrowRight', ui.link).defaultPrevented, undefined);
    ui.state.dialogOpen = true;
    assert.equal(ui.escape().defaultPrevented, undefined);
    assert.equal(ui.primary.open, true);
});
test('Chuyển trang thường hiện tiến trình ngay, bfcache dọn trạng thái; không bắt link mở tab mới/tải file/hash', () => {
    const ui = fixture();
    ui.open(ui.primary);
    ui.click(ui.link);
    assert.equal(ui.primary.open, false);
    assert.equal(ui.state.progress.hidden, false);
    ui.events.pageshow({ persisted: true });
    assert.equal(ui.state.progress.hidden, true);
    ui.click(ui.link, { ctrlKey: true });
    assert.equal(ui.state.progress.hidden, true);
    ui.link.target = '_blank';
    ui.click(ui.link);
    assert.equal(ui.state.progress.hidden, true);
    ui.link.target = '';
    ui.link.attrs.download = '';
    ui.click(ui.link);
    assert.equal(ui.state.progress.hidden, true);
    delete ui.link.attrs.download;
    ui.link.href = 'https://external.test/';
    ui.click(ui.link);
    assert.equal(ui.state.progress.hidden, true);
    ui.link.href = 'https://fixture.test/operations/accounts?lang=vi#anchor';
    ui.click(ui.link);
    assert.equal(ui.state.progress.hidden, true);
});
