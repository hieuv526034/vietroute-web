package vn.vietroute.auth.repo;

/** SQL kiểm scope dùng trên connection nghiệp vụ; owner là alias nhà xe do repository khai báo. */
public final class ScopeAccessSql {

    private ScopeAccessSql() {}

    public static final String ADMIN = """
    SELECT 1 FROM auth_users u JOIN auth_user_roles ur ON ur.user_id=u.id AND ur.scope_id=0 AND ur.active
    JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted
    WHERE u.id=? AND u.token_version=? AND u.active AND NOT u.deleted AND NOT u.activation_pending
      AND NOT u.must_change_password AND u.email_verified_at IS NOT NULL AND r.code='ADMIN'
    """;

    public static final String VISIBLE = """
    EXISTS(SELECT 1 FROM auth_users su JOIN carriers security_owner ON security_owner.scope_id=owner.scope_id AND security_owner.scope_active
        WHERE su.id=? AND su.token_version=? AND su.active AND NOT su.deleted AND NOT su.activation_pending
          AND NOT su.must_change_password AND su.email_verified_at IS NOT NULL
          AND (EXISTS(SELECT 1 FROM auth_user_roles ar JOIN auth_roles rr ON rr.code=ar.role_code AND rr.active AND NOT rr.deleted
                WHERE ar.user_id=su.id AND ar.scope_id=0 AND ar.active AND rr.code='ADMIN')
            OR EXISTS(SELECT 1 FROM auth_user_roles sr JOIN auth_roles r ON r.code=sr.role_code AND r.active AND NOT r.deleted
                JOIN auth_role_functions rf ON rf.role_code=r.code JOIN auth_functions f ON f.code=rf.function_code AND f.active AND NOT f.deleted
                JOIN auth_functions p ON p.code=ANY(rf.operation_codes) AND p.active AND NOT p.deleted
                WHERE sr.user_id=su.id AND sr.scope_id=security_owner.scope_id AND sr.active AND p.code=?
                AND (?::text IS NULL OR EXISTS(SELECT 1 FROM auth_role_functions rg JOIN auth_functions fg ON fg.code=rg.function_code AND fg.active AND NOT fg.deleted
                    JOIN auth_function_resources fr ON fr.function_code=fg.code WHERE rg.role_code=r.code AND fr.target=? AND NOT fr.public_route)))))
    """;

    public static final String REQUIRE = "SELECT " + VISIBLE + " FROM carriers owner WHERE owner.scope_id=?";
}
