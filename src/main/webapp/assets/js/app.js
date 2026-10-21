/* Client dùng chung: chỉ sử dụng cookie HttpOnly, không lưu JWT hoặc mật khẩu trong trình duyệt. */
(() => {
    'use strict';
    const meta = name => document.querySelector(`meta[name="${name}"]`)?.content || '';
    const base = meta('app-base');
    const locale = meta('app-locale') || 'vi';
    const api = axios.create({ baseURL: base || '/', headers: { 'X-Locale': locale }, withCredentials: true });
    const route = path => `${base}${path}?lang=${locale}`;
    const feedback = (message, success = false, id = 'form-feedback') => {
        const node = document.getElementById(id);
        if (!node) return;
        node.textContent = message; // Không đưa message từ server vào innerHTML.
        node.classList.toggle('success', success);
        node.hidden = !message;
    };
    const failure = (error, id) => {
        const data = error.response?.data;
        const lines = [data?.message || meta('app-network-error')];
        for (const field of data?.errors || []) lines.push(`${field.field}: ${field.message}`);
        if (data?.traceId) lines.push(`${meta('app-request-label')}: ${data.traceId}`);
        feedback(lines.join('\n'), false, id);
        if (error.response?.status === 401 && !document.getElementById('login-form')) {
            window.location.assign(route('/login'));
        }
    };
    // CSRF lấy trước mỗi mutation; không đọc cookie auth hoặc ghi token vào storage.
    const mutate = async (path, body) => {
        const csrf = (await api.get('/api/v1/auth/csrf')).data.data;
        return api.post(path, body, { headers: { [csrf.headerName]: csrf.token } });
    };
    const bindForm = (id, action) => {
        const form = document.getElementById(id);
        if (!form) return;
        form.addEventListener('submit', async event => {
            event.preventDefault(); feedback('');
            if (!form.reportValidity()) return;
            const button = form.querySelector('button[type="submit"]');
            const label = button.textContent;
            button.disabled = true;
            if (button.dataset.loading) button.textContent = button.dataset.loading;
            try { await action(Object.fromEntries(new FormData(form)), form); }
            catch (error) { failure(error); }
            finally { button.disabled = false; button.textContent = label; }
        });
    };
    bindForm('login-form', async (body, form) => {
        const response = await mutate('/api/v1/auth/login', body);
        form.reset();
        const user = response.data.data.user;
        window.location.assign(route(user.mustChangePassword ? '/account' : user.roles.includes('ADMIN') ? '/admin' : '/account'));
    });
    const loadAccount = async () => {
        const node = document.getElementById('account-name');
        if (!node) return;
        try {
            const user = (await api.get('/api/v1/auth/me')).data.data;
            node.textContent = user.displayName;
            document.getElementById('account-username').textContent = user.username;
            document.getElementById('account-roles').textContent = user.roles.join(', ');
            document.getElementById('account-created').textContent = new Intl.DateTimeFormat(locale === 'vi' ? 'vi-VN' : 'en-GB',
                { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date(user.createdAt));
            document.getElementById('initial-password').hidden = !user.mustChangePassword;
        } catch (error) { failure(error); }
    };
    bindForm('password-form', async (body, form) => {
        const response = await mutate('/api/v1/auth/change-password', body);
        form.reset(); feedback(response.data.message, true); await loadAccount();
    });
    document.getElementById('logout-button')?.addEventListener('click', async () => {
        try { await mutate('/api/v1/auth/logout', {}); window.location.assign(route('/login')); }
        catch (error) { failure(error); }
    });
    const loadCatalog = async () => {
        const body = document.getElementById('catalog-body');
        if (!body) return;
        try {
            const type = document.getElementById('catalog-filter').value;
            const page = (await api.get(`/api/v1/catalogs/${type}`, { params: { size: 100 } })).data.data;
            body.replaceChildren();
            for (const item of page.content) {
                const row = document.createElement('tr');
                for (const value of [item.code, item.name, item.sortOrder]) {
                    const cell = document.createElement('td'); cell.textContent = String(value); row.append(cell);
                }
                body.append(row);
            }
            if (!page.content.length) {
                const row = document.createElement('tr'), cell = document.createElement('td');
                cell.colSpan = 3; cell.textContent = body.dataset.empty; row.append(cell); body.append(row);
            }
            feedback('', false, 'catalog-feedback');
        } catch (error) { failure(error, 'catalog-feedback'); }
    };
    document.getElementById('catalog-filter')?.addEventListener('change', loadCatalog);
    bindForm('catalog-form', async (body, form) => {
        body.sortOrder = Number(body.sortOrder);
        if (!body.code) delete body.code;
        const response = await mutate('/api/v1/admin/catalogs', body);
        document.getElementById('catalog-filter').value = body.type;
        form.reset(); feedback(response.data.message, true); await loadCatalog();
    });
    void loadAccount(); void loadCatalog();
})();
