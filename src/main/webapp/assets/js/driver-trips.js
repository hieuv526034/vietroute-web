/* Màn lái xe chỉ gợi ý bước kế tiếp từ snapshot; API luôn kiểm lại phân công, thứ tự và version. */
(() => {
    'use strict';
    const root = document.getElementById('driver-trip-workspace');
    if (!root) return;
    const node = (id) => document.getElementById(id),
        { api, mutate, feedback, failure } = window.VietRoute,
        tables = window.VietRouteTable,
        labels = root.dataset,
        path = '/api/v1/driver/trips';
    const form = node('driver-trip-form'),
        search = node('driver-trip-search'),
        english = document.documentElement.lang.startsWith('en');
    let selected = null,
        busy = false,
        capability = { targets: [] },
        next = null;
    const can = (method, suffix = '') => capability.targets.includes(`${method} ${path}${suffix}`);
    const modal = window.VietRouteUI.modal('driver-trip-dialog', { busy: () => busy }),
        tabs = window.VietRouteUI.tabs(node('driver-trip-tabs'));
    const pager = tables.pager('driver-trip', { busy: () => busy, onChange: (query) => perform(() => load(query)) });
    const passengers = tables.pager('driver-passenger', {
        busy: () => busy,
        onChange: (query) => perform(() => loadPassengers(query)),
    });
    const text = (tag, value) => {
        const item = document.createElement(tag);
        item.textContent = String(value ?? '');
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
            : labels.pending;
    const empty = (message) => {
        const row = text('tr', ''),
            cell = text('td', message);
        cell.colSpan = 7;
        row.append(cell);
        node('driver-trip-body').replaceChildren(row);
    };
    const step = () => {
        if (!selected || !selected.canUpdate || !can('PUT', '/{id}/progress')) return null;
        const stops = selected.stops;
        if (selected.status === 'OPEN') return { action: 'START', stopOrder: 1, label: labels.start };
        if (selected.status !== 'IN_PROGRESS') return null;
        for (const stop of stops) {
            if (!stop.actualArrivalAt)
                return {
                    action: 'ARRIVE',
                    stopOrder: stop.stopOrder,
                    label: `${labels.arrive}: ${english ? stop.nameEn : stop.nameVi}`,
                };
            if (!stop.actualDepartureAt)
                return {
                    action: stop.stopOrder === stops.length ? 'COMPLETE' : 'DEPART',
                    stopOrder: stop.stopOrder,
                    label: `${stop.stopOrder === stops.length ? labels.complete : labels.depart}: ${english ? stop.nameEn : stop.nameVi}`,
                };
        }
        return null;
    };
    const sync = () => {
        node('driver-trip-dialog-controls').disabled = busy;
        next = step();
        node('driver-trip-progress').hidden = !next;
        node('driver-trip-progress').textContent = next?.label || '';
        form.elements.note.disabled = busy || !next;
    };
    const perform = async (action) => {
        if (busy) return;
        busy = true;
        root.setAttribute('aria-busy', 'true');
        node('driver-trip-controls').disabled = true;
        node('driver-trip-status').textContent = labels.loading;
        node('driver-trip-conflict').hidden = true;
        sync();
        feedback('', false, modal.isOpen ? 'driver-trip-form-feedback' : 'driver-trip-feedback');
        try {
            await action();
        } catch (error) {
            if ([401, 403].includes(error.response?.status)) {
                empty(labels.noRead);
                selected = null;
                sync();
            }
            failure(error, modal.isOpen ? 'driver-trip-form-feedback' : 'driver-trip-feedback');
            node('driver-trip-conflict').hidden = error.response?.status !== 409;
        } finally {
            busy = false;
            root.setAttribute('aria-busy', 'false');
            node('driver-trip-controls').disabled = false;
            node('driver-trip-status').textContent = '';
            sync();
            if (modal.isOpen && !node('driver-trip-dialog').contains(document.activeElement))
                node('driver-trip-dialog-title').focus();
        }
    };
    const refresh = async () => {
        capability = await window.VietRouteAccess.load('DRIVER_TRIP');
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
        node('driver-trip-body').replaceChildren();
        for (const item of page.content) {
            const row = text('tr', '');
            for (const value of [
                item.code,
                english ? item.nameEn : item.nameVi,
                item.carrierName,
                date(item.departureAt),
                item.plateNumber,
                state(item.status),
            ])
                row.append(text('td', value));
            const cell = text('td', '');
            cell.className = 'table-actions';
            if (can('GET', '/{id}'))
                cell.append(
                    tables.actionButton(`${labels.view}: ${item.code}`, 'view', (trigger) =>
                        perform(() => open(item, trigger)),
                    ),
                );
            row.append(cell);
            node('driver-trip-body').append(row);
        }
        if (!page.content.length) empty(labels.empty);
        pager.update(page);
    };
    const detail = () => {
        const item = selected;
        node('driver-trip-summary').replaceChildren(
            text('h3', `${item.code} · ${state(item.status)}`),
            text('p', english ? item.nameEn : item.nameVi),
            text('p', `${item.carrierName} · ${item.carrierPhone}`),
            text('p', `${item.plateNumber} · ${date(item.departureAt)}`),
            text('p', item.operationNote),
        );
        node('driver-trip-stop-list').replaceChildren();
        for (const stop of item.stops) {
            const section = text('section', '');
            section.className = 'route-stop-row';
            section.append(
                text('h3', `${stop.stopOrder}. ${english ? stop.nameEn : stop.nameVi}`),
                text('p', stop.address),
                text('p', `${labels.planned}: ${date(stop.scheduledArrivalAt)} → ${date(stop.scheduledDepartureAt)}`),
                text('p', `${labels.actual}: ${date(stop.actualArrivalAt)} → ${date(stop.actualDepartureAt)}`),
                text(
                    'p',
                    [stop.pickupAllowed ? labels.pickup : '', stop.dropoffAllowed ? labels.dropoff : '']
                        .filter(Boolean)
                        .join(' / '),
                ),
            );
            node('driver-trip-stop-list').append(section);
        }
        node('driver-trip-event-list').replaceChildren();
        for (const event of item.events) {
            const section = text('section', '');
            section.className = 'route-stop-row';
            section.append(text('h3', `${date(event.createdAt)} · ${state(event.eventType)}`), text('p', event.note));
            node('driver-trip-event-list').append(section);
        }
        sync();
    };
    const open = async (item, trigger) => {
        selected = (await api.get(`${path}/${item.id}`, { timeout: 10000 })).data.data;
        form.reset();
        detail();
        await loadPassengers({ ...passengers.query, page: 0 });
        feedback('', false, 'driver-trip-form-feedback');
        modal.open(trigger);
        requestAnimationFrame(() => {
            tabs.select('driver-trip-info');
            node('driver-trip-dialog-title').focus();
        });
    };
    const loadPassengers = async (query = passengers.query) => {
        node('driver-passenger-body').replaceChildren();
        if (!selected || !can('GET', '/{id}/passengers')) return;
        const page = await tables.readPage(
            async (paging) =>
                (await api.get(`${path}/${selected.id}/passengers`, { params: paging, timeout: 10000 })).data.data,
            query,
        );
        for (const item of page.content) {
            const row = text('tr', '');
            for (const value of [
                item.bookingCode,
                `${english ? item.pickupNameEn : item.pickupNameVi} → ${english ? item.dropoffNameEn : item.dropoffNameVi}`,
                item.seats,
                item.contactName,
            ])
                row.append(text('td', value));
            const phone = text('a', item.contactPhone),
                contact = text('td', '');
            phone.href = `tel:${item.contactPhone}`;
            contact.append(phone);
            row.append(contact, text('td', item.status === 'CONFIRMED' ? labels.confirmed : labels.completed));
            node('driver-passenger-body').append(row);
        }
        if (!page.content.length) {
            const row = text('tr', ''),
                cell = text('td', labels.empty);
            cell.colSpan = 6;
            row.append(cell);
            node('driver-passenger-body').append(row);
        }
        passengers.update(page);
    };
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !next || !window.VietRouteForms.validate(form)) return;
        const action = {
            action: next.action,
            stopOrder: next.stopOrder,
            version: selected.version,
            note: form.elements.note.value.trim(),
        };
        void perform(async () => {
            const response = await mutate(`${path}/${selected.id}/progress`, action, 'put');
            selected = response.data.data;
            detail();
            form.reset();
            feedback(response.data.message, true, 'driver-trip-form-feedback');
            await load();
            await loadPassengers();
        });
    });
    node('driver-trip-refresh').addEventListener('click', () =>
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
