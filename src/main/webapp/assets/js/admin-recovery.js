/* Cứu hộ quyền chỉ trên account bootstrap; modal không giữ password/storage hoặc tự gửi lại khi lỗi. */
(() => {
    'use strict';
    const form = document.getElementById('admin-recovery-form');
    if (!form) return;
    const app = window.VietRoute;
    const dialog = document.getElementById('admin-recovery-dialog');
    const controls = document.getElementById('admin-recovery-controls');
    const open = document.getElementById('admin-recovery-open');
    const login = document.getElementById('admin-recovery-login');
    let busy = false;
    const modal = window.VietRouteUI.modal('admin-recovery-dialog', {
        busy: () => busy,
        onClose: () => form.reset(),
    });
    open.addEventListener('click', () => {
        app.feedback('', false, 'admin-recovery-feedback');
        window.VietRouteForms.clear(form);
        modal.open();
    });
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (busy || !window.VietRouteForms.validate(form)) return;
        const body = Object.fromEntries(new FormData(form));
        const submit = form.querySelector('[type="submit"]');
        const original = submit.textContent;
        busy = true;
        controls.disabled = true;
        dialog.setAttribute('aria-busy', 'true');
        submit.setAttribute('aria-busy', 'true');
        try {
            await app.mutate(form.dataset.endpoint, body);
            form.reset();
            form.hidden = true;
            open.disabled = true;
            login.hidden = false;
            app.feedback(form.dataset.success, true, 'admin-recovery-feedback');
        } catch (error) {
            app.failure(error, 'admin-recovery-feedback');
        } finally {
            body.currentPassword = '';
            busy = false;
            controls.disabled = false;
            dialog.setAttribute('aria-busy', 'false');
            submit.textContent = original;
            submit.removeAttribute('aria-busy');
            if (!login.hidden) login.focus();
        }
    });
})();
