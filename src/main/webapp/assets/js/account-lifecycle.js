/* Đăng ký/xác minh/reset gửi JSON có CSRF; không tự retry, không lưu code/password vào URL/storage. */
(() => {
    'use strict';
    const root = document.getElementById('account-flow-workspace');
    if (!root) return;
    const app = window.VietRoute;
    const forms = Array.from(root.querySelectorAll('form[data-account-action]'));
    const paths = Object.freeze({
        register: '/register',
        activate: '/activate',
        forgot: '/password/forgot',
        reset: '/password/reset',
    });
    let busy = false,
        enabled = false;
    const status = document.getElementById('account-flow-status');
    const run = async (form, path, body) => {
        if (busy || !enabled) return;
        busy = true;
        root.setAttribute('aria-busy', 'true');
        forms.forEach((item) => (item.querySelector('fieldset').disabled = true));
        const feedbackId = form.dataset.accountAction + '-feedback';
        app.feedback('', false, feedbackId);
        try {
            const response = await app.mutate('/api/v1/auth' + path, body);
            form.reset();
            app.feedback(response.data.message, true, feedbackId);
        } catch (error) {
            // Lỗi giữ form trên tab hiện tại; người dùng tự sửa, không tự gửi lại mutation.
            app.failure(error, feedbackId);
        } finally {
            busy = false;
            root.setAttribute('aria-busy', 'false');
            forms.forEach((item) => (item.querySelector('fieldset').disabled = !enabled));
        }
    };
    for (const form of forms)
        form.addEventListener('submit', (event) => {
            event.preventDefault();
            if (!window.VietRouteForms.validate(form)) return;
            run(form, paths[form.dataset.accountAction], Object.fromEntries(new FormData(form)));
        });
    document.getElementById('account-resend').addEventListener('click', () => {
        const form = document.getElementById('account-activate-form');
        const email = form.elements.email;
        if (!email.reportValidity()) return;
        run(form, '/activation/request', { email: email.value });
    });
    app.api
        .get('/api/v1/auth/options')
        .then((response) => {
            enabled = response.data.data.emailFlowsEnabled;
            status.textContent = enabled ? status.dataset.ready : status.dataset.disabled;
            forms.forEach((form) => (form.querySelector('fieldset').disabled = !enabled));
        })
        .catch((error) => {
            app.failure(error, 'account-flow-status');
        });
})();
