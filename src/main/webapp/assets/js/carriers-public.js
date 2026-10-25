/* Tra cứu nhà xe công khai bằng projection riêng, không gọi API quản trị hoặc lưu dữ liệu vào storage. */
(() => {
    'use strict';
    const root = document.getElementById('public-carrier-workspace');
    if (!root) return;
    const node = (id) => document.getElementById(id),
        { api, feedback, failure } = window.VietRoute,
        tables = window.VietRouteTable,
        labels = root.dataset,
        form = node('public-carrier-search'),
        path = '/api/v1/carriers';
    let busy = false,
        selected = null;
    const modal = window.VietRouteUI.modal('public-carrier-dialog', { busy: () => busy }),
        pager = tables.pager('public-carrier', { busy: () => busy, onChange: (query) => perform(() => load(query)) });
    const text = (tag, value) => {
        const item = document.createElement(tag);
        item.textContent = value ?? '';
        return item;
    };
    const phone = (value) => {
        const link = text('a', value);
        link.href = `tel:${value}`;
        return link;
    };
    const perform = async (action) => {
        if (busy) return;
        busy = true;
        root.setAttribute('aria-busy', 'true');
        node('public-carrier-controls').disabled = true;
        node('public-carrier-dialog-controls').disabled = true;
        node('public-carrier-status').textContent = labels.loading;
        const target = () => (modal.isOpen ? 'public-carrier-detail-feedback' : 'public-carrier-feedback');
        feedback('', false, target());
        try {
            await action();
        } catch (error) {
            failure(error, target());
        } finally {
            busy = false;
            root.setAttribute('aria-busy', 'false');
            node('public-carrier-controls').disabled = false;
            node('public-carrier-dialog-controls').disabled = false;
            node('public-carrier-status').textContent = '';
        }
    };
    const detail = async (id) => {
        selected = (await api.get(`${path}/${id}`, { timeout: 10000 })).data.data;
        const summary = node('public-carrier-summary');
        summary.replaceChildren(
            text('h3', selected.name),
            text('p', selected.code),
            phone(selected.phone),
            text('p', selected.description),
        );
        if (selected.logoUrl) {
            const logo = document.createElement('img');
            logo.src = selected.logoUrl;
            logo.alt = selected.name;
            logo.className = 'public-carrier-logo';
            logo.loading = 'lazy';
            logo.referrerPolicy = 'no-referrer';
            logo.addEventListener(
                'error',
                () => {
                    logo.hidden = true;
                },
                { once: true },
            );
            summary.prepend(logo);
        }
    };
    const open = async (id, trigger) => {
        await detail(id);
        feedback('', false, 'public-carrier-detail-feedback');
        modal.open(trigger);
        requestAnimationFrame(() => node('public-carrier-dialog-title').focus());
    };
    const load = async (query = pager.query) => {
        const page = await tables.readPage(
            async (paging) =>
                (
                    await api.get(path, {
                        params: { query: form.elements.query.value.trim(), ...paging },
                        timeout: 10000,
                    })
                ).data.data,
            query,
        );
        const body = node('public-carrier-body');
        body.replaceChildren();
        for (const item of page.content) {
            const row = text('tr', ''),
                contact = text('td', ''),
                actions = text('td', '');
            contact.append(phone(item.phone));
            actions.className = 'table-actions';
            actions.append(
                tables.actionButton(`${labels.view}: ${item.name}`, 'view', (trigger) =>
                    perform(() => open(item.id, trigger)),
                ),
            );
            row.append(text('td', item.code), text('td', item.name), contact, actions);
            body.append(row);
        }
        if (!page.content.length) {
            const row = text('tr', ''),
                cell = text('td', labels.empty);
            cell.colSpan = 4;
            row.append(cell);
            body.append(row);
        }
        pager.update(page);
    };
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        if (window.VietRouteForms.validate(form)) void perform(() => load({ ...pager.query, page: 0 }));
    });
    node('public-carrier-refresh').addEventListener('click', () => perform(() => load()));
    node('public-carrier-detail-refresh').addEventListener('click', () => perform(() => detail(selected.id)));
    void perform(async () => {
        await load();
        const id = Number(new URLSearchParams(location.search).get('carrierId'));
        if (Number.isSafeInteger(id) && id > 0) await open(id, null);
    });
})();
