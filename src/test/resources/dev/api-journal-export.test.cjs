const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');
const source = fs.readFileSync(path.resolve(__dirname, '../../../main/webapp/assets/js/api-journal-export.js'), 'utf8');
const context = { window: {}, URLSearchParams }; vm.runInNewContext(source, context);
const exporter = context.window.VietRouteJournalExport;
const detail = { entry: { method:'PUT', endpoint:'/api/v1/admin/catalogs/{id}', status:409 }, replayPath:'/api/v1/admin/catalogs/12', query:{lang:'vi'}, requestBody:'{"nameVi":"[REDACTED]","version":2}', responseBody:'{"code":"COM008"}' };
test('Export dùng baseUrl/token placeholder, giữ version và không có credential/script replay', () => {
 const collection = JSON.parse(JSON.stringify(exporter.collection(detail, 'Filtered debug only')));
 assert.equal(collection.item[0].request.url, '{{baseUrl}}/api/v1/admin/catalogs/12?lang=vi');
 assert.equal(collection.item[0].request.auth.bearer[0].value, '{{accessToken}}');
 assert.equal(JSON.parse(collection.item[0].request.body.raw).version, 2);
 assert(!collection.event); assert(!collection.item[0].event);
 assert(collection.variable.every(v => v.value === ''));
 assert(exporter.curl(detail).includes('Authorization: Bearer {{accessToken}}'));
 assert(!exporter.curl(detail).includes('Cookie:'));
});
test('Auth bị bỏ body thật, multipart không tự dựng payload, path template còn placeholder', () => {
 const login = {...detail, entry:{method:'POST',endpoint:'/api/v1/auth/login'},replayPath:'/api/v1/auth/login',requestBody:'{"capture":"OMITTED"}'};
 const request=exporter.collection(login,'test').item[0].request;
 assert.deepEqual(JSON.parse(request.body.raw),{username:'{{username}}',password:'{{password}}'});
 const upload={...detail,entry:{method:'POST',endpoint:'/api/v1/media'},replayPath:'/api/v1/media',requestBody:'{"capture":"OMITTED"}'};
 assert(!exporter.collection(upload,'test').item[0].request.body);
 assert(exporter.curl({...detail,replayPath:'/api/v1/admin/catalogs/{id}'}).includes('{{id}}'));
});
test('cURL quote an toàn khi payload projection có ký tự shell', () => {
 const curl=exporter.curl({...detail,requestBody:JSON.stringify({nameVi:"' ; $(echo nope)"})});
 assert(curl.includes("'\\''"));
 assert(!curl.includes('--location')); // Không tự theo redirect sang đích khác.
});
