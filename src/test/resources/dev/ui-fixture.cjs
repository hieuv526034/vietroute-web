/* Chỉ kiểm UI ở loopback: HTML Thymeleaf thật trong target, dữ liệu giả; không DB/.env/provider hoặc token thật. */
const http = require('node:http');
const fs = require('node:fs/promises');
const path = require('node:path');
const root = process.cwd();
const preview = path.join(root, 'target/ui-preview');
const port = Number(process.env.UI_FIXTURE_PORT || 18119);
const parse = async (file) => JSON.parse(await fs.readFile(path.join(preview, file), 'utf8'));
const pages = {
    '/': 'home', '/login': 'login', '/account': 'account', '/account-access': 'lifecycle',
    '/admin': 'admin', '/media': 'media', '/monitoring': 'monitoring', '/docs': 'docs',
    '/operations/access': 'access', '/operations/accounts': 'accounts', '/operations/jobs': 'jobs',
    '/operations/configuration': 'configuration', '/operations/api-journal': 'api-journal',
};
let requestDelay = 0;
let readFailure = 0;
let conflict = false;
let pending = false;
const requests = [];
const mutations = [];
const now = () => new Date().toISOString();
const state = (permissions = [], extra = {}) => ({roles: [], permissions, mappingCount: permissions.length, delta: true, active: null, deleted: null, enabled: null, nameVi: null, nameEn: null, policyKind: null, policyTarget: null, version: 1, ...extra});
let history = Array.from({length: 87}, (_, i) => ({id: 87 - i, event: 'ROLE_PERMISSIONS', targetKind: 'ROLE', targetKey: i % 2 ? 'SUPPORT' : 'OPERATOR', actorId: i % 3 ? 1 : 0, actorUsername: i % 3 ? 'previewadmin' : 'system', traceId: `fixture-history-trace-${i + 1}`, createdAt: new Date(Date.now() - i * 60000).toISOString(), before: state(['CATALOG_READ'], {version: i}), after: state(['CATALOG_CREATE'], {version: i + 1})}));
const page = (values, query) => {
    const index = Number(query.get('page') || 0);
    const size = Number(query.get('size') || 20);
    const total = values.length;
    const pages = Math.ceil(total / size);
    return {content: values.slice(index * size, (index + 1) * size), page: index, size, totalElements: total, totalPages: pages, hasNext: index + 1 < pages, hasPrevious: index > 0};
};

(async () => {
    const viTree = (await parse('tree-vi.json')).data;
    const enTree = (await parse('tree-en.json')).data;
    const permissionNames = new Map(viTree.flatMap(feature => feature.actions.map(action => [action.permission, action.name])));
    const permissions = [...permissionNames.keys()];
    const roles = [{role: 'ADMIN', active: true, version: 0, permissions, assignedUsers: 1}, {role: 'SUPPORT', active: true, version: 2, permissions: ['CATALOG_VIEW', 'CATALOG_READ'], assignedUsers: 2}, {role: 'OPERATOR', active: true, version: 3, permissions: ['CATALOG_VIEW', 'CATALOG_CREATE', 'ARCHIVE_READ'], assignedUsers: 1}, {role: 'DRIVER', active: true, version: 0, permissions: [], assignedUsers: 5}, {role: 'CUSTOMER', active: true, version: 0, permissions: [], assignedUsers: 42}];
    const registry = roles.map(item => ({code: item.role, nameVi: {ADMIN: 'Quản trị viên', SUPPORT: 'Hỗ trợ khách hàng', OPERATOR: 'Điều hành nhà xe', DRIVER: 'Tài xế', CUSTOMER: 'Khách hàng'}[item.role], nameEn: {ADMIN: 'Administrator', SUPPORT: 'Customer support', OPERATOR: 'Carrier operator', DRIVER: 'Driver', CUSTOMER: 'Customer'}[item.role], active: item.active, builtIn: item.role === 'ADMIN', version: 0}));
    for (let i = 1; i <= 46; i++) {
        const code = `CUSTOM_${String(i).padStart(3, '0')}`;
        roles.push({role: code, active: true, version: 0, permissions: ['CATALOG_VIEW'], assignedUsers: i});
        registry.push({code, nameVi: `Vai trò thử ${i}`, nameEn: `Test role ${i}`, active: true, builtIn: false, version: 0});
    }
    const users = [{id: 1, username: 'previewadmin', displayName: 'Quản trị viên thử nghiệm', roles: ['ADMIN'], active: true, mustChangePassword: false, version: 0}, ...Array.from({length: 47}, (_, i) => ({id: i + 2, username: `member${i + 2}`, displayName: `Thành viên thử ${i + 2}`, roles: [i ? 'CUSTOMER' : 'OPERATOR'], active: true, mustChangePassword: false, version: 2}))];
    const profile = {...users[0], permissions, createdAt: now(), updatedAt: now(), avatarUrl: null, avatarMediaId: null};
    const resources = [...new Map(viTree.flatMap(feature => feature.actions.flatMap(action => action.endpoints.map(endpoint => [endpoint.target, {kind: endpoint.target.startsWith('GET /api/') ? 'ENDPOINT' : 'MENU', target: endpoint.target, group: feature.code, function: feature.code, declaredPermissions: endpoint.permissions, description: endpoint.description, handler: 'FixtureController#action', publicRoute: false, configurable: true, lastSeenAt: now()}])))).values()];
    const body = async req => {
        let value = '';
        for await (const chunk of req) { value += chunk; if (value.length > 65536) throw new Error('Fixture body too large'); }
        return value ? JSON.parse(value) : {};
    };
    const server = http.createServer(async (req, res) => {
        const url = new URL(req.url, `http://127.0.0.1:${port}`);
        const route = url.pathname;
        const respond = (data, status = 200) => {
            res.writeHead(status, {'Content-Type': 'application/json; charset=utf-8'});
            res.end(JSON.stringify({success: status < 400, code: status === 409 ? 'COM008' : status === 403 ? 'AUTH003' : status === 503 ? 'COM007' : 'OK000', message: status < 400 ? 'Fixture OK' : 'Fixture error', data, errors: [], traceId: 'fixture-request-trace', locale: url.searchParams.get('lang') || 'vi'}));
        };
        try {
            if (route === '/_fixture/state') return respond({requests, mutations, pending});
            if (route === '/_fixture/control' && req.method === 'POST') {
                const control = await body(req);
                requestDelay = Number(control.delayNext || 0);
                readFailure = Number(control.failNext || 0);
                conflict = Boolean(control.conflictNext);
                return respond({configured: true});
            }
            if (route.startsWith('/assets/') || route.startsWith('/webjars/')) {
                const prefix = route.startsWith('/assets/') ? '/assets/' : '/webjars/';
                const base = prefix === '/assets/' ? path.join(root, 'src/main/webapp/assets') : path.join(preview, 'webjars');
                const file = path.resolve(base, decodeURIComponent(route.slice(prefix.length)));
                if (!file.startsWith(base + path.sep)) return respond(null, 404);
                const extension = path.extname(file);
                res.writeHead(200, {'Content-Type': {'.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml', '.png': 'image/png', '.woff2': 'font/woff2'}[extension] || 'application/octet-stream'});
                return res.end(await fs.readFile(file));
            }
            if (pages[route]) {
                const suffix = url.searchParams.get('lang') === 'en' ? '-en' : '';
                res.writeHead(200, {'Content-Type': 'text/html; charset=utf-8'});
                return res.end(await fs.readFile(path.join(preview, pages[route] + suffix + '.html')));
            }
            if (!route.startsWith('/api/')) return respond(null, 404);
            requests.push({method: req.method, path: route, query: url.search});
            if (requests.length > 500) requests.shift();
            if (requestDelay) { const delay = requestDelay; requestDelay = 0; pending = true; await new Promise(resolve => setTimeout(resolve, delay)); pending = false; }
            if (readFailure) { const status = readFailure; readFailure = 0; return respond(null, status); }
            if (route === '/api/v1/auth/me') return respond(profile);
            if (route === '/api/v1/auth/csrf') { res.setHeader('Set-Cookie', 'XSRF-TOKEN=fixture-csrf; Path=/; SameSite=Lax'); return respond({token: 'fixture-csrf', headerName: 'X-XSRF-TOKEN'}); }
            if (route === '/api/v1/auth/capabilities') return respond((await parse('capabilities.json')).data);
            if (route === '/api/v1/admin/access/tree') return respond(url.searchParams.get('lang') === 'en' ? enTree : viTree);
            if (route === '/api/v1/admin/access/history') return respond(page(history.filter(item => (!url.searchParams.get('event') || item.event === url.searchParams.get('event')) && item.targetKey.startsWith(url.searchParams.get('target') || '') && (!url.searchParams.get('actorId') || item.actorId === Number(url.searchParams.get('actorId')))), url.searchParams));
            if (route === '/api/v1/admin/access/permissions') return respond(permissions.map(code => ({code, name: permissionNames.get(code), resource: code.slice(0, code.lastIndexOf('_')), actions: [code.slice(code.lastIndexOf('_') + 1)]})));
            if (route === '/api/v1/admin/access/roles') return respond(roles);
            if (route === '/api/v1/admin/access/users') return respond(page(users.filter(user => user.username.startsWith(url.searchParams.get('username') || '')), url.searchParams));
            if (route === '/api/v1/admin/access/resources') return respond(page(resources.filter(item => (!url.searchParams.get('kind') || item.kind === url.searchParams.get('kind')) && `${item.target} ${item.description}`.toLowerCase().includes((url.searchParams.get('query') || '').toLowerCase())), url.searchParams));
            if (route === '/api/v1/admin/access/registry/roles' && req.method === 'GET') return respond(registry);
            if (route === '/api/v1/admin/access/registry/permissions' && req.method === 'GET') return respond(permissions.map(code => ({code, nameVi: permissionNames.get(code), nameEn: code, active: true, builtIn: true, version: 0})));
            if (route === '/api/v1/admin/access/policies' || route === '/api/v1/admin/access/targets') return respond([]);
            if (route === '/api/v1/admin/accounts/options') return respond({roles, canCreate: true, canRead: true, canUpdate: true});
            if (route === '/api/v1/admin/accounts' && req.method === 'GET') return respond(page(users.filter(user => user.username.startsWith(url.searchParams.get('username') || '') && (!url.searchParams.get('role') || user.roles.includes(url.searchParams.get('role'))) && (!url.searchParams.get('active') || String(user.active) === url.searchParams.get('active'))), url.searchParams));
            if (/^\/api\/v1\/admin\/access\/roles\/[^/]+\/permissions$/.test(route) && req.method === 'PUT') {
                const input = await body(req);
                mutations.push({method: req.method, path: route});
                if (conflict) { conflict = false; return respond(null, 409); }
                const role = roles.find(item => item.role === decodeURIComponent(route.split('/').at(-2)));
                const before = role.permissions;
                role.permissions = input.permissions;
                role.version++;
                history.unshift({id: history[0].id + 1, event: 'ROLE_PERMISSIONS', targetKind: 'ROLE', targetKey: role.role, actorId: 1, actorUsername: 'previewadmin', traceId: 'fixture-new-change', createdAt: now(), before: state(before.filter(code => !input.permissions.includes(code)), {mappingCount: before.length, version: role.version - 1}), after: state(input.permissions.filter(code => !before.includes(code)), {mappingCount: role.permissions.length, version: role.version})});
                return respond(role);
            }
            return respond(null, 404);
        } catch (error) {
            if (!res.headersSent) return respond(null, 500);
            res.end();
        }
    });
    server.listen(port, '127.0.0.1', () => console.log(`UI fixture PID=${process.pid} URL=http://127.0.0.1:${port}/operations/access?lang=vi`));
    process.on('SIGINT', () => server.close(() => process.exit(0)));
    process.stdin.on('data', value => { if (value.includes(3)) server.close(() => process.exit(0)); });
})().catch(() => { console.error('Chưa có HTML fixture trong target/ui-preview; chạy RenderSystemUi trước.'); process.exitCode = 1; });
