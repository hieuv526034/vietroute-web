/* Snapshot DB nhiều node, online phân trang server; lỗi giữ dữ liệu cũ và thời điểm quan sát. */
(() => {
    'use strict';
    const root = document.getElementById('activity-dashboard');
    if (!root) return;
    const app = window.VietRoute,
        labels = root.dataset,
        base = '/api/v1/admin/system/activity';
    const locale = document.documentElement.lang === 'en' ? 'en-GB' : 'vi-VN';
    let busy = false,
        timer;
    const node = (id) => document.getElementById(id);
    const pager = window.VietRouteTable.pager('activity-online', {
        busy: () => busy,
        onChange: (query) => load(query),
    });
    const row = (body, values) => {
        const item = document.createElement('tr');
        for (const value of values) {
            const cell = document.createElement('td');
            cell.textContent = value ?? '—';
            item.append(cell);
        }
        body.append(item);
    };
    const get = async (path, params) => (await app.api.get(base + path, { params, timeout: 8000 })).data.data;
    const load = async (query = pager.query) => {
        clearTimeout(timer);
        if (busy || document.hidden || node('activity-panel').hidden) {
            timer = setTimeout(load, 30000);
            return;
        }
        busy = true;
        node('activity-refresh').disabled = true;
        try {
            const summary = await get('');
            const fields = {
                onlineUsers: summary.onlineUsers,
                onlineBrowsers: summary.onlineBrowsers,
                onlineGuests: summary.onlineGuests,
                visitors: summary.visitors24Hours,
                requests: summary.requests60Minutes,
                denied: summary.denied60Minutes,
                limited: summary.limited60Minutes,
                failures: summary.failures60Minutes,
                missed: summary.missedWrites,
            };
            for (const [key, value] of Object.entries(fields))
                node('activity-' + key).textContent = summary.enabled ? value : '—';
            node('activity-missed').classList.toggle('text-danger', summary.missedWrites > 0);
            node('activity-status').textContent = summary.enabled
                ? new Date(summary.observedAt).toLocaleString(locale)
                : labels.disabled;
            if (!summary.enabled) return;
            const [online, anomalies] = await Promise.all([
                window.VietRouteTable.readPage((params) => get('/online', params), query),
                get('/anomalies'),
            ]);
            const onlineBody = node('activity-online-body'),
                anomalyBody = node('activity-anomaly-body');
            onlineBody.replaceChildren();
            anomalyBody.replaceChildren();
            pager.update(online);
            for (const value of online.content)
                row(onlineBody, [
                    value.username || labels.guest,
                    value.visitor,
                    new Date(value.lastSeen).toLocaleString(locale),
                ]);
            for (const value of anomalies)
                row(anomalyBody, [
                    value.userId || labels.guest,
                    value.subject,
                    new Date(value.minute).toLocaleString(locale),
                    value.requests,
                    value.denied,
                    value.limited,
                    value.failures,
                ]);
            if (!online.content.length) row(onlineBody, [labels.empty]);
            if (!anomalies.length) row(anomalyBody, [labels.empty]);
            app.feedback('', false, 'activity-feedback');
        } catch (error) {
            app.failure(error, 'activity-feedback');
        } finally {
            busy = false;
            node('activity-refresh').disabled = false;
            timer = setTimeout(load, 30000);
        }
    };
    node('activity-refresh').addEventListener('click', () => load());
    node('activity-tab').addEventListener('click', () => load());
    document.addEventListener('visibilitychange', () => {
        if (!document.hidden) load();
        else clearTimeout(timer);
    });
    timer = setTimeout(load, 30000);
})();
