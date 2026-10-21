package vn.vietroute.common.jdbc;

/** Schema cố định của ứng dụng; không nhận tên schema từ HTTP hoặc tenant do client gửi. */
public final class JdbcSchema {

    public static final String NAME = "vietroute";

    private JdbcSchema() {}
}
