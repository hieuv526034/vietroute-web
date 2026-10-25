/* Tra cứu công khai theo cặp điểm/ngày đón; giá và chỗ do server, không suy GPS từ lịch dự kiến. */
(() => {
    'use strict';
    const root = document.getElementById('journey-workspace');
    if (!root) return;
    const node = (id) => document.getElementById(id),
        { api, feedback, failure } = window.VietRoute,
        tables = window.VietRouteTable,
        labels = root.dataset,
        form = node('journey-form'),
        english = document.documentElement.lang.startsWith('en'),
        base = document.querySelector('meta[name="app-base"]').content;
    let busy = false,
        selected = null,
        selectedMatch = null,
        criteria = null,
        mayBook = false;
    const modal = window.VietRouteUI.modal('journey-dialog', { busy: () => busy }),
        tabs = window.VietRouteUI.tabs(node('journey-tabs')),
        pager = tables.pager('journey', { busy: () => busy, onChange: (query) => perform(() => load(query)) });
    const pickers = Array.from(form.querySelectorAll('.journey-picker')).map((element) => ({
        element,
        side: element.dataset.side,
        kind: element.querySelector('[data-place-kind]'),
        query: element.querySelector('[data-place-query]'),
        select: element.querySelector('[data-place-select]'),
        page: 0,
        hasNext: false,
    }));
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
            : labels.pending;
    const money = (value) =>
        new Intl.NumberFormat(english ? 'en-GB' : 'vi-VN', { style: 'currency', currency: 'VND' }).format(value);
    const empty = (message) => {
        const row = text('tr', ''),
            cell = text('td', message);
        cell.colSpan = 8;
        row.append(cell);
        node('journey-body').replaceChildren(row);
    };
    const sync = () => {
        node('journey-dialog-controls').disabled = busy;
        for (const picker of pickers) {
            picker.element.querySelector('[data-place-prev]').disabled = busy || picker.page === 0;
            picker.element.querySelector('[data-place-next]').disabled = busy || !picker.hasNext;
        }
    };
    const perform = async (action) => {
        if (busy) return;
        busy = true;
        root.setAttribute('aria-busy', 'true');
        node('journey-controls').disabled = true;
        node('journey-status').textContent = labels.loading;
        sync();
        feedback('', false, modal.isOpen ? 'journey-detail-feedback' : 'journey-feedback');
        try {
            await action();
        } catch (error) {
            failure(error, modal.isOpen ? 'journey-detail-feedback' : 'journey-feedback');
        } finally {
            busy = false;
            root.setAttribute('aria-busy', 'false');
            node('journey-controls').disabled = false;
            node('journey-status').textContent = '';
            sync();
            if (modal.isOpen && !node('journey-dialog').contains(document.activeElement))
                node('journey-dialog-title').focus();
        }
    };
    const findPlaces = async (picker, page = 0) => {
        const result = (
                await api.get(picker.kind.value === 'LOCATION' ? '/api/v1/locations' : '/api/v1/provinces', {
                    params: { query: picker.query.value.trim(), page, size: 10 },
                    timeout: 10000,
                })
            ).data.data,
            current = picker.select.selectedOptions[0],
            options = result.content.map((item) =>
                option(item.id, `${item.name}${item.provinceName ? ` · ${item.provinceName}` : ''}`),
            );
        if (current?.value && !result.content.some((item) => String(item.id) === current.value))
            options.unshift(option(current.value, current.textContent));
        picker.select.replaceChildren(option('', labels.choose), ...options);
        if (current?.value) picker.select.value = current.value;
        picker.page = result.page;
        picker.hasNext = result.hasNext;
        picker.element.querySelector('[data-place-count]').textContent =
            `${result.totalElements ? result.page + 1 : 0} / ${result.totalPages}`;
    };
    const load = async (query = pager.query) => {
        if (!criteria) {
            empty(labels.empty);
            return;
        }
        const page = await tables.readPage(
            async (paging) =>
                (await api.get('/api/v1/trips', { params: { ...criteria, ...paging }, timeout: 10000 })).data.data,
            query,
        );
        node('journey-body').replaceChildren();
        for (const item of page.content) {
            const row = text('tr', '');
            for (const value of [
                item.code,
                item.carrierName,
                `${english ? item.pickupNameEn : item.pickupNameVi} → ${english ? item.dropoffNameEn : item.dropoffNameVi}`,
                date(item.pickupAt),
                date(item.dropoffAt),
                money(item.unitPrice),
                item.availableSeats,
            ])
                row.append(text('td', value));
            const cell = text('td', '');
            cell.className = 'table-actions';
            cell.append(
                tables.actionButton(`${labels.detail}: ${item.code}`, 'view', (trigger) =>
                    perform(() => open(item.tripId, trigger, item)),
                ),
            );
            row.append(cell);
            node('journey-body').append(row);
        }
        if (!page.content.length) empty(labels.empty);
        pager.update(page);
    };
    const detail = () => {
        const item = selected,
            confirmed = item.stops.filter((stop) => stop.actualArrivalAt).at(-1);
        node('journey-summary').replaceChildren(
            text('h3', `${item.code} · ${state(item.status)}`),
            text('p', english ? item.nameEn : item.nameVi),
            text('p', `${item.carrierName} · ${item.carrierPhone}`),
            text('p', `${english ? item.vehicleTypeNameEn : item.vehicleTypeNameVi} · ${item.plateNumber}`),
            text('p', `${date(item.departureAt)} → ${date(item.endAt)}`),
            text('p', item.operationNote),
            text(
                'p',
                `${labels.confirmedStop}: ${confirmed ? `${english ? confirmed.nameEn : confirmed.nameVi} · ${date(confirmed.actualArrivalAt)}` : labels.pending}`,
            ),
        );
        node('journey-stop-list').replaceChildren();
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
            const map = text('a', labels.map);
            map.href = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${stop.latitude},${stop.longitude}`)}`;
            map.target = '_blank';
            map.rel = 'noopener noreferrer';
            map.className = 'button button-outline';
            section.append(map);
            node('journey-stop-list').append(section);
        }
        const book = node('journey-book');
        book.hidden = (!mayBook && labels.authenticated === 'true') || !['OPEN', 'IN_PROGRESS'].includes(item.status);
        book.textContent = labels.authenticated === 'true' ? labels.book : labels.login;
        book.href =
            labels.authenticated === 'true'
                ? `${base}/my-bookings?${new URLSearchParams({ lang: document.documentElement.lang, tripId: item.id, ...(selectedMatch ? { pickupStopId: selectedMatch.pickupStopId, dropoffStopId: selectedMatch.dropoffStopId, seats: criteria?.seats || 1 } : {}) })}`
                : `${base}/login?lang=${document.documentElement.lang}`;
    };
    const open = async (id, trigger, match = null) => {
        selected = (await api.get(`/api/v1/trips/${id}`, { timeout: 10000 })).data.data;
        selectedMatch = match;
        detail();
        modal.open(trigger);
        requestAnimationFrame(() => {
            tabs.select('journey-info');
            node('journey-dialog-title').focus();
        });
    };
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !window.VietRouteForms.validate(form)) return;
        criteria = { date: form.elements.date.value, seats: form.elements.seats.value };
        for (const picker of pickers)
            criteria[picker.side + (picker.kind.value === 'LOCATION' ? 'LocationId' : 'ProvinceId')] =
                picker.select.value;
        void perform(() => load({ ...pager.query, page: 0 }));
    });
    for (const picker of pickers) {
        picker.kind.addEventListener('change', () =>
            perform(async () => {
                picker.select.replaceChildren();
                await findPlaces(picker);
            }),
        );
        picker.element
            .querySelector('[data-place-search]')
            .addEventListener('click', () => perform(() => findPlaces(picker)));
        picker.element
            .querySelector('[data-place-prev]')
            .addEventListener('click', () => perform(() => findPlaces(picker, picker.page - 1)));
        picker.element
            .querySelector('[data-place-next]')
            .addEventListener('click', () => perform(() => findPlaces(picker, picker.page + 1)));
        picker.query.addEventListener('keydown', (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
                void perform(() => findPlaces(picker));
            }
        });
    }
    node('journey-refresh').addEventListener('click', () =>
        perform(async () => {
            selected = (await api.get(`/api/v1/trips/${selected.id}`, { timeout: 10000 })).data.data;
            detail();
        }),
    );
    void perform(async () => {
        const parts = Object.fromEntries(
            new Intl.DateTimeFormat('en-CA', {
                timeZone: 'Asia/Ho_Chi_Minh',
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
            })
                .formatToParts(new Date())
                .map((part) => [part.type, part.value]),
        );
        form.elements.date.value = `${parts.year}-${parts.month}-${parts.day}`;
        for (const picker of pickers) await findPlaces(picker);
        empty(labels.empty);
        if (labels.authenticated === 'true') {
            const access = await window.VietRouteAccess.load('MY_BOOKING');
            mayBook = access.view && access.targets.includes('POST /api/v1/my-bookings');
        }
        const id = Number(new URLSearchParams(location.search).get('tripId'));
        if (Number.isSafeInteger(id) && id > 0) await open(id, null);
    });
})();
