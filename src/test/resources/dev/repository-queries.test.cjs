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
        const invoke = (schema) => spawnSync(python, [script, '--schema', schema, '--source-root', source, '--output', output], { encoding: 'utf8' });
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
        assert.doesNotMatch(sql, /\{h-schema\}/);
        assert.match(result.stdout, /^2 SQL/);
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
