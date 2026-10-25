/* Hướng dẫn mở riêng, dùng modal chung để giữ bàn phím và trả focus về đúng nút. */
(() => {
    'use strict';
    document.querySelectorAll('[data-guide-open]').forEach((button) => {
        const guide = window.VietRouteUI.modal(button.dataset.guideOpen);
        button.addEventListener('click', () => guide.open(button));
    });
})();
