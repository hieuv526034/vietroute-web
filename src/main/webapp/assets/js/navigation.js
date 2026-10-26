/* Thu gọn theo chỗ trống thực tế và viewport; quyền/menu vẫn do server quyết định. */
(() => {
    'use strict';
    const header = document.querySelector('.site-nav');
    const toggle = document.getElementById('navigation-toggle');
    const panel = document.getElementById('navigation-links');
    if (!header || !toggle || !panel) return;

    const compact = window.matchMedia('(max-width: 1399px)');
    const menus = Array.from(header.querySelectorAll('.operations-menu'));
    let expanded = false;
    let isCompact = compact.matches;

    const closeMenus = (except, within = header) => {
        for (const menu of menus) {
            if (menu !== except && within.contains(menu)) menu.open = false;
        }
    };
    const render = () => {
        toggle.hidden = !isCompact;
        panel.hidden = isCompact && !expanded;
        toggle.setAttribute('aria-expanded', String(isCompact && expanded));
        toggle.setAttribute('aria-label', expanded ? toggle.dataset.closeLabel : toggle.dataset.openLabel);
    };
    const collapse = (restoreFocus = false) => {
        expanded = false;
        closeMenus(null, panel);
        render();
        if (restoreFocus && isCompact) toggle.focus();
    };

    // Đo một hàng tự nhiên, kể cả khi panel đang ẩn; không phụ thuộc số menu hoặc độ dài bản dịch.
    const fit = () => {
        const focusInPanel = panel.contains(document.activeElement);
        const focusOnToggle = document.activeElement === toggle;
        const previous = isCompact;
        header.classList.remove('navigation-compact');
        toggle.hidden = true;
        panel.hidden = false;
        const gap = parseFloat(window.getComputedStyle(panel).columnGap) || 0;
        const items = Array.from(panel.children).filter((item) => !item.hidden);
        const required =
            items.reduce((width, item) => width + item.getBoundingClientRect().width, 0) +
            Math.max(0, items.length - 1) * gap;
        isCompact = compact.matches || required > panel.clientWidth;
        header.classList.toggle('navigation-compact', isCompact);
        if (previous !== isCompact) {
            closeMenus();
            collapse(isCompact && focusInPanel);
            if (!isCompact && focusOnToggle) panel.querySelector('a,summary,button')?.focus();
        } else {
            render();
        }
    };

    header.classList.add('navigation-ready');
    fit();
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
            if (isCompact && !panel.contains(menu)) collapse();
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
        } else if (isCompact && expanded) {
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
    compact.addEventListener('change', fit);
    new ResizeObserver(fit).observe(header);
    new MutationObserver(fit).observe(header, { childList: true, characterData: true, subtree: true });
    document.fonts?.ready.then(fit);
})();
