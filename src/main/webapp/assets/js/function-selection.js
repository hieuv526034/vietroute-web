/* Bản nháp endpoint trong modal: giữ lựa chọn ngoài bộ lọc, chỉ thêm endpoint trong trần được cấp. */
((factory) => {
    const value = factory();
    if (typeof module === 'object' && module.exports) module.exports = value;
    else window.VietRouteFunctionSelection = value;
})(() => {
    'use strict';
    const model = (initial = [], allowed = []) => {
        const original = new Set(initial),
            selected = new Set(initial),
            ceiling = new Set(allowed);
        const values = () => [...selected].sort();
        return {
            has: (target) => selected.has(target),
            get size() {
                return selected.size;
            },
            [Symbol.iterator]: () => selected[Symbol.iterator](),
            toggle(target, checked) {
                if (!checked) selected.delete(target);
                else if (ceiling.has(target)) selected.add(target);
            },
            values,
            changes: () => ({
                added: values().filter((target) => !original.has(target)),
                removed: [...original].filter((target) => !selected.has(target)).sort(),
            }),
            state(targets) {
                const count = targets.filter((target) => selected.has(target)).length;
                return {
                    checked: targets.length > 0 && count === targets.length,
                    partial: count > 0 && count < targets.length,
                };
            },
        };
    };
    return { model };
});
