/* Role CRUD và modal gán menu/API; mọi mutation dùng version, lỗi giữ bản nháp và không tự gửi lại. */
(() => {
    'use strict';
    const root = document.querySelector('[data-role-workspace]');
    if (!root) return;
    const node = (id) => document.getElementById(id),
        app = window.VietRoute,
        table = window.VietRouteTable;
    const labels = node('grants-labels').dataset,
        base = '/api/v1/admin/access';
    const get = async (path) => (await app.api.get(base + path, { timeout: 10000 })).data.data;
    const text = (tag, value) => {
        const item = document.createElement(tag);
        item.textContent = value ?? '';
        return item;
    };
    let busy = false,
        currentRole,
        grantRole,
        state,
        version,
        features = [],
        inventory = [],
        allowed = new Set(),
        query = '';
    const can = (method, path) => allowed.has(method + ' ' + base + path);
    const roleModal = window.VietRouteUI.modal('role-dialog', { busy: () => busy });
    const grantModal = window.VietRouteUI.modal('grants-dialog', {
        busy: () => busy,
        onClose: () => {
            state = undefined;
        },
    });
    const perform = async (work) => {
        if (busy) return;
        busy = true;
        root.setAttribute('aria-busy', 'true');
        const controls = [node('roles-controls'), node('grants-controls'), node('role-form').querySelector('fieldset')];
        controls.forEach((item) => {
            item.disabled = true;
        });
        const feedback = node('grants-dialog').open
            ? 'grants-feedback'
            : node('role-dialog').open
              ? 'role-feedback'
              : 'form-feedback';
        app.feedback('', false, feedback);
        try {
            await work();
        } catch (error) {
            app.failure(error, feedback);
            if (error.response?.status === 409) node(feedback).append(text('p', labels.conflict));
        } finally {
            busy = false;
            root.removeAttribute('aria-busy');
            controls.forEach((item) => {
                item.disabled = false;
            });
            node('roles-status').textContent = '';
        }
    };
    const pager = table.pager('roles', { busy: () => busy, onChange: (params) => perform(() => load(params)) });
    const load = async (params = pager.query) => {
        if (!can('GET', '/registry/{kind}/page')) {
            node('roles-status').textContent = labels.readonly;
            return;
        }
        const page = await table.readPage(
            async (values) => (await get('/registry/roles/page?' + new URLSearchParams({ ...values, query }))).page,
            params,
        );
        const body = node('roles-body');
        body.replaceChildren();
        for (const role of page.content) {
            const row = text('tr');
            for (const value of [role.code, role.nameVi, role.nameEn, role.active ? labels.on : labels.off])
                row.append(text('td', value));
            const actions = text('td'),
                buttons = text('div');
            actions.className = 'table-actions';
            buttons.className = 'row-actions';
            if (!role.builtIn && can('PUT', '/registry/{kind}/{code}'))
                buttons.append(table.actionButton(labels.edit, 'edit', () => edit(role)));
            if (can('GET', '/roles/{role}/resources') && can('GET', '/resource-catalog') && can('GET', '/tree')) {
                const assign = table.actionButton(
                    labels.assign,
                    'assign',
                    () => void perform(() => openGrants(role, assign)),
                );
                buttons.append(assign);
            }
            if (!role.builtIn && can('DELETE', '/registry/roles/{code}'))
                buttons.append(
                    table.actionButton(
                        labels.delete,
                        'delete',
                        () => {
                            if (!window.confirm(labels.confirm)) return;
                            void perform(async () => {
                                await app.mutate(
                                    `${base}/registry/roles/${encodeURIComponent(role.code)}?version=${role.version}`,
                                    null,
                                    'delete',
                                );
                                await load();
                            });
                        },
                        true,
                    ),
                );
            actions.append(buttons);
            row.append(actions);
            body.append(row);
        }
        if (!page.content.length) {
            const row = text('tr'),
                cell = text('td', labels.empty);
            cell.colSpan = 5;
            row.append(cell);
            body.append(row);
        }
        pager.update(page);
    };
    const edit = (role) => {
        if (busy) return;
        currentRole = role;
        const form = node('role-form');
        form.reset();
        window.VietRouteForms.clear(form);
        form.elements.code.value = role?.code || '';
        form.elements.code.readOnly = !!role;
        form.elements.nameVi.value = role?.nameVi || '';
        form.elements.nameEn.value = role?.nameEn || '';
        form.elements.active.checked = role?.active ?? true;
        app.feedback('', false, 'role-feedback');
        roleModal.open();
    };
    const update = () => {
        const delta = state.changes();
        node('grants-count').textContent = `${labels.selected}: ${state.values().length}`;
        node('grants-changes').textContent =
            `${labels.added}: ${delta.added.length} · ${labels.removed}: ${delta.removed.length}`;
    };
    const render = () =>
        window.VietRouteResourceGrants.render(
            node('grants-tree'),
            state,
            features,
            inventory,
            grantRole.builtIn || !can('PUT', '/roles/{role}/resources') ? [] : [...allowed],
            labels,
            node('grants-search').value,
            update,
        );
    const openGrants = async (role, opener) => {
        const result = await get(`/roles/${encodeURIComponent(role.code)}/resources`);
        grantRole = role;
        version = result.version;
        state = window.VietRouteResourceGrants.model(result.targets, [...allowed]);
        node('grants-target').textContent =
            `${role.nameVi} · ${role.code} · v${version}${role.builtIn ? ' · ' + labels.readonly : ''}`;
        node('grants-search').value = '';
        node('grants-save').hidden = role.builtIn || !can('PUT', '/roles/{role}/resources');
        app.feedback('', false, 'grants-feedback');
        render();
        grantModal.open(opener);
    };
    node('grants-search').addEventListener('input', render);
    node('role-create').addEventListener('click', () => edit());
    node('roles-refresh').addEventListener('click', () => void perform(() => load()));
    node('roles-search').addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy) return;
        query = node('roles-query').value.trim();
        void perform(() => load({ ...pager.query, page: 0 }));
    });
    node('role-form').addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !window.VietRouteForms.validate(event.currentTarget)) return;
        const form = event.currentTarget,
            path = `${base}/registry/roles/${encodeURIComponent(form.elements.code.value)}`;
        void perform(async () => {
            await app.mutate(
                path,
                {
                    nameVi: form.elements.nameVi.value,
                    nameEn: form.elements.nameEn.value,
                    active: form.elements.active.checked,
                    version: currentRole?.version || 0,
                },
                currentRole ? 'put' : 'post',
            );
            node('role-dialog').close();
            app.feedback(labels.success, true);
            await load();
        });
    });
    node('grants-form').addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !state || grantRole.builtIn || !can('PUT', '/roles/{role}/resources')) return;
        const body = { targets: state.values(), version };
        void perform(async () => {
            await app.mutate(`${base}/roles/${encodeURIComponent(grantRole.code)}/resources`, body, 'put');
            node('grants-dialog').close();
            app.feedback(labels.success, true);
            await load();
        });
    });
    void perform(async () => {
        const capabilities = (await app.api.get('/api/v1/auth/capabilities')).data.data;
        allowed = new Set(capabilities.flatMap((item) => item.targets));
        node('role-create').hidden = !can('POST', '/registry/{kind}/{code}');
        if (can('GET', '/resource-catalog'))
            [features, inventory] = await Promise.all([get('/tree'), get('/resource-catalog')]);
        await load();
    });
})();
