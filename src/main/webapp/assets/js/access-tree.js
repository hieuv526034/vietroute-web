/* Cây quyền dùng một Set cho cả quyền trùng giữa chức năng; tìm kiếm không làm mất lựa chọn ngoài kết quả. */
(() => {
    'use strict';
    const model = (initial, features) => {
        const selected = new Set(initial);
        const original = new Set(initial);
        return {
            values: () => Array.from(selected).sort(),
            has: (code) => selected.has(code),
            toggle(code, checked, featureCode) {
                if (checked) {
                    selected.add(code);
                    const matches = features.filter((feature) =>
                        feature.actions.some((action) => action.permission === code),
                    );
                    for (const feature of matches.filter((feature) =>
                        featureCode ? feature.code === featureCode : matches.length === 1,
                    ))
                        if (feature.viewPermission) selected.add(feature.viewPermission);
                } else selected.delete(code);
            },
            changes: () => ({
                added: Array.from(selected)
                    .filter((code) => !original.has(code))
                    .sort(),
                removed: Array.from(original)
                    .filter((code) => !selected.has(code))
                    .sort(),
            }),
            state(codes) {
                const count = codes.filter((code) => selected.has(code)).length;
                return {
                    checked: codes.length > 0 && count === codes.length,
                    partial: count > 0 && count < codes.length,
                };
            },
        };
    };
    const element = (tag, value, className) => {
        const item = document.createElement(tag);
        if (value != null) item.textContent = value;
        if (className) item.className = className;
        return item;
    };
    // Bộ quyền là mẫu chọn nhiều mapping cụ thể; không thêm authority toàn cục hoặc quyền vượt ADMIN.
    const systemSelection = (features) =>
        features
            .filter((feature) => feature.systemBundle)
            .flatMap((feature) =>
                feature.actions
                    .filter(
                        (action) =>
                            !action.permission.endsWith('_WRITE') &&
                            action.endpoints.some((endpoint) => !endpoint.adminOnly),
                    )
                    .map((action) => ({ code: action.permission, feature: feature.code })),
            );
    // Hiển thị cả mã đã tắt/ngoài registry để ADMIN bỏ mapping cũ, tránh không thể lưu vì quyền vô hình.
    const unbound = (registry, initial, features, inactiveCaption) => {
        const indexed = new Set(features.flatMap((feature) => feature.actions.map((action) => action.permission)));
        return [
            ...registry
                .filter((item) => !indexed.has(item.code))
                .map((item) => ({
                    permission: item.code,
                    name: item.name,
                    action: item.actions.join('/'),
                    endpoints: [],
                })),
            ...initial
                .filter((code) => !indexed.has(code) && !registry.some((item) => item.code === code))
                .map((code) => ({
                    permission: code,
                    name: `${code} · ${inactiveCaption}`,
                    action: code.substring(code.lastIndexOf('_') + 1),
                    endpoints: [],
                    inactive: true,
                })),
        ];
    };
    const render = (host, features, registry, initial, options) => {
        const labels = document.getElementById('access-tree-labels').dataset;
        const state = model(initial, features);
        const permitted = (code) => options.editable && (!options.allowed || options.allowed.includes(code));
        const extra = unbound(registry, initial, features, labels.inactive);
        const nodes = [
            ...features,
            ...(extra.length
                ? [
                      { code: 'UNBOUND', parent: null, name: labels.custom, actions: [] },
                      ...extra.map((action) => ({
                          code: 'UNBOUND:' + action.permission,
                          parent: 'UNBOUND',
                          name: action.name,
                          actions: [action],
                      })),
                  ]
                : []),
        ];
        const toolbar = element('div', null, 'access-tree-toolbar');
        const search = element('input');
        search.type = 'search';
        search.className = 'form-control';
        search.placeholder = labels.search;
        search.setAttribute('aria-label', labels.search);
        const allLabel = element('label', null, 'access-tree-select');
        const all = element('input');
        all.type = 'checkbox';
        all.className = 'form-check-input';
        allLabel.append(all, element('span', labels.all));
        const expand = element('button', labels.expand, 'button button-outline');
        expand.type = 'button';
        const collapse = element('button', labels.collapse, 'button button-outline');
        collapse.type = 'button';
        toolbar.append(search, expand, collapse);
        const preset = element('button', labels.systemBundle, 'button button-outline');
        preset.type = 'button';
        const systemItems = systemSelection(features);
        preset.disabled = !systemItems.length || systemItems.some((item) => !permitted(item.code));
        preset.title = labels.systemBundleNote;
        toolbar.append(preset);
        const content = element('div', null, 'access-tree-content');
        const preview = element('div', null, 'access-tree-preview');
        preview.setAttribute('role', 'status');
        preview.setAttribute('aria-live', 'polite');
        const hint = element('p', labels.dependency, 'muted access-tree-hint');
        host.replaceChildren(toolbar, hint, allLabel, content, preview);
        let visibleActions = [];
        let visibleCodes = [],
            inputs = [],
            groups = [];
        const update = () => {
            for (const { input, code } of inputs) input.checked = state.has(code);
            for (const { input, codes } of groups) {
                const value = state.state(codes);
                input.checked = value.checked;
                input.indeterminate = value.partial;
            }
            const value = state.state(visibleCodes);
            all.checked = value.checked;
            all.indeterminate = value.partial;
            const changes = state.changes();
            preview.replaceChildren(element('strong', labels.preview));
            if (!changes.added.length && !changes.removed.length) preview.append(element('p', labels.unchanged));
            for (const [key, caption] of [
                ['added', labels.added],
                ['removed', labels.removed],
            ])
                if (changes[key].length)
                    preview.append(element('p', `${caption} (${changes[key].length}): ${changes[key].join(', ')}`));
        };
        const paint = () => {
            const query = search.value.trim().toLocaleLowerCase();
            inputs = [];
            groups = [];
            content.replaceChildren();
            const matchesBranch = (feature) => {
                let current = feature;
                while (current) {
                    if (`${current.name} ${current.code}`.toLocaleLowerCase().includes(query)) return true;
                    current = nodes.find((item) => item.code === current.parent);
                }
                return false;
            };
            const matching = new Map(
                nodes.map((feature) => [
                    feature.code,
                    feature.actions.filter(
                        (action) =>
                            !query ||
                            matchesBranch(feature) ||
                            `${feature.name} ${feature.code} ${action.name} ${action.permission}`
                                .toLocaleLowerCase()
                                .includes(query),
                    ),
                ]),
            );
            const descendants = (feature) => [
                ...matching.get(feature.code),
                ...nodes.filter((item) => item.parent === feature.code).flatMap(descendants),
            ];
            const columns = Array.from(
                new Set(nodes.flatMap((feature) => feature.actions.map((action) => action.action))),
            );
            const order = ['VIEW', 'READ', 'LIST', 'DETAIL', 'CREATE', 'UPDATE', 'DELETE', 'ASSIGN', 'RETRY', 'SEND'];
            columns.sort(
                (left, right) =>
                    (order.indexOf(left) < 0 ? 99 : order.indexOf(left)) -
                    (order.indexOf(right) < 0 ? 99 : order.indexOf(right)),
            );
            const table = element('table', null, 'access-matrix');
            const head = element('thead');
            const header = element('tr');
            header.append(element('th', labels.menu));
            for (const column of columns)
                header.append(
                    element('th', labels['action' + column.charAt(0) + column.slice(1).toLowerCase()] || column),
                );
            header.append(element('th', labels.endpoints));
            head.append(header);
            table.append(head);
            const body = element('tbody');
            const build = (feature, depth = 0) => {
                const below = descendants(feature);
                if (!below.length) return;
                const actions = matching.get(feature.code);
                const row = element('tr', null, feature.parent ? 'access-matrix-feature' : 'access-matrix-group');
                row.dataset.feature = feature.code;
                const title = element('td');
                const label = element('label', null, 'access-matrix-title');
                label.style.paddingInlineStart = `${depth * 12}px`;
                const group = element('input');
                group.type = 'checkbox';
                group.className = 'form-check-input';
                const codes = Array.from(new Set(below.map((action) => action.permission)));
                group.disabled = !codes.some(permitted);
                group.setAttribute('aria-label', feature.name);
                group.addEventListener('change', () => {
                    const branch = (node) => [
                        node,
                        ...nodes.filter((item) => item.parent === node.code).flatMap(branch),
                    ];
                    for (const item of branch(feature))
                        for (const action of matching.get(item.code))
                            if (permitted(action.permission)) state.toggle(action.permission, group.checked, item.code);
                    update();
                });
                groups.push({ input: group, codes });
                const name = element('span');
                name.append(element('strong', feature.name));
                if (feature.path) name.append(element('small', feature.path, 'muted'));
                if (
                    actions.length &&
                    actions.every(
                        (action) => action.endpoints.length && action.endpoints.every((endpoint) => endpoint.adminOnly),
                    )
                )
                    name.append(element('small', labels.admin, 'muted'));
                label.append(group, name);
                title.append(label);
                row.append(title);
                for (const column of columns) {
                    const cell = element('td', null, 'access-matrix-check');
                    const choices = actions.filter((action) => action.action === column);
                    for (const action of choices) {
                        const label = element('label');
                        label.title = `${action.name} · ${action.permission}`;
                        const input = element('input');
                        input.type = 'checkbox';
                        input.className = 'form-check-input';
                        input.disabled = !permitted(action.permission);
                        input.setAttribute('aria-label', `${feature.name}: ${action.name} (${action.permission})`);
                        input.addEventListener('change', () => {
                            state.toggle(action.permission, input.checked, feature.code);
                            update();
                        });
                        inputs.push({ input, code: action.permission });
                        label.append(input);
                        if (action.permission.endsWith('_WRITE')) label.append(element('small', 'WRITE', 'muted'));
                        cell.append(label);
                    }
                    if (!choices.length) cell.append(element('span', '—', 'muted'));
                    row.append(cell);
                }
                const endpoints = element('td');
                if (actions.length) {
                    const details = element('details', null, 'access-endpoints');
                    const targets = Array.from(
                        new Map(
                            actions
                                .flatMap((action) => action.endpoints)
                                .map((endpoint) => [endpoint.target, endpoint]),
                        ).values(),
                    );
                    details.append(element('summary', `${labels.endpoints} (${targets.length})`));
                    for (const endpoint of targets) {
                        const item = element('div');
                        item.append(
                            element('code', endpoint.target),
                            element('p', endpoint.description, 'muted'),
                            element(
                                'small',
                                `${endpoint.match}: ${endpoint.permissions.join(', ')}${endpoint.adminOnly ? ' · ' + labels.admin : ''}`,
                            ),
                        );
                        details.append(item);
                    }
                    endpoints.append(details);
                }
                row.append(endpoints);
                body.append(row);
                for (const child of nodes.filter((item) => item.parent === feature.code)) build(child, depth + 1);
            };
            for (const feature of nodes.filter((item) => !item.parent)) build(feature);
            table.append(body);
            if (body.childElementCount) content.append(table);
            visibleCodes = Array.from(
                new Set(
                    Array.from(matching.values())
                        .flat()
                        .map((action) => action.permission),
                ),
            );
            visibleActions = nodes.flatMap((feature) =>
                matching
                    .get(feature.code)
                    .filter((action) => permitted(action.permission))
                    .map((action) => ({ code: action.permission, feature: feature.code })),
            );
            all.disabled = !visibleActions.length;
            if (!content.childElementCount) content.append(element('p', labels.empty, 'muted'));
            update();
        };
        all.addEventListener('change', () => {
            for (const item of visibleActions) state.toggle(item.code, all.checked, item.feature);
            update();
        });
        preset.addEventListener('click', () => {
            if (preset.disabled) return;
            for (const item of systemItems) state.toggle(item.code, true, item.feature);
            update();
        });
        search.addEventListener('input', paint);
        expand.addEventListener('click', () =>
            content.querySelectorAll('.access-endpoints').forEach((item) => {
                item.open = true;
            }),
        );
        collapse.addEventListener('click', () =>
            content.querySelectorAll('.access-endpoints').forEach((item) => {
                item.open = false;
            }),
        );
        paint();
        return state;
    };
    // Nguồn quyền tải theo yêu cầu, chỉ thay vùng chi tiết; lỗi giữ nguyên modal gán role.
    const sources = (host, load) => {
        host.querySelector('[data-effective-sources]')?.remove();
        const labels = document.getElementById('access-tree-labels').dataset;
        const section = element('section', null, 'access-effective');
        section.dataset.effectiveSources = 'true';
        const button = element('button', labels.showSources, 'button button-outline');
        button.type = 'button';
        const output = element('div');
        output.setAttribute('aria-live', 'polite');
        button.addEventListener('click', async () => {
            if (button.disabled) return;
            button.disabled = true;
            try {
                const value = await load();
                output.replaceChildren(element('h3', labels.source, 'h6'));
                for (const [permission, roles] of Object.entries(value.sources))
                    output.append(element('p', `${permission} ← ${roles.join(', ')}`));
                if (!Object.keys(value.sources).length) output.append(element('p', labels.empty));
            } catch (error) {
                window.VietRoute.failure(error, 'user-roles-feedback');
            } finally {
                button.disabled = false;
            }
        });
        section.append(button, output);
        host.append(section);
    };
    window.VietRouteAccessTree = { model, render, sources, systemSelection, unbound };
})();
