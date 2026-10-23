/* Client dùng chung: chỉ sử dụng cookie HttpOnly, không lưu JWT hoặc mật khẩu trong trình duyệt. */
(() => {
    'use strict';
    const meta = (name) => document.querySelector(`meta[name="${name}"]`)?.content || '';
    const base = meta('app-base');
    const locale = meta('app-locale') || 'vi';
    const api = axios.create({ baseURL: base || '/', headers: { 'X-Locale': locale }, withCredentials: true });
    const captureTrigger = window.VietRouteRequests.attach(api);
    const runMutation = window.VietRouteRequests.singleFlight();
    const route = (path) => `${base}${path}?lang=${locale}`;
    const feedback = (message, success = false, id = 'form-feedback') => {
        const node = document.getElementById(id);
        if (!node) {
            return;
        }

        node.textContent = message; // Không đưa message từ server vào innerHTML.
        node.classList.toggle('success', success);
        node.hidden = !message;
    };

    const failure = (error, id) => {
        const data = error.response?.data;
        const lines = [data?.message || meta('app-network-error')];
        if (data?.code) {
            lines.push(`[${data.code}]`);
        }
        for (const field of data?.errors || []) {
            lines.push(field.message);
        }

        if (data?.traceId) {
            lines.push(`${meta('app-request-label')}: ${data.traceId}`);
            if (meta('app-request-help')) {
                lines.push(meta('app-request-help'));
            }
        }

        feedback(lines.join('\n'), false, id);
        const scope =
            document.getElementById(id || 'form-feedback')?.closest('dialog') || document.querySelector('main');
        window.VietRouteForms.serverErrors(scope, data?.errors);
        if (error.response?.status === 401 && !document.getElementById('login-form')) {
            // Hết phiên phải báo rõ và giữ form/modal; chỉ điều hướng khi người dùng chủ động đăng nhập lại.
            const target = document.getElementById(id || 'form-feedback');
            if (target) {
                const link = document.createElement('a');
                link.href = route('/login');
                link.className = 'feedback-login';
                link.textContent = meta('app-login-action');
                target.append(document.createElement('br'), link);
            }
        }
    };
    // CSRF lấy trước mỗi mutation; không đọc cookie auth hoặc ghi token vào storage.
    const mutate = (path, body, method = 'post', options = {}) =>
        runMutation(`${method}:${path}`, async () => {
            const uiTrigger = captureTrigger();
            const uiSilent = options.uiSilent === true;
            const csrf = (await api.get('/api/v1/auth/csrf', { uiTrigger, uiSilent })).data.data;
            return api.request({
                url: path,
                method,
                data: body,
                uiTrigger,
                uiSilent,
                headers: { [csrf.headerName]: csrf.token },
            });
        });

    // Các màn hình mở rộng dùng cùng HTTP client và cơ chế CSRF/lỗi, không sao chép luồng xác thực.
    window.VietRoute = Object.freeze({ api, mutate, failure, feedback });

    const bindForm = (id, action, feedbackId = 'form-feedback') => {
        const form = document.getElementById(id);
        if (!form) {
            return;
        }

        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (form.dataset.submitting === 'true') return;
            feedback('', false, feedbackId);
            if (!window.VietRouteForms.validate(form)) {
                return;
            }

            const button = form.querySelector('button[type="submit"]');
            // Lấy dữ liệu trước khi khóa fieldset, vì FormData bỏ các field đang disabled.
            const body = Object.fromEntries(new FormData(form));
            const controls = form.closest('.dialog-controls');
            form.dataset.submitting = 'true';
            if (controls) controls.disabled = true;
            const label = button.textContent;
            button.disabled = true;
            if (button.dataset.loading) {
                button.textContent = button.dataset.loading;
            }

            try {
                await action(body, form);
            } catch (error) {
                failure(error, feedbackId);
            } finally {
                button.disabled = false;
                button.textContent = label;
                form.dataset.submitting = 'false';
                if (controls) controls.disabled = false;
            }
        });
    };

    bindForm('login-form', async (body, form) => {
        const response = await mutate('/api/v1/auth/login', body);
        form.reset();
        const user = response.data.data.user;
        window.location.assign(
            route(user.mustChangePassword ? '/account' : user.roles.includes('ADMIN') ? '/admin' : '/account'),
        );
    });

    const loadAccount = async () => {
        const node = document.getElementById('account-name');
        if (!node) {
            return;
        }

        try {
            const user = (await api.get('/api/v1/auth/me')).data.data;
            node.textContent = user.displayName;
            document.getElementById('account-username').textContent = user.username;
            document.getElementById('account-roles').textContent = user.roles.join(', ');
            document.getElementById('account-email').textContent = user.email || '—';
            const emailStatus = document.getElementById('account-email-status');
            emailStatus.textContent = user.emailVerifiedAt
                ? emailStatus.dataset.verified
                : emailStatus.dataset.unverified;
            document.getElementById('account-created').textContent = new Intl.DateTimeFormat(
                locale === 'vi' ? 'vi-VN' : 'en-GB',
                { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' },
            ).format(new Date(user.createdAt));
            document.getElementById('initial-password').hidden = !user.mustChangePassword;
        } catch (error) {
            failure(error);
        }
    };

    const passwordForm = document.getElementById('password-form');
    if (passwordForm) {
        const passwordModal = window.VietRouteUI.modal('password-dialog', {
            busy: () => passwordForm.dataset.submitting === 'true',
            onClose: () => {
                passwordForm.reset();
                feedback('', false, 'password-feedback');
            },
        });
        document.getElementById('password-open').addEventListener('click', () => passwordModal.open());
    }
    bindForm(
        'password-form',
        async (body, form) => {
            const response = await mutate('/api/v1/auth/change-password', body);
            form.reset();
            feedback(response.data.message, true);
            document.getElementById('password-dialog').close();
            await loadAccount();
        },
        'password-feedback',
    );

    document.getElementById('logout-button')?.addEventListener('click', async (event) => {
        const button = event.currentTarget;
        if (button.dataset.submitting === 'true') return;
        button.dataset.submitting = 'true';
        button.disabled = true;
        try {
            await mutate('/api/v1/auth/logout', {});
            window.location.assign(route('/'));
        } catch (error) {
            failure(error);
        } finally {
            button.dataset.submitting = 'false';
            button.disabled = false;
        }
    });

    // Mỗi lần chỉ mở một menu; Escape/click ngoài đóng menu và trả focus cho bàn phím.
    const menus = Array.from(document.querySelectorAll('.operations-menu'));
    for (const menu of menus)
        menu.addEventListener('toggle', () => {
            if (menu.open) for (const other of menus) if (other !== menu) other.open = false;
        });
    document.addEventListener('click', (event) => {
        for (const menu of menus) if (!menu.contains(event.target)) menu.open = false;
    });
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape')
            for (const menu of menus)
                if (menu.open) {
                    menu.open = false;
                    menu.querySelector('summary').focus();
                }
    });
    document.dispatchEvent(new Event('vietroute-navigation-ready'));
    document.addEventListener('vietroute-profile-updated', (event) => {
        const user = event.detail;
        document.querySelectorAll('[data-user-name]').forEach((node) => {
            node.textContent = user.displayName;
        });
        document.querySelectorAll('[data-avatar-shell]').forEach((node) => {
            node.replaceChildren();
            if (user.avatarUrl) {
                const img = document.createElement('img');
                img.src = user.avatarUrl;
                img.alt = '';
                node.append(img);
            } else {
                node.textContent = user.username.slice(0, 1).toUpperCase();
            }
        });
        void loadAccount();
    });

    void loadAccount();
})();
