/* Điều hướng dùng chung: thu gọn theo viewport, chỉ điều khiển UI; quyền và menu vẫn do server quyết định. */
(() => {
    'use strict';
    const header = document.querySelector('.site-nav');
    const toggle = document.getElementById('navigation-toggle');
    const panel = document.getElementById('navigation-links');
    if (!header || !toggle || !panel) return;

    const compact = window.matchMedia('(max-width: 1399px)');
    const menus = Array.from(header.querySelectorAll('.operations-menu'));
    let expanded = false;

    const closeMenus = (except, within = header) => {
        for (const menu of menus) {
            if (menu !== except && within.contains(menu)) menu.open = false;
        }
    };
    const render = () => {
        toggle.hidden = !compact.matches;
        panel.hidden = compact.matches && !expanded;
        toggle.setAttribute('aria-expanded', String(compact.matches && expanded));
        toggle.setAttribute('aria-label', expanded ? toggle.dataset.closeLabel : toggle.dataset.openLabel);
    };
    const collapse = (restoreFocus = false) => {
        expanded = false;
        closeMenus(null, panel);
        render();
        if (restoreFocus && compact.matches) toggle.focus();
    };

    header.classList.add('navigation-ready');
    render();
    toggle.addEventListener('click', () => {
        if (expanded) {
            collapse(true);
        } else {
            closeMenus();
            expanded = true;
            render();
            panel.querySelector('a,summary,button')?.focus();
        }
    });
    // Tài khoản luôn nằm trên thanh đầu; khi mở avatar thì đóng bảng điều hướng và ngược lại.
    for (const menu of menus) {
        menu.addEventListener('toggle', () => {
            if (!menu.open) return;
            closeMenus(menu);
            if (compact.matches && !panel.contains(menu)) collapse();
        });
    }
    document.addEventListener('click', (event) => {
        for (const menu of menus) {
            if (!menu.contains(event.target)) menu.open = false;
        }
        if (!header.contains(event.target) || (panel.contains(event.target) && event.target.closest('a[href]'))) {
            collapse();
        }
    });
    document.addEventListener('keydown', (event) => {
        // Dialog có cơ chế Escape/busy riêng; không giành bàn phím của modal đang nhập liệu.
        if (event.key !== 'Escape' || event.defaultPrevented || document.querySelector('dialog[open]')) return;
        const opened = menus.find((menu) => menu.open);
        if (opened) {
            opened.open = false;
            opened.querySelector('summary').focus();
            event.preventDefault();
        } else if (compact.matches && expanded) {
            collapse(true);
            event.preventDefault();
        }
    });
    // Đây là disclosure, không phải modal: Tab ra nội dung sẽ đóng menu, không khóa focus hoặc cuộn trang.
    document.addEventListener('focusin', (event) => {
        if (!header.contains(event.target)) {
            closeMenus();
            collapse();
        }
    });
    compact.addEventListener('change', () => {
        const focusInPanel = panel.contains(document.activeElement);
        const focusOnToggle = document.activeElement === toggle;
        closeMenus();
        collapse(compact.matches && focusInPanel);
        if (!compact.matches && focusOnToggle) panel.querySelector('a,summary,button')?.focus();
    });
})();
