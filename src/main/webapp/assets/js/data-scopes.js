/* Quản trị scope dùng API/role picker chung; không giữ scope/token ở storage, không replay mutation hoặc tự đổi version. */
(() => {
    'use strict';
    const node = (id) => document.getElementById(id);
    const section = node('access-scopes-section');
    if (!section) return;
    const app = window.VietRoute,
        tables = window.VietRouteTable,
        forms = window.VietRouteForms;
    const labels = node('scope-labels').dataset,
        base = '/api/v1/admin/access';
    let busy = false,
        loaded = false,
        me,
        metadata = [],
        scope,
        editing,
        member,
        candidates = [];
    let filters = { query: '', active: '' };
    const get = async (path, params) => (await app.api.get(base + path, { params, timeout: 10000 })).data.data;
    const text = (tag, value, css) => {
        const item = document.createElement(tag);
        item.textContent = value;
        if (css) item.className = css;
        return item;
    };
    const can = (action) => me?.permissions?.includes(`ACCESS_${action}`);
    const roleName = (code) => {
        const value = metadata.find((item) => item.code === code);
        return `${value ? (document.documentElement.lang === 'en' ? value.nameEn : value.nameVi) : code} · ${code}`;
    };
    const scopeModal = window.VietRouteUI.modal('scope-dialog', { busy: () => busy });
    const membersModal = window.VietRouteUI.modal('scope-members-dialog', { busy: () => busy });
    const memberModal = window.VietRouteUI.modal('scope-member-dialog', {
        busy: () => busy,
        onClose: () => {
            candidates = [];
            member = undefined;
            node('scope-member-form').reset();
        },
    });
    const roleSearch = window.VietRouteRolePicker.bind(
        node('scope-role-query'),
        node('scope-role-options'),
        node('scope-role-count'),
    );
    const pager = tables.pager('scopes', { busy: () => busy, onChange: (query) => perform(() => loadScopes(query)) });
    const memberPager = tables.pager('scope-members', {
        busy: () => busy,
        onChange: (query) => perform(() => loadMembers(query)),
    });
    const perform = async (action) => {
        if (busy) return;
        busy = true;
        section.setAttribute('aria-busy', 'true');
        const controls = ['scopes-controls', 'scope-form-controls', 'scope-members-controls', 'scope-member-controls'];
        controls.forEach((id) => (node(id).disabled = true));
        node('scope-status').textContent = labels.loading;
        const feedback = node('scope-dialog').open
            ? 'scope-form-feedback'
            : node('scope-member-dialog').open
              ? 'scope-member-feedback'
              : membersModal.isOpen
                ? 'scope-members-feedback'
                : 'scopes-feedback';
        app.feedback('', false, feedback);
        try {
            await action();
        } catch (error) {
            app.failure(error, feedback);
            if (error.response?.status === 409)
                node(feedback).append(document.createElement('br'), text('span', labels.conflict));
        } finally {
            busy = false;
            section.setAttribute('aria-busy', 'false');
            controls.forEach((id) => (node(id).disabled = false));
            node('scope-status').textContent = '';
        }
    };
    const editScope = (value) => {
        if (busy) return;
        editing = value;
        node('scope-form').reset();
        forms.clear(node('scope-form'));
        node('scope-code').value = value?.code || '';
        node('scope-code').readOnly = !!value;
        node('scope-name-vi').value = value?.nameVi || '';
        node('scope-name-en').value = value?.nameEn || '';
        node('scope-active').checked = value ? value.active : true;
        app.feedback('', false, 'scope-form-feedback');
        scopeModal.open();
    };
    const loadScopes = async (query = pager.query) => {
        const applied = Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== ''));
        const page = await tables.readPage((params) => get('/scopes', { ...params, ...applied }), query);
        const body = node('scopes-body');
        body.replaceChildren();
        for (const value of page.content) {
            const row = document.createElement('tr');
            row.append(
                text('td', value.code),
                text('td', document.documentElement.lang === 'en' ? value.nameEn : value.nameVi),
                text('td', value.active ? labels.on : labels.off),
            );
            const cell = text('td', '', 'table-actions'),
                buttons = text('div', '', 'row-actions');
            const edit = tables.actionButton(labels.edit + ': ' + value.code, 'edit', () => editScope(value), false);
            edit.disabled = !can('UPDATE');
            buttons.append(
                edit,
                tables.actionButton(
                    labels.title + ': ' + value.code,
                    'assign',
                    (trigger) => {
                        if (busy) return;
                        scope = value;
                        node('scope-members-body').replaceChildren();
                        node('scope-members-title').textContent = `${labels.title} · ${scope.code}`;
                        membersModal.open(trigger);
                        return perform(() => loadMembers({ page: 0, size: memberPager.query.size }));
                    },
                    false,
                ),
            );
            cell.append(buttons);
            row.append(cell);
            body.append(row);
            if (scope?.id === value.id) scope = value;
        }
        if (!page.content.length) empty(body, 4);
        pager.update(page);
        node('scope-create').hidden = !can('CREATE');
        node('scope-member-create').hidden = !can('ASSIGN');
    };
    const empty = (body, columns) => {
        const row = document.createElement('tr'),
            cell = text('td', labels.empty, 'muted');
        cell.colSpan = columns;
        row.append(cell);
        body.append(row);
    };
    const loadMembers = async (query = memberPager.query) => {
        if (!scope) return;
        const page = await tables.readPage((params) => get(`/scopes/${scope.id}/members`, params), query);
        const body = node('scope-members-body');
        body.replaceChildren();
        for (const value of page.content) {
            const row = document.createElement('tr');
            row.append(
                text('td', `${value.username} · #${value.userId}`),
                text('td', value.roles.map(roleName).join(', ') || '—'),
                text('td', value.active ? labels.on : labels.off),
            );
            const cell = text('td', '', 'table-actions');
            const edit = tables.actionButton(
                labels.assign + ': ' + value.username,
                'assign',
                (trigger) => perform(() => openMember(value, trigger)),
                false,
            );
            edit.disabled = !can('ASSIGN');
            cell.append(edit);
            row.append(cell);
            body.append(row);
        }
        if (!page.content.length) empty(body, 4);
        memberPager.update(page);
    };
    const renderRoleChoices = () => {
        const user = candidates.find((item) => String(item.id) === node('scope-user').value);
        const host = node('scope-role-options');
        host.querySelectorAll('label').forEach((label) => label.remove());
        for (const code of user?.roles || []) {
            if (code === 'ADMIN') continue;
            const label = text('label', '', 'account-role-option');
            label.dataset.roleChoice = code;
            const input = document.createElement('input');
            input.type = 'checkbox';
            input.value = code;
            input.name = 'roles';
            input.className = 'form-check-input';
            input.checked = !!member?.roles.includes(code);
            const active = metadata.find((value) => value.code === code)?.active;
            input.disabled = active === false && !input.checked;
            label.append(input, text('span', roleName(code) + (active === false ? ' · ' + labels.off : '')));
            host.append(label);
        }
        roleSearch.reset();
    };
    const searchUsers = async (username = node('scope-user-query').value.trim()) => {
        const page = await get('/users', { username, size: 20 });
        candidates = page.content.filter(
            (user) => user.id !== me.id && (user.active || user.id === member?.userId) && !user.roles.includes('ADMIN'),
        );
        const select = node('scope-user');
        select.replaceChildren();
        for (const user of candidates) {
            const option = text('option', `${user.displayName} · @${user.username} · #${user.id}`);
            option.value = user.id;
            select.append(option);
        }
        if (member) select.value = String(member.userId);
        renderRoleChoices();
    };
    const openMember = async (value, trigger = document.activeElement) => {
        if (!scope) return;
        member = value;
        node('scope-member-dialog-title').textContent =
            `${labels.assign} · ${scope.code}${value ? ' · @' + value.username : ''}`;
        node('scope-member-form').reset();
        forms.clear(node('scope-member-form'));
        node('scope-member-active').checked = value ? value.active : true;
        node('scope-user-search-fields').hidden = !!value;
        node('scope-user').disabled = !!value;
        app.feedback('', false, 'scope-member-feedback');
        if (value) await searchUsers(value.username);
        else {
            candidates = [];
            node('scope-user').replaceChildren();
            renderRoleChoices();
        }
        memberModal.open(trigger);
    };
    node('scope-create').addEventListener('click', () => editScope());
    node('scope-member-create').addEventListener('click', () => {
        if (!busy) void openMember();
    });
    node('scope-user-search').addEventListener('click', () => {
        if (forms.validate(node('scope-user-search-fields'))) void perform(() => searchUsers());
    });
    node('scope-user').addEventListener('change', renderRoleChoices);
    node('scopes-refresh').addEventListener('click', () =>
        perform(async () => {
            await loadScopes();
            if (scope) await loadMembers();
        }),
    );
    node('scopes-search').addEventListener('submit', (event) => {
        event.preventDefault();
        if (!forms.validate(event.target)) return;
        filters = { query: node('scope-query').value.trim(), active: node('scope-active-filter').value };
        void perform(() => loadScopes({ page: 0, size: pager.query.size }));
    });
    node('scope-form').addEventListener('submit', (event) => {
        event.preventDefault();
        if (!forms.validate(event.target)) return;
        const body = {
            code: node('scope-code').value.trim(),
            nameVi: node('scope-name-vi').value.trim(),
            nameEn: node('scope-name-en').value.trim(),
            active: node('scope-active').checked,
            version: editing?.version || 0,
        };
        void perform(async () => {
            await app.mutate(base + '/scopes' + (editing ? '/' + editing.id : ''), body, editing ? 'put' : 'post');
            node('scope-dialog').close();
            await loadScopes();
        });
    });
    node('scope-member-form').addEventListener('submit', (event) => {
        event.preventDefault();
        if (!forms.validate(event.target) || !scope) return;
        const roles = Array.from(node('scope-role-options').querySelectorAll('input:checked'), (input) => input.value);
        if (node('scope-member-active').checked && !roles.length) {
            app.feedback(labels.required, true, 'scope-member-feedback');
            return;
        }
        const id = member?.userId || Number(node('scope-user').value);
        if (!id) return;
        void perform(async () => {
            await app.mutate(
                `${base}/scopes/${scope.id}/members/${id}`,
                { roles, active: node('scope-member-active').checked, version: member?.version || 0 },
                'put',
            );
            node('scope-member-dialog').close();
            await loadMembers();
        });
    });
    new MutationObserver(() => {
        if (section.hidden || loaded || busy) return;
        void perform(async () => {
            [me, metadata] = await Promise.all([
                (async () => (await app.api.get('/api/v1/auth/me')).data.data)(),
                get('/registry/roles'),
            ]);
            await loadScopes();
            loaded = true;
        });
    }).observe(section, { attributes: true, attributeFilter: ['hidden'] });
})();
