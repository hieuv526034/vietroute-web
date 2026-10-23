/* Kiểm quy tắc dùng chung trên file được Git theo dõi; không đọc env, truy cập DB hoặc sửa source. */
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const bridges = Object.freeze({
    'CLAUDE.md': /^@AGENTS\.md\s*$/m,
    'GEMINI.md': /^@\.\/AGENTS\.md\s*$/m,
    '.github/copilot-instructions.md': /\]\(\.\.\/AGENTS\.md\)/,
    '.cursor/rules/vietroute.mdc': /^@AGENTS\.md\s*$/m,
});

// Bỏ ví dụ trong code fence trước khi đọc link; không coi URL hoặc snippet là đường dẫn bàn giao.
const withoutFences = (text) => {
    let fence;
    return text
        .split(/\r?\n/)
        .map((line) => {
            const marker = line.match(/^\s*(`{3,}|~{3,})/);
            if (marker) {
                if (!fence) fence = marker[1];
                else if (marker[1][0] === fence[0] && marker[1].length >= fence.length) fence = undefined;
                return '';
            }
            return fence ? '' : line;
        })
        .join('\n');
};

const links = (text) =>
    Array.from(withoutFences(text).matchAll(/!?\[[^\]\n]*\]\((<[^>\n]+>|[^)\n]+)\)/g), (match) => match[1]);

/** Đổi link tương đối thành đường dẫn từ gốc repo, bỏ URL ngoài và chặn đường dẫn riêng của máy. */
const localTarget = (document, rawLink) => {
    const link = rawLink.trim().replace(/^<|>$/g, '');
    if (!link || link.startsWith('#')) return null;
    if (/^[a-z]:[\\/]/i.test(link) || /^file:/i.test(link) || link.includes('\\'))
        throw new Error('đường dẫn phụ thuộc máy');
    if (/^[a-z][a-z\d+.-]*:/i.test(link) || link.startsWith('//')) return null;
    const target = decodeURIComponent(link.split(/[?#]/, 1)[0]);
    if (path.posix.isAbsolute(target)) throw new Error('đường dẫn tuyệt đối');
    const resolved = path.posix.normalize(path.posix.join(path.posix.dirname(document), target));
    if (resolved === '..' || resolved.startsWith('../')) throw new Error('đường dẫn ra ngoài repo');
    return resolved;
};

/** Chỉ chấp nhận target được chia sẻ qua Git; mục lục phải dẫn tới mọi tài liệu trong docs. */
const checkDocs = (files, read) => {
    const failures = [];
    const tracked = new Set(files);
    const indexed = new Set();
    let checked = 0;
    const documents = files.filter((file) => /\.(?:md|mdc)$/.test(file));
    for (const document of documents) {
        for (const link of links(read(document))) {
            try {
                const target = localTarget(document, link);
                if (!target) continue;
                checked++;
                if (!tracked.has(target) && !files.some((file) => file.startsWith(target.replace(/\/$/, '') + '/'))) {
                    failures.push(`${document}: link chưa có trong Git: ${link}`);
                }
                if (document === 'docs/README.md') indexed.add(target);
            } catch (error) {
                failures.push(`${document}: ${error.message}: ${link}`);
            }
        }
    }
    for (const document of documents.filter((file) => file.startsWith('docs/') && file !== 'docs/README.md')) {
        if (!indexed.has(document)) failures.push(`${document}: thiếu link trong docs/README.md`);
    }
    return { failures, checked, documents: documents.length };
};

/** Giữ file từng công cụ ngắn và dẫn về cùng nguồn, không đánh giá AI đã thực sự hiểu nội dung. */
const checkBridges = (files, read) => {
    const failures = [];
    const required = [
        'AGENTS.md',
        'docs/QUY_TRINH_AI.md',
        'docs/README.md',
        'docs/TIEN_DO_VA_KE_HOACH_NHOM.md',
        ...Object.keys(bridges),
    ];
    for (const file of required) {
        if (!files.includes(file)) failures.push(`${file}: thiếu file chỉ dẫn/ngữ cảnh trong Git`);
    }
    // Để phần luôn nạp gọn và dưới ngân sách mặc định của công cụ; chuyên đề đọc theo task.
    if (files.includes('AGENTS.md') && Buffer.byteLength(read('AGENTS.md'), 'utf8') > 24 * 1024) {
        failures.push('AGENTS.md: vượt 24 KiB; chuyển phần giải thích sang docs thay vì tăng nội dung luôn nạp');
    }
    for (const [file, pattern] of Object.entries(bridges)) {
        if (!files.includes(file)) continue;
        const text = read(file);
        if (!pattern.test(text)) failures.push(`${file}: phải dẫn/import về AGENTS.md`);
        if (!text.includes('QUY_TRINH_AI.md')) failures.push(`${file}: thiếu đường đọc quy trình AI`);
        if (Buffer.byteLength(text, 'utf8') > 2048)
            failures.push(`${file}: vượt 2 KiB, không sao chép quy tắc vào file công cụ`);
    }
    const cursor = '.cursor/rules/vietroute.mdc';
    if (files.includes(cursor) && !/^---\r?\n[\s\S]*?^alwaysApply: true\s*$[\s\S]*?^---\s*$/m.test(read(cursor))) {
        failures.push(`${cursor}: phải có frontmatter alwaysApply: true`);
    }
    return failures;
};

// Loại comment/string/text block để ví dụ trong Javadoc, SQL hay URL không thành lỗi giả.
const javaCode = (text) =>
    text.replace(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\r\n]*|\/\*[\s\S]*?\*\//g, (token) =>
        token.replace(/[^\r\n]/g, ' '),
    );

/** Bắt các lệch quy ước đơn giản bằng kiểm tĩnh; đây không phải Java parser hoặc review nghiệp vụ. */
const checkJava = (file, text) => {
    const failures = [];
    const code = javaCode(text);
    if (/^\s*import\s+(?:static\s+)?[\w.]+\.\*\s*;/m.test(code))
        failures.push(`${file}: import wildcard, cần import tường minh`);
    if (/^\s*import\s+java\.(?:util|sql)\.Date\s*;/m.test(code))
        failures.push(`${file}: không dùng Date cũ, timestamp nghiệp vụ dùng Instant`);
    const declarations = Array.from(
        code.matchAll(/\b(?:class|interface|record|enum)\s+([\w$]+)/g),
        (match) => match[1],
    );
    if (
        declarations.some((name) => /Dto(?:[A-Z\d_$]|$)/.test(name)) ||
        /Dto(?:[A-Z\d_$]|$)/.test(path.posix.basename(file, '.java'))
    ) {
        failures.push(`${file}: tên Java phải viết DTO, không dùng Dto`);
    }
    if (/\/(?:entity|dto|service)\//.test(file) && /\bTimestamp\b/.test(code)) {
        failures.push(`${file}: Timestamp chỉ chuyển đổi tại biên JDBC, contract dùng Instant`);
    }
    const executable = code.replace(/^\s*(?:package|import)\s+[^;]+;/gm, '');
    if (/\b(?:java|javax|jakarta|org|com|vn)\.(?:[a-zA-Z_$][\w$]*\.)+[A-Z][\w$]*/.test(executable)) {
        failures.push(`${file}: chuyển tên class đầy đủ trong code thành import đầu file`);
    }
    return failures;
};

/** Chạy chỉ đọc trên file tracked; trả mã thoát khác 0 để máy thành viên và CI cùng phát hiện lỗi. */
const run = (root, mode) => {
    const files = execFileSync('git', ['ls-files', '-z'], { cwd: root, encoding: 'utf8' }).split('\0').filter(Boolean);
    const read = (file) => fs.readFileSync(path.join(root, file), 'utf8');
    let failures;
    if (mode === '--docs') {
        const result = checkDocs(files, read);
        failures = result.failures;
        if (!failures.length)
            console.log(`Đạt: ${result.checked} liên kết trong ${result.documents} tài liệu; mục lục đủ file docs.`);
    } else if (mode === '--rules') {
        failures = checkBridges(files, read);
        const javaFiles = files.filter((file) => file.startsWith('src/') && file.endsWith('.java'));
        for (const file of javaFiles) failures.push(...checkJava(file, read(file)));
        if (!failures.length) console.log(`Đạt: điểm đọc AI và quy tắc tĩnh trên ${javaFiles.length} file Java.`);
    } else {
        throw new Error('Dùng --docs hoặc --rules tại thư mục repository.');
    }
    if (failures.length) {
        console.error(failures.join('\n'));
        process.exitCode = 1;
    }
};

if (require.main === module) run(path.resolve(__dirname, '../..'), process.argv[2]);

module.exports = { checkDocs, checkBridges, checkJava, localTarget };
