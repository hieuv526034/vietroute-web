/* Tra nhật ký PostgreSQL dùng chung; lỗi chi tiết ở modal, không tự replay mutation hoặc đọc header xác thực. */
(() => {
    'use strict';
    const root = document.getElementById('api-journal');
    if (!root) return;
    const { api, feedback, failure } = window.VietRoute;
    const labels = document.getElementById('journal-labels').dataset;
    const node = (id) => document.getElementById(`journal-${id}`);
    const route = '/api/v1/admin/system/api-journal';
    let busy = false,
        page = 0,
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
        node('curl').value = window.VietRouteJournalExport.curl(detail);
        const detailTabs = node('dialog').querySelector('[data-tabs]');
        // Mở chi tiết từ list khi fieldset đang khóa: truyền nút gốc để trả focus đúng sau đóng.
        modal.open(trigger);
        requestAnimationFrame(() => window.VietRouteUI.tabs(detailTabs).select('journal-request'));
    };
    const load = async () => {
        const data = (await api.get(route, { params: { ...params, page, size: 20 }, timeout: 10000 })).data.data;
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
                `${entry.method} ${entry.endpoint}`,
                `${entry.status} / ${entry.code}`,
                entry.durationMs,
                entry.traceId,
            ])
                row.append(text('td', value));
            const action = text('button', labels.details);
            action.type = 'button';
            action.className = 'button button-outline button-small';
            action.addEventListener('click', () => perform(() => show(entry.id, action)));
            const cell = document.createElement('td');
            cell.append(action);
            row.append(cell);
            body.append(row);
        }
        if (!body.childElementCount) {
            const row = document.createElement('tr');
            const cell = text('td', labels.empty);
            cell.colSpan = 6;
            row.append(cell);
            body.append(row);
        }
        node('previous').disabled = !data.records.hasPrevious;
        node('next').disabled = !data.records.hasNext;
        node('page').textContent =
            `${data.records.page + 1}/${Math.max(1, data.records.totalPages)} · ${data.records.totalElements}`;
    };
    node('search').addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !node('search').reportValidity()) return;
        params = {
            traceId: node('trace').value.trim(),
            endpoint: node('endpoint').value.trim(),
            method: node('method').value,
            status: Number(node('http').value || 0),
            code: node('code').value.trim(),
            minDurationMs: Number(node('duration').value || 0),
            errorsOnly: node('errors').checked,
        };
        page = 0;
        perform(load);
    });
    node('reset').addEventListener('click', () => {
        node('search').reset();
        params = {};
        page = 0;
        perform(load);
    });
    node('previous').addEventListener('click', () => {
        if (!busy && page > 0) {
            page--;
            perform(load);
        }
    });
    node('next').addEventListener('click', () => {
        if (!busy) {
            page++;
            perform(load);
        }
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
            [JSON.stringify(window.VietRouteJournalExport.collection(selected, labels.exportNote), null, 2)],
            { type: 'application/json' },
        );
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `vietroute-request-${selected.entry.id}.postman_collection.json`;
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    });
    perform(load);
})();
