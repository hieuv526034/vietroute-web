const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// Chạy navigation.js thật với DOM và viewport giả; không đăng nhập, gọi API hoặc thay quyền.
function fixture(width = 390) {
    const nodes = new Map();
    const handlers = {};
    const state = { dialogOpen: false };
    const document = {
        activeElement: null,
        getElementById: (id) => nodes.get(id),
        querySelector: (selector) => (selector === '.site-nav' ? header : state.dialogOpen ? {} : null),
        addEventListener(type, action) {
            handlers[type] = action;
        },
    };
    function node(id, parent) {
        const result = {
            parent,
            hidden: false,
            open: false,
            attrs: {},
            dataset: {},
            handlers: {},
            classList: { add() {} },
            addEventListener(type, action) {
                this.handlers[type] = action;
            },
            setAttribute(name, value) {
                this.attrs[name] = value;
            },
            contains(other) {
                for (let current = other; current; current = current.parent) {
                    if (current === this) return true;
                }
                return false;
            },
            focus() {
                document.activeElement = this;
                handlers.focusin?.({ target: this });
            },
            closest(selector) {
                return selector === 'a[href]' && this.link ? this : null;
            },
        };
        nodes.set(id, result);
        return result;
    }
    const header = node('header');
    const panel = node('navigation-links', header);
    const toggle = node('navigation-toggle', header);
    toggle.dataset = { openLabel: 'Open navigation', closeLabel: 'Close navigation' };
    const home = node('home', panel);
    home.link = true;
    const primary = node('primary', panel);
    const primarySummary = node('primary-summary', primary);
    const primaryLink = node('primary-link', primary);
    primaryLink.link = true;
    const account = node('account', header);
    const accountSummary = node('account-summary', account);
    const outside = node('outside');
    header.querySelectorAll = () => [primary, account];
    panel.querySelector = () => home;
    primary.querySelector = () => primarySummary;
    account.querySelector = () => accountSummary;
    const media = {
        matches: false,
        addEventListener(type, action) {
            this.change = action;
        },
    };
    vm.runInNewContext(
        fs.readFileSync(path.resolve(__dirname, '../../../main/webapp/assets/js/navigation.js'), 'utf8'),
        {
            document,
            window: {
                matchMedia(query) {
                    const boundary = /^\(max-width:\s*(\d+)px\)$/.exec(query);
                    assert.ok(boundary, 'Fixture chỉ hỗ trợ truy vấn max-width được khai báo rõ');
                    media.boundary = Number(boundary[1]);
                    media.matches = width <= media.boundary;
                    return media;
                },
            },
        },
    );
    return {
        panel,
        toggle,
        home,
        primary,
        primarySummary,
        primaryLink,
        account,
        accountSummary,
        outside,
        document,
        state,
        clickToggle: () => toggle.handlers.click(),
        open(menu) {
            menu.open = true;
            menu.handlers.toggle();
        },
        click: (target) => handlers.click({ target }),
        escape() {
            const event = {
                key: 'Escape',
                preventDefault() {
                    this.defaultPrevented = true;
                },
            };
            handlers.keydown(event);
            return event;
        },
        resize(nextWidth) {
            const compact = nextWidth <= media.boundary;
            if (media.matches !== compact) {
                media.matches = compact;
                media.change();
            }
        },
    };
}

test('compact navigation exposes one panel with ARIA and Escape closes the submenu before the panel', () => {
    for (const width of [320, 390, 768, 1199, 1280, 1399]) {
        const ui = fixture(width);
        assert.equal(ui.panel.hidden, true);
        assert.equal(ui.toggle.hidden, false);
        assert.equal(ui.toggle.attrs['aria-expanded'], 'false');
        ui.clickToggle();
        assert.equal(ui.panel.hidden, false);
        assert.equal(ui.toggle.attrs['aria-expanded'], 'true');
        assert.equal(ui.toggle.attrs['aria-label'], 'Close navigation');
        assert.equal(ui.document.activeElement, ui.home);
        ui.open(ui.primary);
        assert.equal(ui.escape().defaultPrevented, true);
        assert.equal(ui.primary.open, false);
        assert.equal(ui.panel.hidden, false);
        assert.equal(ui.document.activeElement, ui.primarySummary);
        ui.escape();
        assert.equal(ui.panel.hidden, true);
        assert.equal(ui.document.activeElement, ui.toggle);
    }
});

test('avatar and navigation remain separate and opening one closes the other', () => {
    const ui = fixture();
    ui.clickToggle();
    ui.open(ui.primary);
    ui.open(ui.account);
    assert.equal(ui.account.open, true);
    assert.equal(ui.primary.open, false);
    assert.equal(ui.panel.hidden, true);
    ui.escape();
    assert.equal(ui.account.open, false);
    assert.equal(ui.document.activeElement, ui.accountSummary);
    ui.open(ui.account);
    ui.clickToggle();
    assert.equal(ui.account.open, false);
    assert.equal(ui.panel.hidden, false);
});

test('click outside, selecting a link and Tab leaving the header dismiss navigation without trapping focus', () => {
    const ui = fixture();
    ui.clickToggle();
    ui.click(ui.outside);
    assert.equal(ui.panel.hidden, true);
    ui.clickToggle();
    ui.open(ui.primary);
    ui.click(ui.primaryLink);
    assert.equal(ui.panel.hidden, true);
    assert.equal(ui.primary.open, false);
    ui.clickToggle();
    ui.outside.focus();
    assert.equal(ui.panel.hidden, true);
    assert.equal(ui.document.activeElement, ui.outside);
});

test('resizing restores desktop links and never leaves focus inside a hidden control', () => {
    const ui = fixture(1400);
    assert.equal(ui.panel.hidden, false);
    assert.equal(ui.toggle.hidden, true);
    ui.primaryLink.focus();
    ui.open(ui.primary);
    ui.resize(390);
    assert.equal(ui.panel.hidden, true);
    assert.equal(ui.primary.open, false);
    assert.equal(ui.document.activeElement, ui.toggle);
    ui.resize(1400);
    assert.equal(ui.toggle.hidden, true);
    assert.equal(ui.panel.hidden, false);
    assert.equal(ui.document.activeElement, ui.home);
});

test('header does not consume Escape from a dialog with its own busy guard', () => {
    const ui = fixture();
    ui.clickToggle();
    ui.state.dialogOpen = true;
    assert.equal(ui.escape().defaultPrevented, undefined);
    assert.equal(ui.panel.hidden, false);
    ui.state.dialogOpen = false;
    ui.escape();
    assert.equal(ui.panel.hidden, true);
});
