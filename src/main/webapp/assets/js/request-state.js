/* Trạng thái HTTP dùng chung; single-flight chỉ chặn gửi trùng đang chạy, không replay hoặc lưu payload/token. */
((factory) => {
    const helpers = factory();
    if (typeof module === 'object' && module.exports) module.exports = helpers;
    else window.VietRouteRequests = helpers;
})(() => {
    'use strict';
    const singleFlight = () => {
        const running = new Map();
        return (key, action) => {
            if (running.has(key)) return running.get(key);
            const result = Promise.resolve()
                .then(action)
                .finally(() => running.delete(key));
            running.set(key, result);
            return result;
        };
    };
    const attach = (api) => {
        const progress = document.createElement('div');
        progress.className = 'request-progress';
        progress.setAttribute('role', 'status');
        progress.setAttribute('aria-live', 'polite');
        progress.textContent = document.querySelector('meta[name="app-loading"]')?.content || '';
        progress.hidden = true;
        document.body.append(progress);
        let pending = 0,
            trigger;
        const buttons = new WeakMap();
        document.addEventListener(
            'click',
            (event) => {
                const button = event.target.closest('button');
                if (button) trigger = button;
            },
            true,
        );
        document.addEventListener(
            'submit',
            (event) => {
                trigger = event.submitter || event.target.querySelector('[type="submit"]');
            },
            true,
        );
        const finish = (config) => {
            if (!config?.uiRelease) return;
            const release = config.uiRelease;
            config.uiRelease = null;
            release();
        };
        api.interceptors.request.use((config) => {
            pending++;
            progress.hidden = false;
            document.body.classList.add('is-request-loading');
            const candidate = config.uiTrigger || trigger;
            const button = candidate?.isConnected ? candidate : null;
            if (button) {
                const state = buttons.get(button) || { count: 0, disabled: button.disabled };
                state.count++;
                buttons.set(button, state);
                button.disabled = true;
                button.setAttribute('data-request-loading', 'true');
            }
            config.uiRelease = () => {
                pending--;
                if (button) {
                    const state = buttons.get(button);
                    if (--state.count === 0) {
                        button.removeAttribute('data-request-loading');
                        button.disabled = state.disabled;
                        buttons.delete(button);
                    }
                }
                if (!pending) {
                    progress.hidden = true;
                    document.body.classList.remove('is-request-loading');
                    trigger = null;
                }
            };
            return config;
        });
        api.interceptors.response.use(
            (response) => {
                finish(response.config);
                return response;
            },
            (error) => {
                finish(error.config);
                return Promise.reject(error);
            },
        );
        return () => trigger;
    };
    return Object.freeze({ singleFlight, attach });
});
