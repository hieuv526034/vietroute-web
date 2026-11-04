package vn.vietroute.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Model đầy đủ cột bảng auth_role_functions; chỉ DTO mới được nhận/trả ở API. */
@Entity
@Table(name = "auth_role_functions")
@Getter
@Setter
@NoArgsConstructor
public class RoleFunction {

    // Khóa định danh bản ghi.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Mã vai trò trong auth_roles.
    @Column(name = "role_code", length = 24)
    private String roleCode;

    // Mã chức năng trong auth_functions.
    @Column(name = "function_code", length = 80)
    private String functionCode;

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
