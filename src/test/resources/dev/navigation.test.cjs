const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

// Header thật chạy với kích thước/DOM giả; không mạng, không lưu menu hay quyền vào storage.
function fixture({ width = 390, available = 240, content = 900 } = {}) {
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
    const header = node('header'),
        strip = node('navigation-links', header),
        previous = node('previous', header),
        next = node('next', header);
    const primary = node('primary', strip),
        account = node('account', header),
        outside = node('outside');
    const link = node('link', primary);
    link.href = 'https://fixture.test/operations/reports?lang=vi';
    const summary = node('summary', primary),
        popup = node('popup', primary),
        accountSummary = node('account-summary', account),
        accountPopup = node('account-popup', account);
    header.querySelector = (selector) => (selector.includes('previous') ? previous : next);
    header.querySelectorAll = () => [primary, account];
    primary.querySelector = (selector) => (selector === 'summary' ? summary : popup);
    account.querySelector = (selector) => (selector === 'summary' ? accountSummary : accountPopup);
    strip.clientWidth = available;
    strip.scrollWidth = content;
    strip.scrollLeft = 0;
    strip.scrollTo = ({ left }) => {
        strip.scrollLeft = Math.max(0, Math.min(left, strip.scrollWidth - strip.clientWidth));
        strip.handlers.scroll();
    };
    strip.scrollBy = ({ left }) => strip.scrollTo({ left: strip.scrollLeft + left });
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
    let resized;
    vm.runInNewContext(fs.readFileSync('src/main/webapp/assets/js/navigation.js', 'utf8'), {
        document,
        window,
        URL,
        ResizeObserver: class {
            constructor(fn) {
                resized = fn;
            }
            observe() {}
        },
    });
    return {
        strip,
        previous,
        next,
        primary,
        account,
        outside,
        summary,
        popup,
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
        key(key, target = strip) {
            const e = {
                key,
                target,
                preventDefault() {
                    this.defaultPrevented = true;
                },
            };
            (target === strip ? strip.handlers : handlers).keydown(e);
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
        resize(size) {
            strip.clientWidth = size;
            resized();
            events.resize();
        },
    };
}

test('Header luôn hiện cùng dải cuộn ở mobile/desktop, chỉ thêm nút cuộn khi thiếu chỗ', () => {
    for (const width of [320, 390, 768, 1280, 1600]) {
        const ui = fixture({ width });
        assert.equal(ui.strip.hidden, false);
        assert.equal(ui.previous.hidden, false);
        assert.equal(ui.previous.disabled, true);
        assert.equal(ui.next.disabled, false);
        ui.resize(1000);
        assert.equal(ui.strip.hidden, false);
        assert.equal(ui.previous.hidden, true);
        assert.equal(ui.next.hidden, true);
    }
});
test('Nút cuộn và bàn phím đi được hai chiều, không bắt phím của link/menu con', () => {
    const ui = fixture();
    ui.next.handlers.click();
    assert.ok(ui.strip.scrollLeft > 0);
    ui.key('End');
    assert.equal(ui.next.disabled, true);
    ui.key('Home');
    assert.equal(ui.previous.disabled, true);
    ui.key('ArrowRight');
    assert.ok(ui.strip.scrollLeft > 0);
    ui.previous.handlers.click();
    assert.equal(ui.strip.scrollLeft, 0);
    const e = {
        key: 'ArrowRight',
        target: ui.link,
        preventDefault() {
            throw Error('Không giành phím link');
        },
    };
    ui.strip.handlers.keydown(e);
});
test('Popup không bị cắt bởi vùng cuộn, neo trong viewport và mỗi lần chỉ mở một menu', () => {
    const ui = fixture();
    ui.open(ui.primary);
    assert.equal(ui.popup.style.values['--menu-left'], '138px');
    assert.equal(ui.popup.style.values['--menu-top'], '72px');
    ui.open(ui.account);
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
test('Tab ra ngoài, scroll và resize đóng popup; Escape của modal vẫn được giữ riêng', () => {
    const ui = fixture();
    ui.open(ui.primary);
    ui.outside.focus();
    assert.equal(ui.primary.open, false);
    ui.open(ui.primary);
    ui.strip.scrollBy({ left: 100 });
    assert.equal(ui.primary.open, false);
    ui.open(ui.primary);
    ui.resize(300);
    assert.equal(ui.primary.open, false);
    ui.open(ui.primary);
    ui.state.dialogOpen = true;
    assert.equal(ui.escape().defaultPrevented, undefined);
    assert.equal(ui.primary.open, true);
});
test('Chuyển trang thường hiện tiến trình ngay, bfcache dọn trạng thái; không bắt link mở tab mới/tải file/hash', () => {
    const ui = fixture();
    ui.click(ui.link);
    assert.equal(ui.state.progress.hidden, false);
    ui.events.pageshow();
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
