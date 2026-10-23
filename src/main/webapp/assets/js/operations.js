/* Quản trị hệ thống dùng API/CSRF chung; không đọc token auth hoặc serialize cấu hình/credential. */
(() => {
    'use strict';
    const root = document.querySelector('[data-operations]');
    if (!root) {
        return;
    }
    const app = window.VietRoute;
    const labels = document.getElementById('ops-labels').dataset;
    const node = (id) => document.getElementById(id);
    const routes = Object.freeze({
        jobs: '/api/v1/admin/jobs',
        access: '/api/v1/admin/access',
        config: '/api/v1/admin/system/configuration',
    });
    let busy = false;
    let me;
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
    const button = (title, action) => {
        const element = text('button', title, 'button button-outline button-small');
        element.type = 'button';
        element.addEventListener('click', action);
        return element;
    };
    const empty = (body, columns) => {
        const row = document.createElement('tr');
        const cell = text('td', labels.empty);
        cell.colSpan = columns;
        row.append(cell);
        body.append(row);
    };
    const pagination = (prefix, page) => {
        node(`${prefix}-previous`).disabled = !page.hasPrevious;
        node(`${prefix}-next`).disabled = !page.hasNext;
        node(`${prefix}-page`).textContent =
            `${labels.page}: ${page.totalPages ? page.page + 1 : 0} / ${page.totalPages} / ${page.totalElements}`;
    };
    // Khóa fieldset cha để chặn bấm lặp; không ghi đè disabled riêng của từng nút sau khi tải xong.
    const perform = async (action) => {
        if (busy) {
            return;
        }
        busy = true;
        node('ops-controls').disabled = true;
        node('ops-refresh').disabled = true;
        root.setAttribute('aria-busy', 'true');
        node('ops-status').textContent = labels.loading;
        app.feedback('');
        if (node('job-feedback')) {
            app.feedback('', false, 'job-feedback');
        }
        try {
            await action();
        } catch (error) {
            const feedbackId = node('job-dialog')?.open ? 'job-feedback' : 'form-feedback';
            app.failure(error, feedbackId);
            if (error.response?.status === 409) {
                // Không tự retry mutation; giữ dữ liệu đang xem để người dùng chủ động làm mới.
                const current = node(feedbackId).textContent;
                app.feedback(`${current}\n${labels.conflict}`, false, feedbackId);
            }
        } finally {
            busy = false;
            node('ops-controls').disabled = false;
            node('ops-refresh').disabled = false;
            root.setAttribute('aria-busy', 'false');
            node('ops-status').textContent = '';
        }
    };

    const jobs = () => {
        let page = 0;
        let selected;
        const body = node('jobs-body');
        const dialog = node('job-dialog');
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
                'version',
            ]) {
                details.append(
                    text('dt', details.dataset[key]),
                    text('dd', key === 'attempts' ? `${job.attempts} / ${job.maxAttempts}` : job[key]),
                );
            }
            node('job-retry').disabled = !has('JOB_RETRY') || job.status !== 'DEAD';
        };
        const show = async (id) => {
            const job = await get(`${routes.jobs}/${encodeURIComponent(id)}`);
            renderDetail(job);
            dialog.showModal();
        };
        const load = async () => {
            const status = node('job-status').value;
            const data = await get(routes.jobs, { page, size: 20, ...(status ? { status } : {}) });
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
                actions.append(button(labels.details, () => perform(() => show(job.id))));
                row.append(actions);
                body.append(row);
            }
            if (!data.content.length) {
                empty(body, 6);
            }
            pagination('jobs', data);
        };
        node('job-close').addEventListener('click', () => dialog.close());
        node('jobs-filter').addEventListener('submit', (event) => {
            event.preventDefault();
            page = 0;
            perform(load);
        });
        node('jobs-previous').addEventListener('click', () => {
            if (!busy && page > 0) {
                page--;
                perform(load);
            }
        });
        node('jobs-next').addEventListener('click', () => {
            if (!busy) {
                page++;
                perform(load);
            }
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
        let registry = [];
        let roles = [];
        let selectedRole;
        let selectedUser;
        let usersPage = 0;
        let prefix = '';
        const roleSelect = node('access-role');
        const permissionsBody = node('permissions-body');
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
            permissionsBody.replaceChildren();
            for (const permission of registry) {
                const row = document.createElement('tr');
                row.append(
                    text('td', permission.resource),
                    text('td', `${permission.code} · ${permission.name}`),
                    text('td', permission.actions.join(', ')),
                );
                const cell = document.createElement('td');
                const input = document.createElement('input');
                input.type = 'checkbox';
                input.className = 'form-check-input';
                input.value = permission.code;
                input.checked = selectedRole.permissions.includes(permission.code);
                input.disabled = !editable || (!isAdmin() && !has(permission.code));
                input.setAttribute('aria-label', `${selectedRole.role}: ${permission.name}`);
                cell.append(input);
                row.append(cell);
                permissionsBody.append(row);
            }
            node('permissions-save').disabled = !editable;
        };
        const loadRoles = async () => {
            const previous = roleSelect.value;
            [registry, roles] = await Promise.all([get(`${routes.access}/permissions`), get(`${routes.access}/roles`)]);
            roleSelect.replaceChildren(...roles.map((role) => option(role.role, role.role)));
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
            const editable = canAssign() && user.id !== me.id;
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
                input.disabled = !editable;
                label.append(input, text('span', role.role));
                options.append(label);
            }
            node('user-roles-save').disabled = !editable;
            if (user.id === me.id) {
                app.feedback(labels.self);
            }
        };
        const loadUsers = async () => {
            if (!isAdmin()) {
                return;
            }
            selectedUser = undefined;
            node('user-roles-form').hidden = true;
            const data = await get(`${routes.access}/users`, { page: usersPage, size: 20, username: prefix });
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
                cell.append(button(labels.select, () => chooseUser(user)));
                row.append(cell);
                body.append(row);
            }
            if (!data.content.length) {
                empty(body, 6);
            }
            pagination('users', data);
            node('access-users-section').hidden = false;
        };
        roleSelect.addEventListener('change', drawRole);
        node('permissions-form').addEventListener('submit', (event) => {
            event.preventDefault();
            if (
                busy ||
                !selectedRole ||
                node('permissions-save').disabled ||
                !window.confirm(labels.confirmPermissions)
            ) {
                return;
            }
            const target = selectedRole;
            const permissions = Array.from(permissionsBody.querySelectorAll('input:checked'), (input) => input.value);
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
                app.feedback(labels.saved, true);
            });
        });
        node('users-search').addEventListener('submit', (event) => {
            event.preventDefault();
            if (busy) {
                return;
            }
            prefix = node('user-prefix').value.trim();
            usersPage = 0;
            perform(loadUsers);
        });
        node('users-previous').addEventListener('click', () => {
            if (!busy && usersPage > 0) {
                usersPage--;
                perform(loadUsers);
            }
        });
        node('users-next').addEventListener('click', () => {
            if (!busy) {
                usersPage++;
                perform(loadUsers);
            }
        });
        node('user-roles-form').addEventListener('submit', (event) => {
            event.preventDefault();
            if (busy || !selectedUser || node('user-roles-save').disabled || !window.confirm(labels.confirmRoles)) {
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
        const fieldLabels = Object.fromEntries(
            Array.from(node('configuration-labels').querySelectorAll('[data-field]'), (label) => [
                label.dataset.field,
                label.textContent,
            ]),
        );
        container.replaceChildren();
        // Chỉ render các section định trước; không duyệt cả response để vô tình hiện field mới chưa review.
        for (const key of ['application', 'database', 'traffic', 'worker', 'features', 'monitoring']) {
            const column = document.createElement('section');
            column.className = 'col-lg-6';
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
            container.append(column);
        }
    };

    const load =
        root.dataset.operations === 'jobs' ? jobs() : root.dataset.operations === 'access' ? access() : configuration();
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
