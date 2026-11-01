/* CRUD Function: bản nháp ở modal, giữ lựa chọn khi lọc, không replay mutation hoặc sửa version sau lỗi. */
(() => {
    'use strict';
    const section = document.getElementById('functions-panel');
    if (!section) return;
    const node = (id) => document.getElementById(id),
        app = window.VietRoute,
        table = window.VietRouteTable;
    const labels = node('grants-labels').dataset,
        base = '/api/v1/admin/access';
    const get = async (path) => (await app.api.get(base + path, { timeout: 10000 })).data.data;
    const text = (tag, value) => {
        const item = document.createElement(tag);
        item.textContent = value ?? '';
        return item;
    };
    let busy = false,
        allowed = new Set(),
        current,
        targets = window.VietRouteFunctionSelection.model([], allowed),
        inventory = [],
        query = '',
        loaded = false,
        ready = false;
    const can = (method, path) => allowed.has(method + ' ' + base + path);
    const modal = window.VietRouteUI.modal('function-dialog', {
        busy: () => busy,
        onClose: () => {
            current = undefined;
            targets = window.VietRouteFunctionSelection.model([], allowed);
            ready = false;
        },
    });
    const perform = async (work) => {
        if (busy) return;
        busy = true;
        section.setAttribute('aria-busy', 'true');
        node('functions-controls').disabled = true;
        node('function-form-controls').disabled = true;
        modal.focus();
        const feedback = node('function-dialog').open ? 'function-form-feedback' : 'functions-feedback';
        app.feedback('', false, feedback);
        try {
            await work();
        } catch (error) {
            app.failure(error, node('function-dialog').open ? 'function-form-feedback' : feedback);
        } finally {
            busy = false;
            section.removeAttribute('aria-busy');
            node('functions-controls').disabled = false;
            node('function-form-controls').disabled = false;
            modal.focus();
        }
    };
    const pager = table.pager('functions', { busy: () => busy, onChange: (params) => perform(() => load(params)) });
    const load = async (params = pager.query) => {
        const page = await table.readPage(
            (values) => get('/functions?' + new URLSearchParams({ ...values, query })),
            params,
        );
        const root = node('functions-body');
        root.replaceChildren();
        for (const value of page.content) {
            const row = text('tr');
            for (const item of [
                value.code,
                value.nameVi,
                value.nameEn,
                value.active ? labels.on : labels.off,
                value.targets.length,
            ])
                row.append(text('td', item));
            const cell = text('td'),
                actions = text('div');
            cell.className = 'table-actions';
            actions.className = 'row-actions';
            if (can('PUT', '/functions/{code}'))
                actions.append(table.actionButton(labels.edit, 'edit', () => void perform(() => edit(value))));
            if (can('DELETE', '/functions/{code}'))
                actions.append(
                    table.actionButton(
                        labels.deleteFunction,
                        'delete',
                        () => {
                            if (!window.confirm(labels.confirmFunction)) return;
                            void perform(async () => {
                                await app.mutate(
                                    base + '/functions/' + encodeURIComponent(value.code) + '?version=' + value.version,
                                    null,
                                    'delete',
                                );
                                await load();
                            });
                        },
                        true,
                    ),
                );
            cell.append(actions);
            row.append(cell);
            root.append(row);
        }
        if (!page.content.length) {
            const row = text('tr'),
                cell = text('td', labels.empty);
            cell.colSpan = 6;
            row.append(cell);
            root.append(row);
        }
        pager.update(page);
    };
    const renderTargets = () => {
        const root = node('function-target-options'),
            term = node('function-target-query').value.trim().toLocaleLowerCase();
        root.replaceChildren();
        for (const resource of inventory) {
            if (
                term &&
                !`${resource.target} ${resource.description} ${resource.handler}`.toLocaleLowerCase().includes(term)
            )
                continue;
            const label = text('label');
            label.className = 'd-flex gap-2 p-2 border-bottom';
            const input = document.createElement('input');
            input.type = 'checkbox';
            input.value = resource.target;
            input.checked = targets.has(resource.target);
            input.disabled = !allowed.has(resource.target);
            input.addEventListener('change', () => {
                targets.toggle(resource.target, input.checked);
                node('function-target-count').textContent = labels.selected + ': ' + targets.size;
            });
            label.append(input, text('span', resource.description + ' · ' + resource.target));
            root.append(label);
        }
        const known = new Set(inventory.map((item) => item.target));
        for (const target of targets) {
            if (known.has(target)) continue;
            const label = text('label');
            label.className = 'd-flex gap-2 p-2';
            const input = document.createElement('input');
            input.type = 'checkbox';
            input.checked = true;
            input.addEventListener('change', () => {
                targets.toggle(target, false);
                renderTargets();
            });
            label.append(input, text('span', labels.stale + ' · ' + target));
            root.append(label);
        }
        node('function-target-count').textContent = labels.selected + ': ' + targets.size;
    };
    const edit = async (value) => {
        ready = false;
        if (value) value = await get('/functions/' + encodeURIComponent(value.code));
        current = value;
        targets = window.VietRouteFunctionSelection.model(value?.targets || [], allowed);
        const form = node('function-form');
        form.reset();
        window.VietRouteForms.clear(form);
        form.elements.code.value = value?.code || '';
        form.elements.code.readOnly = !!value;
        form.elements.nameVi.value = value?.nameVi || '';
        form.elements.nameEn.value = value?.nameEn || '';
        form.elements.active.checked = value?.active ?? true;
        form.elements.active.disabled = false;
        node('function-target-query').value = '';
        app.feedback('', false, 'function-form-feedback');
        node('function-target-options').replaceChildren(text('p', labels.loading));
        modal.open();
        inventory = (await get('/resource-catalog')).filter((item) => !item.publicEndpoint && !item.selfService);
        ready = true;
        renderTargets();
    };
    node('function-create').addEventListener('click', () => void perform(() => edit()));
    node('functions-refresh').addEventListener('click', () => void perform(() => load()));
    node('functions-filter').addEventListener('submit', (event) => {
        event.preventDefault();
        query = node('functions-query').value.trim();
        void perform(() => load({ ...pager.query, page: 0 }));
    });
    node('function-target-query').addEventListener('input', renderTargets);
    node('function-form').addEventListener('submit', (event) => {
        event.preventDefault();
        if (busy || !ready || !window.VietRouteForms.validate(event.currentTarget)) return;
        const form = event.currentTarget,
            body = {
                code: form.elements.code.value.trim(),
                nameVi: form.elements.nameVi.value.trim(),
                nameEn: form.elements.nameEn.value.trim(),
                active: form.elements.active.checked,
                targets: [...targets],
            };
        if (current) body.version = current.version;
        void perform(async () => {
            await app.mutate(
                base + '/functions' + (current ? '/' + encodeURIComponent(current.code) : ''),
                body,
                current ? 'put' : 'post',
            );
            node('function-dialog').close();
            await load();
        });
    });
    const initialize = () => {
        if (section.hidden || busy || loaded) return;
        void perform(async () => {
            const capabilities = (await app.api.get('/api/v1/auth/capabilities')).data.data;
            allowed = new Set(capabilities.flatMap((item) => item.targets));
            node('function-create').hidden = !can('POST', '/functions');
            await load();
            loaded = true;
        });
    };
    new MutationObserver(initialize).observe(section, { attributes: true, attributeFilter: ['hidden'] });
    initialize();
})();
