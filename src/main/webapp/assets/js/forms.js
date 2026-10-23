/* Validation giao diện dùng chung: thông báo cạnh field, giữ bản nháp khi server từ chối và không thay validation backend. */
(() => {
    'use strict';
    const labels = document.getElementById('validation-labels').dataset;
    const controls = (form) =>
        Array.from(
            form.querySelectorAll(
                'input:not([type="hidden"]):not([type="checkbox"]):not([type="radio"]),select,textarea',
            ),
        );
    // Dùng cùng khai báo required với validation; không gắn dấu vào trường tùy chọn như mã tự sinh.
    const markRequired = (label) => {
        if (!label || label.querySelector('.required-marker')) return;
        const marker = document.createElement('span');
        marker.className = 'required-marker';
        marker.textContent = '*';
        marker.setAttribute('aria-hidden', 'true');
        label.append(marker);
    };
    const messageNode = (input) => {
        const id = `${input.id || (input.id = `field-${crypto.randomUUID()}`)}-error`;
        let node = document.getElementById(id);
        if (!node) {
            node = document.createElement('div');
            node.id = id;
            node.className = 'invalid-feedback field-error';
            node.setAttribute('aria-live', 'polite');
            // Checkbox nhóm có vùng feedback riêng; input thông thường đặt ngay sau để không đổi bố cục grid.
            input.after(node);
            const described = input.getAttribute('aria-describedby')?.split(' ') || [];
            input.setAttribute('aria-describedby', [...new Set([...described, id])].join(' '));
        }
        return node;
    };
    const show = (input, message) => {
        input.classList.toggle('is-invalid', Boolean(message));
        input.setAttribute('aria-invalid', String(Boolean(message)));
        const node = messageNode(input);
        node.textContent = message;
        node.hidden = !message;
    };
    const validationMessage = (input) => {
        if (input.disabled || input.type === 'checkbox' || input.type === 'radio') return '';
        if (input.dataset.serverError) return input.dataset.serverError;
        const value = input.value;
        if (input.required && (!value || (input.type !== 'password' && input.type !== 'file' && !value.trim())))
            return labels.required;
        if (!value) return '';
        if (input.validity.rangeUnderflow) return labels.minimum.replace('{min}', input.min);
        if (input.validity.rangeOverflow) return labels.maximum.replace('{max}', input.max);
        if (input.minLength > 0 && value.length < input.minLength)
            return labels.minLength.replace('{min}', input.minLength);
        if (input.maxLength > 0 && value.length > input.maxLength)
            return labels.maxLength.replace('{max}', input.maxLength);
        if (input.validity.patternMismatch) return input.dataset.patternMessage || labels.pattern;
        if (!input.validity.valid) return labels.invalid;
        return '';
    };
    const groups = (form) => {
        let valid = true;
        for (const group of form.querySelectorAll('[data-required-selection]')) {
            const message = group.querySelector('input:checked') ? '' : labels.selection;
            show(group, message);
            if (message) valid = false;
        }
        return valid;
    };
    const validate = (form) => {
        form.dataset.validated = 'true';
        let valid = true;
        for (const input of controls(form)) {
            const message = validationMessage(input);
            show(input, message);
            if (message) valid = false;
        }
        if (!groups(form)) valid = false;
        if (!valid) {
            const first = form.querySelector('.is-invalid');
            (first?.matches('fieldset') ? first.querySelector('input') : first)?.focus();
        }
        return valid;
    };
    const clear = (form) => {
        delete form.dataset.validated;
        for (const input of controls(form)) {
            delete input.dataset.serverError;
            input.classList.remove('is-invalid');
            input.removeAttribute('aria-invalid');
        }
        form.querySelectorAll('[data-required-selection]').forEach((group) => {
            group.classList.remove('is-invalid');
            group.removeAttribute('aria-invalid');
        });
        form.querySelectorAll('.field-error').forEach((node) => {
            node.textContent = '';
            node.hidden = true;
        });
    };
    const serverErrors = (container, errors) => {
        if (!container) return;
        for (const error of errors || []) {
            // So khớp tên field đã khai báo trong form, không dùng tên từ response làm CSS selector/HTML.
            const input = Array.from(
                container.querySelectorAll('input,select,textarea,[data-required-selection]'),
            ).find((node) => node.name === error.field || node.dataset.validationKey === error.field);
            if (input) {
                input.dataset.serverError = error.message;
                show(input, error.message);
            }
        }
        // Fieldset còn khóa trong catch; đợi caller mở lại trước khi chuyển focus đến field bị lỗi.
        requestAnimationFrame(() => container.querySelector('.is-invalid')?.focus());
    };
    for (const form of document.querySelectorAll('form')) {
        // Khởi tạo ngay cả khi dialog chưa mở; reset form chỉ xóa lỗi, vẫn giữ dấu bắt buộc.
        form.querySelectorAll('[required]').forEach((input) => {
            Array.from(input.labels || []).forEach(markRequired);
        });
        form.querySelectorAll('[data-required-selection]').forEach((group) => {
            markRequired(group.querySelector('legend'));
        });
        form.noValidate = true;
        form.addEventListener('reset', () => clear(form));
        for (const eventName of ['input', 'change']) {
            form.addEventListener(eventName, (event) => {
                const input = event.target;
                delete input.dataset.serverError;
                if (
                    !['checkbox', 'radio'].includes(input.type) &&
                    (input.classList.contains('is-invalid') || form.dataset.validated)
                )
                    show(input, validationMessage(input));
                if (form.querySelector('[data-required-selection]')) groups(form);
            });
        }
    }
    window.VietRouteForms = Object.freeze({ validate, clear, serverErrors });
})();
