/* Tra nhật ký PostgreSQL dùng chung; lỗi chi tiết ở modal, không tự replay mutation hoặc đọc header xác thực. */
(() => {
    'use strict';
    let readAllowed = false;
    const root = document.getElementById('api-journal');
    if (!root) return;
    const { api, feedback, failure } = window.VietRoute;
    const labels = document.getElementById('journal-labels').dataset;
    const node = (id) => document.getElementById(`journal-${id}`);
    const route = '/api/v1/admin/system/api-journal';
    const exporter = window.VietRouteJournalExport;
    const meta = (name) => document.querySelector(`meta[name="${name}"]`)?.content || '';
    const exportContext = Object.freeze({
        baseUrl: exporter.applicationBase(window.location.origin, meta('app-base')),
        locale: meta('app-locale'),
    });
    node('base-url').textContent = exportContext.baseUrl;
    const tables = window.VietRouteTable;
    const pager = tables.pager('journal', { busy: () => busy, onChange: (query) => perform(() => load(query)) });
    let busy = false,
        selected,
        params = {};
    const modal = window.VietRouteUI.modal('journal-dialog');
    const text = (tag, value) => {
        const element = document.createElement(tag);
        element.textContent = String(value);
        return element;
    };
    const perform = async (action) => {
        if (busy) return;
        busy = true;
        node('controls').disabled = true;
        root.setAttribute('aria-busy', 'true');
        node('status').textContent = labels.loading;
        feedback('');
        try {
            await action();
        } catch (error) {
            failure(error, modal.isOpen ? 'journal-detail-feedback' : 'form-feedback');
        } finally {
            busy = false;
            node('controls').disabled = false;
            root.setAttribute('aria-busy', 'false');
            node('status').textContent = '';
        }
    };
    const pretty = (value) => {
        try {
            return JSON.stringify(JSON.parse(value), null, 2);
        } catch {
            return value;
        }
    };
    const show = async (id, trigger) => {
        const detail = (await api.get(`${route}/${id}`, { timeout: 10000 })).data.data;
        selected = detail;
        feedback('', false, 'journal-detail-feedback');
        const metadata = node('metadata');
        metadata.replaceChildren();
        const e = detail.entry;
        for (const [name, value] of [
            ['traceId', e.traceId],
            [labels.node, detail.instanceId],
            [labels.endpoint, `${e.method} ${e.endpoint}`],
            [labels.controller, e.controller],
            [labels.user, e.userId == null ? labels.unknownUser : `${e.username || '#' + e.userId} (#${e.userId})`],
            ['HTTP / ms', `${e.status} / ${e.durationMs}`],
            [labels.code, `${e.code} · ${e.messageKey}`],
            [labels.diagnostic, `${e.diagnostic}${e.sqlState ? ` · SQLState ${e.sqlState}` : ''}`],
        ]) {
            metadata.append(text('dt', name), text('dd', value));
        }
        node('request-json').textContent = pretty(
            JSON.stringify({
                path: detail.replayPath,
                query: detail.query,
                contentKind: detail.contentKind,
                body: JSON.parse(detail.requestBody),
            }),
        );
        node('response-json').textContent = pretty(detail.responseBody);
        node('curl').value = exporter.curl(detail, exportContext);
        const detailTabs = node('dialog').querySelector('[data-tabs]');
        // Mở chi tiết từ list khi fieldset đang khóa: truyền nút gốc để trả focus đúng sau đóng.
        modal.open(trigger);
        requestAnimationFrame(() => window.VietRouteUI.tabs(detailTabs).select('journal-request'));
    };
    const load = async (query = pager.query) => {
        if (!readAllowed) {
            window.VietRoute.feedback(document.querySelector('meta[name="access-no-read"]')?.content || '');
            return;
        }
        let data;
        const records = await tables.readPage(async (pagination) => {
            data = (await api.get(route, { params: { ...params, ...pagination }, timeout: 10000 })).data.data;
            return data.records;
        }, query);
        const retention =
            data.retentionMinutes % 1440 === 0
                ? `${data.retentionMinutes / 1440} ${labels.days}`
                : `${data.retentionMinutes} ${labels.minutes}`;
        node('node').textContent =
            `${data.storage} · ${labels.node}: ${data.instanceId} · ${data.enabled ? labels.enabled : labels.disabled} · ${labels.capacity}: ${data.capacity} · ${retention} · ${labels.expired}: ${data.failedWrites}`;
        const body = node('body');
        body.replaceChildren();
        for (const entry of data.records.content) {
            const row = document.createElement('tr');
            for (const value of [
                entry.timestamp,
                entry.userId == null
                    ? labels.unknownUser
                    : `${entry.username || '#' + entry.userId} (#${entry.userId})`,
                `${entry.method} ${entry.endpoint}`,
                `${entry.status} / ${entry.code}`,
                entry.durationMs,
                entry.traceId,
            ])
                row.append(text('td', value));
            const action = tables.actionButton(`${labels.details}: ${entry.traceId}`, 'view', (trigger) =>
                perform(() => show(entry.id, trigger)),
            );
            const cell = document.createElement('td');
            cell.className = 'table-actions';
            cell.append(action);
            row.append(cell);
            body.append(row);
        }
        if (!body.childElementCount) {
            const row = document.createElement('tr');
            const cell = text('td', labels.empty);
            cell.colSpan = 7;
            row.append(cell);
            body.append(row);
        }
        pager.update(records);
    };
    node('search').addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !window.VietRouteForms.validate(node('search'))) return;
        params = {
            userId: Number(node('user').value || 0),
            traceId: node('trace').value.trim(),
            endpoint: node('endpoint').value.trim(),
            method: node('method').value,
            status: Number(node('http').value || 0),
            code: node('code').value.trim(),
            minDurationMs: Number(node('duration').value || 0),
            errorsOnly: node('errors').checked,
        };
        perform(() => load({ ...pager.query, page: 0 }));
    });
    node('reset').addEventListener('click', () => {
        node('search').reset();
        params = {};
        perform(() => load({ ...pager.query, page: 0 }));
    });
    node('copy').addEventListener('click', async () => {
        try {
            await navigator.clipboard.writeText(node('curl').value);
            feedback(labels.copied, true, 'journal-detail-feedback');
        } catch {
            node('curl').focus();
            node('curl').select();
            feedback(labels.copyFailed, false, 'journal-detail-feedback');
        }
    });
    node('download').addEventListener('click', () => {
        if (!selected) return;
        const blob = new Blob(
            [JSON.stringify(exporter.collection(selected, labels.exportNote, exportContext), null, 2)],
            { type: 'application/json' },
        );
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `vietroute-request-${selected.entry.id}.postman_collection.json`;
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    });
    window.VietRouteAccess.load('JOURNAL')
        .then((capability) => {
            readAllowed = capability.actions.includes('READ');
            perform(load);
        })
        .catch(window.VietRoute.failure);
})();
