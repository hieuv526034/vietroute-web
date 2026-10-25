/* CRUD staff: chỉ gửi field cho phép, giữ draft/version khi lỗi và dùng capability target + backend scope. */
(() => {
    'use strict';
    const root = document.getElementById('fleet-workspace');
    if (!root) return;
    const node = (id) => document.getElementById(id);
    const { api, mutate, feedback, failure } = window.VietRoute;
    const tables = window.VietRouteTable;
    const labels = root.dataset;
    const kind = labels.kind;
    const path = `/api/v1/admin/${kind}`;
    const form = node('fleet-form');
    const search = node('fleet-search');
    let busy = false;
    let selected = null;
    let capability = { targets: [] };
    const dialog = window.VietRouteUI.modal('fleet-dialog', { busy: () => busy });
    const can = (method, suffix = '') => capability.targets.includes(`${method} ${path}${suffix}`);
    const pager = tables.pager('fleet', { busy: () => busy, onChange: (query) => perform(() => load(query)) });
    const text = (tag, value) => {
        const element = document.createElement(tag);
        element.textContent = String(value ?? '');
        return element;
    };
    const option = (value, label) => {
        const item = text('option', label);
        item.value = String(value);
        return item;
    };
    const empty = (message) => {
        const row = text('tr', '');
        const cell = text('td', message);
        cell.colSpan = kind === 'carriers' ? 6 : 7;
        row.append(cell);
        node('fleet-body').replaceChildren(row);
    };
    const syncActions = () => {
        node('fleet-create').hidden = !can('POST');
        node('fleet-create').disabled = busy || !can('POST');
    };
    const perform = async (action) => {
        if (busy) return;
        busy = true;
        root.setAttribute('aria-busy', 'true');
        node('fleet-controls').disabled = true;
        node('fleet-dialog-controls').disabled = true;
        node('fleet-status').textContent = labels.loading;
        node('fleet-conflict').hidden = true;
        const target = dialog.isOpen ? 'fleet-form-feedback' : 'fleet-feedback';
        feedback('', false, target);
        try {
            await action();
        } catch (error) {
            if ([401, 403].includes(error.response?.status)) empty(labels.noRead);
            failure(error, dialog.isOpen ? 'fleet-form-feedback' : 'fleet-feedback');
            node('fleet-conflict').hidden = error.response?.status !== 409;
        } finally {
            busy = false;
            root.setAttribute('aria-busy', 'false');
            node('fleet-controls').disabled = false;
            node('fleet-dialog-controls').disabled = false;
            const modal = node('fleet-dialog');
            if (dialog.isOpen && (!modal.contains(document.activeElement) || document.activeElement === modal))
                form.querySelector('input:not(:disabled),select:not(:disabled)')?.focus();
            node('fleet-status').textContent = '';
            syncActions();
        }
    };
    const refreshCapability = async () => {
        capability = await window.VietRouteAccess.load(labels.feature);
        syncActions();
    };
    const render = (page) => {
        node('fleet-body').replaceChildren();
        for (const item of page.content) {
            const row = document.createElement('tr');
            const values =
                kind === 'carriers'
                    ? [item.code, item.name, item.phone, item.scopeId]
                    : kind === 'vehicles'
                      ? [item.code, item.carrierName, item.plateNumber, item.vehicleTypeName, item.capacity]
                      : [item.code, item.carrierName, item.name, item.phone, item.userId ?? labels.unlinked];
            values.push(item.active ? labels.active : labels.inactive);
            for (const value of values) row.append(text('td', value));
            const actions = text('td', '');
            actions.className = 'table-actions';
            const group = text('div', '');
            group.className = 'row-actions';
            if (item.canUpdate && can('GET', '/{id}') && can('PUT', '/{id}'))
                group.append(
                    tables.actionButton(`${labels.edit}: ${item.code}`, 'edit', (trigger) =>
                        perform(() => open(item, trigger)),
                    ),
                );
            else group.textContent = '—';
            actions.append(group);
            row.append(actions);
            node('fleet-body').append(row);
        }
        if (!page.content.length) empty(labels.empty);
        pager.update(page);
    };
    const load = async (query = pager.query) => {
        if (!can('GET')) {
            empty(labels.noRead);
            return;
        }
        const params = {
            ...query,
            query: search.elements.query.value.trim(),
            ...(search.elements.active.value ? { active: search.elements.active.value } : {}),
        };
        const page = await tables.readPage(
            async (paging) => (await api.get(path, { params: { ...params, ...paging }, timeout: 10000 })).data.data,
            query,
        );
        render(page);
    };
    // Picker tìm tại DB, giữ lựa chọn đang có dù nằm ngoài 20 kết quả hiện tại; không tải toàn bộ danh bạ.
    const carrierChoices = async () => {
        const select = form.elements.carrierId;
        const current = select.selectedOptions[0];
        const page = (
            await api.get(`${path}/carriers`, {
                params: { query: node('fleet-carrier-query').value.trim(), size: 20 },
                timeout: 10000,
            })
        ).data.data;
        const options = page.content.map((item) => option(item.id, `${item.code} · ${item.name}`));
        if (current?.value && !page.content.some((item) => String(item.id) === current.value))
            options.unshift(option(current.value, current.textContent));
        select.replaceChildren(option('', labels.choose), ...options);
        if (current?.value) select.value = current.value;
        node('fleet-carrier-count').textContent =
            `${Math.min(page.totalElements, 20)} / ${page.totalElements} · ${labels.pickCount}`;
    };
    const vehicleTypes = async () => {
        // Tìm tại DB thay vì giới hạn âm thầm 100 loại; giữ lựa chọn hiện có ngoài trang kết quả.
        const select = form.elements.vehicleTypeId;
        const current = select.selectedOptions[0];
        const page = (
            await api.get('/api/v1/catalogs/VEHICLE_TYPE', {
                params: { size: 20, query: node('fleet-type-query').value.trim() },
                timeout: 10000,
            })
        ).data.data;
        const options = page.content.map((item) => option(item.id, item.name));
        if (current?.value && !page.content.some((item) => String(item.id) === current.value))
            options.unshift(option(current.value, current.textContent));
        select.replaceChildren(option('', '—'), ...options);
        if (current?.value) select.value = current.value;
        node('fleet-type-count').textContent =
            `${Math.min(page.totalElements, 20)} / ${page.totalElements} · ${labels.pickCount}`;
    };
    const open = async (item, trigger) => {
        const current = item ? (await api.get(`${path}/${item.id}`, { timeout: 10000 })).data.data : null;
        selected = current;
        form.reset();
        node('fleet-form-feedback').hidden = true;
        node('fleet-conflict').hidden = true;
        for (const name of ['code', 'name', 'phone', 'description', 'plateNumber', 'capacity', 'userId'])
            if (form.elements[name]) form.elements[name].value = current?.[name] ?? '';
        form.elements.code.disabled = Boolean(current);
        form.elements.active.checked = current?.active ?? true;
        if (form.elements.mediaId)
            form.elements.mediaId.value = current?.[kind === 'carriers' ? 'logoMediaId' : 'photoMediaId'] ?? '';
        if (kind !== 'carriers') {
            form.elements.carrierId.disabled = Boolean(current);
            node('fleet-carrier-picker').hidden = Boolean(current);
            node('fleet-carrier-count').textContent = '';
            node('fleet-carrier-query').value = '';
            form.elements.carrierId.replaceChildren(
                current ? option(current.carrierId, current.carrierName) : option('', labels.choose),
            );
            if (!current) await carrierChoices();
        }
        if (kind === 'vehicles') {
            node('fleet-type-query').value = '';
            form.elements.vehicleTypeId.replaceChildren(
                current ? option(current.vehicleTypeId, current.vehicleTypeName) : option('', '—'),
            );
            await vehicleTypes();
        }
        node('fleet-editor-title').textContent = current ? labels.edit : labels.create;
        node('fleet-save').textContent = current ? labels.save : labels.create;
        dialog.open(trigger);
        // Native dialog mở khi fieldset còn khóa; đặt focus ở frame sau khi perform đã mở lại controls.
        requestAnimationFrame(() => form.querySelector('input:not(:disabled),select:not(:disabled)')?.focus());
    };
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !window.VietRouteForms.validate(form)) return;
        void perform(async () => {
            const data = form.elements;
            const body = { active: data.active.checked };
            if (kind !== 'vehicles')
                Object.assign(body, { name: data.name.value.trim(), phone: data.phone.value.trim() });
            if (kind === 'carriers')
                Object.assign(body, {
                    description: data.description.value.trim(),
                    logoMediaId: data.mediaId.value ? Number(data.mediaId.value) : null,
                });
            if (kind === 'vehicles')
                Object.assign(body, {
                    plateNumber: data.plateNumber.value.trim().toUpperCase(),
                    vehicleTypeId: Number(data.vehicleTypeId.value),
                    capacity: Number(data.capacity.value),
                    photoMediaId: data.mediaId.value ? Number(data.mediaId.value) : null,
                });
            if (kind === 'drivers') body.userId = data.userId.value ? Number(data.userId.value) : null;
            if (selected) body.version = selected.version;
            else {
                body.code = data.code.value.trim();
                if (kind !== 'carriers') body.carrierId = Number(data.carrierId.value);
            }
            const result = await mutate(`${path}${selected ? `/${selected.id}` : ''}`, body, selected ? 'put' : 'post');
            node('fleet-dialog').close();
            selected = null;
            form.reset();
            feedback(result.data.message, true, 'fleet-feedback');
            await load({ ...pager.query, page: 0 });
        });
    });
    node('fleet-create').addEventListener('click', (event) => {
        if (can('POST')) void perform(() => open(null, event.currentTarget));
    });
    node('fleet-refresh').addEventListener('click', () =>
        perform(async () => {
            await refreshCapability();
            await load();
        }),
    );
    search.addEventListener('submit', (event) => {
        event.preventDefault();
        if (window.VietRouteForms.validate(search)) void perform(() => load({ ...pager.query, page: 0 }));
    });
    if (kind !== 'carriers') {
        node('fleet-carrier-search').addEventListener('click', () => perform(carrierChoices));
        node('fleet-carrier-query').addEventListener('keydown', (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
                void perform(carrierChoices);
            }
        });
    }
    if (kind === 'vehicles') {
        node('fleet-type-search').addEventListener('click', () => perform(vehicleTypes));
        node('fleet-type-query').addEventListener('keydown', (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
                void perform(vehicleTypes);
            }
        });
    }
    void perform(async () => {
        await refreshCapability();
        await load();
    });
})();
