/* Ca âm kiểm cơ chế chặn lệch quy ước; chỉ dùng chuỗi fixture, không đọc env/DB hoặc sửa source. */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const {
    checkDocs,
    checkBridges,
    checkJava,
    localTarget,
} = require('../../../../scripts/development/check-repository.cjs');

test('Link bàn giao độc lập máy và chặn đường dẫn thoát repo, đường dẫn tuyệt đối', () => {
    assert.equal(localTarget('docs/README.md', '../src/main/App.java#L2'), 'src/main/App.java');
    assert.equal(localTarget('docs/README.md', '<images/a%20b.png>'), 'docs/images/a b.png');
    assert.equal(localTarget('docs/README.md', 'https://example.com/docs'), null);
    assert.equal(localTarget('docs/README.md', '#nhom-a'), null);
    for (const invalid of [
        '../../outside.md',
        '/Users/member/repo.md',
        'C:/Users/member/file.md',
        'file:///Users/member/file.md',
        '..%2F..%2Foutside.md',
    ]) {
        assert.throws(() => localTarget('docs/README.md', invalid));
    }
});

test('Mục lục thiếu file, link ảnh hỏng và target chỉ có local đều bị phát hiện', () => {
    const contents = {
        'docs/README.md': '[Hướng dẫn](01-bat-dau/GUIDE.md)\n![Ảnh](images/missing.png)\n[Local](../.tools/preview.md)',
        'docs/01-bat-dau/GUIDE.md': '```text\n[Ví dụ](khong-ton-tai.md)\n```\n[Đọc thêm](../README.md)',
        'docs/02-phat-trien/ORPHAN.md': '# Chưa có đường đọc',
    };
    const result = checkDocs(Object.keys(contents), (file) => contents[file]);
    assert.equal(result.failures.length, 3);
    assert.ok(result.failures.some((failure) => failure.includes('images/missing.png')));
    assert.ok(result.failures.some((failure) => failure.includes('.tools/preview.md')));
    assert.ok(result.failures.some((failure) => failure.includes('ORPHAN.md')));
    assert.equal(
        checkDocs(['docs/README.md', 'docs/01-bat-dau/GUIDE.md'], (file) =>
            file.endsWith('README.md') ? '[Hướng dẫn](01-bat-dau/GUIDE.md)' : '# Hướng dẫn',
        ).failures.length,
        0,
    );
});

test('Mục lục gọn dẫn tới cụm thư mục; chặn Markdown mới rải ở gốc hoặc cụm bị bỏ quên', () => {
    const contents = {
        'docs/README.md': '[Bắt đầu](01-bat-dau/)\n[Mục lục](./)',
        'docs/01-bat-dau/START.md': '# Bắt đầu',
        'docs/01-bat-dau/TOOLS.md': '# Công cụ',
    };
    const read = (file) => contents[file];
    assert.deepEqual(checkDocs(Object.keys(contents), read).failures, []);
    contents['docs/02-phat-trien/LOST.md'] = '# Cụm chưa có trong mục lục';
    contents['docs/LOOSE.md'] = '# Tài liệu sai vị trí';
    const failures = checkDocs(Object.keys(contents), read).failures;
    assert.ok(failures.some((failure) => failure.includes('LOST.md')));
    assert.ok(failures.some((failure) => failure.includes('LOOSE.md') && failure.includes('chỉ giữ README.md')));
});

test('File chỉ dẫn công cụ phải dùng chung AGENTS và giữ Cursor alwaysApply', () => {
    const contents = {
        'AGENTS.md': '# Quy tắc',
        'docs/06-ai-va-ban-giao/AI_VA_BAN_GIAO.md': '# AI',
        'docs/README.md': '# Mục lục',
        'docs/05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md': '# Tiến độ',
        'CLAUDE.md': '@AGENTS.md\n[AI](docs/06-ai-va-ban-giao/AI_VA_BAN_GIAO.md)',
        'GEMINI.md': '@./AGENTS.md\n[AI](docs/06-ai-va-ban-giao/AI_VA_BAN_GIAO.md)',
        '.github/copilot-instructions.md': '[Quy tắc](../AGENTS.md)\n[AI](../docs/06-ai-va-ban-giao/AI_VA_BAN_GIAO.md)',
        '.cursor/rules/vietroute.mdc':
            '---\nalwaysApply: true\n---\n@AGENTS.md\n[AI](../../docs/06-ai-va-ban-giao/AI_VA_BAN_GIAO.md)',
    };
    const read = (file) => contents[file];
    assert.deepEqual(checkBridges(Object.keys(contents), read), []);
    contents['CLAUDE.md'] = '# Quy tắc riêng không còn dẫn về nguồn chung';
    contents['.cursor/rules/vietroute.mdc'] = contents['.cursor/rules/vietroute.mdc'].replace(
        'alwaysApply: true',
        'alwaysApply: false',
    );
    const failures = checkBridges(
        Object.keys(contents).filter((file) => file !== 'GEMINI.md'),
        read,
    );
    assert.ok(failures.some((failure) => failure.includes('GEMINI.md')));
    assert.ok(failures.some((failure) => failure.includes('CLAUDE.md')));
    assert.ok(failures.some((failure) => failure.includes('alwaysApply')));
});

test('Chặn DTO/import/Date/Timestamp sai ở contract; vẫn cho Timestamp tại JDBC', () => {
    assert.deepEqual(
        checkJava(
            'src/main/java/demo/dto/GoodDTO.java',
            'import java.time.Instant; public record GoodDTO(Instant createdAt) {}',
        ),
        [],
    );
    assert.ok(
        checkJava('src/main/java/demo/dto/BadDto.java', 'public class BadDto {}').some((failure) =>
            failure.includes('DTO'),
        ),
    );
    assert.ok(
        checkJava('src/main/java/demo/App.java', 'import static java.util.Objects.*;').some((failure) =>
            failure.includes('wildcard'),
        ),
    );
    assert.ok(
        checkJava('src/main/java/demo/dto/BadRequest.java', 'import java.util.Date; class BadRequest {}').some(
            (failure) => failure.includes('Date'),
        ),
    );
    assert.ok(
        checkJava(
            'src/main/java/demo/service/AppService.java',
            'import java.sql.Timestamp; class AppService { Timestamp getTime() { return null; } }',
        ).some((failure) => failure.includes('Timestamp')),
    );
    assert.deepEqual(
        checkJava(
            'src/main/java/demo/repository/AppRepository.java',
            'import java.sql.Timestamp; class AppRepository { Timestamp at; }',
        ),
        [],
    );
    assert.ok(
        checkJava('src/main/java/demo/App.java', 'class App { java.time.Instant at; }').some((failure) =>
            failure.includes('import đầu file'),
        ),
    );
});

test('Ví dụ trong comment/string/text block không tạo lỗi quy tắc Java giả', () => {
    const code =
        '/* import java.util.*; class BadDto {} */\nclass GoodDTO { String example = "java.util.Date"; String sql = """\nimport java.util.*;\nclass BadDto {}\n"""; }';
    assert.deepEqual(checkJava('src/main/java/demo/dto/GoodDTO.java', code), []);
});
