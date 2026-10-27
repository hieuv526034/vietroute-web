/* Tổng hợp server theo ngày khởi hành; không cộng tiền/chỗ từ trang đơn đang hiển thị. */
(() => {
    'use strict';
    const root = document.getElementById('report-workspace');
    if (!root) return;
    const node = (id) => document.getElementById(id),
        { api, feedback, failure } = window.VietRoute,
        labels = root.dataset,
        form = node('report-form'),
        locale = document.documentElement.lang.startsWith('en') ? 'en-GB' : 'vi-VN',
        path = '/api/v1/admin/reports',
        tabs = window.VietRouteUI.tabs(node('report-results'));
    let busy = false,
        query = '',
        allowed = false;
    const text = (tag, value) => {
        const element = document.createElement(tag);
        element.textContent = String(value ?? '');
        return element;
    };
    const carrierModal = window.VietRouteUI.modal('report-carrier-dialog', { busy: () => busy });
    const carrierPager = window.VietRouteTable.pager('report-carriers', {
        busy: () => busy,
        onChange: (page) => perform(() => findCarriers(page)),
    });
    const sync = () => {
        node('report-controls').disabled = busy || !allowed;
        node('report-picker-controls').disabled = busy || !allowed;
    };
    const invalidate = () => {
        node('report-results').hidden = true;
        node('report-snapshot').textContent = '';
    };
    const selectCarrier = (carrier) => {
        node('report-carrier').value = carrier ? String(carrier.id) : '';
        node('report-carrier-name').value = carrier ? carrier.code + ' · ' + carrier.name : labels.all;
        invalidate();
        carrierModal.close();
    };
    const perform = async (action) => {
        if (busy) return;
        busy = true;
        root.setAttribute('aria-busy', 'true');
        node('report-status').textContent = labels.loading;
        const feedbackId = carrierModal.isOpen ? 'report-picker-feedback' : 'report-feedback';
        feedback('', false, feedbackId);
        sync();
        try {
            await action();
        } catch (error) {
            if ([401, 403].includes(error.response?.status)) node('report-carriers-body').replaceChildren();
            failure(error, feedbackId);
        } finally {
            busy = false;
            root.setAttribute('aria-busy', 'false');
            node('report-status').textContent = '';
            sync();
        }
    };
    // Danh sách nhà xe chỉ tải khi người dùng mở picker; phân trang không áp dụng lại số liệu báo cáo.
    const findCarriers = async (requested = carrierPager.query) => {
        const result = await window.VietRouteTable.readPage(
            async (page) =>
                (await api.get(path + '/carriers', { params: { ...page, query }, timeout: 10000 })).data.data,
            requested,
        );
        const body = node('report-carriers-body');
        body.replaceChildren();
        for (const carrier of result.content) {
            const row = document.createElement('tr');
            row.append(
                text('td', carrier.code),
                text('td', carrier.name),
                text('td', carrier.active ? labels.active : labels.inactive),
            );
            const actions = text('td', '');
            actions.className = 'table-actions';
            actions.append(
                window.VietRouteTable.actionButton(labels.select + ': ' + carrier.name, 'assign', () =>
                    selectCarrier(carrier),
                ),
            );
            row.append(actions);
            body.append(row);
        }
        if (!result.content.length) {
            const row = document.createElement('tr'),
                cell = text('td', labels.empty);
            cell.colSpan = 4;
            row.append(cell);
            body.append(row);
        }
        carrierPager.update(result);
    };
    const load = async () => {
        // Ẩn snapshot cũ ngay khi đổi điều kiện để lỗi không gắn số cũ với khoảng ngày mới.
        node('report-results').hidden = true;
        node('report-snapshot').textContent = '';
        const result = (
            await api.get(path, {
                params: {
                    fromDate: form.elements.fromDate.value,
                    toDate: form.elements.toDate.value,
                    ...(form.elements.carrierId.value ? { carrierId: form.elements.carrierId.value } : {}),
                },
                timeout: 15000,
            })
        ).data.data;
        for (const element of root.querySelectorAll('[data-metric]')) {
            element.textContent = new Intl.NumberFormat(
                locale,
                element.hasAttribute('data-money') ? { style: 'currency', currency: 'VND' } : {},
            ).format(result[element.dataset.metric]);
        }
        for (const kind of ['trips', 'bookings']) {
            node(`report-${kind}`).replaceChildren(
                ...result[kind].map((item) => {
                    const row = text('tr', ''),
                        key = item.status.toLowerCase().replace(/_([a-z])/g, (_, letter) => letter.toUpperCase());
                    row.append(
                        text('td', labels[key] || item.status),
                        text('td', new Intl.NumberFormat(locale).format(item.count)),
                    );
                    return row;
                }),
            );
        }
        node('report-snapshot').textContent =
            `${result.fromDate} → ${result.toDate} · ${labels.asOf}: ${new Intl.DateTimeFormat(locale, { dateStyle: 'short', timeStyle: 'medium', timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date(result.asOf))}`;
        node('report-results').hidden = false;
    };
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        if (allowed && window.VietRouteForms.validate(form)) void perform(load);
    });
    for (const name of ['fromDate', 'toDate'])
        form.elements[name].addEventListener('input', () => {
            window.VietRouteForms.clear(form);
            invalidate();
        });
    node('report-carrier-open').addEventListener('click', (event) => {
        if (busy || !allowed) return;
        carrierModal.open(event.currentTarget);
        void perform(() => findCarriers());
    });
    node('report-carrier-all').addEventListener('click', () => {
        if (!busy) selectCarrier(null);
    });
    node('report-carrier-search').addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !allowed || !window.VietRouteForms.validate(event.currentTarget)) return;
        query = node('report-query').value.trim();
        void perform(() => findCarriers({ page: 0, size: carrierPager.query.size }));
    });
    void perform(async () => {
        const capability = await window.VietRouteAccess.load('REPORT');
        allowed = capability.targets.includes(`GET ${path}`) && capability.targets.includes(`GET ${path}/carriers`);
        if (!allowed) {
            feedback(labels.noRead, false, 'report-feedback');
            return;
        }
        await load();
        tabs.select('report-operations');
    });
})();
