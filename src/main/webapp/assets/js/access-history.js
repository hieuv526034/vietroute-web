/* Lịch sử quyền chỉ đọc: phân trang server, không lưu snapshot/token vào storage hoặc tự replay thao tác ghi. */
(() => {
    'use strict';
    const node = (id) => document.getElementById(id);
    if (!node('history-body')) return;
    const app = window.VietRoute;
    const labels = node('history-labels').dataset;
    const section = node('access-history-section');
    let busy = false;
    let loaded = false;
    let filters = { event: '', target: '', actorId: '' };
    const rows = new Map();
    const modal = window.VietRouteUI.modal('history-dialog', { busy: () => busy });
    const pager = window.VietRouteTable.pager('history', { busy: () => busy, onChange: (query) => load(query) });
    const text = (tag, value, className) => {
        const item = document.createElement(tag);
        item.textContent = value == null ? '—' : String(value);
        if (className) item.className = className;
        return item;
    };
    const localTime = (value) =>
        new Date(value).toLocaleString(document.documentElement.lang === 'en' ? 'en-GB' : 'vi-VN');
    const eventName = (value) =>
        Array.from(node('history-event').options).find((item) => item.value === value)?.textContent || value;
    const field = (host, label, value) => host.append(text('dt', label), text('dd', value));
    const renderState = (host, state) => {
        host.replaceChildren();
        for (const [key, label] of [
            ['roles', labels.roles],
            ['permissions', labels.permissions],
            ['resources', labels.resources || 'Menu / API'],
        ]) {
            if (state[key]?.length) field(host, label, state[key].slice().sort().join(', '));
        }
        if (state.delta || state.mappingCount) field(host, labels.mappingCount, state.mappingCount);
        for (const key of ['active', 'deleted', 'enabled'])
            if (state[key] != null) field(host, labels[key], state[key] ? labels.on : labels.off);
        for (const key of ['nameVi', 'nameEn', 'policyKind', 'policyTarget'])
            if (state[key] != null) field(host, labels[key], state[key]);
        if (state.version || state.delta || state.active != null || state.enabled != null || state.nameVi != null)
            field(host, labels.version, state.version);
        if (!host.childElementCount) host.append(text('dd', labels.empty));
    };
    const detail = (id) => {
        if (busy) return;
        const item = rows.get(id);
        if (!item) return;
        const meta = node('history-meta');
        meta.replaceChildren();
        field(meta, labels.time, localTime(item.createdAt));
        field(meta, labels.event, eventName(item.event));
        field(meta, labels.target, `${item.targetKind}: ${item.targetKey}`);
        field(meta, labels.actor, `${item.actorUsername || '—'} · #${item.actorId}`);
        field(meta, labels.trace, item.traceId);
        node('history-delta-note').hidden = !item.before.delta && !item.after.delta;
        renderState(node('history-before'), item.before);
        renderState(node('history-after'), item.after);
        modal.open();
    };
    async function load(query = pager.query) {
        if (busy) return;
        busy = true;
        node('history-controls').disabled = true;
        section.setAttribute('aria-busy', 'true');
        node('history-status').textContent = labels.loading;
        app.feedback('', false, 'history-feedback');
        try {
            const page = await window.VietRouteTable.readPage(async (params) => {
                const applied = Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== ''));
                return (
                    await app.api.get('/api/v1/admin/access/history', {
                        params: { ...params, ...applied },
                        timeout: 10000,
                    })
                ).data.data;
            }, query);
            const body = node('history-body');
            body.replaceChildren();
            rows.clear();
            for (const item of page.content) {
                rows.set(item.id, item);
                const row = document.createElement('tr');
                for (const value of [
                    localTime(item.createdAt),
                    eventName(item.event),
                    `${item.targetKind}: ${item.targetKey}`,
                    `${item.actorUsername || '—'} · #${item.actorId}`,
                ])
                    row.append(text('td', value));
                const actions = text('td', null, 'table-actions');
                actions.replaceChildren(
                    window.VietRouteTable.actionButton(`${labels.detail}: #${item.id}`, 'view', () => detail(item.id)),
                );
                row.append(actions);
                body.append(row);
            }
            if (!page.content.length) {
                const row = document.createElement('tr');
                const cell = text('td', labels.empty);
                cell.colSpan = 5;
                row.append(cell);
                body.append(row);
            }
            pager.update(page);
            loaded = true;
        } catch (error) {
            if ([401, 403].includes(error.response?.status)) {
                rows.clear();
                node('history-body').replaceChildren();
                loaded = false;
            }
            app.failure(error, 'history-feedback');
        } finally {
            busy = false;
            node('history-controls').disabled = false;
            section.setAttribute('aria-busy', 'false');
            node('history-status').textContent = '';
        }
    }
    node('history-filter').addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !window.VietRouteForms.validate(event.currentTarget)) return;
        filters = {
            event: node('history-event').value,
            target: node('history-target').value.trim(),
            actorId: node('history-actor').value,
        };
        void load({ page: 0, size: pager.query.size });
    });
    node('history-refresh').addEventListener('click', () => load());
    // Chỉ tải khi tab được mở, cả click và bàn phím; không thêm truy vấn lịch sử vào mọi lần mở trang.
    new MutationObserver(() => {
        if (!section.hidden && !loaded && !busy) void load();
    }).observe(section, { attributes: true, attributeFilter: ['hidden'] });
})();
