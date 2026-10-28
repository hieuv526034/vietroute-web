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
        editorReady = false,
        rolesLoaded = false,
        roleFilterLoading = false,
        applied = { username: '', role: '', active: '' },
        editing,
        selected,
        capabilities = [];
    const has = (method, path) => capabilities.includes(`${method} ${path}`);
    const can = (action) =>
        has(
            ...({
                READ: ['GET', base],
                CREATE: ['POST', base],
                UPDATE: ['PUT', `${base}/{id}`],
                DELETE: ['DELETE', `${base}/{id}`],
            }[action] || []),
        );
    const canAssign = () => has('PUT', `${access}/users/{id}/roles`);
    const modalFeedback = () => ['state', 'edit', 'detail', 'name'].find((name) => node(`account-${name}-dialog`).open);
    const perform = async (work) => {
        if (busy) return;
        busy = true;
        root.setAttribute('aria-busy', 'true');
        node('accounts-controls').disabled = true;
        root.querySelectorAll('[data-account-controls]').forEach((item) => {
            item.disabled = true;
        });
        for (const dialog of [editModal, detailModal, stateModal, nameModal]) dialog.focus();
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
            if (feedback === 'edit' && !editorReady) node('account-role-count').textContent = '';
            if (error.response?.status === 409)
                node(id).append(document.createElement('br'), text('span', labels.conflict));
        } finally {
            busy = false;
            root.setAttribute('aria-busy', 'false');
            node('accounts-controls').disabled = false;
            root.querySelectorAll('[data-account-controls]').forEach((item) => {
                item.disabled = false;
            });
            node('accounts-create').disabled = !(can('CREATE') && me?.permissions?.includes('ACCESS_ASSIGN'));
            node('account-save').disabled = !editorReady;
            node('accounts-refresh').disabled = false;
            node('accounts-status').textContent = '';
            for (const dialog of [editModal, detailModal, stateModal, nameModal]) dialog.focus();
        }
    };
    const editModal = window.VietRouteUI.modal('account-edit-dialog', {
        busy: () => busy,
        onClose: () => {
            node('account-edit-form').reset();
            forms.clear(node('account-edit-form'));
            node('new-account-password').value = '';
            editing = undefined;
            editorReady = false;
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
            if (user.emailVerificationRequired) state.append(text('small', labels.emailPending, 'd-block muted'));
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
            if (can('UPDATE') && user.id !== me.id && !user.roles.includes('ADMIN'))
                buttons.append(tables.actionButton(`${labels.edit}: ${user.username}`, 'edit', () => openName(user)));
            if (can('DELETE') && user.id !== me.id && !user.roles.includes('ADMIN'))
                buttons.append(
                    tables.actionButton(
                        `${labels.remove}: ${user.username}`,
                        'delete',
                        () => {
                            if (busy || !window.confirm(labels.removeNote + ' @' + user.username)) return;
                            void perform(async () => {
                                await app.mutate(`${base}/${user.id}?version=${user.version}`, null, 'delete');
                                await load();
                            });
                        },
                        true,
                    ),
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
    const roleSearch = window.VietRouteRolePicker.bind(
        node('account-role-search'),
        node('account-role-options'),
        node('account-role-count'),
    );
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
            label.dataset.roleChoice = role.role;
            const input = document.createElement('input');
            input.type = 'checkbox';
            input.name = 'roles';
            input.value = role.role;
            input.className = 'form-check-input';
            input.checked = values.includes(role.role);
            const content = text('span', '');
            content.append(
                text('strong', roleNames.get(role.role) || role.role),
                text('small', `${role.role}${role.active ? '' : ' · ' + labels.off}`, 'muted d-block'),
            );
            label.append(input, content);
            host.append(label);
            input.addEventListener('change', updateSelection);
        }
        roleSearch.reset();
        updateSelection();
    };
    const openEditor = (user) => {
        if (busy) return;
        editing = user;
        editorReady = false;
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
        node('account-role-options').replaceChildren();
        node('account-role-count').textContent = labels.loading;
        node('account-role-summary').textContent = '';
        app.feedback('', false, 'account-edit-feedback');
        editModal.open();
        void perform(async () => {
            await loadRoleMetadata();
            renderRoles(
                user?.roles || (roles.some((role) => role.role === 'CUSTOMER' && role.active) ? ['CUSTOMER'] : []),
            );
            editorReady = true;
        });
    };
    let nameTarget;
    const nameModal = window.VietRouteUI.modal('account-name-dialog', { busy: () => busy });
    const openName = (user) => {
        if (busy) return;
        nameTarget = user;
        node('account-name-value').value = user.displayName;
        app.feedback('', false, 'account-name-feedback');
        nameModal.open();
    };
    node('account-name-form').addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !forms.validate(event.currentTarget)) return;
        void perform(async () => {
            await app.mutate(
                `${base}/${nameTarget.id}`,
                { displayName: node('account-name-value').value, version: nameTarget.version },
                'put',
            );
            node('account-name-dialog').close();
            await load();
        });
    });
    // API trả capabilities thật: tên chức năng lấy từ tree source, không suy quyền từ role hoặc checkbox trên client.
    const show = async (user) => {
        selected = undefined;
        node('account-detail-target').textContent = `${user.displayName} · @${user.username}`;
        node('account-detail-summary').replaceChildren(text('p', labels.loading));
        node('account-effective').replaceChildren();
        node('account-state-open').hidden = true;
        node('account-protection').textContent = '';
        app.feedback('', false, 'account-detail-feedback');
        detailModal.open();
        const [result, features] = await window.VietRouteRequests.allReads([
            get(`${base}/${user.id}/access`),
            get(`${access}/tree`),
        ]);
        selected = result.user;
        node('account-detail-target').textContent =
            `${selected.displayName} · @${selected.username} · v${selected.version}`;
        const summary = node('account-detail-summary');
        summary.replaceChildren(chips(selected.roles), text('p', status(selected)));
        if (
            !selected.active ||
            selected.activationPending ||
            selected.mustChangePassword ||
            selected.emailVerificationRequired
        )
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
                    value?.targets?.filter((target) => !target.startsWith('GET ' + (feature.path || '__'))) || [],
                    labels.noActions,
                ),
            );
            row.append(
                title,
                text(
                    'td',
                    feature.path ? (value?.view ? labels.allowed : labels.denied) : labels.apiOnly,
                    feature.path && value?.view ? 'text-success' : 'muted',
                ),
                actions,
            );
            body.append(row);
        }
        table.append(body);
        node('account-effective').replaceChildren(table);
        const protectedAccount =
            selected.id === me.id || selected.roles.includes('ADMIN') || selected.activationPending;
        node('account-state-open').hidden = protectedAccount || !has('PUT', `${base}/{id}/state`);
        node('account-state-open').textContent = selected.active ? labels.disable : labels.enable;
        node('account-protection').textContent = protectedAccount ? labels.fixed : '';
        app.feedback('', false, 'account-detail-feedback');
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
        if (busy || !editorReady || !forms.validate(form)) return;
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
    // Bộ lọc và modal cần role; bảng chỉ cần me/capabilities. Không để metadata chặn lần tải bảng đầu tiên.
    const loadRoleMetadata = async () => {
        const [currentRoles, registry] = await window.VietRouteRequests.allReads([
            get(`${access}/roles`),
            get(`${access}/registry/roles`),
        ]);
        roles = currentRoles;
        rolesLoaded = true;
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
        const filter = node('account-role-filter'),
            previous = filter.value;
        filter.querySelectorAll('option[data-role-choice]').forEach((item) => item.remove());
        const options = roles.map((role) => {
            const item = option(
                role.role,
                (roleNames.get(role.role) || role.role) +
                    ' (' +
                    role.role +
                    ')' +
                    (role.active ? '' : ' · ' + labels.off),
            );
            item.dataset.roleChoice = role.role;
            return item;
        });
        filter.append(...options);
        filter.value = previous;
    };
    node('account-role-filter').addEventListener('focus', async () => {
        if (rolesLoaded || roleFilterLoading || busy) return;
        roleFilterLoading = true;
        try {
            await perform(loadRoleMetadata);
        } finally {
            // Trả focus sau fieldset disabled; guard vẫn bật khi focus event chạy nên không tự retry nếu đọc lỗi.
            node('account-role-filter').focus();
            roleFilterLoading = false;
        }
    });
    void perform(async () => {
        let accessSnapshot;
        [me, accessSnapshot] = await window.VietRouteRequests.allReads([
            get('/api/v1/auth/me'),
            get('/api/v1/auth/capabilities'),
        ]);
        capabilities = accessSnapshot.flatMap((item) => item.targets);
        await load();
    });
})();
