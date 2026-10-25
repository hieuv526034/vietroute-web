/* Điều hành chuyến: capability/scoped flags chỉ hiển thị; backend kiểm lại lịch, version và capacity. */
(() => {
    'use strict';
    const root = document.getElementById('trip-workspace');
    if (!root) return;
    const node = (id) => document.getElementById(id),
        labels = root.dataset,
        { api, mutate, feedback, failure } = window.VietRoute,
        tables = window.VietRouteTable;
    const path = '/api/v1/admin/trips',
        form = node('trip-form'),
        search = node('trip-search'),
        english = document.documentElement.lang.startsWith('en');
    let busy = false,
        selected = null,
        readOnly = false,
        capability = { targets: [] };
    const modal = window.VietRouteUI.modal('trip-dialog', { busy: () => busy }),
        tabs = window.VietRouteUI.tabs(node('trip-tabs'));
    const tracking = window.VietRouteTracking.attach(node('trip-tracking'));
    const pager = tables.pager('trip', { busy: () => busy, onChange: (query) => perform(() => load(query)) });
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
    const state = (value) =>
        labels[value.toLowerCase().replace(/_([a-z])/g, (_, letter) => letter.toUpperCase())] || value;
    const date = (value) =>
        value
            ? new Intl.DateTimeFormat(english ? 'en-GB' : 'vi-VN', {
                  dateStyle: 'short',
                  timeStyle: 'short',
                  timeZone: 'Asia/Ho_Chi_Minh',
              }).format(new Date(value))
            : '—';
    const inputDate = (value) => new Date(new Date(value).getTime() + 7 * 60 * 60 * 1000).toISOString().slice(0, 16);
    const pickers = Array.from(form.querySelectorAll('.trip-picker')).map((element) => ({
        element,
        select: form.elements[element.dataset.field],
        kind: element.dataset.kind,
        query: element.querySelector('input'),
        page: 0,
        hasNext: false,
    }));
    const empty = (message) => {
        const row = text('tr', ''),
            cell = text('td', message);
        cell.colSpan = 8;
        row.append(cell);
        node('trip-body').replaceChildren(row);
    };
    const editable = () => !readOnly && (!selected || ['DRAFT', 'OPEN'].includes(selected.status));
    const noticeAllowed = () =>
        !readOnly && selected && ['OPEN', 'IN_PROGRESS'].includes(selected.status) && can('PUT', '/{id}/notice');
    const sync = () => {
        node('trip-create').hidden = !can('POST');
        node('trip-create').disabled = busy || !can('POST');
        node('trip-dialog-controls').disabled = busy;
        form.querySelectorAll('input,select,textarea').forEach((input) => {
            input.disabled =
                busy ||
                !editable() ||
                Boolean(selected && ['code', 'carrierId', 'routeId'].includes(input.name)) ||
                Boolean(selected?.status === 'OPEN' && input.name === 'departureAt');
        });
        for (const picker of pickers) {
            const fixed = Boolean(selected && ['CARRIER', 'ROUTE'].includes(picker.kind));
            picker.element.querySelector('.trip-picker-search').hidden = fixed || !editable();
            picker.element.querySelector('.trip-picker-paging').hidden = fixed || !editable();
            picker.element.querySelector('[data-picker-prev]').disabled = busy || picker.page === 0;
            picker.element.querySelector('[data-picker-next]').disabled = busy || !picker.hasNext;
        }
        node('trip-save').hidden = !editable();
        form.elements.note.disabled = busy || (!editable() && !noticeAllowed());
        node('trip-notice').hidden = !noticeAllowed();
        node('trip-open').hidden = !editable() || selected?.status !== 'DRAFT' || !can('PUT', '/{id}/state');
        node('trip-cancel').hidden = !editable() || !selected || !can('PUT', '/{id}/state');
    };
    const perform = async (action) => {
        if (busy) return;
        busy = true;
        root.setAttribute('aria-busy', 'true');
        node('trip-controls').disabled = true;
        node('trip-status').textContent = labels.loading;
        node('trip-conflict').hidden = true;
        sync();
        feedback('', false, modal.isOpen ? 'trip-form-feedback' : 'trip-feedback');
        try {
            await action();
        } catch (error) {
            if ([401, 403].includes(error.response?.status)) empty(labels.noRead);
            failure(error, modal.isOpen ? 'trip-form-feedback' : 'trip-feedback');
            node('trip-conflict').hidden = error.response?.status !== 409;
        } finally {
            busy = false;
            root.setAttribute('aria-busy', 'false');
            node('trip-controls').disabled = false;
            node('trip-status').textContent = '';
            sync();
            if (modal.isOpen && !node('trip-dialog').contains(document.activeElement))
                node('trip-dialog-title').focus();
        }
    };
    const refresh = async () => {
        capability = await window.VietRouteAccess.load('TRIP');
        sync();
    };
    const load = async (query = pager.query) => {
        if (!can('GET')) {
            empty(labels.noRead);
            return;
        }
        const params = {
            query: search.elements.query.value.trim(),
            ...(search.elements.status.value ? { status: search.elements.status.value } : {}),
            ...(search.elements.date.value ? { date: search.elements.date.value } : {}),
        };
        const page = await tables.readPage(
            async (paging) => (await api.get(path, { params: { ...params, ...paging }, timeout: 10000 })).data.data,
            query,
        );
        node('trip-body').replaceChildren();
        for (const item of page.content) {
            const row = text('tr', '');
            for (const value of [
                item.code,
                english ? item.nameEn : item.nameVi,
                item.carrierName,
                date(item.departureAt),
                item.plateNumber,
                item.capacity,
                state(item.status),
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
            if (item.canUpdate && can('GET', '/{id}') && can('PUT', '/{id}/assignment'))
                actions.append(
                    tables.actionButton(`${labels.edit}: ${item.code}`, 'edit', (trigger) =>
                        perform(() => open(item, false, trigger)),
                    ),
                );
            cell.append(actions);
            row.append(cell);
            node('trip-body').append(row);
        }
        if (!page.content.length) empty(labels.empty);
        pager.update(page);
    };
    const findChoices = async (picker, page = 0) => {
        const carrierId = form.elements.carrierId.value;
        if (picker.kind !== 'CARRIER' && !carrierId) return;
        const result = (
            await api.get(`${path}/choices`, {
                params: {
                    kind: picker.kind,
                    query: picker.query.value.trim(),
                    ...(carrierId ? { carrierId } : {}),
                    tripId: selected?.id || 0,
                    page,
                    size: 10,
                },
                timeout: 10000,
            })
        ).data.data;
        const current = picker.select.selectedOptions[0],
            options = result.content.map((item) =>
                option(item.id, `${item.code} · ${item.name}${item.capacity ? ` (${item.capacity})` : ''}`),
            );
        if (current?.value && !result.content.some((item) => String(item.id) === current.value))
            options.unshift(option(current.value, current.textContent));
        picker.select.replaceChildren(option('', labels.choose), ...options);
        if (current?.value) picker.select.value = current.value;
        picker.page = result.page;
        picker.hasNext = result.hasNext;
        picker.element.querySelector('[data-picker-count]').textContent =
            `${result.totalElements ? result.page + 1 : 0} / ${result.totalPages} · ${result.totalElements}`;
    };
    const detail = () => {
        node('trip-stop-list').replaceChildren();
        node('trip-event-list').replaceChildren();
        for (const stop of selected?.stops || []) {
            const section = text('section', '');
            section.className = 'route-stop-row';
            section.append(
                text('h3', `${stop.stopOrder}. ${english ? stop.nameEn : stop.nameVi}`),
                text('p', stop.address),
                text(
                    'p',
                    `${labels.arrival}: ${date(stop.scheduledArrivalAt)} · ${labels.departure}: ${date(stop.scheduledDepartureAt)}`,
                ),
                text(
                    'p',
                    `${labels.fare}: ${new Intl.NumberFormat(english ? 'en-GB' : 'vi-VN', { style: 'currency', currency: 'VND' }).format(stop.cumulativeFare)} · ${[stop.pickupAllowed ? labels.pickup : '', stop.dropoffAllowed ? labels.dropoff : ''].filter(Boolean).join(' / ')}`,
                ),
            );
            node('trip-stop-list').append(section);
        }
        for (const event of selected?.events || []) {
            const section = text('section', '');
            section.className = 'route-stop-row';
            section.append(text('h3', `${date(event.createdAt)} · ${state(event.eventType)}`), text('p', event.note));
            node('trip-event-list').append(section);
        }
        if (!selected) {
            node('trip-stop-list').append(text('p', labels.empty));
            node('trip-event-list').append(text('p', labels.empty));
        }
    };
    const open = async (item, view, trigger) => {
        selected = item ? (await api.get(`${path}/${item.id}`, { timeout: 10000 })).data.data : null;
        readOnly = view;
        window.VietRouteForms.clear(form);
        form.reset();
        for (const picker of pickers) {
            picker.page = 0;
            picker.hasNext = false;
            picker.query.value = '';
            picker.element.querySelector('[data-picker-count]').textContent = '';
            picker.select.replaceChildren(option('', labels.choose));
        }
        if (selected) {
            for (const [key, label] of [
                ['carrierId', selected.carrierName],
                ['routeId', english ? selected.nameEn : selected.nameVi],
                ['vehicleId', selected.plateNumber],
                ['driverId', selected.driverName],
            ])
                form.elements[key].replaceChildren(option(selected[key], label));
            form.elements.code.value = selected.code;
            form.elements.departureAt.value = inputDate(selected.departureAt);
            form.elements.capacity.value = selected.capacity;
            form.elements.note.value = '';
            node('trip-current-state').textContent = `${state(selected.status)} · ${selected.operationNote}`;
        } else {
            node('trip-current-state').textContent = state('DRAFT');
            await findChoices(pickers[0]);
            form.elements.capacity.value = 1;
        }
        detail();
        feedback('', false, 'trip-form-feedback');
        modal.open(trigger);
        tracking.open(
            selected && can('GET', '/{id}/tracking')
                ? { path: `${path}/${selected.id}/tracking`, status: selected.status }
                : null,
        );
        requestAnimationFrame(() => {
            tabs.select('trip-info');
            sync();
            node('trip-dialog-title').focus();
        });
    };
    const saved = async (result) => {
        node('trip-dialog').close();
        selected = null;
        feedback(result.data.message, true, 'trip-feedback');
        await load({ ...pager.query, page: 0 });
    };
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !editable()) return;
        tabs.select('trip-info');
        if (!window.VietRouteForms.validate(form)) return;
        void perform(async () => {
            const data = form.elements,
                departureAt = new Date(`${data.departureAt.value}:00+07:00`).toISOString();
            const body = {
                vehicleId: Number(data.vehicleId.value),
                driverId: Number(data.driverId.value),
                capacity: Number(data.capacity.value),
                ...(selected
                    ? {
                          version: selected.version,
                          note: data.note.value.trim(),
                          departureAt: selected.status === 'DRAFT' ? departureAt : null,
                      }
                    : { code: data.code.value.trim(), routeId: Number(data.routeId.value), departureAt }),
            };
            await saved(
                await mutate(`${path}${selected ? `/${selected.id}/assignment` : ''}`, body, selected ? 'put' : 'post'),
            );
        });
    });
    const transition = (status) => {
        if (busy || !editable() || !selected) return;
        tabs.select('trip-info');
        if (status === 'CANCELLED' && !form.elements.note.value.trim()) {
            feedback(labels.noteRequired, false, 'trip-form-feedback');
            form.elements.note.focus();
            return;
        }
        void perform(async () =>
            saved(
                await mutate(
                    `${path}/${selected.id}/state`,
                    { status, version: selected.version, note: form.elements.note.value.trim() },
                    'put',
                ),
            ),
        );
    };
    node('trip-open').addEventListener('click', () => transition('OPEN'));
    node('trip-cancel').addEventListener('click', () => transition('CANCELLED'));
    node('trip-notice').addEventListener('click', () => {
        if (busy || !noticeAllowed()) return;
        tabs.select('trip-info');
        if (!form.elements.note.value.trim()) {
            feedback(labels.noteRequired, false, 'trip-form-feedback');
            form.elements.note.focus();
            return;
        }
        void perform(async () =>
            saved(
                await mutate(
                    `${path}/${selected.id}/notice`,
                    { note: form.elements.note.value.trim(), version: selected.version },
                    'put',
                ),
            ),
        );
    });
    for (const picker of pickers) {
        picker.element
            .querySelector('[data-picker-search]')
            .addEventListener('click', () => perform(() => findChoices(picker)));
        picker.query.addEventListener('keydown', (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
                void perform(() => findChoices(picker));
            }
        });
        picker.element
            .querySelector('[data-picker-prev]')
            .addEventListener('click', () => perform(() => findChoices(picker, picker.page - 1)));
        picker.element
            .querySelector('[data-picker-next]')
            .addEventListener('click', () => perform(() => findChoices(picker, picker.page + 1)));
    }
    form.elements.carrierId.addEventListener('change', () =>
        perform(async () => {
            for (const picker of pickers.slice(1)) {
                picker.select.replaceChildren(option('', labels.choose));
                picker.query.value = '';
                await findChoices(picker);
            }
        }),
    );
    node('trip-create').addEventListener('click', (event) => perform(() => open(null, false, event.currentTarget)));
    node('trip-refresh').addEventListener('click', () =>
        perform(async () => {
            await refresh();
            await load();
        }),
    );
    search.addEventListener('submit', (event) => {
        event.preventDefault();
        if (window.VietRouteForms.validate(search)) void perform(() => load({ ...pager.query, page: 0 }));
    });
    void perform(async () => {
        await refresh();
        await load();
    });
})();
