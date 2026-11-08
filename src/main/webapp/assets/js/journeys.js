/* Tra cứu công khai theo cặp điểm/ngày đón; giá và chỗ do server, giữ lịch dự kiến và mốc tài xế xác nhận riêng biệt. */
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
        availableMatch = false,
        criteria = { seats: 1 },
        mayBook = false;
    const modal = window.VietRouteUI.modal('journey-dialog', { busy: () => busy }),
        tabs = window.VietRouteUI.tabs(node('journey-tabs')),
        pager = tables.pager('journey', { busy: () => busy, onChange: (query) => perform(() => load(query)) });
    const pickers = Array.from(form.querySelectorAll('.journey-picker')).map((element) => ({
        side: element.dataset.side,
        province: element.querySelector('[data-place-province]'),
        ward: element.querySelector('[data-place-ward]'),
        select: element.querySelector('[data-place-select]'),
        wardsReady: false,
        ready: false,
    }));
    const filters = Array.from(form.querySelectorAll('.journey-filter-picker')).map((element) => ({
        kind: element.dataset.kind,
        select: element.querySelector('[data-filter-select]'),
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
            picker.ward.disabled = busy || !picker.province.value || !picker.wardsReady;
            picker.select.disabled = busy || !picker.ward.value || !picker.ready;
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
    // API vẫn phân trang ở DB; gom danh mục cho select, không đặt pager cạnh từng trường.
    const choices = (path, params = {}) =>
        tables.readOptions(
            async (paging) => (await api.get(path, { params: { ...params, ...paging }, timeout: 10000 })).data.data,
        );
    const findWards = async (picker) => {
        picker.wardsReady = false;
        picker.ready = false;
        picker.ward.replaceChildren(option('', labels.all));
        picker.select.replaceChildren(option('', labels.all));
        if (!picker.province.value) return;
        const items = await choices('/api/v1/categories', { refType: 'WARD', parentId: picker.province.value });
        picker.ward.replaceChildren(option('', labels.all), ...items.map((item) => option(item.id, item.name)));
        picker.wardsReady = true;
    };
    const findPlaces = async (picker) => {
        picker.ready = false;
        picker.select.replaceChildren(option('', labels.all));
        if (!picker.ward.value) return;
        const items = await choices('/api/v1/categories', { refType: 'LOCATION', parentId: picker.ward.value });
        picker.select.replaceChildren(option('', labels.all), ...items.map((item) => option(item.id, item.name)));
        picker.ready = true;
    };
    const load = async (query = pager.query) => {
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
                `${item.availableSeats} · ${labels.available}`,
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
    const findFilter = async (filter) => {
        const path = filter.kind === 'CARRIER' ? '/api/v1/carriers' : '/api/v1/categories';
        const items = await choices(path, filter.kind === 'CARRIER' ? {} : { refType: 'VEHICLE_TYPE' });
        filter.select.replaceChildren(option('', labels.all), ...items.map((item) => option(item.id, item.name)));
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
        book.hidden = (!mayBook && labels.authenticated === 'true') || !availableMatch;
        node('journey-availability').textContent = availableMatch ? labels.available : labels.unavailable;
        book.textContent = labels.authenticated === 'true' ? labels.book : labels.login;
        book.href =
            labels.authenticated === 'true'
                ? `${base}/my-bookings?${new URLSearchParams({ lang: document.documentElement.lang, tripId: item.id, ...(selectedMatch ? { pickupStopId: selectedMatch.pickupStopId, dropoffStopId: selectedMatch.dropoffStopId, seats: criteria?.seats || 1 } : {}) })}`
                : `${base}/login?lang=${document.documentElement.lang}`;
    };
    const findTripMatch = () => {
        const pickup = selected.stops.find((stop) => stop.pickupAllowed && !stop.actualDepartureAt);
        const dropoff =
            pickup && selected.stops.findLast((stop) => stop.dropoffAllowed && stop.stopOrder > pickup.stopOrder);
        return dropoff ? { pickupStopId: pickup.id, dropoffStopId: dropoff.id } : null;
    };
    const checkAvailability = async () => {
        availableMatch = false;
        node('journey-book').hidden = true;
        const match = selectedMatch;
        if (!match) return;
        const quote = (
            await api.get(`/api/v1/trips/${selected.id}/quote`, {
                params: { pickupStopId: match.pickupStopId, dropoffStopId: match.dropoffStopId, seats: criteria.seats },
                timeout: 10000,
            })
        ).data.data;
        availableMatch = quote.accepting;
    };
    const open = async (id, trigger, match = null) => {
        selected = (await api.get(`/api/v1/trips/${id}`, { timeout: 10000 })).data.data;
        selectedMatch = match || findTripMatch();
        await checkAvailability();
        detail();
        modal.open(trigger);
        requestAnimationFrame(() => {
            tabs.select('journey-info');
            node('journey-dialog-title').focus();
        });
    };
    // Đổi một cận phải bỏ lỗi quan hệ cũ ở cận còn lại; server vẫn kiểm lại toàn bộ khoảng giá.
    for (const name of ['minPrice', 'maxPrice'])
        form.elements[name].addEventListener('input', () =>
            window.VietRouteForms.clear(form.elements.maxPrice.closest('label')),
        );
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !window.VietRouteForms.validate(form)) return;
        criteria = { seats: form.elements.seats.value };
        if (form.elements.date.value) criteria.date = form.elements.date.value;
        for (const name of ['carrierId', 'vehicleTypeId', 'minPrice', 'maxPrice', 'fromTime', 'toTime'])
            if (form.elements[name].value) criteria[name] = form.elements[name].value;
        for (const picker of pickers) {
            if (picker.province.value) criteria[picker.side + 'ProvinceId'] = picker.province.value;
            if (picker.ward.value) criteria[picker.side + 'WardId'] = picker.ward.value;
            if (picker.select.value) criteria[picker.side + 'LocationId'] = picker.select.value;
        }
        void perform(() => load({ ...pager.query, page: 0 }));
    });
    for (const picker of pickers) {
        picker.province.addEventListener('change', () => perform(() => findWards(picker)));
        picker.ward.addEventListener('change', () => perform(() => findPlaces(picker)));
    }
    node('journey-refresh').addEventListener('click', () =>
        perform(async () => {
            selected = (await api.get(`/api/v1/trips/${selected.id}`, { timeout: 10000 })).data.data;
            await checkAvailability();
            detail();
        }),
    );
    void perform(async () => {
        const provinces = await choices('/api/v1/categories', { refType: 'PROVINCE' });
        for (const picker of pickers) {
            picker.province.replaceChildren(
                option('', labels.choose),
                ...provinces.map((item) => option(item.id, item.name)),
            );
            picker.ward.replaceChildren(option('', labels.all));
            picker.select.replaceChildren(option('', labels.all));
            picker.wardsReady = false;
            picker.ready = false;
        }
        await window.VietRouteRequests.allReads(filters.map(findFilter));
        const params = new URLSearchParams(location.search),
            carrierId = Number(params.get('carrierId'));
        if (Number.isSafeInteger(carrierId) && carrierId > 0) {
            const carrier = (await api.get(`/api/v1/carriers/${carrierId}`, { timeout: 10000 })).data.data,
                select = form.elements.carrierId;
            if (!Array.from(select.options).some((item) => item.value === String(carrierId)))
                select.append(option(carrier.id, `${carrier.name} · ${carrier.code}`));
            select.value = String(carrierId);
            form.querySelector('.journey-filters').open = true;
        }
        if (carrierId > 0) criteria.carrierId = carrierId;
        await load();
        if (labels.authenticated === 'true') {
            const access = await window.VietRouteAccess.load('MY_BOOKING');
            mayBook =
                access.targets.includes('GET /my-bookings') && access.targets.includes('POST /api/v1/my-bookings');
        }
        const id = Number(new URLSearchParams(location.search).get('tripId'));
        if (Number.isSafeInteger(id) && id > 0) await open(id, null);
    });
})();
