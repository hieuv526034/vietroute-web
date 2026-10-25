/* Seed chỉ theo thao tác chủ động, không tự tạo dữ liệu khi mở trang hoặc tự replay sau lỗi. */
(() => {
    'use strict';
    const root = document.getElementById('demo-workspace');
    if (!root) return;
    const { mutate, feedback, failure } = window.VietRoute;
    const button = document.getElementById('demo-create');
    const status = document.getElementById('demo-status');
    let busy = false;
    let allowed = false;
    const refresh = async () => {
        const capability = await window.VietRouteAccess.load('DEMO');
        allowed = capability.targets.includes('POST /api/v1/admin/demo');
        button.disabled = !allowed;
    };
    button.addEventListener('click', async () => {
        if (busy || !allowed) return;
        busy = true;
        button.disabled = true;
        status.textContent = root.dataset.loading;
        root.setAttribute('aria-busy', 'true');
        feedback('', false, 'demo-feedback');
        try {
            const result = (await mutate('/api/v1/admin/demo', {})).data.data;
            status.textContent = `${root.dataset.created}: ${result.created} · ${root.dataset.reused}: ${result.reused}`;
            feedback(
                ['catalogs', 'provinces', 'locations', 'carriers', 'vehicles', 'drivers']
                    .map((key) => `${root.dataset[key]}: ${result[key]}`)
                    .join(' · '),
                true,
                'demo-feedback',
            );
        } catch (error) {
            failure(error, 'demo-feedback');
            status.textContent = '';
        } finally {
            busy = false;
            button.disabled = !allowed;
            root.setAttribute('aria-busy', 'false');
        }
    });
    void refresh().catch((error) => failure(error, 'demo-feedback'));
})();
