/* Tìm role chỉ ẩn lựa chọn ngoài kết quả; checkbox/bản nháp vẫn giữ nguyên, không tự cấp hoặc bỏ quyền. */
(() => {
    'use strict';
    const normalize = (value) =>
        String(value ?? '')
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .replace(/đ/g, 'd')
            .replace(/Đ/g, 'D')
            .toLowerCase()
            .trim();
    const matches = (code, name, query) => normalize(`${code} ${name}`).includes(normalize(query));
    const bind = (input, host, status) => {
        const update = () => {
            const choices = Array.from(host.querySelectorAll('[data-role-choice]'));
            let visible = 0;
            for (const choice of choices) {
                choice.hidden = !matches(choice.dataset.roleChoice, choice.textContent, input.value);
                if (!choice.hidden) visible++;
            }
            if (status) status.textContent = `${visible} / ${choices.length}`;
        };
        input.addEventListener('input', update);
        return {
            update,
            reset: () => {
                input.value = '';
                update();
            },
        };
    };
    window.VietRouteRolePicker = { matches, bind };
})();
