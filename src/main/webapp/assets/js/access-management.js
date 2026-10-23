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
        me;
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
        onChange: (query) => renderResources(query).catch(app.failure),
        busy: () => busy,
    });
    const renderResources = async (query = resourcePager.query) => {
        const page = await window.VietRouteTable.readPage(
            (params) => get('/resources?' + new URLSearchParams(params)),
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
                item.description,
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
            edit.disabled = !canEdit();
            actions.append(edit);
            row.append(actions);
            body.append(row);
        }
    };
    const renderPolicies = async () => {
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
        node('policy-delete').hidden = !value;
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
    node('registry-kind').addEventListener('change', () => renderRegistry().catch(app.failure));
    node('registry-refresh').addEventListener('click', () => renderRegistry().catch(app.failure));
    node('policies-refresh').addEventListener('click', () => renderPolicies().catch(app.failure));
    node('resources-refresh').addEventListener('click', () => renderResources().catch(app.failure));
    app.api
        .get('/api/v1/auth/me')
        .then(async (response) => {
            me = response.data.data;
            node('registry-create').disabled = !canCreate();
            node('policies-create').disabled = !canCreate();
            node('policy-delete').disabled = !me.permissions.includes('ACCESS_DELETE');
            await Promise.all([renderRegistry(), renderPolicies(), renderResources()]);
        })
        .catch(app.failure);
})();
