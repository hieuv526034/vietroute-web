/* Registry và chính sách có modal/version riêng; lỗi giữ nguyên bản nháp, không replay mutation. */
(() => {
    'use strict';
    const node = (id) => document.getElementById(id);
    if (!node('registry-dialog')) return;
    const app = window.VietRoute,
        labels = node('foundation-labels').dataset,
        base = '/api/v1/admin/access';
    let busy = false,
        selectedRegistry,
        selectedPolicy,
        targets = [],
        me,
        resourceFilters = { kind: '', query: '' };
    const registryForm = node('registry-form'),
        policyForm = node('policy-form');
    const registryModal = window.VietRouteUI.modal('registry-dialog', { busy: () => busy });
    const policyModal = window.VietRouteUI.modal('policy-dialog', { busy: () => busy });
    const text = (tag, value) => {
        const result = document.createElement(tag);
        result.textContent = value ?? '—';
        return result;
    };
    const editButton = (action) => window.VietRouteTable.actionButton(labels.edit, 'edit', action);
    const get = async (path) => (await app.api.get(base + path, { timeout: 8000 })).data.data;
    const canEdit = () => me?.permissions?.includes('ACCESS_UPDATE');
    const canCreate = () => me?.permissions?.includes('ACCESS_CREATE');
    // Danh bạ phân trang tại DB; chỉ đọc metadata server, không render HTML từ chuỗi controller/Operation.
    const resourcePager = window.VietRouteTable.pager('resources', {
        onChange: (query) => read(() => renderResources(query)),
        busy: () => busy,
    });
    const read = async (action) => {
        if (busy) return;
        busy = true;
        const scope = document.querySelector('[data-operations]');
        const controls = [
            'registry-kind',
            'registry-refresh',
            'policies-refresh',
            'resources-refresh',
            'resources-kind',
            'resources-query',
            'resources-search',
        ]
            .map(node)
            .filter(Boolean);
        const disabled = controls.map((control) => control.disabled);
        controls.forEach((control) => {
            control.disabled = true;
        });
        scope.setAttribute('aria-busy', 'true');
        try {
            await action();
        } catch (error) {
            app.failure(error);
        } finally {
            busy = false;
            controls.forEach((control, i) => {
                control.disabled = disabled[i];
            });
            scope.setAttribute('aria-busy', 'false');
        }
    };
    const renderResources = async (query = resourcePager.query) => {
        if (!me?.permissions.includes('ACCESS_READ')) return;
        const page = await window.VietRouteTable.readPage(
            (params) => get('/resources?' + new URLSearchParams({ ...params, ...resourceFilters })),
            query,
        );
        const body = node('resources-body');
        body.replaceChildren();
        node('resources-status').textContent = page.content.length ? '' : labels.empty;
        for (const item of page.content) {
            const row = document.createElement('tr');
            const access = item.publicRoute ? labels.public : item.configurable ? labels.protected : labels.reserved;
            for (const value of [
                item.kind === 'MENU' ? labels.menu : labels.api,
                item.target,
                `${item.group || 'GENERAL'} · ${item.description}${item.declaredPermissions?.length ? '\n' + item.declaredPermissions.join(' | ') : ''}`,
                access,
                item.lastSeenAt
                    ? new Date(item.lastSeenAt).toLocaleString(
                          document.documentElement.lang === 'en' ? 'en-GB' : 'vi-VN',
                      )
                    : '—',
            ])
                row.append(text('td', value));
            row.cells[1].title = item.handler;
            body.append(row);
        }
        resourcePager.update(page);
    };
    const renderRegistry = async () => {
        if (!me?.permissions.includes('ACCESS_READ')) return;
        const values = await get('/registry/' + node('registry-kind').value),
            body = node('registry-body');
        body.replaceChildren();
        node('registry-status').textContent = values.length ? '' : labels.empty;
        for (const value of values) {
            const row = document.createElement('tr');
            for (const item of [
                value.code,
                value.nameVi,
                value.nameEn,
                value.active ? labels.on : labels.off,
                value.version,
            ])
                row.append(text('td', item));
            const actions = text('td', '');
            actions.className = 'table-actions';
            const edit = editButton(() => openRegistry(value));
            edit.disabled = !canEdit() || (node('registry-kind').value === 'roles' && value.builtIn);
            const group = text('div', '');
            group.className = 'row-actions';
            group.append(edit);
            if (node('registry-kind').value === 'roles' && !value.builtIn) {
                const remove = window.VietRouteTable.actionButton(
                    labels.deleteRole,
                    'delete',
                    () => {
                        if (busy || !window.confirm(labels.confirmRole)) return;
                        void read(async () => {
                            await app.mutate(
                                base +
                                    '/registry/roles/' +
                                    encodeURIComponent(value.code) +
                                    '?version=' +
                                    value.version,
                                null,
                                'delete',
                            );
                            if (me.permissions.includes('ACCESS_READ')) await renderRegistry();
                            document.dispatchEvent(new Event('access-registry-changed'));
                        });
                    },
                    true,
                );
                remove.disabled = !me?.permissions?.includes('ACCESS_DELETE');
                group.append(remove);
            }
            actions.append(group);
            row.append(actions);
            body.append(row);
        }
    };
    const renderPolicies = async () => {
        if (!me?.permissions.includes('ACCESS_READ')) return;
        const values = await get('/policies'),
            body = node('policies-body');
        body.replaceChildren();
        node('policies-status').textContent = values.length ? '' : labels.empty;
        for (const value of values) {
            const row = document.createElement('tr');
            for (const item of [value.kind, value.target, value.nameVi, value.enabled ? labels.on : labels.off])
                row.append(text('td', item));
            const actions = text('td', '');
            actions.className = 'table-actions';
            const edit = editButton(() => openPolicy(value).catch(app.failure));
            edit.disabled = !canEdit();
            edit.hidden = !canEdit();
            actions.append(edit);
            row.append(actions);
            body.append(row);
        }
    };
    const openRegistry = (value) => {
        if (busy || !(value ? canEdit() : canCreate())) return;
        selectedRegistry = value;
        registryForm.reset();
        window.VietRouteForms.clear(registryForm);
        app.feedback('', false, 'registry-dialog-feedback');
        registryForm.elements.code.value = value?.code || '';
        registryForm.elements.code.readOnly = !!value;
        registryForm.elements.code.maxLength = node('registry-kind').value === 'roles' ? 24 : 40;
        registryForm.elements.nameVi.value = value?.nameVi || '';
        registryForm.elements.nameEn.value = value?.nameEn || '';
        registryForm.elements.active.checked = value?.active ?? true;
        registryForm.elements.active.disabled = !!value?.builtIn;
        registryModal.open();
    };
    const openPolicy = async (value) => {
        if (busy || !(value ? canEdit() : canCreate())) return;
        const [inventory, permissions, roles] = await Promise.all([
            get('/targets'),
            get('/registry/permissions'),
            get('/registry/roles'),
        ]);
        targets = inventory;
        selectedPolicy = value;
        policyForm.reset();
        window.VietRouteForms.clear(policyForm);
        app.feedback('', false, 'policy-dialog-feedback');
        const select = node('policy-target');
        select.replaceChildren();
        for (const target of targets) {
            const option = text('option', target.kind + ' · ' + target.target);
            option.value = target.kind + '|' + target.target;
            select.append(option);
        }
        if (value) select.value = value.kind + '|' + value.target;
        policyForm.elements.nameVi.value = value?.nameVi || '';
        policyForm.elements.nameEn.value = value?.nameEn || '';
        policyForm.elements.enabled.checked = value?.enabled ?? true;
        for (const [id, values, selected] of [
            ['policy-permissions', permissions, value?.permissions || []],
            ['policy-roles', roles, value?.roles || []],
        ]) {
            const input = node(id);
            input.replaceChildren();
            for (const item of values.filter((item) => item.active || selected.includes(item.code))) {
                const option = text('option', item.code + ' · ' + item.nameVi);
                option.value = item.code;
                option.selected = selected.includes(item.code);
                input.append(option);
            }
        }
        node('policy-delete').hidden = !value || !me.permissions.includes('ACCESS_DELETE');
        policyModal.open();
    };
    const run = async (id, action) => {
        if (busy) return;
        busy = true;
        const controls = node(id).querySelector('fieldset');
        controls.disabled = true;
        app.feedback('', false, id + '-feedback');
        try {
            await action();
            node(id).close();
            await Promise.all([renderRegistry(), renderPolicies()]);
            document.dispatchEvent(new Event('access-registry-changed'));
        } catch (error) {
            app.failure(error, node(id).open ? id + '-feedback' : 'form-feedback');
        } finally {
            busy = false;
            controls.disabled = false;
        }
    };
    registryForm.addEventListener('submit', (event) => {
        event.preventDefault();
        if (!window.VietRouteForms.validate(registryForm)) return;
        const kind = node('registry-kind').value,
            code = registryForm.elements.code.value;
        const body = {
            nameVi: registryForm.elements.nameVi.value,
            nameEn: registryForm.elements.nameEn.value,
            active: registryForm.elements.active.checked,
            version: selectedRegistry?.version || 0,
        };
        run('registry-dialog', () =>
            app.mutate(
                base + '/registry/' + kind + '/' + encodeURIComponent(code),
                body,
                selectedRegistry ? 'put' : 'post',
            ),
        );
    });
    policyForm.addEventListener('submit', (event) => {
        event.preventDefault();
        if (!window.VietRouteForms.validate(policyForm)) return;
        const target = targets.find((item) => item.kind + '|' + item.target === node('policy-target').value);
        if (!target) return;
        const body = {
            kind: target.kind,
            target: target.target,
            nameVi: policyForm.elements.nameVi.value,
            nameEn: policyForm.elements.nameEn.value,
            enabled: policyForm.elements.enabled.checked,
            permissions: Array.from(node('policy-permissions').selectedOptions, (option) => option.value),
            roles: Array.from(node('policy-roles').selectedOptions, (option) => option.value),
            version: selectedPolicy?.version || 0,
        };
        run('policy-dialog', () =>
            app.mutate(
                base + '/policies' + (selectedPolicy ? '/' + selectedPolicy.id : ''),
                body,
                selectedPolicy ? 'put' : 'post',
            ),
        );
    });
    node('policy-delete').addEventListener('click', () => {
        if (selectedPolicy && window.confirm(labels.confirm))
            run('policy-dialog', () =>
                app.mutate(
                    base + '/policies/' + selectedPolicy.id + '?version=' + selectedPolicy.version,
                    null,
                    'delete',
                ),
            );
    });
    node('registry-create').addEventListener('click', () => openRegistry());
    node('policies-create').addEventListener('click', () => openPolicy().catch(app.failure));
    node('registry-kind').addEventListener('change', () => read(renderRegistry));
    node('registry-refresh').addEventListener('click', () => read(renderRegistry));
    node('policies-refresh').addEventListener('click', () => read(renderPolicies));
    node('resources-filter').addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !window.VietRouteForms.validate(event.target)) return;
        resourceFilters = { kind: node('resources-kind').value, query: node('resources-query').value.trim() };
        void read(() => renderResources({ page: 0, size: resourcePager.query.size }));
    });
    node('resources-refresh').addEventListener('click', () => read(renderResources));
    app.api
        .get('/api/v1/auth/me')
        .then(async (response) => {
            me = response.data.data;
            node('registry-create').disabled = !canCreate();
            node('registry-create').hidden = !canCreate();
            node('policies-create').disabled = !canCreate() || !me.permissions.includes('ACCESS_READ');
            node('policies-create').hidden = !canCreate();
            node('policy-delete').disabled = !me.permissions.includes('ACCESS_DELETE');
            node('policy-delete').hidden = !me.permissions.includes('ACCESS_DELETE');
            if (me.permissions.includes('ACCESS_READ'))
                await read(() => Promise.all([renderRegistry(), renderPolicies(), renderResources()]));
        })
        .catch(app.failure);
})();
