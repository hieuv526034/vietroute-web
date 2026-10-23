/* Quản trị danh mục dùng API sẵn có: quyền ở server, CSRF mới cho mỗi lần ghi, version chống ghi đè. */
(() => {
    'use strict';
    const root = document.getElementById('catalog-workspace');
    if (!root) {
        return;
    }
    const { api, mutate, feedback, failure } = window.VietRoute;
    const controls = document.getElementById('catalog-controls');
    const form = document.getElementById('catalog-form');
    const filter = document.getElementById('catalog-filter');
    const rows = document.getElementById('catalog-body');
    const status = document.getElementById('catalog-status');
    const conflict = document.getElementById('catalog-conflict');
    const save = document.getElementById('catalog-save');
    const cancel = document.getElementById('catalog-cancel');
    const previous = document.getElementById('catalog-previous');
    const next = document.getElementById('catalog-next');
    const type = document.getElementById('catalog-type');
    const code = document.getElementById('catalog-code');
    let page = 0;
    let selected = null;
    let permissions = [];
    let busy = false;
    const can = (action) =>
        permissions.includes(`CATALOG_${action}`) ||
        (['CREATE', 'UPDATE'].includes(action) && permissions.includes('CATALOG_WRITE'));

    // Giữ nguyên trường đang sửa khi lỗi; không tự gửi lại mutation sau timeout hoặc conflict.
    const perform = async (action, target = 'form-feedback') => {
        if (busy) {
            return;
        }
        busy = true;
        controls.disabled = true;
        root.setAttribute('aria-busy', 'true');
        status.textContent = root.dataset.loading;
        feedback('', false, target);
        conflict.hidden = true;
        try {
            await action();
        } catch (error) {
            failure(error, target);
            conflict.hidden = error.response?.status !== 409;
        } finally {
            busy = false;
            controls.disabled = false;
            root.setAttribute('aria-busy', 'false');
            status.textContent = '';
        }
    };

    const resetEditor = () => {
        selected = null;
        form.reset();
        type.value = filter.value;
        type.disabled = false;
        code.disabled = false;
        save.disabled = !can('CREATE');
        save.textContent = root.dataset.create;
        cancel.hidden = true;
        document.getElementById('catalog-editor-title').textContent = root.dataset.create;
        const note = document.getElementById('catalog-editor-note');
        note.textContent = note.dataset.create;
    };

    const button = (label, danger, action) => {
        const node = document.createElement('button');
        node.type = 'button';
        node.className = `button button-small ${danger ? 'button-danger' : 'button-outline'}`;
        node.textContent = label;
        node.addEventListener('click', () => perform(action));
        return node;
    };

    // Đọc bản mới nhất khi mở form; version được giữ ở bộ nhớ và gửi lại đúng một lần khi lưu.
    const edit = async (item) => {
        const current = (await api.get(`/api/v1/admin/catalogs/${item.id}`, { timeout: 10000 })).data.data;
        selected = current;
        type.value = current.type;
        code.value = current.code;
        type.disabled = true;
        code.disabled = true;
        form.elements.nameVi.value = current.nameVi;
        form.elements.nameEn.value = current.nameEn;
        form.elements.sortOrder.value = current.sortOrder;
        save.disabled = !current.active || !can('UPDATE');
        save.textContent = root.dataset.save;
        cancel.hidden = false;
        document.getElementById('catalog-editor-title').textContent = root.dataset.edit;
        const note = document.getElementById('catalog-editor-note');
        note.textContent = note.dataset.edit;
        document.getElementById('catalog-editor').scrollIntoView({ block: 'start' });
        // Focus được đặt sau khi fieldset mở lại, tránh focus vào control đang disabled.
        requestAnimationFrame(() => form.elements.nameVi.focus());
    };

    const remove = async (item) => {
        if (!window.confirm(`${root.dataset.confirmDelete}\n${item.code} · ${item.name}`)) {
            return;
        }
        const response = await mutate(`/api/v1/admin/catalogs/${item.id}`, { version: item.version }, 'delete');
        if (selected?.id === item.id) {
            resetEditor();
        }
        feedback(response.data.message, true);
        await load();
    };

    const load = async () => {
        let result = (await api.get(`/api/v1/catalogs/${filter.value}`, { params: { page, size: 10 }, timeout: 10000 }))
            .data.data;
        if (page > 0 && !result.content.length) {
            page = Math.max(0, result.totalPages - 1);
            result = (await api.get(`/api/v1/catalogs/${filter.value}`, { params: { page, size: 10 }, timeout: 10000 }))
                .data.data;
        }
        rows.replaceChildren();
        for (const item of result.content) {
            const row = document.createElement('tr');
            for (const value of [item.code, item.name, item.sortOrder]) {
                const cell = document.createElement('td');
                cell.textContent = String(value);
                row.append(cell);
            }
            const actions = document.createElement('td');
            const group = document.createElement('div');
            group.className = 'catalog-actions';
            if (can('READ') && can('UPDATE')) {
                group.append(button(root.dataset.edit, false, () => edit(item)));
            }
            if (can('DELETE')) {
                group.append(button(root.dataset.delete, true, () => remove(item)));
            }
            if (!group.childElementCount) {
                group.textContent = root.dataset.readonly;
            }
            actions.append(group);
            row.append(actions);
            rows.append(row);
        }
        if (!result.content.length) {
            const cell = document.createElement('td');
            cell.colSpan = 4;
            cell.textContent = root.dataset.empty;
            const row = document.createElement('tr');
            row.append(cell);
            rows.append(row);
        }
        previous.disabled = !result.hasPrevious;
        next.disabled = !result.hasNext;
        document.getElementById('catalog-page').textContent =
            `${root.dataset.page} ${result.page + 1}/${Math.max(1, result.totalPages)} · ${result.totalElements}`;
    };

    form.addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || save.disabled || !form.reportValidity()) {
            return;
        }
        void perform(async () => {
            const body = {
                nameVi: form.elements.nameVi.value.trim(),
                nameEn: form.elements.nameEn.value.trim(),
                sortOrder: Number(form.elements.sortOrder.value),
            };
            const response = selected
                ? await mutate(`/api/v1/admin/catalogs/${selected.id}`, { ...body, version: selected.version }, 'put')
                : await mutate('/api/v1/admin/catalogs', {
                      ...body,
                      type: type.value,
                      ...(code.value.trim() ? { code: code.value.trim() } : {}),
                  });
            filter.value = selected?.type || type.value;
            page = 0;
            resetEditor();
            feedback(response.data.message, true);
            await load();
        });
    });
    cancel.addEventListener('click', () => {
        resetEditor();
        feedback('');
        conflict.hidden = true;
    });
    filter.addEventListener('change', () =>
        perform(async () => {
            page = 0;
            resetEditor();
            await load();
        }, 'catalog-feedback'),
    );
    previous.addEventListener('click', () =>
        perform(async () => {
            page -= 1;
            await load();
        }, 'catalog-feedback'),
    );
    next.addEventListener('click', () =>
        perform(async () => {
            page += 1;
            await load();
        }, 'catalog-feedback'),
    );
    document.getElementById('catalog-refresh').addEventListener('click', () =>
        perform(async () => {
            permissions = (await api.get('/api/v1/auth/me', { timeout: 10000 })).data.data.permissions;
            resetEditor();
            await load();
        }, 'catalog-feedback'),
    );
    void perform(async () => {
        permissions = (await api.get('/api/v1/auth/me', { timeout: 10000 })).data.data.permissions;
        resetEditor();
        await load();
    }, 'catalog-feedback');
})();
