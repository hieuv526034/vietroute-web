/* Gắn email cần JWT + mật khẩu hiện tại; dialog giữ bản nháp khi lỗi và không đóng lúc mutation đang gửi. */
(() => {
    'use strict';
    const form = document.getElementById('email-form');
    if (!form) return;
    const app = window.VietRoute;
    let busy = false;
    const modal = window.VietRouteUI.modal('email-dialog', { busy: () => busy, onClose: () => form.reset() });
    document.getElementById('email-open').addEventListener('click', () => {
        app.feedback('', false, 'email-feedback');
        window.VietRouteForms.clear(form);
        modal.open();
    });
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (busy || !window.VietRouteForms.validate(form)) return;
        const body = Object.fromEntries(new FormData(form));
        busy = true;
        document.getElementById('email-controls').disabled = true;
        try {
            const result = await app.mutate('/api/v1/auth/email', body);
            form.reset();
            app.feedback(result.data.message, true, 'email-feedback');
        } catch (error) {
            app.failure(error, 'email-feedback');
        } finally {
            busy = false;
            document.getElementById('email-controls').disabled = false;
        }
    });
})();
