/* Bảng dùng Bootstrap sẵn có: chỉ tải trang hiện hành, không kéo toàn bộ dữ liệu xuống trình duyệt. */
((factory) => {
    const tables = factory();
    if (typeof module === 'object' && module.exports) module.exports = tables;
    else window.VietRouteTable = tables;
})(() => {
    'use strict';

    // Cửa sổ tối đa 5 số trang, kể cả khi DB có hàng triệu trang; không tạo một option cho mỗi trang.
    const pageWindow = (page, total) => {
        const start = Math.max(0, Math.min(page - 2, total - 5));
        return Array.from({ length: Math.min(5, Math.max(0, total)) }, (_, index) => start + index);
    };
    const boundedPage = (page, total) => Math.max(0, Math.min(page, Math.max(0, total - 1), 1000000));
    const readPage = async (fetcher, query) => {
        const result = await fetcher(query);
        // Sau xóa/lọc, trang cuối có thể biến mất; chỉ đọc lại một lần với số trang đã được DB xác nhận.
        return result.page > 0 && result.page >= Math.max(1, result.totalPages)
            ? fetcher({ ...query, page: boundedPage(result.page, result.totalPages) })
            : result;
    };
    const pager = (prefix, { onChange, busy = () => false } = {}) => {
        const root = document.getElementById(`${prefix}-pagination`);
        const find = (name) => root.querySelector(`[data-page-${name}]`);
        const size = find('size');
        const jump = find('jump');
        let current = { page: 0, size: Number(size.value), totalPages: 0, totalElements: 0 };
        let pending = false;
        const render = () => {
            const locked = pending;
            size.disabled = locked;
            size.value = String(current.size);
            jump.disabled = locked || !current.totalPages;
            jump.max = String(Math.min(current.totalPages || 1, 1000001));
            jump.value = current.totalPages ? String(current.page + 1) : '';
            jump.placeholder = '—';
            jump.setCustomValidity('');
            find('total').textContent = `/ ${current.totalPages}`;
            const start = current.totalElements ? current.page * current.size + 1 : 0;
            const end = Math.min((current.page + 1) * current.size, current.totalElements);
            find('summary').textContent = root.dataset.summary
                .replace('{start}', start)
                .replace('{end}', end)
                .replace('{total}', current.totalElements);
            for (const [name, unavailable] of [
                ['first', current.page === 0],
                ['previous', current.page === 0],
                ['next', current.page + 1 >= current.totalPages],
                ['last', current.page + 1 >= current.totalPages],
            ]) {
                find(name).disabled = locked || unavailable;
                find(name).parentElement.classList.toggle('disabled', locked || unavailable);
            }
            root.querySelectorAll('[data-page-number]').forEach((node) => node.remove());
            const anchor = find('next-item');
            for (const index of pageWindow(current.page, current.totalPages)) {
                const item = document.createElement('li');
                item.dataset.pageNumber = '';
                item.className = `page-item${index === current.page ? ' active' : ''}`;
                const button = document.createElement('button');
                button.type = 'button';
                button.className = 'page-link';
                button.textContent = String(index + 1);
                button.setAttribute('aria-label', root.dataset.pageLabel.replace('{page}', index + 1));
                if (index === current.page) button.setAttribute('aria-current', 'page');
                button.disabled = locked;
                button.addEventListener('click', () => void request(index));
                item.append(button);
                anchor.before(item);
            }
        };
        const request = async (page, nextSize = current.size) => {
            if (pending || busy()) {
                render();
                return;
            }
            const candidate = { page: boundedPage(page, current.totalPages), size: nextSize };
            if (candidate.page === current.page && candidate.size === current.size) {
                render();
                return;
            }
            pending = true;
            const focused = document.activeElement;
            const restoreFocus = root.contains(focused);
            render();
            try {
                // Caller xử lý HTTP/feedback. Chỉ update khi load thành công nên lỗi không làm nhảy số trang giả.
                await onChange(candidate);
            } finally {
                pending = false;
                render();
                // Render số trang thay node cũ; trả focus để người dùng bàn phím có thể đi tiếp.
                if (
                    restoreFocus &&
                    root.isConnected &&
                    (document.activeElement === document.body || document.activeElement === focused)
                ) {
                    const target =
                        focused === size
                            ? size
                            : focused === jump
                              ? jump
                              : focused.isConnected && !focused.disabled
                                ? focused
                                : root.querySelector('[aria-current="page"]');
                    target?.focus({ preventScroll: true });
                }
            }
        };
        find('first').addEventListener('click', () => void request(0));
        find('previous').addEventListener('click', () => void request(current.page - 1));
        find('next').addEventListener('click', () => void request(current.page + 1));
        find('last').addEventListener('click', () => void request(current.totalPages - 1));
        size.addEventListener('change', () => void request(0, Number(size.value)));
        jump.addEventListener('change', () => {
            if (!jump.checkValidity() || !Number.isSafeInteger(Number(jump.value))) {
                render();
                return;
            }
            void request(Number(jump.value) - 1);
        });
        jump.addEventListener('keydown', (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
                jump.dispatchEvent(new Event('change'));
            }
        });
        render();
        return Object.freeze({
            get query() {
                return { page: current.page, size: current.size };
            },
            update(result) {
                current = {
                    page: result.page,
                    size: result.size,
                    totalPages: result.totalPages,
                    totalElements: result.totalElements,
                };
                render();
            },
        });
    };
    const icons = Object.freeze({
        edit: ['M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7', 'm16 3 5 5-10 10-5 1 1-5Z'],
        delete: ['M3 6h18', 'M8 6V4h8v2', 'm5 6 1 15h12l1-15', 'M10 10v7', 'M14 10v7'],
        view: ['M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z', 'M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6'],
        assign: ['M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8', 'M4 21v-2a6 6 0 0 1 10-4', 'M18 14v8', 'M14 18h8'],
    });
    const actionButton = (label, icon, action, danger = false) => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = `button button-outline icon-button${danger ? ' button-danger' : ''}`;
        button.title = label;
        button.setAttribute('aria-label', label);
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('viewBox', '0 0 24 24');
        svg.setAttribute('aria-hidden', 'true');
        svg.setAttribute('fill', 'none');
        svg.setAttribute('stroke', 'currentColor');
        svg.setAttribute('stroke-width', '1.7');
        for (const value of icons[icon]) {
            const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
            path.setAttribute('d', value);
            svg.append(path);
        }
        button.append(svg);
        button.addEventListener('click', () => action(button));
        return button;
    };
    return Object.freeze({ pageWindow, boundedPage, readPage, pager, actionButton });
});
