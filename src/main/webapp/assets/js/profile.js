/* Hồ sơ tự phục vụ chỉ cập nhật tên hiển thị với kiểm tra version. */
(() => {
    'use strict';
    const form = document.getElementById('profile-form');
    if (!form) return;
    const app = window.VietRoute;
    const dialog = document.getElementById('profile-dialog');
    const controls = dialog.querySelector('fieldset');
    let user,
        busy = false;
    const modal = window.VietRouteUI.modal('profile-dialog', {
        busy: () => busy,
        onClose: () => {
            form.reset();
            app.feedback('', false, 'profile-feedback');
        },
    });
    const perform = async (action) => {
        if (busy) return;
        busy = true;
        controls.disabled = true;
        dialog.setAttribute('aria-busy', 'true');
        try {
            await action();
        } catch (error) {
            app.failure(error, dialog.open ? 'profile-feedback' : 'form-feedback');
        } finally {
            busy = false;
            controls.disabled = false;
            dialog.setAttribute('aria-busy', 'false');
        }
    };
    document.getElementById('profile-open').addEventListener('click', () =>
        perform(async () => {
            user = (await app.api.get('/api/v1/auth/me')).data.data;
            form.elements.displayName.value = user.displayName;
            app.feedback('', false, 'profile-feedback');
            modal.open();
        }),
    );
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !window.VietRouteForms.validate(form)) return;
        const name = form.elements.displayName.value.trim();
        void perform(async () => {
            const result = await app.mutate(
                '/api/v1/auth/profile',
                { displayName: name, version: user.version },
                'put',
            );
            document.dispatchEvent(new CustomEvent('vietroute-profile-updated', { detail: result.data.data }));
            dialog.close();
            app.feedback(result.data.message, true);
        });
    });
})();
