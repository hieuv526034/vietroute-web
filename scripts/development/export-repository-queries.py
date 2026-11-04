"""Xuất SQL native @Query từ source để kiểm trong DB editor; không kết nối DB hoặc đọc .env."""
import argparse
from pathlib import Path
import re
import sys
import textwrap


def without_comments(source):
    tokens = r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\r\n]*'
    return re.sub(tokens, lambda match: re.sub(r'[^\r\n]', ' ', match[0])
                  if match[0].startswith(('/*', '//')) else match[0], source)


def closing_parenthesis(source, start):
    depth = 1
    index = start
    while index < len(source):
        if source.startswith('"""', index):
            end = source.find('"""', index + 3)
            if end < 0:
                raise ValueError('Text block chưa đóng')
            index = end + 3
            continue
        if source[index] in ('"', "'"):
            quote = source[index]
            index += 1
            while index < len(source) and source[index] != quote:
                index += 2 if source[index] == '\\' else 1
        elif source[index] == '(':
            depth += 1
        elif source[index] == ')':
            depth -= 1
            if depth == 0:
                return index
        index += 1
    raise ValueError('Annotation @Query chưa đóng')


def java_tokens(source):
    """Tách literal trước delimiter để không cắt nhầm dấu +, phẩy hoặc chấm phẩy trong SQL."""
    return list(re.finditer(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*|\s+|.', source))


def java_literal(token):
    if token.startswith('"""'):
        value = token[3:-3]
        # Java bỏ dòng đầu sau delimiter và incidental indentation kể cả dòng delimiter đóng.
        value = value.removeprefix('\r\n').removeprefix('\n')
        value = textwrap.dedent(value)
    else:
        value = token[1:-1]
    escapes = {'n': '\n', 'r': '\r', 't': '\t', 'b': '\b', 'f': '\f', 's': ' ',
               '\\': '\\', '"': '"', "'": "'"}
    def decode(match):
        code = match[0][1:]
        if code in escapes:
            return escapes[code]
        if code.startswith('u'):
            return chr(int(code.lstrip('u'), 16))
        if code in ('\n', '\r\n'):
            return ''
        raise ValueError('Java escape chưa hỗ trợ: ' + match[0])
    return re.sub(r'\\(?:u+[0-9a-fA-F]{4}|\r?\n|.)', decode, value)


def expressions(annotation):
    parts = []
    current = []
    depth = 0
    for token in java_tokens(annotation):
        value = token[0]
        if value == ',' and depth == 0:
            parts.append(''.join(current))
            current = []
        else:
            current.append(value)
            if value in ('(', '[', '{'):
                depth += 1
            elif value in (')', ']', '}'):
                depth -= 1
    parts.append(''.join(current))
    result = {}
    for part in parts:
        match = re.fullmatch(r'\s*(\w+)\s*=([\s\S]*)', part)
        if match:
            result[match[1]] = match[2].strip()
    return result


def queries(source_root):
    sources = {path: without_comments(path.read_text(encoding='utf-8'))
               for path in sorted(source_root.rglob('*Repo.java'))}
    constants = {}
    for path, source in sources.items():
        tokens = java_tokens(source)
        for marker in re.finditer(r'(?m)^    (?:(?:public|static|final) +)*String (\w+)\s*=', source):
            value = []
            for token in tokens:
                if token.start() < marker.end():
                    continue
                if token[0] == ';':
                    break
                value.append(token[0])
            constants[(path.stem, marker[1])] = ''.join(value)

    def resolve(expression, owner, pending=()):
        result = []
        expect_value = True
        for token in java_tokens(expression):
            value = token[0]
            if value.isspace():
                continue
            if expect_value:
                if value.startswith('"'):
                    result.append(java_literal(value))
                elif re.fullmatch(r'[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)?', value):
                    reference = tuple(value.split('.')) if '.' in value else (owner, value)
                    if reference in pending or reference not in constants:
                        raise ValueError('Hằng SQL không xác định hoặc tham chiếu vòng: ' + value)
                    result.append(resolve(constants[reference], reference[0], pending + (reference,)))
                else:
                    raise ValueError('Biểu thức SQL chỉ hỗ trợ literal/hằng compile time: ' + value)
            elif value != '+':
                raise ValueError('Biểu thức SQL không hỗ trợ: ' + value)
            expect_value = not expect_value
        if expect_value:
            raise ValueError('Biểu thức SQL rỗng hoặc thiếu toán hạng')
        return ''.join(result)

    for path, source in sources.items():
        for marker in re.finditer(r'(?m)^\s*@Query\s*\(', source):
            end = closing_parenthesis(source, marker.end())
            annotation = expressions(source[marker.end():end])
            if annotation.get('nativeQuery') != 'true':
                continue
            prefix = source[end + 1:].split('(', 1)[0].strip()
            method = re.search(r'\b(\w+)\s*$', prefix)
            if method is None or '@' in prefix:
                raise ValueError(str(path) + ': không xác định method sau @Query')
            if 'value' not in annotation:
                raise ValueError(str(path) + ': thiếu SQL value')
            for kind in ('value', 'countQuery'):
                if kind not in annotation:
                    continue
                sql = resolve(annotation[kind], path.stem).strip()
                if not sql:
                    raise ValueError(str(path) + ': SQL rỗng')
                declaration = source[end + 1:].split(';', 1)[0]
                yield path, method[1], kind, sql, bool(re.search(r'\bPageable\b', declaration))


def parameters(sql, transform):
    """Chỉ thay tham số SQL thật, giữ chuỗi/comment để ví dụ không làm sai nội dung query."""
    protected = re.compile(r"'(?:''|[^'])*'|--[^\n]*|/\*[\s\S]*?\*/")
    result = []
    at = 0
    for token in protected.finditer(sql):
        result.append(re.sub(r'(?<!:):([A-Za-z_]\w*)', transform, sql[at:token.start()]))
        result.append(token[0])
        at = token.end()
    result.append(re.sub(r'(?<!:):([A-Za-z_]\w*)', transform, sql[at:]))
    return ''.join(result)


def literal(value):
    if value is None:
        return 'NULL'
    if isinstance(value, bool):
        return 'TRUE' if value else 'FALSE'
    if isinstance(value, int):
        return str(value)
    if isinstance(value, str):
        return "'" + value.replace("'", "''") + "'"
    if isinstance(value, tuple):
        return ', '.join(literal(part) for part in value) or 'NULL'
    raise ValueError('Kiểu giá trị mẫu không hỗ trợ')


def export(source_root, schema, example=False, user_id=1, page_size=20, page=0):
    if not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]{0,62}', schema):
        raise ValueError('Schema chỉ nhận identifier tối đa63 ký tự, không nhận SQL')
    if user_id < 0 or not 1 <= page_size <= 100 or page < 0:
        raise ValueError('user-id/page không âm; page-size từ1 đến100')
    result = [
        '-- Sinh từ native @Query; chạy lại exporter khi query trong Repo thay đổi.',
        '-- Giá trị minh họa đã điền; đổi ID/mã/bộ lọc theo dữ liệu cần kiểm.' if example
        else '-- Giữ :thamSo để DB editor bind giá trị; không có credential hoặc dữ liệu người dùng.',
        '-- File này không tự kết nối hoặc chạy SQL.' if example
        else '-- Các UPDATE chỉ kiểm trên DB test. File này không tự chạy SQL.',
        '',
    ]
    values = {
        'active': None, 'keyword': '%', 'code': 'ADMIN_CONSOLE', 'codes': ('ADMIN_CONSOLE',),
        'role': 'CUSTOMER', 'roleCode': None, 'userId': user_id, 'pageSize': page_size,
        'size': page_size, 'offset': page * page_size, 'id': user_id, 'user': user_id, 'viewerId': user_id,
        'viewerVersion': 0, 'tokenVersion': 0, 'scope': 1, 'scopeId': 1, 'systemId': 0,
        'username': 'admin', 'name': 'admin', 'usernamePrefix': '%', 'email': 'example@example.invalid',
        'names': ('admin',), 'viewTarget': 'GET /operations/access',
        'updateTarget': 'PUT /api/operations/trips/{id}', 'assignTarget': 'PUT /api/operations/access/users/{id}/roles',
        'carrierId': None, 'status': None, 'fromAt': None, 'toAt': None, 'driverUserId': None,
        'type': None, 'refType': None, 'parentId': 0, 'publicOnly': False, 'customerId': None,
        'tripId': 1, 'pickup': 1, 'dropoff': 2, 'routeId': 1, 'seats': 1,
        'originId': None, 'destinationId': None, 'provinceId': None,
        'fromProvinceId': None, 'toProvinceId': None, 'fromLocationId': None, 'toLocationId': None,
        'vehicleTypeId': None, 'departureFrom': None, 'departureTo': None, 'day': None,
        'date': None, 'requestKey': '00000000-0000-0000-0000-000000000000',
        'key': '00000000-0000-0000-0000-000000000000', 'carriers': '[]', 'fields': '[]', 'props': '{}',
        'fromWardId': None, 'toWardId': None, 'minPrice': None, 'maxPrice': None,
        'exceptId': 0, 'driverId': 1, 'vehicleId': 1, 'rangeStart': '2027-01-01T00:00:00Z',
        'rangeEnd': '2027-01-02T00:00:00Z', 'starting': False,
    }
    if example:
        result.extend([
            '-- Ví dụ chạy trực tiếp, chỉ SELECT; bỏ UPDATE/DELETE/INSERT và SELECT giữ khóa.',
            '-- Giá trị mẫu: userId=' + str(user_id) + ', role=CUSTOMER, code=ADMIN_CONSOLE, keyword=%.',
            '-- active=NULL: không lọc trạng thái; keyword=%: không lọc từ khóa; page bắt đầu từ0.',
            '-- Đổi ID/mã/bộ lọc theo dữ liệu cần đối chiếu. File không có password/token.',
            'BEGIN READ ONLY;', 'SET LOCAL search_path TO "' + schema + '", pg_temp;', '',
        ])
    count = 0
    for path, method, kind, sql, pageable in queries(source_root):
        if example and (not re.match(r'^SELECT\b', sql, re.I) or re.search(
                r'\bFOR\s+(?:NO\s+KEY\s+UPDATE|KEY\s+SHARE|UPDATE|SHARE)\b'
                r'|\b(?:NEXTVAL|SETVAL|SET_CONFIG|PG_NOTIFY|PG_(?:TRY_)?ADVISORY_\w*LOCK\w*)\s*\(', sql, re.I)):
            continue
        count += 1
        rendered = sql.replace('{h-schema}', '"' + schema + '".')
        if '{h-' in rendered:
            raise ValueError(str(path) + ': placeholder Hibernate chưa hỗ trợ')
        if pageable and kind != 'countQuery':
            rendered += '\nLIMIT :pageSize OFFSET :offset'
        names = set()
        parameters(rendered, lambda match: names.add(match[1]) or match[0])
        if example:
            def bind(match):
                if match[1] not in values:
                    raise ValueError(str(path) + ': chưa có giá trị mẫu cho :' + match[1])
                return literal(values[match[1]])
            rendered = parameters(rendered, bind)
        result.extend([
            '-- ' + path.stem + '.' + method + (' [count]' if kind == 'countQuery' else ''),
            '-- Params: ' + (', '.join(sorted(names)) if names else '(không có)'),
        ])
        if pageable and kind != 'countQuery':
            result.append('-- LIMIT/OFFSET tương ứng Pageable do Spring Data áp dụng khi chạy source.')
        result.extend([rendered.rstrip(';') + ';', ''])
    if count == 0:
        raise ValueError('Không tìm thấy native @Query để xuất')
    if example:
        result.append('ROLLBACK;')
    return '\n'.join(result), count


def main():
    sys.stdout.reconfigure(encoding='utf-8')
    sys.stderr.reconfigure(encoding='utf-8')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--schema', required=True, help='Schema thực tế, ví dụ vietroute')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--source-root', type=Path, default=Path('src/main/java'))
    parser.add_argument('--example', action='store_true', help='SQL chỉ đọc đã điền giá trị mẫu, chạy trực tiếp')
    parser.add_argument('--user-id', type=int, default=1, help='ID đối chiếu quyền trong ví dụ, mặc định1')
    parser.add_argument('--page-size', type=int, default=20)
    parser.add_argument('--page', type=int, default=0)
    arguments = parser.parse_args()
    try:
        sql, count = export(arguments.source_root, arguments.schema, arguments.example,
                            arguments.user_id, arguments.page_size, arguments.page)
    except (ValueError, OSError) as error:
        parser.error(str(error))
    output = arguments.output or Path('target/repository-queries' + ('-example' if arguments.example else '') + '.sql')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(sql, encoding='utf-8', newline='\n')
    print(str(count) + ' SQL → ' + str(output))


if __name__ == '__main__':
    main()
