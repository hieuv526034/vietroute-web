/* Dải menu cuộn ngang có sẵn từ HTML/CSS; JS chỉ hỗ trợ cuộn, popup, focus và trạng thái chuyển trang. */
(() => {
    'use strict';
    const header = document.querySelector('.site-nav');
    const strip = document.getElementById('navigation-links');
    if (!header || !strip) return;
    const previous = header.querySelector('[data-nav-scroll="previous"]');
    const next = header.querySelector('[data-nav-scroll="next"]');
    const menus = Array.from(header.querySelectorAll('.operations-menu'));
    const closeMenus = (except, restoreFocus = false) => {
        for (const menu of menus) {
            if (menu === except) continue;
            const focused = restoreFocus && menu.open && menu.contains(document.activeElement);
            menu.open = false;
            if (focused) menu.querySelector('summary').focus();
        }
    };
    const syncScroll = () => {
        const overflow = strip.scrollWidth > strip.clientWidth + 1;
        previous.hidden = next.hidden = !overflow;
        previous.disabled = strip.scrollLeft <= 1;
        next.disabled = strip.scrollLeft + strip.clientWidth >= strip.scrollWidth - 1;
    };
    const move = (direction) => {
        closeMenus(null, true);
        strip.scrollBy({ left: direction * Math.max(160, strip.clientWidth * 0.75), behavior: 'auto' });
    };
    previous.addEventListener('click', () => move(-1));
    next.addEventListener('click', () => move(1));
    strip.addEventListener('scroll', () => {
        closeMenus(null, true);
        syncScroll();
    });
    strip.addEventListener('keydown', (event) => {
        if (event.target !== strip || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
        event.preventDefault();
        if (event.key === 'Home' || event.key === 'End')
            strip.scrollTo({ left: event.key === 'Home' ? 0 : strip.scrollWidth });
        else move(event.key === 'ArrowLeft' ? -1 : 1);
    });
    // Popup fixed thoát khỏi vùng overflow của dải cuộn; luôn neo trong viewport, không đè logo khi đóng.
    const position = (menu) => {
        const summary = menu.querySelector('summary').getBoundingClientRect();
        const popup = menu.querySelector('.operations-menu-items');
        const top = Math.min(summary.bottom + 8, window.innerHeight - 80);
        popup.style.setProperty('--menu-top', `${Math.max(8, top)}px`);
        const width = popup.getBoundingClientRect().width;
        popup.style.setProperty(
            '--menu-left',
            `${Math.max(12, Math.min(summary.left, window.innerWidth - width - 12))}px`,
        );
    };
    for (const menu of menus)
        menu.addEventListener('toggle', () => {
            if (menu.open) {
                closeMenus(menu);
                position(menu);
            }
        });
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
        if (!header.contains(event.target)) closeMenus();
    });
    window.addEventListener('pageshow', () => {
        progress.hidden = true;
        closeMenus();
        syncScroll();
    });
    window.addEventListener('resize', () => {
        closeMenus(null, true);
        syncScroll();
    });
    new ResizeObserver(syncScroll).observe(strip);
    document.fonts?.ready.then(syncScroll);
    syncScroll();
})();
