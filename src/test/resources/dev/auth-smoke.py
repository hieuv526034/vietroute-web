"""Nghiệm thu HTTP auth thật trên loopback; credential chỉ lấy từ env test, không in token/body."""
import http.cookiejar
import json
import os
import sys
import urllib.error
import urllib.request
from urllib.parse import urlparse

base = sys.argv[1].rstrip('/')
if urlparse(base).hostname != '127.0.0.1':
    raise ValueError('Chỉ dùng server test loopback')


class Client:
    def __init__(self):
        self.cookies = http.cookiejar.CookieJar()
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))
        self.token = None

    def call(self, expected, method, path, body=None, csrf=True):
        headers = {'Content-Type': 'application/json'}
        if self.token:
            headers['Authorization'] = 'Bearer ' + self.token
        if csrf and method not in ('GET', 'HEAD'):
            value = self.call(200, 'GET', '/api/v1/auth/csrf')
            headers[value['headerName']] = value['token']
        request = urllib.request.Request(base + path, method=method, headers=headers,
                                         data=None if body is None else json.dumps(body).encode())
        try:
            response = self.http.open(request, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            status = response.code
            content = response.read()
            if status != expected:
                raise AssertionError(f'{method} {path}: expected {expected}, got {status}')
            if response.headers.get('Content-Type', '').startswith('application/json'):
                value = json.loads(content)
                if expected >= 400:
                    assert value.get('success') is False
                assert b'passwordHash' not in content and b'previousPasswordHash' not in content
                return value if 'openapi' in value else value.get('data')
            return content

    def login(self, username, password):
        self.token = None
        value = self.call(200, 'POST', '/api/v1/auth/login', {'username': username, 'password': password})
        self.token = value['accessToken']
        return value['user']


admin = Client()
admin.login(os.environ.get('CI_AUTH_USERNAME', 'admin'), os.environ['CI_AUTH_PASSWORD'])
access = '/api/v1/admin/access'
if '--after-restart' in sys.argv:
    assert admin.call(200, 'GET', access + '/roles/CI_READER/functions')['functionCodes'] == []
    print('Auth restart: giữ credential và không phục hồi function đã thu hồi')
    sys.exit(0)

admin.call(200, 'GET', '/operations/access?lang=vi')
admin.call(200, 'GET', '/operations/access?lang=en')
spec = admin.call(200, 'GET', '/v3/api-docs')
assert '/api/v1/auth/register' not in spec['paths']
assert '/api/v1/auth/admin-recovery' not in spec['paths']
assert 'UserSearchRequest' not in spec['components']['schemas']
assert 'UserRequest' in spec['components']['schemas']
admin.call(201, 'POST', access + '/registry/roles/CI_READER', {'nameVi': 'Nhân viên tra cứu', 'nameEn': 'Reader'})
target = 'GET ' + access + '/functions'
admin.call(201, 'POST', access + '/functions', {'code': 'CI_LOOKUP', 'nameVi': 'Tra cứu chức năng',
                                              'nameEn': 'Function lookup', 'targets': [target]})
admin.call(200, 'PUT', access + '/roles/CI_READER/functions', {'functionCodes': ['CI_LOOKUP'], 'version': 0})
initial_password = 'CiInitialOnly!2026Aa'
next_password = 'CiChangedOnly!2026Aa'
created = admin.call(201, 'POST', '/api/v1/admin/accounts', {'username': 'cireader2026', 'displayName': 'Nhân viên tra cứu',
                    'email': 'ci-reader@example.invalid', 'password': initial_password, 'roles': ['CI_READER']})
reader = Client()
user = reader.login('cireader2026', initial_password)
assert user['mustChangePassword'] and not user['emailVerificationRequired']
reader.call(403, 'GET', access + '/functions')
reader.call(200, 'POST', '/api/v1/auth/change-password', {'currentPassword': initial_password, 'newPassword': next_password})
reader.call(401, 'GET', '/api/v1/auth/me')
reader.login('cireader2026', next_password)
reader.call(200, 'GET', access + '/functions')
reader.call(403, 'GET', access + '/registry/roles')
reader.call(403, 'GET', '/operations/access')
admin.call(200, 'PUT', access + '/functions/CI_LOOKUP', {'code': 'CI_LOOKUP', 'nameVi': 'Tra cứu chức năng', 'nameEn': 'Function lookup',
                                                      'targets': [target, 'GET /operations/access'], 'version': 0})
reader.call(200, 'GET', '/operations/access')
role = admin.call(200, 'GET', access + '/roles/CI_READER/functions')
admin.call(200, 'PUT', access + '/roles/CI_READER/functions', {'functionCodes': [], 'version': role['version']})
reader.call(403, 'GET', access + '/functions')
reader.call(403, 'GET', '/operations/access')
current = admin.call(200, 'GET', '/api/v1/admin/accounts/' + str(created['id']))
admin.call(200, 'DELETE', '/api/v1/admin/accounts/' + str(created['id']) + '?version=' + str(current['version']))
reader.call(401, 'GET', '/api/v1/auth/me')
admin.call(404, 'POST', '/api/v1/auth/register', {})
admin.call(404, 'POST', '/api/v1/auth/admin-recovery', {})
print('Auth HTTP: JPA CRUD, đổi mật khẩu, menu/API độc lập, thu hồi, xóa mềm, Swagger và URL đã bỏ đạt')
