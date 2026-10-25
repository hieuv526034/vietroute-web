/* Dashboard chỉ đọc API nghiệp vụ hiện hành; không cộng dữ liệu ngoài scope hoặc tự gửi mutation. */
((factory) => {
    const dashboard = factory();
    if (typeof module === 'object' && module.exports) module.exports = dashboard;
    else {
        window.VietRouteDashboard = dashboard;
        dashboard.mount();
    }
})(() => {
    'use strict';
    const permitted = (capabilities, feature, target) =>
        capabilities.some((row) => row.function === feature && row.targets.includes(target));
    const plan = (capabilities, roles = []) => {
        const result = [];
        if (roles.includes('CUSTOMER') && permitted(capabilities, 'MY_BOOKING', 'GET /api/v1/my-bookings'))
            result.push('customer');
        if (roles.includes('DRIVER') && permitted(capabilities, 'DRIVER_TRIP', 'GET /api/v1/driver/trips'))
            result.push('driver');
        if (permitted(capabilities, 'REPORT', 'GET /api/v1/admin/reports')) result.push('report');
        if (permitted(capabilities, 'BOOKING', 'GET /api/v1/admin/bookings')) result.push('staff');
        return result;
    };
    const dateRange = (now = new Date()) => {
        const today = new Intl.DateTimeFormat('en-CA', {
            timeZone: 'Asia/Ho_Chi_Minh',
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
        }).format(now);
        const [year, month] = today.split('-').map(Number);
        const end = new Date(Date.UTC(year, month + 1, 0)).toISOString().slice(0, 10);
        return { today, fromDate: today.slice(0, 7) + '-01', toDate: end };
    };
    const mount = () => {
        const root = document.getElementById('business-dashboard');
        if (!root) return;
        const { api, route, failure, feedback } = window.VietRoute,
            labels = root.dataset;
        const refresh = document.getElementById('dashboard-refresh'),
            metrics = document.getElementById('dashboard-metrics'),
            tasks = document.getElementById('dashboard-tasks');
        let busy = false;
        const node = (tag, value, className) => {
            const element = document.createElement(tag);
            element.textContent = String(value ?? '');
            if (className) element.className = className;
            return element;
        };
        const number = (value) =>
            new Intl.NumberFormat(document.documentElement.lang === 'en' ? 'en-GB' : 'vi-VN').format(value);
        const money = (value) =>
            new Intl.NumberFormat(document.documentElement.lang === 'en' ? 'en-GB' : 'vi-VN', {
                style: 'currency',
                currency: 'VND',
                maximumFractionDigits: 0,
            }).format(value);
        const time = (value) =>
            new Intl.DateTimeFormat(document.documentElement.lang === 'en' ? 'en-GB' : 'vi-VN', {
                dateStyle: 'medium',
                timeStyle: 'short',
                timeZone: 'Asia/Ho_Chi_Minh',
            }).format(new Date(value));
        const metric = (label, value, note = '') => {
            const card = node('article', '', 'panel dashboard-metric');
            card.append(node('h3', label), node('strong', value), node('p', note, 'muted'));
            metrics.append(card);
        };
        const section = (title, page, canView) => {
            const card = node('section', '', 'panel dashboard-task');
            card.append(node('h2', title));
            if (canView) {
                const link = node('a', labels.open, 'dashboard-open');
                link.href = route(page);
                card.append(link);
            }
            tasks.append(card);
            return card;
        };
        const rows = (card, items, kind) => {
            if (!items.length) {
                card.append(node('p', labels.empty, 'muted'));
                return;
            }
            const list = node('ul', '', 'dashboard-records');
            for (const item of items) {
                const line = node('li');
                line.append(
                    node('strong', kind === 'trip' ? item.code : item.code + ' · ' + item.tripCode),
                    node(
                        'span',
                        { OPEN: labels.openStatus, IN_PROGRESS: labels.inProgress }[item.status] ||
                            labels[item.status.toLowerCase()] ||
                            item.status,
                        'status-chip',
                    ),
                    node(
                        'p',
                        kind === 'trip'
                            ? (document.documentElement.lang === 'en' ? item.nameEn : item.nameVi) +
                                  ' · ' +
                                  time(item.departureAt)
                            : (document.documentElement.lang === 'en' ? item.pickupNameEn : item.pickupNameVi) +
                                  ' → ' +
                                  (document.documentElement.lang === 'en' ? item.dropoffNameEn : item.dropoffNameVi) +
                                  ' · ' +
                                  time(item.pickupAt),
                    ),
                );
                list.append(line);
            }
            card.append(list);
        };
        const get = async (path, params = {}) => (await api.get(path, { params, timeout: 15000 })).data.data;
        const load = async () => {
            if (busy) return;
            busy = true;
            refresh.disabled = true;
            root.setAttribute('aria-busy', 'true');
            metrics.replaceChildren();
            tasks.replaceChildren();
            feedback('', false, 'dashboard-feedback');
            document.getElementById('dashboard-status').textContent = labels.loading;
            try {
                const user = await get('/api/v1/auth/me');
                if (user.mustChangePassword || user.emailVerificationRequired) {
                    window.location.assign(route('/account'));
                    return;
                }
                const capabilities = await get('/api/v1/auth/capabilities');
                // CUSTOMER/DRIVER là điều kiện nghiệp vụ owner/phân công, không được suy từ ADMIN có đủ permission.
                const sections = plan(capabilities, user.roles);
                const views = (feature) => capabilities.some((row) => row.function === feature && row.view);
                const range = dateRange();
                document.getElementById('dashboard-range').textContent = sections.includes('report')
                    ? labels.range + ' ' + range.fromDate + ' — ' + range.toDate
                    : '';
                // Đọc tuần tự để không chiếm toàn bộ pool/request slots của instance nhỏ; mỗi section có lỗi riêng.
                for (const kind of sections) {
                    try {
                        if (kind === 'report') {
                            const report = await get('/api/v1/admin/reports', {
                                fromDate: range.fromDate,
                                toDate: range.toDate,
                            });
                            metric(labels.trips, number(report.tripCount), labels.cohort);
                            metric(labels.orders, number(report.bookingCount), labels.cohort);
                            metric(labels.collected, money(report.paidAmount), labels.actualMoney);
                            metric(labels.refundDue, money(report.refundOutstanding), labels.cohort);
                        } else if (kind === 'staff') {
                            const pending = await get('/api/v1/admin/bookings', { status: 'PENDING', size: 5 });
                            metric(labels.pending, number(pending.totalElements), labels.scope);
                            const card = section(labels.toProcess, '/operations/bookings', views('BOOKING'));
                            rows(card, pending.content, 'booking');
                        } else if (kind === 'driver') {
                            const active = await get('/api/v1/driver/trips', { status: 'IN_PROGRESS', size: 5 });
                            const today = await get('/api/v1/driver/trips', { date: range.today, size: 5 });
                            const open = await get('/api/v1/driver/trips', { status: 'OPEN', size: 5 });
                            metric(labels.running, number(active.totalElements), labels.assigned);
                            metric(labels.today, number(today.totalElements), range.today);
                            metric(labels.upcoming, number(open.totalElements), labels.assigned);
                            rows(
                                section(labels.running, '/driver/trips', views('DRIVER_TRIP')),
                                active.content,
                                'trip',
                            );
                            rows(section(labels.today, '/driver/trips', views('DRIVER_TRIP')), today.content, 'trip');
                            rows(section(labels.upcoming, '/driver/trips', views('DRIVER_TRIP')), open.content, 'trip');
                        } else {
                            const pending = await get('/api/v1/my-bookings', { status: 'PENDING', size: 1 });
                            const confirmed = await get('/api/v1/my-bookings', { status: 'CONFIRMED', size: 1 });
                            const recent = await get('/api/v1/my-bookings', { size: 5 });
                            metric(labels.myOrders, number(recent.totalElements), labels.owner);
                            metric(labels.pending, number(pending.totalElements), labels.owner);
                            metric(labels.confirmed, number(confirmed.totalElements), labels.owner);
                            rows(
                                section(labels.recent, '/my-bookings', views('MY_BOOKING')),
                                recent.content,
                                'booking',
                            );
                        }
                    } catch (error) {
                        const card = section(labels.partial, '', false);
                        const box = node('div');
                        box.id = 'dashboard-' + kind + '-error';
                        box.setAttribute('role', 'alert');
                        card.append(box);
                        failure(error, box.id);
                    }
                }
                if (!sections.length) tasks.append(node('p', labels.noData, 'muted'));
                document.getElementById('dashboard-updated').textContent = labels.updated + ' ' + time(new Date());
            } catch (error) {
                metrics.replaceChildren();
                tasks.replaceChildren();
                failure(error, 'dashboard-feedback');
            } finally {
                busy = false;
                refresh.disabled = false;
                root.setAttribute('aria-busy', 'false');
                document.getElementById('dashboard-status').textContent = '';
            }
        };
        refresh.addEventListener('click', load);
        void load();
    };
    return Object.freeze({ plan, dateRange, mount });
});
