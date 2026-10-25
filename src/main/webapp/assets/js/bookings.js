/* WEB và PHONE dùng một form/quote server; UUID ở bộ nhớ tab, không lưu contact/token vào storage. */
(() => {
    'use strict';
    const root = document.getElementById('booking-workspace');
    if (!root) return;
    const node = (id) => document.getElementById(id),
        labels = root.dataset,
        customer = labels.customer === 'true',
        english = document.documentElement.lang.startsWith('en');
    const { api, mutate, feedback, failure } = window.VietRoute,
        tables = window.VietRouteTable,
        path = customer ? '/api/v1/my-bookings' : '/api/v1/admin/bookings';
    const base = document.querySelector('meta[name="app-base"]').content,
        form = node('booking-create-form'),
        stateForm = node('booking-state-form'),
        moneyForm = node('booking-money-form'),
        search = node('booking-search');
    let busy = false,
        selected = null,
        trip = null,
        capability = { targets: [] },
        requestKey = null,
        moneyKey = null,
        quoteReady = false,
        tripPage = 0,
        tripHasNext = false;
    const createModal = window.VietRouteUI.modal('booking-create-dialog', { busy: () => busy }),
        detailModal = window.VietRouteUI.modal('booking-detail-dialog', { busy: () => busy }),
        tabs = window.VietRouteUI.tabs(node('booking-tabs'));
    const pager = tables.pager('booking', { busy: () => busy, onChange: (query) => perform(() => load(query)) });
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
    const state = (value) => labels[value.toLowerCase()] || value;
    const date = (value) =>
        new Intl.DateTimeFormat(english ? 'en-GB' : 'vi-VN', {
            dateStyle: 'short',
            timeStyle: 'short',
            timeZone: 'Asia/Ho_Chi_Minh',
        }).format(new Date(value));
    const money = (value) =>
        new Intl.NumberFormat(english ? 'en-GB' : 'vi-VN', { style: 'currency', currency: 'VND' }).format(value);
    const feedbackId = () =>
        createModal.isOpen
            ? 'booking-create-feedback'
            : detailModal.isOpen
              ? 'booking-detail-feedback'
              : 'booking-feedback';
    const empty = (message) => {
        const row = text('tr', ''),
            cell = text('td', message);
        cell.colSpan = 8;
        row.append(cell);
        node('booking-body').replaceChildren(row);
    };
    const writable = () => selected?.canUpdate && can('PUT', '/{id}/state');
    const sync = () => {
        node('booking-create-controls').disabled = busy;
        node('booking-detail-controls').disabled = busy;
        if (node('booking-create')) {
            node('booking-create').hidden = !can('POST');
            node('booking-create').disabled = busy || !can('POST');
            node('booking-trip-prev').disabled = busy || tripPage === 0;
            node('booking-trip-next').disabled = busy || !tripHasNext;
        }
        node('booking-submit').disabled = busy || !can('POST') || !quoteReady;
        node('booking-confirm').hidden = customer || !writable() || selected.status !== 'PENDING';
        node('booking-reject').hidden = customer || !writable() || selected.status !== 'PENDING';
        node('booking-cancel').hidden = !writable() || !['PENDING', 'CONFIRMED'].includes(selected.status);
        stateForm.elements.note.disabled = busy || !writable() || !['PENDING', 'CONFIRMED'].includes(selected.status);
        if (moneyForm) {
            const enabled =
                can('PUT', '/{id}/payment') &&
                selected?.canUpdate &&
                ['CONFIRMED', 'COMPLETED', 'CANCELLED'].includes(selected.status);
            for (const control of moneyForm.elements) control.disabled = busy || !enabled;
        }
    };
    const perform = async (action) => {
        if (busy) return;
        busy = true;
        root.setAttribute('aria-busy', 'true');
        node('booking-controls').disabled = true;
        node('booking-status').textContent = labels.loading;
        node('booking-conflict').hidden = true;
        sync();
        feedback('', false, feedbackId());
        try {
            await action();
        } catch (error) {
            if ([401, 403].includes(error.response?.status)) empty(labels.noRead);
            failure(error, feedbackId());
            node('booking-conflict').hidden = error.response?.status !== 409;
        } finally {
            busy = false;
            root.setAttribute('aria-busy', 'false');
            node('booking-controls').disabled = false;
            node('booking-status').textContent = '';
            sync();
            const dialog = createModal.isOpen
                ? node('booking-create-dialog')
                : detailModal.isOpen
                  ? node('booking-detail-dialog')
                  : null;
            if (dialog && !dialog.contains(document.activeElement)) dialog.querySelector('h2').focus();
        }
    };
    const refresh = async () => {
        capability = await window.VietRouteAccess.load(customer ? 'MY_BOOKING' : 'BOOKING');
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
        };
        const page = await tables.readPage(
            async (paging) => (await api.get(path, { params: { ...params, ...paging }, timeout: 10000 })).data.data,
            query,
        );
        node('booking-body').replaceChildren();
        for (const item of page.content) {
            const row = text('tr', '');
            for (const value of [
                item.code,
                item.tripCode,
                `${english ? item.pickupNameEn : item.pickupNameVi} → ${english ? item.dropoffNameEn : item.dropoffNameVi}`,
                date(item.pickupAt),
                item.seats,
                money(item.totalPrice),
                state(item.status),
            ])
                row.append(text('td', value));
            const cell = text('td', '');
            cell.className = 'table-actions';
            if (can('GET', '/{id}'))
                cell.append(
                    tables.actionButton(`${labels.view}: ${item.code}`, 'view', (trigger) =>
                        perform(() => openDetail(item, trigger)),
                    ),
                );
            row.append(cell);
            node('booking-body').append(row);
        }
        if (!page.content.length) empty(labels.empty);
        pager.update(page);
    };
    const findTrips = async (page = 0) => {
        const result = (
                await api.get(`${path}/trips`, {
                    params: { query: node('booking-trip-query').value.trim(), page, size: 10 },
                    timeout: 10000,
                })
            ).data.data,
            current = form.elements.tripId.selectedOptions[0],
            options = result.content.map((item) =>
                option(item.id, `${item.code} · ${item.carrierName} · ${date(item.departureAt)}`),
            );
        if (current?.value && !result.content.some((item) => String(item.id) === current.value))
            options.unshift(option(current.value, current.textContent));
        form.elements.tripId.replaceChildren(option('', labels.choose), ...options);
        if (current?.value) form.elements.tripId.value = current.value;
        tripPage = result.page;
        tripHasNext = result.hasNext;
        node('booking-trip-count').textContent =
            `${result.totalElements ? result.page + 1 : 0} / ${result.totalPages} · ${result.totalElements}`;
    };
    const quote = async () => {
        quoteReady = false;
        const data = form.elements;
        node('booking-quote').textContent = '';
        if (!trip || !data.pickupStopId.value || !data.dropoffStopId.value || !data.seats.validity.valid) return;
        const result = (
            await api.get(`/api/v1/trips/${trip.id}/quote`, {
                params: {
                    pickupStopId: data.pickupStopId.value,
                    dropoffStopId: data.dropoffStopId.value,
                    seats: data.seats.value,
                },
                timeout: 10000,
            })
        ).data.data;
        quoteReady = result.accepting;
        node('booking-quote').textContent =
            `${labels.price}: ${money(result.unitPrice)} · ${labels.total}: ${money(result.totalPrice)} · ${labels.available}: ${result.availableSeats}${result.accepting ? '' : ` · ${labels.notAccepting}`}`;
    };
    const updateDropoffs = () => {
        const selectedId = form.elements.dropoffStopId.value,
            pickup = trip?.stops.find((item) => String(item.id) === form.elements.pickupStopId.value);
        form.elements.dropoffStopId.replaceChildren(
            option('', labels.choose),
            ...(trip?.stops || [])
                .filter((item) => item.dropoffAllowed && (!pickup || item.stopOrder > pickup.stopOrder))
                .map((item) =>
                    option(item.id, `${english ? item.nameEn : item.nameVi} · ${date(item.scheduledArrivalAt)}`),
                ),
        );
        if (Array.from(form.elements.dropoffStopId.options).some((item) => item.value === selectedId))
            form.elements.dropoffStopId.value = selectedId;
    };
    const loadTrip = async (id) => {
        quoteReady = false;
        trip = id ? (await api.get(`/api/v1/trips/${id}`, { timeout: 10000 })).data.data : null;
        form.elements.pickupStopId.replaceChildren(
            option('', labels.choose),
            ...(trip?.stops || [])
                .filter((item) => item.pickupAllowed && !item.actualDepartureAt)
                .map((item) =>
                    option(item.id, `${english ? item.nameEn : item.nameVi} · ${date(item.scheduledDepartureAt)}`),
                ),
        );
        updateDropoffs();
        node('booking-quote').textContent = '';
    };
    const openCreate = async (trigger, initial = null) => {
        if (!can('POST')) return;
        form.reset();
        requestKey = crypto.randomUUID();
        trip = null;
        quoteReady = false;
        window.VietRouteForms.clear(form);
        form.elements.tripId.replaceChildren(option('', labels.choose));
        form.elements.pickupStopId.replaceChildren(option('', labels.choose));
        form.elements.dropoffStopId.replaceChildren(option('', labels.choose));
        if (initial) {
            await loadTrip(initial.tripId);
            form.elements.tripId.replaceChildren(option(trip.id, `${trip.code} · ${trip.carrierName}`));
            form.elements.pickupStopId.value = String(initial.pickupStopId || '');
            updateDropoffs();
            form.elements.dropoffStopId.value = String(initial.dropoffStopId || '');
            form.elements.seats.value = String(initial.seats || 1);
            await quote();
        } else await findTrips();
        feedback('', false, 'booking-create-feedback');
        createModal.open(trigger);
        requestAnimationFrame(() => node('booking-create-title').focus());
    };
    const openDetail = async (item, trigger) => {
        selected = (await api.get(`${path}/${item.id}`, { timeout: 10000 })).data.data;
        stateForm.reset();
        moneyForm?.reset();
        moneyKey = crypto.randomUUID();
        const row = selected;
        node('booking-summary').replaceChildren(
            text('h3', `${row.code} · ${state(row.status)}`),
            text('p', `${row.tripCode} · ${row.carrierName} · ${row.carrierPhone}`),
            text(
                'p',
                `${english ? row.pickupNameEn : row.pickupNameVi} → ${english ? row.dropoffNameEn : row.dropoffNameVi}`,
            ),
            text('p', `${date(row.pickupAt)} → ${date(row.dropoffAt)}`),
            text('p', `${labels.source}: ${row.source} · ${labels.seats}: ${row.seats}`),
            text('p', `${labels.contact}: ${row.contactName} · ${row.contactPhone}`),
            text(
                'p',
                `${labels.total}: ${money(row.totalPrice)} · ${labels.paid}: ${money(row.paidAmount)} · ${labels.refunded}: ${money(row.refundedAmount)}`,
            ),
            text('p', row.refundDue ? labels.refundDue : ''),
        );
        node('booking-journey-link').href =
            `${base}/journeys?lang=${document.documentElement.lang}&tripId=${row.tripId}`;
        node('booking-event-list').replaceChildren();
        for (const event of row.events) {
            const section = text('section', '');
            section.className = 'route-stop-row';
            section.append(
                text('h3', `${date(event.createdAt)} · ${state(event.eventType)}`),
                text('p', event.note),
                text('p', Number(event.amount) > 0 ? money(event.amount) : ''),
            );
            node('booking-event-list').append(section);
        }
        if (moneyForm) moneyForm.elements.kind.value = row.status === 'CANCELLED' ? 'REFUND' : 'PAYMENT';
        feedback('', false, 'booking-detail-feedback');
        detailModal.open(trigger);
        requestAnimationFrame(() => {
            tabs.select('booking-info');
            node('booking-detail-title').focus();
        });
    };
    const saved = async (response, dialog) => {
        dialog.close();
        selected = null;
        feedback(response.data.message, true, 'booking-feedback');
        await load({ ...pager.query, page: 0 });
    };
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !can('POST') || !quoteReady || !window.VietRouteForms.validate(form)) return;
        void perform(async () => {
            const data = form.elements;
            await saved(
                await mutate(
                    path,
                    {
                        tripId: Number(data.tripId.value),
                        pickupStopId: Number(data.pickupStopId.value),
                        dropoffStopId: Number(data.dropoffStopId.value),
                        seats: Number(data.seats.value),
                        contactName: data.contactName.value.trim(),
                        contactPhone: data.contactPhone.value.trim(),
                        requestKey,
                    },
                    'post',
                ),
                node('booking-create-dialog'),
            );
        });
    });
    const transition = (status) => {
        if (busy || !writable()) return;
        if (status !== 'CONFIRMED' && !stateForm.elements.note.value.trim()) {
            feedback(labels.reasonRequired, false, 'booking-detail-feedback');
            stateForm.elements.note.focus();
            return;
        }
        void perform(async () =>
            saved(
                await mutate(
                    `${path}/${selected.id}/state`,
                    { status, version: selected.version, note: stateForm.elements.note.value.trim() },
                    'put',
                ),
                node('booking-detail-dialog'),
            ),
        );
    };
    node('booking-confirm').addEventListener('click', () => transition('CONFIRMED'));
    node('booking-reject').addEventListener('click', () => transition('REJECTED'));
    node('booking-cancel').addEventListener('click', () => transition('CANCELLED'));
    stateForm.addEventListener('submit', (event) => event.preventDefault());
    moneyForm?.addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !selected || !window.VietRouteForms.validate(moneyForm)) return;
        void perform(async () =>
            saved(
                await mutate(
                    `${path}/${selected.id}/payment`,
                    {
                        kind: moneyForm.elements.kind.value,
                        amount: moneyForm.elements.amount.value,
                        note: moneyForm.elements.note.value.trim(),
                        version: selected.version,
                        requestKey: moneyKey,
                    },
                    'put',
                ),
                node('booking-detail-dialog'),
            ),
        );
    });
    form.elements.tripId.addEventListener('change', () => perform(() => loadTrip(form.elements.tripId.value)));
    form.elements.pickupStopId.addEventListener('change', () =>
        perform(async () => {
            updateDropoffs();
            await quote();
        }),
    );
    for (const name of ['dropoffStopId', 'seats']) form.elements[name].addEventListener('change', () => perform(quote));
    node('booking-quote-refresh').addEventListener('click', () => perform(quote));
    if (!customer) {
        node('booking-create').addEventListener('click', (event) => perform(() => openCreate(event.currentTarget)));
        node('booking-trip-search').addEventListener('click', () => perform(() => findTrips()));
        node('booking-trip-prev').addEventListener('click', () => perform(() => findTrips(tripPage - 1)));
        node('booking-trip-next').addEventListener('click', () => perform(() => findTrips(tripPage + 1)));
        node('booking-trip-query').addEventListener('keydown', (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
                void perform(() => findTrips());
            }
        });
    }
    search.addEventListener('submit', (event) => {
        event.preventDefault();
        if (window.VietRouteForms.validate(search)) void perform(() => load({ ...pager.query, page: 0 }));
    });
    node('booking-refresh').addEventListener('click', () =>
        perform(async () => {
            await refresh();
            await load();
        }),
    );
    void perform(async () => {
        await refresh();
        await load();
        const params = new URLSearchParams(location.search),
            id = Number(params.get('tripId'));
        if (customer && Number.isSafeInteger(id) && id > 0 && can('POST'))
            await openCreate(null, {
                tripId: id,
                pickupStopId: Number(params.get('pickupStopId')),
                dropoffStopId: Number(params.get('dropoffStopId')),
                seats: Number(params.get('seats')) || 1,
            });
    });
})();
