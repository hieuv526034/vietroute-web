/* Chỉ đọc snapshot của instance, không tải token vận hành. Poll tuần tự và dừng khi tab ẩn. */
(() => {
    'use strict';
    const app = window.VietRoute;
    const labels = document.getElementById('monitor-labels').dataset;
    const button = document.getElementById('monitoring-refresh');
    const history = document.getElementById('monitor-history');
    let previous;
    let timer;
    let loading = false;
    const display = (id, value) => {
        document.getElementById(id).textContent = value;
    };
    const mib = (value) => `${(value / 1024 / 1024).toFixed(1)} MiB`;
    const load = async () => {
        clearTimeout(timer);
        if (loading || document.hidden) {
            return;
        }
        // Khi đang xem tab truy cập, dừng gọi DB/JWT cho các số liệu tài nguyên bị ẩn.
        if (document.getElementById('monitor-resources-panel')?.hidden) {
            timer = setTimeout(load, 5000);
            return;
        }
        loading = true;
        button.disabled = true;
        try {
            const value = (await app.api.get('/api/v1/admin/system/runtime', { timeout: 8000 })).data.data;
            const now = performance.now();
            // Load balancer có thể trả node khác; không lấy hiệu hai bộ đếm khác instance.
            if (previous?.instanceId !== value.instanceId) {
                history.replaceChildren();
                previous = undefined;
            }
            const rps =
                previous && value.completedRequests >= previous.count
                    ? ((value.completedRequests - previous.count) / ((now - previous.time) / 1000)).toFixed(2)
                    : '—';
            display('monitor-instance', value.instanceId);
            display(
                'monitor-heap',
                `${mib(value.heapUsedBytes)} / ${value.heapMaxBytes > 0 ? mib(value.heapMaxBytes) : labels.unknown}`,
            );
            display(
                'monitor-cpu',
                value.processCpuRatio == null ? labels.unknown : `${(value.processCpuRatio * 100).toFixed(1)}%`,
            );
            display('monitor-active', value.activeRequests);
            display(
                'monitor-errors',
                `${value.serverErrors} / ${value.rateLimitedRequests} / ${value.unavailableRequests}`,
            );
            display('monitor-pool', `${value.activeConnections} / ${value.waitingConnections}`);
            display('monitor-worker', `${value.workerRunning ? labels.on : labels.off} / ${value.activeJobs}`);
            display(
                'monitor-database',
                !value.databaseProbeEnabled
                    ? labels.off
                    : value.databaseAvailable == null
                      ? labels.unknown
                      : value.databaseAvailable
                        ? labels.up
                        : labels.down,
            );
            display(
                'monitor-jobs',
                `${value.queuedJobs ?? '—'} / ${value.deadJobs ?? '—'} / ${value.oldestDueJobSeconds?.toFixed(1) ?? '—'}`,
            );
            const row = document.createElement('tr');
            for (const item of [
                value.timestamp,
                value.uptimeSeconds,
                rps,
                value.completedRequests,
                value.meanResponseMs.toFixed(2),
            ]) {
                const cell = document.createElement('td');
                cell.textContent = String(item);
                row.append(cell);
            }
            history.prepend(row);
            while (history.children.length > 30) {
                history.lastElementChild.remove();
            }
            previous = { instanceId: value.instanceId, count: value.completedRequests, time: now };
            app.feedback('');
        } catch (error) {
            app.failure(error);
        } finally {
            loading = false;
            button.disabled = false;
            if (!document.hidden) {
                timer = setTimeout(load, 5000);
            }
        }
    };
    button.addEventListener('click', load);
    document.addEventListener('visibilitychange', () => {
        clearTimeout(timer);
        if (!document.hidden) {
            load();
        }
    });
    load();
})();
