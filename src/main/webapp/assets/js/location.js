/* Danh mục địa lý dùng API phân trang; modal giữ bản nháp/version khi lỗi, không tự replay thao tác ghi. */
(() => {
    'use strict';
    const root = document.getElementById('location-workspace');
    if (!root) return;
    const node = (id) => document.getElementById(id);
    const { api, mutate, feedback, failure } = window.VietRoute;
    const tables = window.VietRouteTable;
    const labels = root.dataset;
    const form = node('location-form');
    const kinds = ['locations', 'provinces'];
    let busy = false;
    let selected = null;
    let kind = 'locations';
    let capability = { actions: [], targets: [] };
    const editor = window.VietRouteUI.modal('location-dialog', { busy: () => busy });
    const can = (method, resource, suffix = '') =>
        capability.targets.includes(`${method} /api/v1/admin/${resource}${suffix}`);
    const pagers = Object.fromEntries(
        kinds.map((resource) => [
            resource,
            tables.pager(resource, {
                busy: () => busy,
                onChange: (query) => perform(() => load(resource, query)),
            }),
        ]),
    );
    const text = (tag, value) => {
        const element = document.createElement(tag);
        element.textContent = String(value ?? '');
        return element;
    };
    const option = (value, label) => {
        const element = text('option', label);
        element.value = String(value);
        return element;
    };
    const empty = (resource, label) => {
        const cell = text('td', label);
        cell.colSpan = resource === 'locations' ? 6 : 5;
        const row = document.createElement('tr');
        row.append(cell);
        node(`${resource}-body`).replaceChildren(row);
    };
    const syncActions = () => {
        root.querySelectorAll('[data-create]').forEach((button) => {
            button.hidden = !can('POST', button.dataset.create);
            button.disabled = busy || button.hidden;
        });
    };

    // Một chỗ quản lý busy: tab/modal không đổi khi đang ghi; lỗi 401/403 xóa dữ liệu bảng, giữ draft modal.
    const perform = async (action) => {
        if (busy) return;
        busy = true;
        root.setAttribute('aria-busy', 'true');
        node('location-controls').disabled = true;
        node('location-dialog-controls').disabled = true;
        node('location-status').textContent = labels.loading;
        const target = editor.isOpen ? 'location-form-feedback' : 'location-feedback';
        feedback('', false, target);
        node('location-conflict').hidden = true;
        try {
            await action();
        } catch (error) {
            if ([401, 403].includes(error.response?.status))
                kinds.forEach((resource) => empty(resource, labels.noRead));
            failure(error, editor.isOpen ? 'location-form-feedback' : 'location-feedback');
            node('location-conflict').hidden = !editor.isOpen || error.response?.status !== 409;
        } finally {
            busy = false;
            root.setAttribute('aria-busy', 'false');
            node('location-controls').disabled = false;
            node('location-dialog-controls').disabled = false;
            node('location-status').textContent = '';
            syncActions();
        }
    };

    const load = async (resource, query = pagers[resource].query) => {
        if (!can('GET', resource)) {
            empty(resource, labels.noRead);
            return;
        }
        const search = node(`${resource}-search`).elements;
        const params = {
            ...query,
            query: search.query.value.trim(),
            ...(search.active.value ? { active: search.active.value } : {}),
        };
        const page = await tables.readPage(
            async (paging) =>
                (await api.get(`/api/v1/admin/${resource}`, { params: { ...params, ...paging }, timeout: 10000 })).data
                    .data,
            query,
        );
        const body = node(`${resource}-body`);
        body.replaceChildren();
        for (const item of page.content) {
            const row = document.createElement('tr');
            const values =
                resource === 'provinces'
                    ? [item.code, item.name, item.sortOrder, item.active ? labels.active : labels.inactive]
                    : [
                          item.code,
                          item.name,
                          `${item.provinceNameVi}${item.provinceActive ? '' : ` · ${labels.parentOff}`}`,
                          item.address,
                          item.active ? labels.active : labels.inactive,
                      ];
            // Chọn tên tỉnh theo locale như tên địa điểm, không dùng HTML từ dữ liệu API.
            if (resource === 'locations' && document.documentElement.lang === 'en')
                values[2] = `${item.provinceNameEn}${item.provinceActive ? '' : ` · ${labels.parentOff}`}`;
            values.forEach((value) => row.append(text('td', value)));
            const actions = document.createElement('td');
            actions.className = 'table-actions';
            if (can('GET', resource, '/{id}') && can('PUT', resource, '/{id}')) {
                const group = document.createElement('div');
                group.className = 'row-actions';
                group.append(
                    tables.actionButton(`${labels.edit}: ${item.code}`, 'edit', (trigger) =>
                        perform(() => open(resource, item.id, trigger)),
                    ),
                );
                actions.append(group);
            } else actions.textContent = '—';
            row.append(actions);
            body.append(row);
        }
        if (!page.content.length) empty(resource, labels.empty);
        pagers[resource].update(page);
    };

    const searchProvinces = async () => {
        const select = node('place-province');
        const previous = select.selectedOptions[0];
        const response = await api.get('/api/v1/provinces', {
            params: { query: node('place-province-query').value.trim(), size: 20 },
            timeout: 10000,
        });
        select.replaceChildren(option('', labels.choose));
        for (const item of response.data.data.content) select.append(option(item.id, `${item.name} · ${item.code}`));
        // Tìm tiếp không làm mất tỉnh đã chọn ngoài trang hiện tại.
        if (previous?.value && !Array.from(select.options).some((item) => item.value === previous.value))
            select.append(previous.cloneNode(true));
        if (previous?.value) select.value = previous.value;
    };

    const open = async (resource, id, trigger) => {
        const current = id ? (await api.get(`/api/v1/admin/${resource}/${id}`, { timeout: 10000 })).data.data : null;
        kind = resource;
        selected = current;
        form.reset();
        node('place-province-query').value = '';
        feedback('', false, 'location-form-feedback');
        node('place-details').hidden = resource === 'provinces';
        node('place-details').disabled = resource === 'provinces';
        form.elements.code.disabled = Boolean(current);
        for (const field of ['code', 'nameVi', 'nameEn', 'address', 'latitude', 'longitude'])
            form.elements[field].value = current?.[field] ?? '';
        form.elements.sortOrder.value = current?.sortOrder ?? 0;
        form.elements.active.checked = current?.active ?? true;
        for (const field of ['address', 'latitude', 'longitude'])
            form.elements[field].disabled = resource === 'provinces';
        node('place-province').disabled = resource === 'provinces' || Boolean(current);
        node('place-province-query').disabled = resource === 'provinces' || Boolean(current);
        node('place-province-search').disabled = resource === 'provinces' || Boolean(current);
        node('place-province').replaceChildren(option('', labels.choose));
        node('location-editor-note').textContent = resource === 'provinces' ? labels.provinceNote : labels.placeNote;
        node('place-province-query').parentElement.parentElement.hidden = Boolean(current);
        node('place-picker-note').hidden = Boolean(current);
        if (resource === 'locations') {
            if (current) {
                const name = document.documentElement.lang === 'en' ? current.provinceNameEn : current.provinceNameVi;
                node('place-province').append(option(current.provinceId, name));
                node('place-province').value = String(current.provinceId);
            } else await searchProvinces();
        }
        node('location-editor-kind').textContent = labels[resource];
        node('location-editor-title').textContent = `${current ? labels.edit : labels.create} · ${labels[resource]}`;
        node('location-save').textContent = current ? labels.save : labels.create;
        editor.open(trigger);
        requestAnimationFrame(() => (current ? form.elements.nameVi : form.elements.code).focus());
    };

    form.addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !window.VietRouteForms.validate(form)) return;
        const current = selected;
        const resource = kind;
        if (!(current ? can('PUT', resource, '/{id}') : can('POST', resource))) return;
        void perform(async () => {
            const body = {
                nameVi: form.elements.nameVi.value.trim(),
                nameEn: form.elements.nameEn.value.trim(),
                sortOrder: Number(form.elements.sortOrder.value),
                active: form.elements.active.checked,
            };
            if (resource === 'locations')
                Object.assign(body, {
                    address: form.elements.address.value.trim(),
                    latitude: Number(form.elements.latitude.value),
                    longitude: Number(form.elements.longitude.value),
                });
            if (current) body.version = current.version;
            else {
                body.code = form.elements.code.value.trim();
                if (resource === 'locations') body.provinceId = Number(form.elements.provinceId.value);
            }
            const response = await mutate(
                `/api/v1/admin/${resource}${current ? `/${current.id}` : ''}`,
                body,
                current ? 'put' : 'post',
            );
            node('location-dialog').close();
            selected = null;
            form.reset();
            feedback(response.data.message, true, 'location-feedback');
            await load(resource, { ...pagers[resource].query, page: 0 });
        });
    });
    node('place-province-search').addEventListener('click', () => perform(searchProvinces));
    node('place-province-query').addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
            event.preventDefault();
            void perform(searchProvinces);
        }
    });
    root.querySelectorAll('[data-create]').forEach((button) =>
        button.addEventListener('click', () => perform(() => open(button.dataset.create, null, button))),
    );
    root.querySelectorAll('[data-refresh]').forEach((button) =>
        button.addEventListener('click', () =>
            perform(async () => {
                capability = await window.VietRouteAccess.load('LOCATION');
                await load(button.dataset.refresh);
            }),
        ),
    );
    kinds.forEach((resource) =>
        node(`${resource}-search`).addEventListener('submit', (event) => {
            event.preventDefault();
            if (window.VietRouteForms.validate(event.currentTarget))
                void perform(() => load(resource, { ...pagers[resource].query, page: 0 }));
        }),
    );
    void perform(async () => {
        capability = await window.VietRouteAccess.load('LOCATION');
        await Promise.all(kinds.map((resource) => load(resource)));
    });
})();
