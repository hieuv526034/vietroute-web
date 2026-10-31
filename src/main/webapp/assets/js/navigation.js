/* Menu đóng sẵn từ HTML; JS quản lý đóng/mở, focus và tiến trình chuyển trang. */
(() => {
    'use strict';
    const header = document.querySelector('.site-nav');
    if (!header) return;
    const menus = Array.from(header.querySelectorAll('.navigation-menu, .account-menu'));
    const closeMenus = (except) => {
        for (const menu of menus) {
            if (menu === except) continue;
            menu.open = false;
        }
    };
    // Bảng chức năng dùng vị trí theo header; popup tài khoản nhỏ vẫn neo vào viewport.
    const positionAccount = (menu) => {
        if (!menu.classList.contains('account-menu')) return;
        const summary = menu.querySelector('summary').getBoundingClientRect();
        const popup = menu.querySelector('.operations-menu-items');
        const top = Math.max(8, Math.min(summary.bottom + 8, window.innerHeight - 80));
        popup.style.setProperty('--menu-top', `${top}px`);
        const width = popup.getBoundingClientRect().width;
        popup.style.setProperty(
            '--menu-left',
            `${Math.max(12, Math.min(summary.left, window.innerWidth - width - 12))}px`,
        );
    };
    for (const menu of menus) {
        menu.addEventListener('toggle', () => {
            if (menu.open) {
                closeMenus(menu);
                positionAccount(menu);
            }
        });
    }
    const progress = document.createElement('div');
    progress.className = 'page-navigation-progress';
    progress.hidden = true;
    progress.setAttribute('role', 'status');
    progress.setAttribute('aria-label', document.querySelector('meta[name="app-loading"]')?.content || '');
    document.body.append(progress);
    document.addEventListener('click', (event) => {
        for (const menu of menus) if (!menu.contains(event.target)) menu.open = false;
        const link = event.target.closest('a[href]');
        if (
            !link ||
            event.defaultPrevented ||
            event.button !== 0 ||
            event.ctrlKey ||
            event.metaKey ||
            event.shiftKey ||
            event.altKey ||
            link.hasAttribute('download') ||
            (link.target && link.target !== '_self')
        )
            return;
        const url = new URL(link.href, window.location.href);
        if (
            url.origin !== window.location.origin ||
            (url.pathname === window.location.pathname && url.search === window.location.search && url.hash)
        )
            return;
        if (header.contains(link)) closeMenus();
        progress.hidden = false;
    });
    document.addEventListener('keydown', (event) => {
        if (event.key !== 'Escape' || event.defaultPrevented || document.querySelector('dialog[open]')) return;
        const opened = menus.find((menu) => menu.open);
        if (opened) {
            opened.open = false;
            opened.querySelector('summary').focus();
            event.preventDefault();
        }
    });
    document.addEventListener('focusin', (event) => {
        for (const menu of menus) if (!menu.contains(event.target)) menu.open = false;
    });
    window.addEventListener('pageshow', (event) => {
        progress.hidden = true;
        // Trang mới đã đóng sẵn trong HTML; không đóng lại menu người dùng vừa mở lúc tài nguyên còn tải.
        if (event.persisted) closeMenus();
    });
    window.addEventListener('resize', () => {
        for (const menu of menus) if (menu.open) positionAccount(menu);
    });
})();
