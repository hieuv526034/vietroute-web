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


def queries(source_root):
    for path in sorted(source_root.rglob('*Repo.java')):
        source = without_comments(path.read_text(encoding='utf-8'))
        for marker in re.finditer(r'(?m)^\s*@Query\s*\(', source):
            end = closing_parenthesis(source, marker.end())
            annotation = source[marker.end():end]
            if not re.search(r'\bnativeQuery\s*=\s*true\b', annotation):
                continue
            prefix = source[end + 1:].split('(', 1)[0].strip()
            method = re.search(r'\b(\w+)\s*$', prefix)
            if method is None or '@' in prefix:
                raise ValueError(str(path) + ': không xác định method sau @Query')
            blocks = list(re.finditer(r'\b(value|countQuery)\s*=\s*"""([\s\S]*?)"""', annotation))
            if not blocks or not any(block[1] == 'value' for block in blocks):
                raise ValueError(str(path) + ': native @Query phải viết value bằng text block đầy đủ')
            for block in blocks:
                sql = textwrap.dedent(block[2]).strip()
                if not sql:
                    raise ValueError(str(path) + ': SQL rỗng')
                yield path, method[1], block[1], sql


def export(source_root, schema):
    if not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]{0,62}', schema):
        raise ValueError('Schema chỉ nhận identifier tối đa63 ký tự, không nhận SQL')
    result = [
        '-- Sinh từ native @Query; chạy lại exporter khi query trong Repo thay đổi.',
        '-- Giữ :thamSo để DB editor bind giá trị; không có credential hoặc dữ liệu người dùng.',
        '-- Các UPDATE chỉ kiểm trên DB test. File này không tự chạy SQL.',
        '',
    ]
    count = 0
    for path, method, kind, sql in queries(source_root):
        count += 1
        rendered = sql.replace('{h-schema}', '"' + schema + '".')
        if '{h-' in rendered:
            raise ValueError(str(path) + ': placeholder Hibernate chưa hỗ trợ')
        parameters = sorted(set(re.findall(r'(?<!:):([A-Za-z_]\w*)', rendered)))
        result.extend([
            '-- ' + path.stem + '.' + method + (' [count]' if kind == 'countQuery' else ''),
            '-- Params: ' + (', '.join(parameters) if parameters else '(không có)'),
            rendered.rstrip(';') + ';', '',
        ])
    if count == 0:
        raise ValueError('Không tìm thấy native @Query để xuất')
    return '\n'.join(result), count


def main():
    sys.stdout.reconfigure(encoding='utf-8')
    sys.stderr.reconfigure(encoding='utf-8')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--schema', required=True, help='Schema thực tế, ví dụ vietroute')
    parser.add_argument('--output', type=Path, default=Path('target/repository-queries.sql'))
    parser.add_argument('--source-root', type=Path, default=Path('src/main/java'))
    arguments = parser.parse_args()
    try:
        sql, count = export(arguments.source_root, arguments.schema)
    except (ValueError, OSError) as error:
        parser.error(str(error))
    arguments.output.parent.mkdir(parents=True, exist_ok=True)
    arguments.output.write_text(sql, encoding='utf-8', newline='\n')
    print(str(count) + ' SQL → ' + str(arguments.output))


if __name__ == '__main__':
    main()
