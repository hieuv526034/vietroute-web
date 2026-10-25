/* Mẫu tuyến dùng capability + scope backend, giữ draft/version khi lỗi; không tính tồn chỗ ở trình duyệt. */
(() => {
    'use strict';
    const root = document.getElementById('route-workspace');
    if (!root) return;
    const node = (id) => document.getElementById(id);
    const { api, mutate, feedback, failure } = window.VietRoute;
    const tables = window.VietRouteTable,
        labels = root.dataset,
        path = '/api/v1/admin/routes';
    const form = node('route-form'),
        search = node('route-search');
    let busy = false,
        selected = null,
        readOnly = false,
        capability = { targets: [] };
    const modal = window.VietRouteUI.modal('route-dialog', { busy: () => busy });
    const tabs = window.VietRouteUI.tabs(node('route-tabs'));
    const pager = tables.pager('route', { busy: () => busy, onChange: (query) => perform(() => load(query)) });
    const locations = new Map();
    const english = document.documentElement.lang.startsWith('en');
    const can = (method, suffix = '') => capability.targets.includes(`${method} ${path}${suffix}`);
    const text = (tag, value) => {
        const item = document.createElement(tag);
        item.textContent = String(value ?? '');
        return item;
    };
    const option = (value, label) => {
        const item = text('option', label);
        item.value = String(value);
        return item;
    };
    // Lookup công khai đã dịch trường name; điểm snapshot vẫn giữ cặp tên vi/en.
    const placeLabel = (item) =>
        `${item.code ? `${item.code} · ` : ''}${item.name ?? (english ? item.nameEn : item.nameVi) ?? ''}`;
    const empty = (message) => {
        const row = text('tr', ''),
            cell = text('td', message);
        cell.colSpan = 6;
        row.append(cell);
        node('route-body').replaceChildren(row);
    };
    const sync = () => {
        node('route-create').hidden = !can('POST');
        node('route-create').disabled = busy || !can('POST');
        node('route-dialog-controls').disabled = busy;
        form.querySelectorAll('input,textarea,select').forEach(
            (input) =>
                (input.disabled = busy || readOnly || Boolean(selected && ['code', 'carrierId'].includes(input.name))),
        );
        node('route-save').hidden = readOnly;
        node('route-add-stop').hidden = readOnly;
        node('route-carrier-picker').hidden = Boolean(selected) || readOnly;
        node('route-location-picker').hidden = readOnly;
    };
    const perform = async (action) => {
        if (busy) return;
        busy = true;
        root.setAttribute('aria-busy', 'true');
        node('route-controls').disabled = true;
        node('route-status').textContent = labels.loading;
        node('route-conflict').hidden = true;
        sync();
        feedback('', false, modal.isOpen ? 'route-form-feedback' : 'route-feedback');
        try {
            await action();
        } catch (error) {
            if ([401, 403].includes(error.response?.status)) empty(labels.noRead);
            failure(error, modal.isOpen ? 'route-form-feedback' : 'route-feedback');
            node('route-conflict').hidden = error.response?.status !== 409;
        } finally {
            busy = false;
            root.setAttribute('aria-busy', 'false');
            node('route-controls').disabled = false;
            node('route-status').textContent = '';
            sync();
            if (modal.isOpen && !node('route-dialog').contains(document.activeElement))
                node('route-dialog-title').focus();
        }
    };
    const refresh = async () => {
        capability = await window.VietRouteAccess.load('ROUTE');
        sync();
    };
    const load = async (query = pager.query) => {
        if (!can('GET')) {
            empty(labels.noRead);
            return;
        }
        const params = {
            query: search.elements.query.value.trim(),
            ...(search.elements.active.value ? { active: search.elements.active.value } : {}),
        };
        const page = await tables.readPage(
            async (paging) => (await api.get(path, { params: { ...params, ...paging }, timeout: 10000 })).data.data,
            query,
        );
        node('route-body').replaceChildren();
        for (const item of page.content) {
            const row = text('tr', '');
            for (const value of [
                item.code,
                english ? item.nameEn : item.nameVi,
                item.carrierName,
                item.stopCount,
                item.active ? labels.active : labels.inactive,
            ])
                row.append(text('td', value));
            const cell = text('td', '');
            cell.className = 'table-actions';
            const actions = text('div', '');
            actions.className = 'row-actions';
            if (can('GET', '/{id}'))
                actions.append(
                    tables.actionButton(`${labels.view}: ${item.code}`, 'view', (trigger) =>
                        perform(() => open(item, true, trigger)),
                    ),
                );
            if (item.canUpdate && can('GET', '/{id}') && can('PUT', '/{id}'))
                actions.append(
                    tables.actionButton(`${labels.edit}: ${item.code}`, 'edit', (trigger) =>
                        perform(() => open(item, false, trigger)),
                    ),
                );
            cell.append(actions);
            row.append(cell);
            node('route-body').append(row);
        }
        if (!page.content.length) empty(labels.empty);
        pager.update(page);
    };
    const updateLocations = () => {
        for (const select of node('route-stop-list').querySelectorAll('select')) {
            const value = select.value;
            select.replaceChildren(
                option('', labels.choose),
                ...Array.from(locations.values()).map((item) => option(item.id, placeLabel(item))),
            );
            select.value = value;
        }
    };
    const findLocations = async () => {
        const page = (
            await api.get('/api/v1/locations', {
                params: { query: node('route-location-query').value.trim(), size: 20 },
                timeout: 10000,
            })
        ).data.data;
        const keep = new Set(
            Array.from(node('route-stop-list').querySelectorAll('select')).map((select) => Number(select.value)),
        );
        for (const id of locations.keys()) if (!keep.has(id)) locations.delete(id);
        for (const item of page.content) locations.set(item.id, item);
        updateLocations();
        node('route-location-count').textContent = `${page.content.length} / ${page.totalElements}`;
    };
    const findCarriers = async () => {
        const page = (
            await api.get(`${path}/carriers`, {
                params: { query: node('route-carrier-query').value.trim(), size: 20 },
                timeout: 10000,
            })
        ).data.data;
        const select = form.elements.carrierId,
            current = select.selectedOptions[0],
            options = page.content.map((item) => option(item.id, `${item.code} · ${item.name}`));
        if (current?.value && !page.content.some((item) => String(item.id) === current.value))
            options.unshift(option(current.value, current.textContent));
        select.replaceChildren(option('', labels.choose), ...options);
        if (current?.value) select.value = current.value;
        node('route-carrier-count').textContent = `${page.content.length} / ${page.totalElements}`;
    };
    const renumber = () =>
        Array.from(node('route-stop-list').children).forEach((row, index) => {
            row.querySelector('h3').textContent = `${labels.stop} ${index + 1}`;
            row.setAttribute('role', 'group');
            row.setAttribute('aria-label', `${labels.stop} ${index + 1}`);
        });
    const addStop = (item = {}) => {
        const row = text('section', '');
        row.className = 'route-stop-row';
        row.append(text('h3', ''));
        const grid = text('div', '');
        grid.className = 'dialog-form-grid';
        const field = (name, label, type, value) => {
            const wrap = text('label', '');
            wrap.append(text('span', label));
            const input = document.createElement(type === 'select' ? 'select' : 'input');
            input.name = name;
            input.className = type === 'select' ? 'form-select' : 'form-control';
            if (type !== 'select') input.type = type;
            input.required = true;
            if (type === 'number') {
                input.min = '0';
                input.max = name === 'cumulativeFare' ? '9999999999.99' : '43200';
                input.step = name === 'cumulativeFare' ? '0.01' : '1';
            }
            if (type === 'select')
                input.replaceChildren(
                    option('', labels.choose),
                    ...Array.from(locations.values()).map((place) => option(place.id, placeLabel(place))),
                );
            input.value = String(value ?? '');
            wrap.append(input);
            grid.append(wrap);
        };
        field('locationId', labels.location, 'select', item.locationId);
        field('arrivalOffsetMinutes', labels.arrival, 'number', item.arrivalOffsetMinutes ?? 0);
        field('departureOffsetMinutes', labels.departure, 'number', item.departureOffsetMinutes ?? 0);
        field('cumulativeFare', labels.fare, 'number', item.cumulativeFare ?? 0);
        for (const [name, label] of [
            ['pickupAllowed', labels.pickup],
            ['dropoffAllowed', labels.dropoff],
        ]) {
            const wrap = text('label', '');
            wrap.className = 'check-label';
            const input = document.createElement('input');
            input.type = 'checkbox';
            input.name = name;
            input.checked = item[name] ?? true;
            wrap.append(input, text('span', label));
            grid.append(wrap);
        }
        row.append(grid);
        const actions = text('div', '');
        actions.className = 'toolbar-actions';
        for (const [action, label] of [
            ['up', labels.up],
            ['down', labels.down],
            ['remove', labels.remove],
        ]) {
            const button = text('button', label);
            button.type = 'button';
            button.className = 'button button-outline';
            button.hidden = readOnly;
            button.addEventListener('click', () => {
                if (busy || readOnly) return;
                if (action === 'up' && row.previousElementSibling) row.previousElementSibling.before(row);
                if (action === 'down' && row.nextElementSibling) row.nextElementSibling.after(row);
                if (action === 'remove') row.remove();
                renumber();
            });
            actions.append(button);
        }
        row.append(actions);
        node('route-stop-list').append(row);
        renumber();
    };
    const open = async (item, view, trigger) => {
        readOnly = view;
        selected = item ? (await api.get(`${path}/${item.id}`, { timeout: 10000 })).data.data : null;
        form.reset();
        locations.clear();
        node('route-stop-list').replaceChildren();
        if (selected) {
            form.elements.carrierId.replaceChildren(option(selected.carrierId, selected.carrierName));
            for (const key of ['code', 'nameVi', 'nameEn', 'description']) form.elements[key].value = selected[key];
            form.elements.active.checked = selected.active;
            for (const stop of selected.stops)
                locations.set(stop.locationId, { id: stop.locationId, nameVi: stop.nameVi, nameEn: stop.nameEn });
            for (const stop of selected.stops) addStop(stop);
        } else {
            await findCarriers();
            await findLocations();
            addStop();
            addStop();
        }
        feedback('', false, 'route-form-feedback');
        node('route-conflict').hidden = true;
        modal.open(trigger);
        requestAnimationFrame(() => {
            tabs.select('route-info');
            sync();
            form.elements.nameVi.focus();
        });
    };
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || readOnly) return;
        const invalid = form.querySelector(':invalid');
        if (invalid) {
            const panel = invalid.closest('[role="tabpanel"]');
            if (panel) tabs.select(panel.id);
        }
        if (!window.VietRouteForms.validate(form)) return;
        void perform(async () => {
            const data = form.elements;
            const stops = Array.from(node('route-stop-list').children).map((row) => {
                const get = (name) => row.querySelector(`[name="${name}"]`);
                return {
                    locationId: Number(get('locationId').value),
                    arrivalOffsetMinutes: Number(get('arrivalOffsetMinutes').value),
                    departureOffsetMinutes: Number(get('departureOffsetMinutes').value),
                    cumulativeFare: get('cumulativeFare').value,
                    pickupAllowed: get('pickupAllowed').checked,
                    dropoffAllowed: get('dropoffAllowed').checked,
                };
            });
            const body = {
                carrierId: Number(data.carrierId.value),
                code: data.code.value.trim(),
                nameVi: data.nameVi.value.trim(),
                nameEn: data.nameEn.value.trim(),
                description: data.description.value.trim(),
                active: data.active.checked,
                stops,
                ...(selected ? { version: selected.version } : {}),
            };
            const result = await mutate(`${path}${selected ? `/${selected.id}` : ''}`, body, selected ? 'put' : 'post');
            node('route-dialog').close();
            selected = null;
            feedback(result.data.message, true, 'route-feedback');
            await load({ ...pager.query, page: 0 });
        });
    });
    node('route-create').addEventListener('click', (event) => perform(() => open(null, false, event.currentTarget)));
    node('route-refresh').addEventListener('click', () =>
        perform(async () => {
            await refresh();
            await load();
        }),
    );
    search.addEventListener('submit', (event) => {
        event.preventDefault();
        if (window.VietRouteForms.validate(search)) void perform(() => load({ ...pager.query, page: 0 }));
    });
    node('route-add-stop').addEventListener('click', () => {
        if (!busy && !readOnly && node('route-stop-list').children.length < 32) {
            addStop();
            sync();
        }
    });
    node('route-location-search').addEventListener('click', () => perform(findLocations));
    node('route-carrier-search').addEventListener('click', () => perform(findCarriers));
    for (const id of ['route-location-query', 'route-carrier-query'])
        node(id).addEventListener('keydown', (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
                void perform(id === 'route-location-query' ? findLocations : findCarriers);
            }
        });
    void perform(async () => {
        await refresh();
        await load();
    });
})();
