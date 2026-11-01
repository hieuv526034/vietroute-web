package vn.vietroute.auth.repo;

/** Projection nội bộ từ Function; không có bảng permission/policy hoặc marker cấp quyền khác. */
public final class FunctionSql {

    private FunctionSql() {}

    public static final String ROLE_OPERATIONS = """
    SELECT DISTINCT rf.role_code,op.code AS permission_code
    FROM auth_role_functions rf JOIN auth_functions f ON f.code=rf.function_code AND f.active AND NOT f.deleted
    CROSS JOIN LATERAL unnest(rf.operation_codes) op(code)
    JOIN auth_functions p ON p.code=op.code AND p.active AND NOT p.deleted
    """;

    public static final String ROLE_TARGETS = """
    SELECT DISTINCT rf.role_code,fr.kind,fr.target
    FROM auth_role_functions rf JOIN auth_functions f ON f.code=rf.function_code AND f.active AND NOT f.deleted
    JOIN auth_function_resources fr ON fr.function_code=f.code WHERE NOT fr.public_route
    """;
}
