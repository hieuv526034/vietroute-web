/* Tab/modal dùng chung: chỉ điều khiển trình bày; không cấp quyền hay giữ token/dữ liệu nghiệp vụ. */
(() => {
    'use strict';
    const groups = new WeakMap();
    const tabs = (root) => {
        if (groups.has(root)) {
            return groups.get(root);
        }
        const buttons = Array.from(root.querySelectorAll('[data-tab-target]'));
        let active = (
            buttons.find((button) => !button.hidden && button.getAttribute('aria-selected') === 'true') ||
            buttons.find((button) => !button.hidden)
        )?.dataset.tabTarget;
        const sync = () => {
            if (!buttons.some((button) => !button.hidden && button.dataset.tabTarget === active)) {
                active = buttons.find((button) => !button.hidden)?.dataset.tabTarget;
            }
            for (const button of buttons) {
                const selected = !button.hidden && button.dataset.tabTarget === active;
                const panel = document.getElementById(button.dataset.tabTarget);
                button.setAttribute('role', 'tab');
                button.setAttribute('aria-selected', String(selected));
                button.setAttribute('aria-controls', panel.id);
                button.tabIndex = selected ? 0 : -1;
                panel.setAttribute('role', 'tabpanel');
                panel.setAttribute('aria-labelledby', button.id);
                panel.tabIndex = 0;
                panel.hidden = !selected;
            }
        };
        const select = (id, focus = false) => {
            const button = buttons.find((item) => !item.hidden && item.dataset.tabTarget === id);
            if (!button || button.disabled || root.closest('[aria-busy="true"]')) {
                return;
            }
            active = id;
            sync();
            if (focus) {
                button.focus();
            }
        };
        for (const button of buttons) {
            button.addEventListener('click', () => select(button.dataset.tabTarget));
            button.addEventListener('keydown', (event) => {
                const available = buttons.filter((item) => !item.hidden && !item.disabled);
                const index = available.indexOf(button);
                let next;
                if (event.key === 'ArrowRight') next = available[(index + 1) % available.length];
                if (event.key === 'ArrowLeft') next = available[(index - 1 + available.length) % available.length];
                if (event.key === 'Home') next = available[0];
                if (event.key === 'End') next = available[available.length - 1];
                if (next) {
                    event.preventDefault();
                    select(next.dataset.tabTarget, true);
                }
            });
        }
        const controller = {
            select,
            show(id, visible) {
                const button = buttons.find((item) => item.dataset.tabTarget === id);
                if (button) button.hidden = !visible;
                sync();
            },
        };
        groups.set(root, controller);
        sync();
        return controller;
    };
    const initializeTabs = (scope = document) => scope.querySelectorAll('[data-tabs]').forEach(tabs);

    // Dialog native giữ focus bên trong. Không cho đóng khi request đang gửi; đóng xong trả focus về nút mở.
    const modal = (id, { busy = () => false, onClose = () => {} } = {}) => {
        const dialog = document.getElementById(id);
        let opener;
        const close = () => {
            if (!busy() && dialog.open) dialog.close();
        };
        // Khóa fieldset có thể làm browser đưa focus ra body. Giữ tại tiêu đề, không cướp focus đang ở trong modal.
        const focus = () => {
            if (dialog.open && !dialog.contains(document.activeElement)) {
                const heading = dialog.querySelector('h2');
                if (heading) {
                    heading.tabIndex = -1;
                    heading.focus();
                }
            }
        };
        dialog.querySelectorAll('[data-dialog-close]').forEach((button) => button.addEventListener('click', close));
        dialog.addEventListener('cancel', (event) => {
            if (busy()) event.preventDefault();
        });
        dialog.addEventListener('close', () => {
            onClose();
            if (opener?.isConnected && !opener.disabled) opener.focus();
        });
        return {
            open(trigger = document.activeElement) {
                if (!dialog.open) {
                    opener = trigger;
                    dialog.showModal();
                    focus();
                }
            },
            close,
            focus,
            get isOpen() {
                return dialog.open;
            },
        };
    };
    window.VietRouteUI = Object.freeze({ tabs, initializeTabs, modal });
    initializeTabs();
})();
