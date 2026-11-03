'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const { mkdtempSync, mkdirSync, writeFileSync, readFileSync, existsSync, rmSync } = require('node:fs');
const path = require('node:path');
const os = require('node:os');

const script = path.resolve('scripts/development/export-repository-queries.py');
const python = process.env.PYTHON || (process.platform === 'win32' ? 'python' : 'python3');

const fixture = (body, run) => {
    const root = mkdtempSync(path.join(os.tmpdir(), 'vietroute-query-'));
    try {
        const source = path.join(root, 'source');
        mkdirSync(source);
        writeFileSync(path.join(source, 'SampleRepo.java'), body);
        const output = path.join(root, 'output.sql');
        const invoke = (schema, extra = []) => spawnSync(
            python,
            [script, '--schema', schema, '--source-root', source, '--output', output, ...extra],
            { encoding: 'utf8' },
        );
        run(invoke, output);
    } finally {
        assert.equal(path.dirname(path.resolve(root)), path.resolve(os.tmpdir()));
        assert.ok(path.basename(root).startsWith('vietroute-query-'));
        rmSync(root, { recursive: true, force: true });
    }
};

test('SQL export dùng schema đã chọn, giữ named params và xuất count riêng từ chính source', () => {
    fixture(`interface SampleRepo {
        @Query(value = """
            SELECT f.code FROM {h-schema}auth_functions f
            WHERE f.name_vi = :name
            """, countQuery = """
            SELECT count(*) FROM {h-schema}auth_functions f WHERE f.name_vi = :name
            """, nativeQuery = true)
        Page<String> search(@Param("name") String name, Pageable page);
    }`, (invoke, output) => {
        const result = invoke('schema_test');
        assert.equal(result.status, 0, result.stderr);
        const sql = readFileSync(output, 'utf8');
        assert.match(sql, /FROM "schema_test"\.auth_functions/);
        assert.match(sql, /SampleRepo\.search \[count\]/);
        assert.match(sql, /:name/);
        assert.match(sql, /LIMIT :pageSize OFFSET :offset/);
        assert.doesNotMatch(sql, /\{h-schema\}/);
        assert.match(result.stdout, /^2 SQL/);
    });
});

test('SQL ví dụ điền giá trị và phân trang, giữ literal, bỏ mutation/khóa trong transaction chỉ đọc', () => {
    fixture(`interface SampleRepo {
        @Query(value = """
            SELECT code, ':literal' AS sample FROM {h-schema}auth_functions
            WHERE code LIKE :keyword AND (CAST(:active AS boolean) IS NULL OR active = :active)
            """, countQuery = """
            SELECT count(*) FROM {h-schema}auth_functions
            """, nativeQuery = true)
        Page<String> search(Pageable page);
        @Query(value = """UPDATE {h-schema}auth_functions SET active = false""", nativeQuery = true)
        int disable();
        @Query(value = """SELECT * FROM {h-schema}auth_functions FOR UPDATE""", nativeQuery = true)
        Function lock();
        @Query(value = """SELECT * FROM {h-schema}auth_functions FOR NO KEY UPDATE""", nativeQuery = true)
        Function lockKey();
        @Query(value = """SELECT * FROM {h-schema}auth_functions FOR KEY SHARE""", nativeQuery = true)
        Function lockSharedKey();
    }`, (invoke, output) => {
        const result = invoke('schema_test', ['--example', '--page-size', '5', '--page', '2']);
        assert.equal(result.status, 0, result.stderr);
        const sql = readFileSync(output, 'utf8');
        assert.match(result.stdout, /^2 SQL/);
        assert.match(sql, /BEGIN READ ONLY;/);
        assert.match(sql, /SET LOCAL search_path TO "schema_test", pg_temp;/);
        assert.match(sql, /LIKE '%'/);
        assert.match(sql, /CAST\(NULL AS boolean\)/);
        assert.match(sql, /':literal'/);
        assert.match(sql, /LIMIT 5 OFFSET 10/);
        assert.doesNotMatch(sql, /SET active|FOR UPDATE;|FOR NO KEY UPDATE;|FOR KEY SHARE;/);
        assert.match(sql, /ROLLBACK;$/);
    });
});

test('SQL export bỏ comment/JPQL và không đóng annotation khi SQL chứa ngoặc hoặc chuỗi', () => {
    fixture(`interface SampleRepo {
        // @Query(value = """SELECT bad""", nativeQuery = true)
        @Query(value = """
            SELECT code FROM {h-schema}auth_functions
            WHERE description = 'a)b' AND active = true
            """, nativeQuery = true)
        List<String> names();
        @Query("select f from Function f")
        List<Function> all();
    }`, (invoke, output) => {
        const result = invoke('vietroute');
        assert.equal(result.status, 0, result.stderr);
        assert.match(result.stdout, /^1 SQL/);
        const sql = readFileSync(output, 'utf8');
        assert.match(sql, /'a\)b'/);
        assert.doesNotMatch(sql, /SELECT bad|select f from Function/);
    });
});

test('SQL export từ chối schema có SQL injection trước khi tạo file', () => {
    fixture('interface SampleRepo {}', (invoke, output) => {
        const result = invoke('vietroute; DROP SCHEMA other');
        assert.notEqual(result.status, 0);
        assert.equal(existsSync(output), false);
        assert.match(result.stderr, /Schema/);
    });
});
