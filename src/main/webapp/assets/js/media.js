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
    const enabled = root.dataset.enabled === 'true';
    const tables = window.VietRouteTable;
    const pager = tables.pager('media', {
        busy: () => uploading || loading,
        onChange: (query) => load(query).catch(failure),
    });
    let loading = false;
    let permissions = [];
    let uploading = false;
    const open = document.getElementById('media-open');
    const uploadModal = window.VietRouteUI.modal('media-dialog', {
        busy: () => uploading,
        onClose: () => {
            form.reset();
            feedback('', false, 'media-upload-feedback');
        },
    });
    open.addEventListener('click', () => {
        if (!uploading && enabled && permissions.includes('MEDIA_CREATE')) uploadModal.open();
    });

    const loadPage = async (query) => {
        const result = await tables.readPage(
            async (params) => (await api.get('/api/v1/media', { params })).data.data,
            query,
        );
        gallery.replaceChildren();
        pager.update(result);
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
                if (enabled && permissions.includes('MEDIA_DELETE')) {
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

    // Không cho refresh/phân trang chạy đua và thay kết quả của một request mới hơn.
    const load = async (query = pager.query) => {
        if (loading) return;
        loading = true;
        root.setAttribute('aria-busy', 'true');
        document.getElementById('media-refresh').disabled = true;
        try {
            await loadPage(query);
        } finally {
            loading = false;
            root.setAttribute('aria-busy', 'false');
            document.getElementById('media-refresh').disabled = false;
        }
    };

    // File đi qua backend để kiểm pixel/chuẩn hóa; trình duyệt không biết Cloudinary API secret.
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (uploading || !enabled || !permissions.includes('MEDIA_CREATE') || !window.VietRouteForms.validate(form)) {
            return;
        }
        upload.disabled = true;
        const body = new FormData(form);
        uploading = true;
        document.getElementById('media-dialog-controls').disabled = true;
        feedback('', false, 'media-upload-feedback');
        try {
            const response = await mutate('/api/v1/media', body);
            feedback(response.data.message, true);
            form.reset();
            document.getElementById('media-dialog').close();
            await load({ ...pager.query, page: 0 });
        } catch (error) {
            failure(error, document.getElementById('media-dialog').open ? 'media-upload-feedback' : 'form-feedback');
        } finally {
            upload.disabled = !enabled || !permissions.includes('MEDIA_CREATE');
            uploading = false;
            document.getElementById('media-dialog-controls').disabled = false;
        }
    });
    document.getElementById('media-refresh').addEventListener('click', () => load().catch(failure));
    api.get('/api/v1/auth/me')
        .then(async (response) => {
            permissions = response.data.data.permissions;
            upload.disabled = !enabled || !permissions.includes('MEDIA_CREATE');
            open.disabled = upload.disabled;
            await load();
        })
        .catch(failure);
})();
