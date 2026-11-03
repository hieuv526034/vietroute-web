/* Chỉ đọc một trang của nhóm/cha đang mở; không tải con khi dựng nhóm hoặc khi rê chuột. */
(() => {
    'use strict';
    const root = document.getElementById('category-workspace');
    if (!root) return;
    const { api, mutate, feedback, failure } = window.VietRoute;
    const table = window.VietRouteTable;
    const ui = window.VietRouteUI;
    const base = '/api/v1/admin/categories';
    const node = (id) => document.getElementById(`category-${id}`);
    const form = node('form');
    const labels = root.dataset;
    let groups = [];
    let groupPage = 0;
    let moreGroups = false;
    let targets = [];
    let draft = null;
    let busy = false;
    let placeMap = null;
    const editor = ui.modal('category-dialog', {
        busy: () => busy,
        onClose: () => {
            placeMap?.remove();
            placeMap = null;
            node('dialog').classList.remove('category-map-open');
            draft = null;
            form.reset();
        },
    });
    const can = (method, suffix = '') => targets.includes(`${method} ${base}${suffix}`);
    const run = async (action) => {
        if (busy) return;
        busy = true;
        node('controls').disabled = true;
        node('dialog-controls').disabled = true;
        root.setAttribute('aria-busy', 'true');
        if (placeMap) {
            placeMap.dragging.disable();
            placeMap.keyboard.disable();
            node('location-map').style.pointerEvents = 'none';
        }
        node('status').textContent = labels.loading;
        const target = editor.isOpen ? 'category-form-feedback' : 'category-feedback';
        feedback('', false, target);
        try {
            await action();
        } catch (error) {
            failure(error, editor.isOpen ? 'category-form-feedback' : 'category-feedback');
            if ([401, 403].includes(error.response?.status)) node('rows').replaceChildren();
        } finally {
            busy = false;
            node('controls').disabled = false;
            node('dialog-controls').disabled = false;
            root.setAttribute('aria-busy', 'false');
            if (placeMap) {
                placeMap.dragging.enable();
                placeMap.keyboard.enable();
                node('location-map').style.pointerEvents = '';
            }
            node('status').textContent = '';
        }
    };
    const pageSize = 20;
    const rowState = (item, parent = null) => ({
        item,
        parent,
        children: [],
        expanded: false,
        page: 0,
        more: false,
        query: '',
    });
    const groupFor = (code) => groups.find((entry) => entry.item.code === code)?.item;
    const response = async (suffix, params) => (await api.get(base + suffix, { params, timeout: 10000 })).data.data;
    const childGroups = (item) =>
        item.type === 'GROUP'
            ? item.props.parentRefType
                ? []
                : [item]
            : groups
                  .map((entry) => entry.item)
                  .filter(
                      (group) =>
                          group.props.parentRefType === item.refType ||
                          (group.code === item.refType && group.props.hierarchical),
                  );
    const button = (label, action, className = 'button button-outline') => {
        const element = document.createElement('button');
        element.type = 'button';
        element.className = className;
        element.textContent = label;
        element.addEventListener('click', () => void run(() => action(element)));
        return element;
    };
    const appendCell = (row, text) => {
        const cell = document.createElement('td');
        cell.textContent = text;
        row.append(cell);
        return cell;
    };
    const focusRow = (item) =>
        requestAnimationFrame(() => document.getElementById(`category-expand-${item.id}`)?.focus());
    // Chỉ đọc đúng một trang con trực tiếp; refType chỉ dùng ở cấp nhóm, parentId dùng ở nút giá trị.
    const readChildren = async (entry, page = 0) => {
        const item = entry.item;
        const result = await response('', {
            ...(item.type === 'GROUP' ? { refType: item.code } : { parentId: item.id }),
            page,
            size: pageSize,
            query: entry.query,
        });
        const children = result.content.map((child) => rowState(child, entry));
        entry.children = page ? [...entry.children, ...children] : children;
        entry.page = result.page;
        entry.more = result.hasNext;
        entry.expanded = true;
    };
    const toggle = async (entry) => {
        if (entry.expanded) {
            entry.expanded = false;
            entry.children = [];
            entry.query = '';
        } else {
            await readChildren(entry);
        }
        render();
        focusRow(entry.item);
    };
    const toolsRow = (depth) => {
        const row = document.createElement('tr');
        const cell = appendCell(row, '');
        cell.colSpan = 5;
        cell.style.paddingInlineStart = `${depth * 1.3 + 1}rem`;
        node('rows').append(row);
        return cell;
    };
    const filterBranch = (entry, depth) => {
        const filter = document.createElement('form');
        filter.className = 'category-branch-filter';
        const query = document.createElement('input');
        query.type = 'search';
        query.className = 'form-control';
        query.maxLength = 160;
        query.value = entry.query;
        query.placeholder = labels.search;
        query.setAttribute('aria-label', `${labels.search} · ${entry.item.name}`);
        const submit = document.createElement('button');
        submit.type = 'submit';
        submit.className = 'button button-outline';
        submit.textContent = labels.filter;
        filter.append(query, submit);
        filter.addEventListener('submit', (event) => {
            event.preventDefault();
            void run(async () => {
                entry.query = query.value.trim();
                await readChildren(entry);
                render();
                focusRow(entry.item);
            });
        });
        toolsRow(depth).append(filter);
    };
    const draw = (entry, depth) => {
        const item = entry.item;
        const children = childGroups(item);
        const row = document.createElement('tr');
        row.dataset.categoryId = item.id;
        row.dataset.depth = depth;
        appendCell(row, item.props.administrativeCode || item.code);
        const name = appendCell(row, '');
        name.style.paddingInlineStart = `${depth * 1.3 + 0.65}rem`;
        if (children.length) {
            const expand = button(
                `${entry.expanded ? '▾' : '▸'} ${item.name}`,
                () => toggle(entry),
                'button button-outline category-expand',
            );
            expand.id = `category-expand-${item.id}`;
            expand.setAttribute('aria-expanded', String(entry.expanded));
            name.append(expand);
        } else {
            name.textContent = item.name;
        }
        const details =
            item.props.seatCount != null
                ? `${labels.seatCount}: ${item.props.seatCount}`
                : item.props.address ||
                  item.props.divisionType ||
                  (item.props.parentRefType
                      ? `${labels.parentLabel} ${groupFor(item.props.parentRefType)?.name || item.props.parentRefType}`
                      : '—');
        appendCell(row, details);
        appendCell(row, item.active ? labels.active : labels.stop);
        const cell = appendCell(row, '');
        cell.className = 'table-actions';
        const actions = document.createElement('div');
        actions.className = 'row-actions';
        if (item.active && can('POST'))
            for (const group of children.filter((group) => group.active)) {
                const create = button(`+ ${group.name}`, (trigger) => openEditor('VALUE', null, group, entry, trigger));
                create.title = `${labels.create}: ${group.name}`;
                create.setAttribute('aria-label', create.title);
                actions.append(create);
            }
        if (can('PUT', '/{id}') && can('GET', '/{id}'))
            actions.append(
                table.actionButton(
                    `${labels.edit}: ${item.name}`,
                    'edit',
                    (trigger) =>
                        run(async () => {
                            const detail = await response(`/${item.id}`);
                            openEditor(item.type, { entry, detail }, groupFor(item.refType), entry.parent, trigger);
                        }),
                    false,
                ),
            );
        if (can('DELETE', '/{id}') && item.active)
            actions.append(
                table.actionButton(
                    `${labels.stop}: ${item.name}`,
                    'delete',
                    () =>
                        run(async () => {
                            if (!window.confirm(`${labels.stop}: ${item.name}?`)) return;
                            entry.item = (
                                await mutate(`${base}/${item.id}`, { version: item.version }, 'delete')
                            ).data.data;
                            render();
                            focusRow(entry.item);
                        }),
                    true,
                ),
            );
        cell.append(actions);
        node('rows').append(row);
        if (!entry.expanded) return;
        if (entry.more || entry.query || entry.page > 0) filterBranch(entry, depth + 1);
        if (!entry.children.length) toolsRow(depth + 1).textContent = labels.empty;
        for (const child of entry.children) draw(child, depth + 1);
        if (entry.more)
            toolsRow(depth + 1).append(
                button(labels.more, async () => {
                    const previous = entry.children.length;
                    await readChildren(entry, entry.page + 1);
                    render();
                    const first = entry.children[previous];
                    if (first) {
                        const row = node('rows').querySelector(`[data-category-id="${first.item.id}"]`);
                        row?.querySelector('button')?.focus();
                    }
                }),
            );
    };
    const render = () => {
        node('rows').replaceChildren();
        for (const entry of groups) draw(entry, 0);
        if (moreGroups) toolsRow(0).append(button(labels.more, () => loadGroups(groupPage + 1)));
        if (!groups.length) toolsRow(0).textContent = labels.empty;
    };
    // Bản đồ chỉ khởi tạo khi người dùng mở; không theo dõi thiết bị hoặc tự gửi tọa độ.
    const mapPicker = (container) => {
        const latitude = node('prop-latitude'),
            longitude = node('prop-longitude');
        if (!latitude || !longitude) return;
        latitude.readOnly = true;
        longitude.readOnly = true;
        const section = document.createElement('section'),
            canvas = document.createElement('div'),
            hint = document.createElement('p');
        section.className = 'category-map-picker';
        canvas.id = 'category-location-map';
        canvas.className = 'category-location-map';
        canvas.hidden = true;
        canvas.setAttribute('aria-label', labels.mapHelp);
        hint.textContent = labels.mapHelp;
        hint.className = 'text-muted';
        hint.setAttribute('role', 'status');
        const open = button(labels.mapOpen, () => {
            if (placeMap) return;
            node('dialog').classList.add('category-map-open');
            canvas.hidden = false;
            open.setAttribute('aria-expanded', 'true');
            const hasPoint = latitude.value !== '' && longitude.value !== '';
            placeMap = window.L.map(canvas).setView(
                hasPoint ? [Number(latitude.value), Number(longitude.value)] : [16.1, 106.2],
                hasPoint ? 16 : 6,
            );
            const tiles = window.L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19,
                attribution:
                    '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors',
            }).addTo(placeMap);
            tiles.on('tileerror', () => {
                hint.textContent = labels.mapError;
            });
            let marker = null;
            const choose = (point) => {
                if (busy) return;
                latitude.value = point.lat.toFixed(6);
                longitude.value = point.lng.toFixed(6);
                if (marker) marker.setLatLng(point);
                else {
                    marker = window.L.marker(point, { draggable: true, title: labels.mapHelp }).addTo(placeMap);
                    marker.on('dragend', () => choose(marker.getLatLng()));
                }
                hint.textContent = `${labels.latitude}: ${latitude.value} · ${labels.longitude}: ${longitude.value}`;
            };
            if (hasPoint) {
                marker = window.L.marker([Number(latitude.value), Number(longitude.value)], {
                    draggable: true,
                    title: labels.mapHelp,
                }).addTo(placeMap);
                marker.on('dragend', () => choose(marker.getLatLng()));
            }
            placeMap.on('click', (event) => choose(event.latlng));
            const center = document.createElement('button');
            center.type = 'button';
            center.className = 'button button-outline';
            center.textContent = labels.mapCenter;
            center.addEventListener('click', () => choose(placeMap.getCenter()));
            section.append(center);
            requestAnimationFrame(() => placeMap.invalidateSize());
        });
        open.setAttribute('aria-controls', canvas.id);
        open.setAttribute('aria-expanded', 'false');
        section.append(hint, open, canvas);
        container.append(section);
    };
    const openEditor = (type, selection, group, parent, trigger) => {
        const item = selection?.detail;
        draft = { type, item, group, parent, entry: selection?.entry };
        form.reset();
        node('editor-title').textContent = item ? labels.edit : labels.create;
        node('editor-context').textContent =
            type === 'GROUP' ? labels.group : `${group.name} · ${parent?.item.name || group.name}`;
        form.elements.code.value = item?.code || '';
        form.elements.code.disabled = Boolean(item);
        form.elements.name.value = item?.nameVi || '';
        form.elements.nameEn.value = item?.nameEn || '';
        form.elements.description.value = item?.description || '';
        form.elements.active.checked = item?.active ?? true;
        form.elements.sortOrder.value = item?.sortOrder ?? 0;
        node('metadata').hidden = type !== 'GROUP';
        const { nameEn, ...metadata } = item?.props || { fields: [] };
        form.elements.metadata.value = JSON.stringify(metadata, null, 2);
        placeMap?.remove();
        placeMap = null;
        const props = node('props');
        props.dataset.location = String(type === 'VALUE' && group.code === 'LOCATION');
        props.replaceChildren();
        props.hidden = type === 'GROUP';
        if (type === 'VALUE')
            for (const field of draft.group.props.fields || []) {
                const container = document.createElement('div');
                const label = document.createElement('label');
                const input = document.createElement(field.values ? 'select' : 'input');
                input.id = `category-prop-${field.name}`;
                input.dataset.prop = field.name;
                input.dataset.type = field.type;
                label.htmlFor = input.id;
                label.textContent =
                    field.name === 'seatCount'
                        ? labels.seatCount
                        : field.name === 'latitude'
                          ? labels.latitude
                          : field.name === 'longitude'
                            ? labels.longitude
                            : field.name === 'address'
                              ? labels.address
                              : field.name;
                input.required = Boolean(field.required);
                input.className = 'form-control';
                if (field.values) {
                    input.append(new Option('—', ''));
                    for (const value of field.values) input.append(new Option(String(value), String(value)));
                } else if (field.type === 'boolean') {
                    input.type = 'checkbox';
                    input.required = false;
                    input.checked = Boolean(item?.props[field.name]);
                } else if (field.type === 'number') {
                    input.type = 'number';
                    input.step = field.scale == null ? 'any' : String(10 ** -field.scale);
                    if (field.min != null) input.min = field.min;
                    if (field.max != null) input.max = field.max;
                } else if (field.maxLength != null) input.maxLength = field.maxLength;
                input.value = item?.props[field.name] ?? '';
                container.append(label, input);
                props.append(container);
            }
        if (type === 'VALUE' && group.code === 'LOCATION') mapPicker(props);
        feedback('', false, 'category-form-feedback');
        editor.open(trigger);
        requestAnimationFrame(() => form.elements.name.focus());
    };
    const loadGroups = async (page = 0) => {
        if (!can('GET', '/groups') || !can('GET')) {
            feedback(labels.noRead, false, 'category-feedback');
            return;
        }
        const result = await response('/groups', { page, size: 100 });
        const entries = result.content.map((item) => rowState(item));
        groups = page ? [...groups, ...entries] : entries;
        groupPage = result.page;
        moreGroups = result.hasNext;
        render();
        node('add-group').hidden = !can('POST');
    };
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        if (!draft || !window.VietRouteForms.validate(form)) return;
        if (
            draft.type === 'VALUE' &&
            draft.group.code === 'LOCATION' &&
            (!node('prop-latitude').value || !node('prop-longitude').value)
        ) {
            feedback(labels.mapRequired, false, 'category-form-feedback');
            return;
        }
        void run(async () => {
            const { type, item, group, parent, entry } = draft;
            let props = {};
            if (type === 'GROUP') {
                try {
                    props = JSON.parse(form.elements.metadata.value);
                    if (!props || Array.isArray(props) || typeof props !== 'object') throw new Error();
                } catch {
                    feedback(labels.invalid, false, 'category-form-feedback');
                    return;
                }
            } else
                for (const field of node('props').querySelectorAll('[data-prop]')) {
                    if (field.type === 'checkbox') props[field.dataset.prop] = field.checked;
                    else if (field.value !== '')
                        props[field.dataset.prop] =
                            field.dataset.type === 'number'
                                ? Number(field.value)
                                : field.dataset.type === 'boolean'
                                  ? field.value === 'true'
                                  : field.value.trim();
                }
            if (form.elements.nameEn.value.trim()) props.nameEn = form.elements.nameEn.value.trim();
            const code = form.elements.code.value.trim();
            const body = {
                code,
                type,
                refType: type === 'GROUP' ? code : group.code,
                name: form.elements.name.value.trim(),
                description: form.elements.description.value.trim(),
                parentId: type === 'VALUE' && parent?.item.type === 'VALUE' ? parent.item.id : null,
                props,
                active: form.elements.active.checked,
                sortOrder: Number(form.elements.sortOrder.value),
                ...(item ? { version: item.version } : {}),
            };
            const result = await mutate(item ? `${base}/${item.id}` : base, body, item ? 'put' : 'post');
            node('dialog').close();
            feedback(result.data.message, true, 'category-feedback');
            if (entry) {
                entry.item = result.data.data;
                render();
                focusRow(entry.item);
            } else if (type === 'GROUP') {
                await loadGroups();
            } else {
                await readChildren(parent);
                render();
                focusRow(parent.item);
            }
        });
    });
    node('add-group').addEventListener('click', (event) => openEditor('GROUP', null, null, null, event.currentTarget));
    void run(async () => {
        targets = (await window.VietRouteAccess.load()).targets;
        await loadGroups();
    });
})();
