/* Tab chọn nhóm danh mục. Cây tải từng nhánh, tìm kiếm dựng lại đường dẫn tổ tiên. */
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
    let tab = null;
    let roots = [];
    let query = '';
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
    const rowState = (item, parent = null) => ({ item, parent, children: [], expanded: false, loaded: false });
    const groupFor = (code) => groups.find((group) => group.code === code);
    const response = async (suffix, params) => (await api.get(base + suffix, { params, timeout: 10000 })).data.data;
    const canEdit = (item) => item.refType === tab.code || (tab.code === 'WARD' && item.refType === 'PROVINCE');
    const childGroup = (item) =>
        tab.props.parentRefType === item.refType || (tab.code === item.refType && tab.props.hierarchical) ? tab : null;
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
        requestAnimationFrame(() => {
            const row = node('rows').querySelector(`[data-category-id="${item.id}"]`);
            row?.querySelector('button')?.focus();
        });
    const readChildren = async (entry) => {
        const result = await response('', { tree: true, refType: tab.code, parentId: entry.item.id });
        entry.children = result.content.map((item) => rowState(item, entry));
        entry.loaded = true;
        entry.expanded = true;
    };
    const toggle = async (entry) => {
        if (entry.expanded) entry.expanded = false;
        else if (entry.loaded) entry.expanded = true;
        else await readChildren(entry);
        render();
        focusRow(entry.item);
    };
    const emptyRow = (depth) => {
        const row = document.createElement('tr');
        const cell = appendCell(row, labels.empty);
        cell.colSpan = 5;
        cell.className = 'category-empty';
        cell.style.paddingInlineStart = depth * 1.3 + 1 + 'rem';
        node('rows').append(row);
    };
    const buildTree = (items) => {
        const entries = new Map(items.map((item) => [item.id, rowState(item)]));
        const top = [];
        for (const entry of entries.values()) {
            const parent = entries.get(entry.item.parentId);
            if (parent) {
                entry.parent = parent;
                parent.children.push(entry);
                parent.expanded = true;
                parent.loaded = true;
            } else top.push(entry);
        }
        return top;
    };
    const draw = (entry, depth) => {
        const item = entry.item;
        const child = childGroup(item);
        const row = document.createElement('tr');
        row.dataset.categoryId = item.id;
        row.dataset.depth = depth;
        row.classList.toggle('category-match', Boolean(item.searchMatch));
        row.classList.toggle('category-context', Boolean(query && !item.searchMatch));
        appendCell(row, item.props.administrativeCode || item.code);
        const name = appendCell(row, '');
        name.style.paddingInlineStart = `${depth * 1.3 + 0.65}rem`;
        if (item.hasChildren || entry.children.length) {
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
        if (item.active && child?.active && can('POST')) {
            actions.append(
                table.actionButton(`${labels.create}: ${child.name} · ${item.name}`, 'add', (trigger) =>
                    openEditor('VALUE', null, child, entry, trigger),
                ),
            );
        }
        if (canEdit(item) && can('PUT', '/{id}') && can('GET', '/{id}'))
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
        if (canEdit(item) && can('DELETE', '/{id}') && item.active)
            actions.append(
                table.actionButton(
                    `${labels.stop}: ${item.name}`,
                    'delete',
                    () =>
                        run(async () => {
                            if (!window.confirm(`${labels.stop}: ${item.name}?`)) return;
                            const updated = (await mutate(`${base}/${item.id}`, { version: item.version }, 'delete'))
                                .data.data;
                            entry.item = { ...updated, hasChildren: item.hasChildren, searchMatch: item.searchMatch };
                            render();
                            focusRow(entry.item);
                        }),
                    true,
                ),
            );
        cell.append(actions);
        node('rows').append(row);
        if (!entry.expanded) return;
        if (!entry.children.length) emptyRow(depth + 1);
        for (const child of entry.children) draw(child, depth + 1);
    };
    const render = () => {
        node('rows').replaceChildren();
        for (const entry of roots) draw(entry, 0);
        if (!roots.length) emptyRow(0);
    };
    const tabLabel = (group) => (group.code === 'WARD' && groupFor('PROVINCE') ? labels.geography : group.name);
    const rootGroup = () => (tab?.code === 'WARD' ? groupFor('PROVINCE') || tab : tab);
    const renderTabs = () => {
        const tabs = node('tabs');
        tabs.replaceChildren();
        for (const group of groups) {
            if (group.code === 'PROVINCE' && groupFor('WARD')?.props.parentRefType === 'PROVINCE') continue;
            const entry = button(tabLabel(group), async () => {
                await loadTree(group, '');
                document.getElementById('category-tab-' + group.id)?.focus();
            });
            const selected = group.id === tab?.id;
            entry.id = 'category-tab-' + group.id;
            entry.setAttribute('role', 'tab');
            entry.setAttribute('aria-controls', 'category-panel');
            entry.setAttribute('aria-selected', String(selected));
            entry.tabIndex = selected ? 0 : -1;
            tabs.append(entry);
        }
        if (tab) node('panel').setAttribute('aria-labelledby', 'category-tab-' + tab.id);
        const group = rootGroup();
        node('add-value').hidden = !(group?.active && !group.props.parentRefType && can('POST'));
        node('edit-group').hidden = !(tab && can('GET', '/{id}') && can('PUT', '/{id}'));
        node('add-group').hidden = !can('POST');
        node('search').hidden = !tab;
        node('clear-search').hidden = !query;
        node('tree-title').textContent = tab ? tabLabel(tab) : labels.group;
        node('tree-help').textContent = tab?.props.parentRefType ? labels.branchHelp : labels.searchHelp;
    };
    const loadTree = async (group, text) => {
        const result = await response('', { tree: true, refType: group.code, ...(text ? { query: text } : {}) });
        tab = group;
        query = text;
        roots = buildTree(result.content);
        node('keyword').value = text;
        node('result-summary').textContent = text
            ? labels.matches.replace('{0}', String(result.content.filter((item) => item.searchMatch).length))
            : '';
        renderTabs();
        render();
    };
    node('tabs').addEventListener('keydown', (event) => {
        const entries = Array.from(node('tabs').querySelectorAll('[role="tab"]'));
        const index = entries.indexOf(event.target);
        if (index < 0 || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
        event.preventDefault();
        const next =
            event.key === 'Home'
                ? 0
                : event.key === 'End'
                  ? entries.length - 1
                  : (index + (event.key === 'ArrowRight' ? 1 : -1) + entries.length) % entries.length;
        entries[next].click();
    });
    node('search').addEventListener('submit', (event) => {
        event.preventDefault();
        if (tab) void run(() => loadTree(tab, node('keyword').value.trim()));
    });
    node('clear-search').addEventListener(
        'click',
        () =>
            void run(async () => {
                await loadTree(tab, '');
                requestAnimationFrame(() => node('keyword').focus());
            }),
    );
    // Chỉ tra cứu chuỗi người dùng gửi, giữ nguyên địa chỉ nhập tay và ghim khi truy vấn lỗi.
    const mapSearch = (map, choose) => {
        const search = node('map-search').content.firstElementChild.cloneNode(true);
        const input = search.querySelector('input');
        const submit = search.querySelector('[data-map-submit]');
        const status = search.querySelector('[data-map-status]');
        const results = search.querySelector('[data-map-results]');
        let request = null;
        map.on('unload', () => request?.abort());
        const find = async () => {
            if (busy || request) return;
            const keyword = input.value.trim();
            results.replaceChildren();
            results.hidden = true;
            if (keyword.length < 3) {
                status.textContent = labels.mapSearchShort;
                return;
            }
            request = new AbortController();
            const timer = setTimeout(() => request.abort(), 6000);
            submit.disabled = true;
            input.readOnly = true;
            status.textContent = labels.loading;
            try {
                const url = new URL('https://photon.komoot.io/api/');
                url.search = new URLSearchParams({ q: keyword, countrycode: 'VN', limit: '5' });
                const response = await fetch(url, {
                    credentials: 'omit',
                    referrerPolicy: 'no-referrer',
                    signal: request.signal,
                });
                if (!response.ok) throw new Error();
                const { features } = await response.json();
                if (placeMap !== map) return;
                for (const feature of features.slice(0, 5)) {
                    const coordinates = feature?.geometry?.coordinates;
                    if (!Array.isArray(coordinates)) continue;
                    const [lng, lat] = coordinates;
                    if (!Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180)
                        continue;
                    const { name, housenumber, street, locality, district, city, state } = feature.properties || {};
                    const address = [housenumber, street].filter(Boolean).join(' ');
                    const title = [...new Set([name, address, locality, district, city, state].filter(Boolean))].join(
                        ', ',
                    );
                    if (!title) continue;
                    const item = document.createElement('li');
                    const option = document.createElement('button');
                    option.type = 'button';
                    option.className = 'button button-outline';
                    option.textContent = title;
                    option.addEventListener('click', () => {
                        if (busy) return;
                        map.setView([lat, lng], 17);
                        choose({ lat, lng });
                    });
                    item.append(option);
                    results.append(item);
                }
                results.hidden = results.childElementCount === 0;
                status.textContent = results.hidden ? labels.mapSearchEmpty : labels.mapSearchResults;
            } catch {
                if (placeMap === map) status.textContent = labels.mapSearchError;
            } finally {
                clearTimeout(timer);
                request = null;
                input.readOnly = false;
                submit.disabled = false;
            }
        };
        input.addEventListener('keydown', (event) => {
            if (event.key !== 'Enter' || event.isComposing) return;
            event.preventDefault();
            void find();
        });
        input.addEventListener('input', () => {
            results.hidden = true;
            results.replaceChildren();
            status.textContent = '';
        });
        submit.addEventListener('click', () => void find());
        return search;
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
            const map = placeMap;
            const search = mapSearch(map, choose);
            canvas.before(search);
            open.hidden = true;
            const center = document.createElement('button');
            center.type = 'button';
            center.className = 'button button-outline';
            center.textContent = labels.mapCenter;
            center.addEventListener('click', () => choose(placeMap.getCenter()));
            section.append(center);
            requestAnimationFrame(() => {
                if (placeMap !== map) return;
                map.invalidateSize();
                search.querySelector('input').focus();
            });
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
    const loadGroups = async () => {
        if (!can('GET', '/groups') || !can('GET')) {
            feedback(labels.noRead, false, 'category-feedback');
            return;
        }
        const result = await response('/groups', { tree: true });
        groups = result.content;
        const selected = groupFor(tab?.code) || groupFor('WARD') || groups[0];
        if (selected) await loadTree(selected, '');
        else {
            tab = null;
            roots = [];
            renderTabs();
            render();
        }
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
            if (type === 'GROUP') await loadGroups();
            else if (query) await loadTree(tab, query);
            else if (entry) {
                entry.item = { ...result.data.data, hasChildren: entry.item.hasChildren };
                render();
                focusRow(entry.item);
            } else if (parent) {
                parent.item.hasChildren = true;
                await readChildren(parent);
                render();
                focusRow(parent.item);
            } else await loadTree(tab, '');
        });
    });
    const toolbarAction = (id, label, icon, action) => {
        const control = table.actionButton(label, icon, action);
        control.id = `category-${id}`;
        control.hidden = true;
        node('actions').append(control);
    };
    toolbarAction('add-value', labels.create, 'add', (trigger) =>
        openEditor('VALUE', null, rootGroup(), null, trigger),
    );
    toolbarAction('edit-group', labels.editGroup, 'edit', (trigger) =>
        run(async () => {
            const detail = await response('/' + tab.id);
            openEditor('GROUP', { detail }, tab, null, trigger);
        }),
    );
    toolbarAction('add-group', labels.addGroup, 'addGroup', (trigger) =>
        openEditor('GROUP', null, null, null, trigger),
    );
    void run(async () => {
        targets = (await window.VietRouteAccess.load()).targets;
        await loadGroups();
    });
})();
