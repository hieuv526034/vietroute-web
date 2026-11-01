/* Role CRUD và modal gán chức năng; mọi mutation dùng version, lỗi giữ bản nháp và không tự gửi lại. */
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
        grantReady = false,
        version,
        inventory = [],
        allowed = new Set(),
        query = '';
    const can = (method, path) => allowed.has(method + ' ' + base + path);
    const roleModal = window.VietRouteUI.modal('role-dialog', { busy: () => busy });
    const grantModal = window.VietRouteUI.modal('grants-dialog', {
        busy: () => busy,
        onClose: () => {
            state = undefined;
            grantReady = false;
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
        roleModal.focus();
        grantModal.focus();
        const feedback = node('grants-dialog').open
            ? 'grants-feedback'
            : node('role-dialog').open
              ? 'role-feedback'
              : 'form-feedback';
        app.feedback('', false, feedback);
        try {
            await work();
        } catch (error) {
            const target = node('grants-dialog').open
                ? 'grants-feedback'
                : node('role-dialog').open
                  ? 'role-feedback'
                  : feedback;
            app.failure(error, target);
            if (target === 'grants-feedback' && !state) node('grants-tree').replaceChildren();
            if (error.response?.status === 409) node(target).append(text('p', labels.conflict));
        } finally {
            busy = false;
            root.removeAttribute('aria-busy');
            controls.forEach((item) => {
                item.disabled = false;
            });
            node('grants-save').disabled = !state || !grantReady;
            node('roles-status').textContent = '';
            roleModal.focus();
            grantModal.focus();
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
            if (can('GET', '/roles/{role}/functions') && can('GET', '/functions')) {
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
        node('grants-count').textContent = labels.selected + ': ' + state.size;
        node('grants-changes').textContent =
            labels.added +
            ': ' +
            [...state].filter((code) => !original.has(code)).length +
            ' · ' +
            labels.removed +
            ': ' +
            [...original].filter((code) => !state.has(code)).length;
    };
    const grantPager = table.pager('function-grants', {
        busy: () => busy,
        onChange: (params) => perform(() => loadFunctions(params)),
    });
    const render = () => {
        const root = node('grants-tree');
        root.replaceChildren();
        for (const item of inventory) {
            const label = text('label');
            label.className = 'd-flex gap-2 p-2 border-bottom';
            const input = document.createElement('input');
            input.type = 'checkbox';
            input.value = item.code;
            input.checked = state.has(item.code);
            input.disabled =
                grantRole.builtIn || !can('PUT', '/roles/{role}/functions') || (!item.active && !input.checked);
            input.addEventListener('change', () => {
                if (input.checked) state.add(item.code);
                else state.delete(item.code);
                update();
            });
            const name = document.documentElement.lang === 'en' ? item.nameEn : item.nameVi;
            label.append(input, text('span', name + ' · ' + item.code + (item.active ? '' : ' · ' + labels.off)));
            root.append(label);
        }
        if (!inventory.length) root.append(text('p', labels.empty));
        update();
    };
    const loadFunctions = async (params = grantPager.query) => {
        const page = await table.readPage(
            (values) =>
                get('/functions?' + new URLSearchParams({ ...values, query: node('grants-search').value.trim() })),
            params,
        );
        inventory = page.content;
        grantPager.update(page);
        render();
        grantReady = true;
    };
    const openGrants = async (role, opener) => {
        grantRole = role;
        state = undefined;
        grantReady = false;
        node('grants-tree').replaceChildren(text('p', labels.loading));
        node('grants-count').textContent = '';
        node('grants-changes').textContent = '';
        node('grants-search').value = '';
        node('grants-save').hidden = role.builtIn || !can('PUT', '/roles/{role}/functions');
        node('grants-save').disabled = true;
        app.feedback('', false, 'grants-feedback');
        grantModal.open(opener);
        const result = await get('/roles/' + encodeURIComponent(role.code) + '/functions');
        version = result.version;
        state = new Set(result.functionCodes);
        original = new Set(result.functionCodes);
        node('grants-target').textContent = role.nameVi + ' · ' + role.code + ' · v' + version;
        await loadFunctions({ ...grantPager.query, page: 0 });
    };
    let original = new Set(),
        searchTimer;
    node('grants-search').addEventListener('input', () => {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(() => {
            if (state && !busy) void perform(() => loadFunctions({ ...grantPager.query, page: 0 }));
        }, 250);
    });
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
        if (busy || !state || !grantReady || grantRole.builtIn || !can('PUT', '/roles/{role}/functions')) return;
        const body = { functionCodes: [...state], version };
        void perform(async () => {
            await app.mutate(`${base}/roles/${encodeURIComponent(grantRole.code)}/functions`, body, 'put');
            node('grants-dialog').close();
            app.feedback(labels.success, true);
            await load();
        });
    });
    void perform(async () => {
        const capabilities = (await app.api.get('/api/v1/auth/capabilities')).data.data;
        allowed = new Set(capabilities.flatMap((item) => item.targets));
        node('role-create').hidden = !can('POST', '/registry/{kind}/{code}');
        await load();
    });
})();
