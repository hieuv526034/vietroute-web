/* Hồ sơ tự phục vụ dùng version và MediaService chung; chỉ lưu ID ảnh, không nhận URL/CDN publicId từ form. */
(() => {
    'use strict';
    const form = document.getElementById('profile-form');
    if (!form) return;
    const app = window.VietRoute;
    const dialog = document.getElementById('profile-dialog');
    const controls = dialog.querySelector('fieldset');
    let user,
        avatarId,
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
    const preview = (url) => {
        const img = document.getElementById('profile-preview');
        img.hidden = !url;
        if (url) img.src = url;
        else img.removeAttribute('src');
    };
    document.getElementById('profile-open').addEventListener('click', () =>
        perform(async () => {
            user = (await app.api.get('/api/v1/auth/me')).data.data;
            avatarId = user.avatarMediaId;
            form.elements.displayName.value = user.displayName;
            preview(user.avatarUrl);
            app.feedback('', false, 'profile-feedback');
            modal.open();
        }),
    );
    document.getElementById('profile-remove-avatar').addEventListener('click', () => {
        if (!busy) {
            avatarId = null;
            preview(null);
            form.elements.file.value = '';
        }
    });
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !window.VietRouteForms.validate(form)) return;
        const name = form.elements.displayName.value.trim(),
            file = form.elements.file.files[0];
        void perform(async () => {
            // Upload xong mới gắn ID; nếu profile lỗi version, giữ ID đã upload để người dùng không upload lặp.
            if (file) {
                const data = new FormData();
                data.append('file', file);
                const image = (await app.mutate('/api/v1/auth/avatar', data)).data.data;
                avatarId = image.id;
                preview(image.thumbnailUrl);
                form.elements.file.value = '';
            }
            const result = await app.mutate(
                '/api/v1/auth/profile',
                { displayName: name, avatarMediaId: avatarId ?? null, version: user.version },
                'put',
            );
            document.dispatchEvent(new CustomEvent('vietroute-profile-updated', { detail: result.data.data }));
            dialog.close();
            app.feedback(result.data.message, true);
        });
    });
})();
