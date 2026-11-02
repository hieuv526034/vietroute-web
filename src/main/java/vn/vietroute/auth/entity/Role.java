package vn.vietroute.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.jdbc.JdbcTable;

/** Model đầy đủ cột bảng auth_roles; chỉ DTO mới được nhận/trả ở API. */
@Entity
@Table(name = "auth_roles")
@JdbcTable(name = "auth_roles", primaryKey = { "code" })
@Getter
@Setter
@NoArgsConstructor
public class Role {

    // Mã vai trò, đồng thời là khóa chính.
    @Id
    @Column(name = "code", length = 24)
    private String code;

    // Tên hiển thị tiếng Việt.
    @Column(name = "name_vi", length = 120)
    private String nameVi;

    // Tên hiển thị tiếng Anh.
    @Column(name = "name_en", length = 120)
    private String nameEn;

    // Trạng thái cho phép sử dụng bản ghi.
    @Column(name = "active")
    private boolean active;

    // Đánh dấu xóa mềm, không xóa lịch sử liên quan.
    @Column(name = "deleted")
    private boolean deleted;

    // Đã khởi tạo gán chức năng; restart không tự cấp lại quyền đã thu hồi.
    @Column(name = "functions_initialized")
    private boolean functionsInitialized;

    // Vai trò cấp quyền trên mọi phạm vi cho các endpoint nó được gán.
    @Column(name = "all_scopes")
    private boolean allScopes;

    // Vai trò được bảo vệ khỏi thao tác quản trị không hợp lệ.
    @Column(name = "protected_role")
    private boolean protectedRole;

    // Phiên bản khóa lạc quan; từ chối cập nhật từ dữ liệu đã cũ.
    @Version
    @Column(name = "version")
    private long version;

    // Thời điểm tạo, lưu theo UTC.
    @Column(name = "created_at")
    private Instant createdAt;

    // Thời điểm cập nhật gần nhất, lưu theo UTC.
    @Column(name = "updated_at")
    private Instant updatedAt;

    // ID người tạo; tác vụ hệ thống dùng tài khoản system (0).
    @Column(name = "created_by")
    private long createdBy;

    // ID người sửa gần nhất; tác vụ hệ thống dùng system (0).
    @Column(name = "updated_by")
    private long updatedBy;
}
