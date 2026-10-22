/* Thư viện ảnh công khai: mọi thao tác ghi lấy CSRF mới, quyền/owner luôn được backend kiểm lại. */
(() => {
    'use strict';
    const root = document.getElementById('media-library');
    if (!root) {
        return;
    }
    const { api, mutate, failure, feedback } = window.VietRoute;
    const gallery = document.getElementById('media-gallery');
    const form = document.getElementById('media-upload-form');
    const upload = document.getElementById('media-upload');
    const previous = document.getElementById('media-prev');
    const next = document.getElementById('media-next');
    let page = 0;
    let permissions = [];

    const load = async () => {
        const result = (await api.get('/api/v1/media', { params: { page, size: 12 } })).data.data;
        gallery.replaceChildren();
        previous.disabled = !result.hasPrevious;
        next.disabled = !result.hasNext;
        document.getElementById('media-page').textContent =
            `${result.totalElements} · ${result.page + 1}/${Math.max(1, result.totalPages)}`;
        if (!result.content.length) {
            gallery.textContent = root.dataset.empty;
        }
        for (const image of result.content) {
            const card = document.createElement('article');
            card.className = 'panel media-card';
            const status = document.createElement('p');
            status.textContent = `#${image.id} · ${root.dataset[image.status.toLowerCase()] || image.status}`;
            card.append(status);
            if (image.status === 'READY') {
                const preview = document.createElement('img');
                preview.src = image.thumbnailUrl;
                preview.alt = `#${image.id}`;
                preview.loading = 'lazy';
                preview.width = 640;
                preview.height = 480;
                card.append(preview);
                const view = document.createElement('a');
                view.href = image.url;
                view.target = '_blank';
                view.rel = 'noopener noreferrer';
                view.className = 'button button-outline button-small';
                view.textContent = root.dataset.view;
                card.append(view);
                if (permissions.includes('MEDIA_DELETE')) {
                    const remove = document.createElement('button');
                    remove.type = 'button';
                    remove.className = 'button button-outline button-small';
                    remove.textContent = root.dataset.delete;
                    remove.addEventListener('click', async () => {
                        if (!window.confirm(root.dataset.confirm)) {
                            return;
                        }
                        remove.disabled = true;
                        try {
                            const response = await mutate(
                                `/api/v1/media/${image.id}`,
                                { version: image.version },
                                'delete',
                            );
                            feedback(response.data.message, true);
                            await load();
                        } catch (error) {
                            failure(error);
                        } finally {
                            remove.disabled = false;
                        }
                    });
                    card.append(remove);
                }
            }
            gallery.append(card);
        }
    };

    // File đi qua backend để kiểm pixel/chuẩn hóa; trình duyệt không biết Cloudinary API secret.
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (!form.reportValidity()) {
            return;
        }
        upload.disabled = true;
        feedback('');
        try {
            const response = await mutate('/api/v1/media', new FormData(form));
            feedback(response.data.message, true);
            form.reset();
            page = 0;
            await load();
        } catch (error) {
            failure(error);
        } finally {
            upload.disabled = !permissions.includes('MEDIA_CREATE');
        }
    });
    document.getElementById('media-refresh').addEventListener('click', () => load().catch(failure));
    previous.addEventListener('click', () => {
        page -= 1;
        load().catch(failure);
    });
    next.addEventListener('click', () => {
        page += 1;
        load().catch(failure);
    });
    api.get('/api/v1/auth/me')
        .then(async (response) => {
            permissions = response.data.data.permissions;
            upload.disabled = !permissions.includes('MEDIA_CREATE');
            await load();
        })
        .catch(failure);
})();
