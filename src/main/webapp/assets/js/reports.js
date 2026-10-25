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
        page = 0,
        hasNext = false,
        allowed = false;
    const text = (tag, value) => {
        const element = document.createElement(tag);
        element.textContent = String(value ?? '');
        return element;
    };
    const option = (value, label) => {
        const element = text('option', label);
        element.value = value;
        return element;
    };
    const sync = () => {
        node('report-controls').disabled = busy || !allowed;
        node('report-pick-prev').disabled = busy || !allowed || page === 0;
        node('report-pick-next').disabled = busy || !allowed || !hasNext;
    };
    const perform = async (action) => {
        if (busy) return;
        busy = true;
        root.setAttribute('aria-busy', 'true');
        node('report-status').textContent = labels.loading;
        feedback('', false, 'report-feedback');
        sync();
        try {
            await action();
        } catch (error) {
            failure(error, 'report-feedback');
        } finally {
            busy = false;
            root.setAttribute('aria-busy', 'false');
            node('report-status').textContent = '';
            sync();
        }
    };
    const findCarriers = async (requestedPage = 0) => {
        const result = (
                await api.get(`${path}/carriers`, {
                    params: { query: node('report-query').value.trim(), page: requestedPage, size: 10 },
                    timeout: 10000,
                })
            ).data.data,
            select = node('report-carrier'),
            current = select.selectedOptions[0],
            options = result.content.map((item) =>
                option(String(item.id), `${item.code} · ${item.name}${item.active ? '' : ` · ${labels.inactive}`}`),
            );
        if (current?.value && !result.content.some((item) => String(item.id) === current.value))
            options.unshift(option(current.value, current.textContent));
        select.replaceChildren(option('', labels.all), ...options);
        if (current?.value) select.value = current.value;
        page = result.page;
        hasNext = result.hasNext;
        node('report-pick-count').textContent = `${result.totalElements ? page + 1 : 0} / ${result.totalPages}`;
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
        form.elements[name].addEventListener('input', () => window.VietRouteForms.clear(form));
    node('report-pick-search').addEventListener('click', () => perform(() => findCarriers()));
    node('report-pick-prev').addEventListener('click', () => perform(() => findCarriers(page - 1)));
    node('report-pick-next').addEventListener('click', () => perform(() => findCarriers(page + 1)));
    node('report-query').addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
            event.preventDefault();
            void perform(() => findCarriers());
        }
    });
    void perform(async () => {
        const capability = await window.VietRouteAccess.load('REPORT');
        allowed = capability.targets.includes(`GET ${path}`) && capability.targets.includes(`GET ${path}/carriers`);
        if (!allowed) {
            feedback(labels.noRead, false, 'report-feedback');
            return;
        }
        await findCarriers();
        await load();
        tabs.select('report-operations');
    });
})();
