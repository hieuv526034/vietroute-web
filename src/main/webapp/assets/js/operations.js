/* Quản trị hệ thống dùng API/CSRF chung; không đọc token auth hoặc serialize cấu hình/credential. */
(() => {
    'use strict';
    const root = document.querySelector('[data-operations]');
    if (!root) {
        return;
    }
    const app = window.VietRoute;
    const tables = window.VietRouteTable;
    const labels = document.getElementById('ops-labels').dataset;
    const node = (id) => document.getElementById(id);
    const routes = Object.freeze({
        jobs: '/api/v1/admin/jobs',
        access: '/api/v1/admin/access',
        config: '/api/v1/admin/system/configuration',
    });
    let busy = false;
    let me;
    let currentCapabilities = { actions: [] };
    const has = (permission) => me?.permissions?.includes(permission);
    const canAssign = () => has('ACCESS_ASSIGN') || has('ACCESS_WRITE');
    const isAdmin = () => me?.roles?.includes('ADMIN');
    const get = async (path, params) => (await app.api.get(path, { params, timeout: 10000 })).data.data;
    const text = (tag, value, className) => {
        const element = document.createElement(tag);
        element.textContent = value == null ? '—' : String(value);
        if (className) {
            element.className = className;
        }
        return element;
    };
    const option = (value, title) => {
        const element = text('option', title);
        element.value = value;
        return element;
    };
    const empty = (body, columns) => {
        const row = document.createElement('tr');
        const cell = text('td', labels.empty);
        cell.colSpan = columns;
        row.append(cell);
        body.append(row);
    };
    // Khóa fieldset cha để chặn bấm lặp; không ghi đè disabled riêng của từng nút sau khi tải xong.
    const perform = async (action) => {
        if (busy) {
            return;
        }
        busy = true;
        node('ops-controls').disabled = true;
        root.querySelectorAll('[data-modal-controls]').forEach((control) => {
            control.disabled = true;
        });
        node('ops-refresh').disabled = true;
        root.setAttribute('aria-busy', 'true');
        node('ops-status').textContent = labels.loading;
        app.feedback('');
        if (node('job-feedback')) {
            app.feedback('', false, 'job-feedback');
        }
        for (const id of ['permissions-feedback', 'user-roles-feedback']) {
            app.feedback('', false, id);
        }
        try {
            await action();
        } catch (error) {
            const feedbackId = node('job-dialog')?.open
                ? 'job-feedback'
                : node('permissions-dialog')?.open
                  ? 'permissions-feedback'
                  : node('user-roles-dialog')?.open
                    ? 'user-roles-feedback'
                    : 'form-feedback';
            app.failure(error, feedbackId);
            if (error.response?.status === 409) {
                // Không tự retry mutation; giữ dữ liệu đang xem để người dùng chủ động làm mới.
                const current = node(feedbackId).textContent;
                app.feedback(`${current}\n${labels.conflict}`, false, feedbackId);
            }
        } finally {
            busy = false;
            node('ops-controls').disabled = false;
            root.querySelectorAll('[data-modal-controls]').forEach((control) => {
                control.disabled = false;
            });
            node('ops-refresh').disabled = false;
            root.setAttribute('aria-busy', 'false');
            node('ops-status').textContent = '';
        }
    };

    const jobs = () => {
        const pager = tables.pager('jobs', { busy: () => busy, onChange: (query) => perform(() => load(query)) });
        let selected;
        const body = node('jobs-body');
        const dialog = node('job-dialog');
        dialog.addEventListener('cancel', (event) => {
            if (busy) event.preventDefault();
        });
        const renderDetail = (job) => {
            selected = job;
            const details = node('job-details');
            details.replaceChildren();
            for (const key of [
                'id',
                'type',
                'status',
                'attempts',
                'availableAt',
                'lockedUntil',
                'lastErrorCode',
                'finishedAt',
                'createdAt',
                'updatedAt',
                'createdBy',
                'updatedBy',
                'version',
            ]) {
                details.append(
                    text('dt', details.dataset[key]),
                    text(
                        'dd',
                        key === 'attempts'
                            ? `${job.attempts} / ${job.maxAttempts}`
                            : (key === 'createdBy' || key === 'updatedBy') && job[key] === 0
                              ? 'system'
                              : job[key],
                    ),
                );
            }
            node('job-retry').disabled = !currentCapabilities.actions.includes('RETRY') || job.status !== 'DEAD';
            node('job-retry').hidden = !currentCapabilities.actions.includes('RETRY');
        };
        const show = async (id) => {
            const job = await get(`${routes.jobs}/${encodeURIComponent(id)}`);
            renderDetail(job);
            dialog.showModal();
        };
        const load = async (query = pager.query) => {
            const status = node('job-status').value;
            const data = await tables.readPage(
                (params) => get(routes.jobs, { ...params, ...(status ? { status } : {}) }),
                query,
            );
            body.replaceChildren();
            for (const job of data.content) {
                const row = document.createElement('tr');
                for (const value of [
                    job.id,
                    job.type,
                    job.status,
                    `${job.attempts} / ${job.maxAttempts}`,
                    job.updatedAt,
                ]) {
                    row.append(text('td', value));
                }
                const actions = document.createElement('td');
                actions.className = 'table-actions';
                actions.append(
                    tables.actionButton(`${labels.details}: ${job.id}`, 'view', () => perform(() => show(job.id))),
                );
                row.append(actions);
                body.append(row);
            }
            if (!data.content.length) {
                empty(body, 6);
            }
            pager.update(data);
        };
        node('job-close').addEventListener('click', () => {
            if (!busy) dialog.close();
        });
        node('jobs-filter').addEventListener('submit', (event) => {
            event.preventDefault();
            if (!window.VietRouteForms.validate(event.currentTarget)) return;
            perform(() => load({ ...pager.query, page: 0 }));
        });
        node('job-retry').addEventListener('click', () => {
            if (
                busy ||
                !selected ||
                selected.status !== 'DEAD' ||
                !has('JOB_RETRY') ||
                !window.confirm(labels.confirmRetry)
            ) {
                return;
            }
            const target = selected;
            perform(async () => {
                const response = await app.mutate(`${routes.jobs}/${encodeURIComponent(target.id)}/retry`, {
                    version: target.version,
                });
                renderDetail(response.data.data);
                await load();
                app.feedback(labels.saved, true, dialog.open ? 'job-feedback' : 'form-feedback');
            });
        });
        return async () => {
            if (dialog.open) {
                dialog.close();
            }
            await load();
        };
    };

    const access = () => {
        let treeData = [];
        let roleTree;
        let registry = [];
        let roles = [];
        let selectedRole;
        let selectedUser;
        const pager = tables.pager('users', { busy: () => busy, onChange: (query) => perform(() => loadUsers(query)) });
        let prefix = '';
        const roleSelect = node('access-role');
        const permissionsBody = node('permissions-body');
        const roleModal = window.VietRouteUI.modal('permissions-dialog', {
            busy: () => busy,
            onClose: () => {
                app.feedback('', false, 'permissions-feedback');
                drawRole();
            },
        });
        const userModal = window.VietRouteUI.modal('user-roles-dialog', {
            busy: () => busy,
            onClose: () => {
                selectedUser = undefined;
                app.feedback('', false, 'user-roles-feedback');
                window.VietRouteForms.clear(node('user-roles-form'));
            },
        });
        const accessTabs = window.VietRouteUI.tabs(node('ops-controls'));
        const drawRole = () => {
            selectedRole = roles.find((role) => role.role === roleSelect.value);
            if (!selectedRole) {
                return;
            }
            const fixed = selectedRole.role === 'ADMIN';
            const outside = !isAdmin() && selectedRole.permissions.some((permission) => !has(permission));
            const editable = canAssign() && !fixed && !outside;
            node('role-note').textContent = fixed
                ? labels.fixed
                : outside
                  ? labels.restricted
                  : editable
                    ? ''
                    : labels.readonly;
            node('role-version').textContent = String(selectedRole.version);
            roleTree = window.VietRouteAccessTree.render(
                permissionsBody,
                treeData,
                registry,
                selectedRole.permissions,
                { editable, allowed: isAdmin() ? null : me.permissions },
            );
            node('permissions-save').disabled = !editable;
            node('permissions-edit').disabled = false;
            node('permissions-save').hidden = !editable;
            node('permissions-target').textContent =
                `${selectedRole.role} · v${selectedRole.version} · ${selectedRole.assignedUsers || 0} ${document.getElementById('access-tree-labels').dataset.impact}`;
            const summary = node('role-permission-summary');
            summary.replaceChildren();
            for (const permission of registry.filter((item) => selectedRole.permissions.includes(item.code))) {
                summary.append(text('span', permission.name, 'permission-chip'));
            }
            if (!summary.childElementCount) summary.append(text('p', labels.empty));
        };
        node('permissions-edit').addEventListener('click', () => {
            if (!busy && !node('permissions-edit').disabled) {
                drawRole();
                roleModal.open();
            }
        });
        const loadRoles = async () => {
            const previous = roleSelect.value;
            [registry, roles, treeData] = await Promise.all([
                get(`${routes.access}/permissions`),
                get(`${routes.access}/roles`),
                get(`${routes.access}/tree`),
            ]);
            roleSelect.replaceChildren(
                ...roles.map((role) =>
                    option(role.role, role.role + (role.active === false ? ' · ' + labels.off : '')),
                ),
            );
            if (roles.some((role) => role.role === previous)) {
                roleSelect.value = previous;
            }
            roleSelect.disabled = false;
            drawRole();
        };
        const chooseUser = (user) => {
            app.feedback('');
            selectedUser = user;
            node('user-roles-form').hidden = false;
            node('selected-user').textContent = `${user.username} · ${user.displayName} · #${user.id}`;
            node('user-version').textContent = String(user.version);
            const editable = canAssign() && user.id !== me.id && (isAdmin() || !user.roles.includes('ADMIN'));
            const options = node('user-role-options');
            options.querySelectorAll('label').forEach((label) => label.remove());
            for (const role of roles) {
                const label = document.createElement('label');
                label.className = 'd-inline-flex align-items-center gap-2 me-4 mb-3';
                const input = document.createElement('input');
                input.type = 'checkbox';
                input.className = 'form-check-input';
                input.value = role.role;
                input.checked = user.roles.includes(role.role);
                input.disabled =
                    !editable ||
                    (role.active === false && !input.checked) ||
                    (!isAdmin() && (role.role === 'ADMIN' || role.permissions.some((permission) => !has(permission))));
                label.append(input, text('span', role.role + (role.active === false ? ' · ' + labels.off : '')));
                options.append(label);
            }
            node('user-roles-save').disabled = !editable;
            if (user.id === me.id) {
                app.feedback(labels.self, false, 'user-roles-feedback');
            }
            window.VietRouteAccessTree.sources(node('user-role-options'), () =>
                get(`${routes.access}/users/${user.id}/effective`),
            );
            userModal.open();
        };
        const loadUsers = async (query = pager.query) => {
            selectedUser = undefined;
            node('user-roles-form').hidden = true;
            const data = await tables.readPage(
                (params) => get(`${routes.access}/users`, { ...params, username: prefix }),
                query,
            );
            const body = node('users-body');
            body.replaceChildren();
            for (const user of data.content) {
                const row = document.createElement('tr');
                for (const value of [
                    user.id,
                    user.username,
                    user.displayName,
                    user.roles.join(', '),
                    user.active ? labels.on : labels.off,
                ]) {
                    row.append(text('td', value));
                }
                const cell = document.createElement('td');
                cell.className = 'table-actions';
                cell.append(
                    tables.actionButton(`${labels.select}: ${user.username}`, 'assign', () => chooseUser(user)),
                );
                row.append(cell);
                body.append(row);
            }
            if (!data.content.length) {
                empty(body, 6);
            }
            pager.update(data);
            accessTabs.show('access-users-section', true);
        };
        roleSelect.addEventListener('change', drawRole);
        node('permissions-form').addEventListener('submit', (event) => {
            event.preventDefault();
            if (busy || !selectedRole || node('permissions-save').disabled) {
                return;
            }
            const target = selectedRole;
            const permissions = roleTree.values();
            perform(async () => {
                await app.mutate(
                    `${routes.access}/roles/${encodeURIComponent(target.role)}/permissions`,
                    { permissions, version: target.version },
                    'put',
                );
                // Sửa mapping có thể thu hồi chính quyền của người được ủy quyền ngay ở request tiếp theo.
                me = await get('/api/v1/auth/me');
                if (!has('ACCESS_READ')) {
                    const meta = (name) => document.querySelector(`meta[name="${name}"]`)?.content || '';
                    window.location.assign(
                        `${meta('app-base')}/account?lang=${encodeURIComponent(meta('app-locale') || 'vi')}`,
                    );
                    return;
                }
                await loadRoles();
                node('permissions-dialog').close();
                app.feedback(labels.saved, true);
            });
        });
        node('users-search').addEventListener('submit', (event) => {
            event.preventDefault();
            if (busy || !window.VietRouteForms.validate(event.currentTarget)) {
                return;
            }
            prefix = node('user-prefix').value.trim();
            perform(() => loadUsers({ ...pager.query, page: 0 }));
        });
        node('user-roles-form').addEventListener('submit', (event) => {
            event.preventDefault();
            if (
                busy ||
                !selectedUser ||
                node('user-roles-save').disabled ||
                !window.VietRouteForms.validate(event.currentTarget)
            ) {
                return;
            }
            const target = selectedUser;
            const selected = Array.from(
                node('user-role-options').querySelectorAll('input:checked'),
                (input) => input.value,
            );
            perform(async () => {
                await app.mutate(
                    `${routes.access}/users/${encodeURIComponent(target.id)}/roles`,
                    { roles: selected, version: target.version },
                    'put',
                );
                await loadUsers();
                node('user-roles-dialog').close();
                app.feedback(labels.saved, true);
            });
        });
        return async () => {
            await loadRoles();
            await loadUsers();
        };
    };

    const configuration = () => async () => {
        const value = await get(routes.config);
        node('config-timestamp').textContent = value.timestamp;
        const container = node('configuration-sections');
        const previousTab = container.querySelector('[role="tab"][aria-selected="true"]')?.dataset.tabTarget;
        const fieldLabels = Object.fromEntries(
            Array.from(node('configuration-labels').querySelectorAll('[data-field]'), (label) => [
                label.dataset.field,
                label.textContent,
            ]),
        );
        container.replaceChildren();
        const group = document.createElement('div');
        group.dataset.tabs = '';
        const tablist = document.createElement('div');
        tablist.className = 'workspace-tabs';
        tablist.setAttribute('role', 'tablist');
        tablist.setAttribute('aria-label', root.querySelector('h1').textContent);
        group.append(tablist);
        // Chỉ render các section định trước; không duyệt cả response để vô tình hiện field mới chưa review.
        for (const key of ['application', 'database', 'traffic', 'worker', 'features', 'monitoring']) {
            const column = document.createElement('section');
            column.id = `config-${key}`;
            const tab = text('button', container.dataset[key]);
            tab.type = 'button';
            tab.id = `config-tab-${key}`;
            tab.dataset.tabTarget = column.id;
            if (column.id === previousTab) tab.setAttribute('aria-selected', 'true');
            tablist.append(tab);
            const card = document.createElement('div');
            card.className = 'card h-100 p-4';
            card.append(text('h2', container.dataset[key], 'h4'));
            const details = document.createElement('dl');
            details.className = 'operations-details';
            for (const [field, item] of Object.entries(value[key])) {
                if (fieldLabels[field]) {
                    details.append(
                        text('dt', fieldLabels[field]),
                        text('dd', typeof item === 'boolean' ? (item ? labels.on : labels.off) : item),
                    );
                }
            }
            card.append(details);
            column.append(card);
            group.append(column);
        }
        container.append(group);
        window.VietRouteUI.initializeTabs(container);
    };

    const loadData =
        root.dataset.operations === 'jobs' ? jobs() : root.dataset.operations === 'access' ? access() : configuration();
    const load = async () => {
        const feature =
            root.dataset.operations === 'jobs'
                ? 'JOBS'
                : root.dataset.operations === 'access'
                  ? 'ACCESS'
                  : 'CONFIGURATION';
        const capabilities = await window.VietRouteAccess.load(feature);
        currentCapabilities = capabilities;
        if (
            !capabilities.actions.includes('READ') ||
            (feature === 'JOBS' && !capabilities.targets.includes('GET /api/v1/admin/jobs'))
        ) {
            app.feedback(
                document.getElementById('access-tree-labels')?.dataset.noRead ||
                    document.querySelector('meta[name="access-no-read"]')?.content ||
                    '',
            );
            return;
        }
        await loadData();
    };
    document.addEventListener('access-registry-changed', () => perform(load));
    node('ops-refresh').addEventListener('click', () =>
        perform(async () => {
            me = await get('/api/v1/auth/me');
            await load();
        }),
    );
    perform(async () => {
        me = await get('/api/v1/auth/me');
        await load();
    });
})();
