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
    const type = document.getElementById('catalog-type');
    const code = document.getElementById('catalog-code');
    const tables = window.VietRouteTable;
    const pager = tables.pager('catalog', {
        busy: () => busy,
        onChange: (query) => perform(() => load(query), 'catalog-feedback'),
    });
    let selected = null;
    let permissions = [];
    let busy = false;
    const editor = window.VietRouteUI.modal('catalog-dialog', {
        busy: () => busy,
        onClose: () => {
            resetEditor();
            feedback('');
            conflict.hidden = true;
        },
    });
    const can = (action) => permissions.includes(action);

    // Giữ nguyên trường đang sửa khi lỗi; không tự gửi lại mutation sau timeout hoặc conflict.
    const perform = async (action, target = editor.isOpen ? 'form-feedback' : 'catalog-feedback') => {
        if (busy) {
            return;
        }
        busy = true;
        controls.disabled = true;
        document.getElementById('catalog-dialog-controls').disabled = true;
        root.setAttribute('aria-busy', 'true');
        status.textContent = root.dataset.loading;
        feedback('', false, target);
        conflict.hidden = true;
        try {
            await action();
        } catch (error) {
            failure(error, editor.isOpen ? 'form-feedback' : 'catalog-feedback');
            conflict.hidden = error.response?.status !== 409;
        } finally {
            busy = false;
            controls.disabled = false;
            document.getElementById('catalog-dialog-controls').disabled = false;
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
        document.getElementById('catalog-editor-title').textContent = root.dataset.create;
        const note = document.getElementById('catalog-editor-note');
        note.textContent = note.dataset.create;
    };

    const button = (label, icon, danger, action) =>
        tables.actionButton(label, icon, (trigger) => perform(() => action(trigger)), danger);

    // Đọc bản mới nhất khi mở form; version được giữ ở bộ nhớ và gửi lại đúng một lần khi lưu.
    const edit = async (item, trigger) => {
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
        document.getElementById('catalog-editor-title').textContent = root.dataset.edit;
        const note = document.getElementById('catalog-editor-note');
        note.textContent = note.dataset.edit;
        editor.open(trigger);
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
        feedback(response.data.message, true, 'catalog-feedback');
        await load();
    };

    const load = async (query = pager.query) => {
        const result = await tables.readPage(
            async (params) => (await api.get(`/api/v1/catalogs/${filter.value}`, { params, timeout: 10000 })).data.data,
            query,
        );
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
            group.className = 'row-actions';
            actions.className = 'table-actions';
            if (can('READ') && can('UPDATE')) {
                group.append(
                    button(`${root.dataset.edit}: ${item.code}`, 'edit', false, (trigger) => edit(item, trigger)),
                );
            }
            if (can('DELETE')) {
                group.append(button(`${root.dataset.delete}: ${item.code}`, 'delete', true, () => remove(item)));
            }
            if (!group.childElementCount) {
                group.textContent = '—';
                group.title = root.dataset.readonly;
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
        pager.update(result);
    };

    form.addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || save.disabled || !window.VietRouteForms.validate(form)) {
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
            resetEditor();
            // Thành công mới đóng modal; lỗi/409 vẫn giữ bản nháp và phản hồi ngay trong modal.
            document.getElementById('catalog-dialog').close();
            feedback(response.data.message, true, 'catalog-feedback');
            await load({ ...pager.query, page: 0 });
        });
    });
    document.getElementById('catalog-create').addEventListener('click', () => {
        if (!busy && can('CREATE')) {
            resetEditor();
            editor.open();
        }
    });
    filter.addEventListener('change', () =>
        perform(async () => {
            resetEditor();
            await load({ ...pager.query, page: 0 });
        }, 'catalog-feedback'),
    );
    document.getElementById('catalog-refresh').addEventListener('click', () =>
        perform(async () => {
            permissions = (await window.VietRouteAccess.load('CATALOG')).actions;
            document.getElementById('catalog-create').disabled = !can('CREATE');
            document.getElementById('catalog-create').hidden = !can('CREATE');
            resetEditor();
            await load();
        }, 'catalog-feedback'),
    );
    void perform(async () => {
        permissions = (await window.VietRouteAccess.load('CATALOG')).actions;
        document.getElementById('catalog-create').disabled = !can('CREATE');
        document.getElementById('catalog-create').hidden = !can('CREATE');
        resetEditor();
        await load();
    }, 'catalog-feedback');
})();
