/* Workspace danh tính: không giữ password/token ở storage; mọi gán/khóa có version và kiểm quyền backend. */
(() => {
    'use strict';
    const root = document.querySelector('[data-accounts]');
    if (!root) return;
    const app = window.VietRoute,
        tables = window.VietRouteTable,
        forms = window.VietRouteForms;
    const node = (id) => document.getElementById(id);
    const labels = node('accounts-labels').dataset;
    const time = (value) => new Date(value).toLocaleString(document.documentElement.lang === 'en' ? 'en-GB' : 'vi-VN');
    const base = '/api/v1/admin/accounts',
        access = '/api/v1/admin/access';
    const get = async (path, params) => (await app.api.get(path, { params, timeout: 10000 })).data.data;
    const text = (tag, value, css) => {
        const item = document.createElement(tag);
        item.textContent = value;
        if (css) item.className = css;
        return item;
    };
    const option = (value, label) => {
        const item = text('option', label);
        item.value = value;
        return item;
    };
    let roleNames = new Map();
    let busy = false,
        me,
        roles = [],
        features = [],
        applied = { username: '', role: '', active: '' },
        editing,
        selected;
    const can = (action) => me?.permissions?.includes(`ACCOUNT_${action}`);
    const canAssign = () => me?.permissions?.includes('ACCESS_ASSIGN') || me?.permissions?.includes('ACCESS_WRITE');
    const modalFeedback = () => ['state', 'edit', 'detail'].find((name) => node(`account-${name}-dialog`).open);
    const perform = async (work) => {
        if (busy) return;
        busy = true;
        root.setAttribute('aria-busy', 'true');
        node('accounts-controls').disabled = true;
        root.querySelectorAll('[data-account-controls]').forEach((item) => {
            item.disabled = true;
        });
        node('accounts-create').disabled = true;
        node('accounts-refresh').disabled = true;
        node('accounts-status').textContent = labels.loading;
        const target = modalFeedback();
        app.feedback('', false, target ? `account-${target}-feedback` : 'form-feedback');
        try {
            await work();
        } catch (error) {
            const feedback = modalFeedback();
            const id = feedback ? `account-${feedback}-feedback` : 'form-feedback';
            app.failure(error, id);
            if (error.response?.status === 409)
                node(id).append(document.createElement('br'), text('span', labels.conflict));
        } finally {
            busy = false;
            root.setAttribute('aria-busy', 'false');
            node('accounts-controls').disabled = false;
            root.querySelectorAll('[data-account-controls]').forEach((item) => {
                item.disabled = false;
            });
            node('accounts-create').disabled = !(can('CREATE') && me.permissions.includes('ACCESS_ASSIGN'));
            node('accounts-refresh').disabled = false;
            node('accounts-status').textContent = '';
        }
    };
    const editModal = window.VietRouteUI.modal('account-edit-dialog', {
        busy: () => busy,
        onClose: () => {
            node('account-edit-form').reset();
            forms.clear(node('account-edit-form'));
            node('new-account-password').value = '';
            editing = undefined;
        },
    });
    const detailModal = window.VietRouteUI.modal('account-detail-dialog', { busy: () => busy });
    const stateModal = window.VietRouteUI.modal('account-state-dialog', { busy: () => busy });
    const pager = tables.pager('accounts', { busy: () => busy, onChange: (query) => perform(() => load(query)) });
    const chips = (values, emptyCaption = labels.noRole) => {
        const host = text('div', '', 'account-role-chips');
        for (const value of values) host.append(text('span', value, 'permission-chip'));
        if (!values.length) host.append(text('span', emptyCaption, 'muted'));
        return host;
    };
    const status = (user) => (user.activationPending ? labels.pending : user.active ? labels.active : labels.inactive);
    const load = async (query = pager.query) => {
        const page = await tables.readPage(
            (params) =>
                get(base, {
                    ...Object.fromEntries(Object.entries(applied).filter(([, value]) => value !== '')),
                    ...params,
                }),
            query,
        );
        const body = node('accounts-body');
        body.replaceChildren();
        for (const user of page.content) {
            const row = document.createElement('tr'),
                identity = document.createElement('td');
            identity.append(
                text('strong', user.displayName),
                text('small', `@${user.username} · #${user.id}`, 'd-block muted'),
            );
            const role = document.createElement('td');
            role.append(chips(user.roles));
            const state = document.createElement('td');
            state.append(text('span', status(user), `account-badge ${user.active ? 'is-active' : 'is-inactive'}`));
            if (user.mustChangePassword) state.append(text('small', labels.passwordPending, 'd-block muted'));
            if (user.lockedUntil && Date.parse(user.lockedUntil) > Date.now())
                state.append(text('small', `${labels.locked} ${time(user.lockedUntil)}`, 'd-block muted'));
            const actions = text('td', '', 'table-actions'),
                buttons = text('div', '', 'row-actions');
            buttons.append(
                tables.actionButton(
                    `${labels.detail}: ${user.username}`,
                    'view',
                    () => void perform(() => show(user)),
                    false,
                ),
            );
            if (canAssign() && user.id !== me.id)
                buttons.append(
                    tables.actionButton(`${labels.assign}: ${user.username}`, 'assign', () => openEditor(user), false),
                );
            actions.append(buttons);
            row.append(identity, role, state, text('td', time(user.updatedAt)), actions);
            body.append(row);
        }
        if (!page.content.length) {
            const row = document.createElement('tr'),
                cell = text('td', labels.empty, 'muted text-center py-4');
            cell.colSpan = 5;
            row.append(cell);
            body.append(row);
        }
        node('accounts-total').textContent = String(page.totalElements);
        pager.update(page);
    };
    const selection = () =>
        Array.from(node('account-role-options').querySelectorAll('input:checked'), (item) => item.value);
    const updateSelection = () => {
        node('account-role-summary').textContent = `${labels.selection}: ${selection().join(', ') || '—'}`;
    };
    const renderRoles = (values) => {
        const host = node('account-role-options');
        host.querySelectorAll('label').forEach((item) => item.remove());
        for (const role of roles.filter((item) => item.active || values.includes(item.role))) {
            const label = text('label', '', 'account-role-option');
            const input = document.createElement('input');
            input.type = 'checkbox';
            input.name = 'roles';
            input.value = role.role;
            input.className = 'form-check-input';
            input.checked = values.includes(role.role);
            const content = text('span', '');
            content.append(
                text('strong', roleNames.get(role.role) || role.role),
                text(
                    'small',
                    `${role.role} · ${role.permissions.length} ${labels.permissions}${role.active ? '' : ' · ' + labels.off}`,
                    'muted d-block',
                ),
            );
            label.append(input, content);
            host.append(label);
            input.addEventListener('change', updateSelection);
        }
        updateSelection();
    };
    const openEditor = (user) => {
        if (busy) return;
        editing = user;
        const form = node('account-edit-form');
        form.reset();
        forms.clear(form);
        const create = !user;
        node('account-edit-title').textContent = create ? labels.create : labels.assign;
        node('account-edit-target').textContent = create
            ? ''
            : `${user.displayName} · @${user.username} · v${user.version}`;
        node('account-create-fields').hidden = !create;
        node('account-create-fields')
            .querySelectorAll('input')
            .forEach((input) => {
                input.disabled = !create;
            });
        node('account-save').textContent = create ? labels.create : labels.save;
        node('account-grant-note').hidden = create;
        renderRoles(user?.roles || (roles.some((role) => role.role === 'CUSTOMER' && role.active) ? ['CUSTOMER'] : []));
        app.feedback('', false, 'account-edit-feedback');
        editModal.open();
    };
    // API trả capabilities thật: tên chức năng lấy từ tree source, không suy quyền từ role hoặc checkbox trên client.
    const show = async (user) => {
        const result = await get(`${base}/${user.id}/access`);
        selected = result.user;
        node('account-detail-target').textContent =
            `${selected.displayName} · @${selected.username} · v${selected.version}`;
        const summary = node('account-detail-summary');
        summary.replaceChildren(chips(selected.roles), text('p', status(selected)));
        if (!selected.active || selected.activationPending || selected.mustChangePassword)
            summary.append(text('p', labels.blocked, 'account-scope-note'));
        const table = text('table', '', 'table align-middle access-effective-table'),
            head = document.createElement('thead'),
            heading = document.createElement('tr');
        for (const key of ['feature', 'view', 'actions']) heading.append(text('th', labels[key]));
        head.append(heading);
        table.append(head);
        const body = document.createElement('tbody');
        for (const feature of features.filter((item) => item.path || item.actions.length)) {
            const value = result.capabilities.find((item) => item.function === feature.code);
            const row = document.createElement('tr'),
                title = document.createElement('td');
            title.append(text('strong', feature.name), text('small', feature.path || 'API', 'd-block muted'));
            const actions = document.createElement('td');
            actions.append(
                chips(
                    (value?.actions?.filter((item) => item !== 'VIEW') || []).map(
                        (action) => labels['action' + action.charAt(0) + action.slice(1).toLowerCase()] || action,
                    ),
                    labels.noActions,
                ),
            );
            row.append(
                title,
                text('td', value?.view ? labels.allowed : labels.denied, value?.view ? 'text-success' : 'muted'),
                actions,
            );
            body.append(row);
        }
        table.append(body);
        node('account-effective').replaceChildren(table);
        const protectedAccount =
            selected.id === me.id || selected.roles.includes('ADMIN') || selected.activationPending;
        node('account-state-open').hidden = protectedAccount || !can('UPDATE');
        node('account-state-open').textContent = selected.active ? labels.disable : labels.enable;
        node('account-protection').textContent = protectedAccount ? labels.fixed : '';
        app.feedback('', false, 'account-detail-feedback');
        detailModal.open();
    };
    node('accounts-create').addEventListener('click', () => openEditor());
    for (const [id, role] of [
        ['preset-driver', 'DRIVER'],
        ['preset-customer', 'CUSTOMER'],
    ])
        node(id).addEventListener('click', () => {
            if (!busy && roles.some((item) => item.active && item.role === role)) renderRoles([role]);
        });
    node('accounts-refresh').addEventListener('click', () => void perform(() => load()));
    node('accounts-search').addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !forms.validate(event.currentTarget)) return;
        const candidate = Object.fromEntries(new FormData(event.currentTarget));
        void perform(async () => {
            const previous = applied;
            applied = candidate;
            try {
                await load({ ...pager.query, page: 0 });
            } catch (error) {
                applied = previous;
                throw error;
            }
        });
    });
    node('account-edit-form').addEventListener('submit', (event) => {
        event.preventDefault();
        const form = event.currentTarget;
        if (busy || !forms.validate(form)) return;
        const values = Object.fromEntries(new FormData(form));
        const body = editing
            ? { roles: selection(), version: editing.version }
            : {
                  username: values.username,
                  displayName: values.displayName,
                  password: values.password,
                  roles: selection(),
              };
        const path = editing ? `${access}/users/${editing.id}/roles` : base;
        void perform(async () => {
            const create = !editing;
            await app.mutate(path, body, create ? 'post' : 'put');
            node('account-edit-dialog').close();
            app.feedback(create ? labels.created : labels.success, true);
            await load();
        });
    });
    node('account-state-open').addEventListener('click', () => {
        if (busy || !selected) return;
        node('account-state-target').textContent =
            `${selected.active ? labels.disable : labels.enable}: @${selected.username} · v${selected.version}`;
        node('account-state-save').textContent = selected.active ? labels.disable : labels.enable;
        app.feedback('', false, 'account-state-feedback');
        stateModal.open();
    });
    node('account-state-save').addEventListener(
        'click',
        () =>
            void perform(async () => {
                await app.mutate(
                    `${base}/${selected.id}/state`,
                    { active: !selected.active, version: selected.version },
                    'put',
                );
                node('account-state-dialog').close();
                node('account-detail-dialog').close();
                app.feedback(labels.success, true);
                await load();
            }),
    );
    void perform(async () => {
        me = await get('/api/v1/auth/me');
        let registry;
        [roles, features, registry] = await Promise.all([
            get(`${access}/roles`),
            get(`${access}/tree`),
            get(`${access}/registry/roles`),
        ]);
        roleNames = new Map(
            registry.map((role) => [role.code, document.documentElement.lang === 'en' ? role.nameEn : role.nameVi]),
        );
        for (const [id, role] of [
            ['preset-driver', 'DRIVER'],
            ['preset-customer', 'CUSTOMER'],
        ]) {
            const available = roles.some((item) => item.role === role && item.active);
            node(id).disabled = !available;
            if (!available) node(id).title = labels.off;
        }
        node('account-role-filter').append(
            ...roles.map((role) =>
                option(
                    role.role,
                    (roleNames.get(role.role) || role.role) +
                        ' (' +
                        role.role +
                        ')' +
                        (role.active ? '' : ' · ' + labels.off),
                ),
            ),
        );
        await load();
    });
})();
