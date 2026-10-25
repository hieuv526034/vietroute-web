/* Tập target tường minh; chọn nhóm chỉ tác động các mục hiện tại đang thấy, không tạo wildcard quyền. */
(() => {
    'use strict';
    const model = (initial, allowed) => {
        const selected = new Set(initial),
            original = new Set(initial),
            permitted = new Set(allowed);
        return {
            values: () => [...selected].sort(),
            has: (target) => selected.has(target),
            toggle(target, checked) {
                if (!checked) selected.delete(target);
                else if (permitted.has(target)) selected.add(target);
            },
            state(targets) {
                const count = targets.filter((target) => selected.has(target)).length;
                return {
                    checked: targets.length > 0 && count === targets.length,
                    partial: count > 0 && count < targets.length,
                };
            },
            changes: () => ({
                added: [...selected].filter((target) => !original.has(target)).sort(),
                removed: [...original].filter((target) => !selected.has(target)).sort(),
            }),
        };
    };
    const element = (tag, text, css) => {
        const node = document.createElement(tag);
        if (text != null) node.textContent = text;
        if (css) node.className = css;
        return node;
    };
    const render = (host, state, features, inventory, allowed, labels, query = '', changed = () => {}) => {
        const permitted = new Set(allowed),
            updates = [];
        const keyword = query.trim().toLocaleLowerCase();
        host.replaceChildren();
        const sync = () => {
            updates.forEach((update) => update());
            changed();
        };
        const checkbox = (targets, text, container) => {
            const input = element('input');
            input.type = 'checkbox';
            input.className = 'form-check-input';
            const choices = targets.filter((target) => permitted.has(target));
            input.disabled = !choices.length;
            input.setAttribute('aria-label', text);
            updates.push(() => {
                const value = state.state(targets);
                input.checked = value.checked;
                input.indeterminate = value.partial;
            });
            input.addEventListener('click', (event) => event.stopPropagation());
            input.addEventListener('change', () => {
                for (const target of choices) state.toggle(target, input.checked);
                sync();
            });
            container.append(input);
            return input;
        };
        const filtered = inventory
            .filter((item) => !item.publicRoute && item.declaredPermissions.length)
            .filter(
                (item) =>
                    !keyword ||
                    `${item.description} ${item.target} ${features.find((f) => f.code === item.function)?.name || ''}`
                        .toLocaleLowerCase()
                        .includes(keyword),
            );
        const branch = (feature, parent) => {
            const children = features.filter((item) => item.parent === feature.code);
            const own = filtered
                .filter((item) => item.function === feature.code)
                .sort((left, right) =>
                    left.kind === right.kind ? left.target.localeCompare(right.target) : left.kind === 'MENU' ? -1 : 1,
                );
            const descendants = new Set([feature.code]);
            const collect = (code) =>
                features
                    .filter((item) => item.parent === code)
                    .forEach((item) => {
                        descendants.add(item.code);
                        collect(item.code);
                    });
            collect(feature.code);
            const leaves = filtered.filter((item) => descendants.has(item.function));
            if (!leaves.length) return;
            const details = element('details', null, 'access-tree-node');
            details.open = !!keyword || !feature.path || leaves.some((item) => state.has(item.target));
            const summary = element('summary');
            checkbox(
                leaves
                    .filter((item) => !item.publicRoute && item.declaredPermissions.length)
                    .map((item) => item.target),
                feature.name,
                summary,
            );
            summary.append(element('span', feature.name), element('small', String(leaves.length), 'muted'));
            details.append(summary);
            for (const item of own) {
                const row = element('label', null, 'resource-grant-leaf');
                const assignable = !item.publicRoute && item.declaredPermissions.length;
                const caption = item.kind === 'MENU' ? labels.openMenu + ' ' + feature.name : item.description;
                const input = checkbox(assignable ? [item.target] : [], caption || item.target, row);
                if (assignable) input.dataset.resourceTarget = item.target;
                const content = element('span');
                content.append(element('strong', caption), element('code', item.target));
                if (item.publicRoute || !assignable || item.adminOnly)
                    content.append(
                        element(
                            'small',
                            item.publicRoute ? labels.public : !assignable ? labels.self : labels.admin,
                            'muted',
                        ),
                    );
                row.append(
                    content,
                    element('small', item.kind === 'MENU' ? labels.menu : labels.api, 'permission-chip'),
                );
                details.append(row);
            }
            for (const child of children) branch(child, details);
            parent.append(details);
        };
        for (const feature of features.filter((item) => !item.parent)) branch(feature, host);
        const knownTargets = new Set(inventory.map((item) => item.target));
        for (const target of state.values().filter((item) => !knownTargets.has(item))) {
            const row = element('label', null, 'resource-grant-leaf');
            const input = element('input');
            input.type = 'checkbox';
            input.checked = true;
            input.disabled = !allowed.length;
            input.addEventListener('change', () => {
                state.toggle(target, false);
                row.remove();
                sync();
            });
            row.append(input, element('span', labels.stale), element('code', target));
            host.append(row);
        }
        if (!host.childElementCount) host.append(element('p', labels.empty, 'muted'));
        sync();
    };
    if (typeof module !== 'undefined') module.exports = { model };
    if (typeof window !== 'undefined') window.VietRouteResourceGrants = { model, render };
})();
