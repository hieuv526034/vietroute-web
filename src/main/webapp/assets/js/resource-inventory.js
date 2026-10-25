/* Danh bạ route tự phát hiện từ source; không cho thay cờ public hoặc tạo URL giả từ màn quản trị. */
(() => {
    'use strict';
    const root = document.querySelector('[data-resource-inventory]');
    if (!root) return;
    const node = (id) => document.getElementById(id),
        app = window.VietRoute,
        labels = node('inventory-labels').dataset;
    let busy = false,
        filters = { kind: '', query: '' },
        names = new Map();
    const text = (tag, value) => {
        const item = document.createElement(tag);
        item.textContent = value;
        return item;
    };
    const pager = window.VietRouteTable.pager('inventory', { busy: () => busy, onChange: (params) => load(params) });
    const load = async (params = pager.query) => {
        if (busy) return;
        busy = true;
        node('inventory-controls').disabled = true;
        node('inventory-status').textContent = labels.loading;
        try {
            const page = await window.VietRouteTable.readPage(
                async (values) =>
                    (
                        await app.api.get('/api/v1/admin/access/resources', {
                            params: { ...values, ...filters },
                            timeout: 10000,
                        })
                    ).data.data,
                params,
            );
            const host = node('inventory-body');
            host.replaceChildren();
            for (const item of page.content) {
                const row = text('tr', ''),
                    target = text('td', '');
                target.append(
                    text(
                        'strong',
                        item.kind === 'MENU' ? names.get(item.function) || item.description : item.description,
                    ),
                    text('code', item.target),
                );
                row.append(
                    text('td', item.kind === 'MENU' ? labels.menu : labels.api),
                    text('td', names.get(item.function) || item.function || '—'),
                    target,
                    text(
                        'td',
                        item.publicRoute
                            ? labels.public
                            : !item.declaredPermissions.length
                              ? labels.self
                              : item.adminOnly
                                ? labels.admin
                                : labels.private,
                    ),
                );
                host.append(row);
            }
            if (!page.content.length) {
                const row = text('tr', ''),
                    cell = text('td', labels.empty);
                cell.colSpan = 4;
                row.append(cell);
                host.append(row);
            }
            pager.update(page);
            node('inventory-status').textContent = '';
        } catch (error) {
            node('inventory-status').textContent = '';
            app.failure(error);
        } finally {
            busy = false;
            node('inventory-controls').disabled = false;
        }
    };
    node('inventory-search').addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy) return;
        filters = Object.fromEntries(new FormData(event.currentTarget));
        void load({ ...pager.query, page: 0 });
    });
    node('inventory-refresh').addEventListener('click', () => void load());
    void (async () => {
        try {
            const caps = (await app.api.get('/api/v1/auth/capabilities')).data.data.flatMap((item) => item.targets);
            if (!caps.includes('GET /api/v1/admin/access/resources')) {
                node('inventory-status').textContent = labels.readonly;
                return;
            }
            if (caps.includes('GET /api/v1/admin/access/tree')) {
                const features = (await app.api.get('/api/v1/admin/access/tree')).data.data;
                names = new Map(features.map((item) => [item.code, item.name]));
            }
            await load();
        } catch (error) {
            app.failure(error);
        }
    })();
})();
