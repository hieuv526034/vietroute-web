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

/** Model đầy đủ cột bảng auth_user_roles; chỉ DTO mới được nhận/trả ở API. */
@Entity
@Table(name = "auth_user_roles")
@Getter
@Setter
@NoArgsConstructor
public class UserRole {

    // Khóa định danh bản ghi.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ID tài khoản liên kết trong auth_users.
    @Column(name = "user_id")
    private Long userId;

    // Mã vai trò trong auth_roles.
    @Column(name = "role_code", length = 24)
    private String roleCode;

    // Phạm vi gán vai trò; 0 là gán global, số dương là phạm vi nhà xe.
    @Column(name = "scope_id")
    private Long scopeId;

    // Trạng thái cho phép sử dụng bản ghi.
    @Column(name = "active")
    private boolean active;

    // Phiên bản khóa lạc quan; từ chối cập nhật từ dữ liệu đã cũ.
    @Version
    @Column(name = "version")
    private long version;

    // DB sinh 0 cho gán scoped, null cho gán global; ràng buộc scoped phải có global.
    @Column(name = "global_scope_id", insertable = false, updatable = false)
    private Long globalScopeId;

    // Cột DB sinh từ scope dương để ràng buộc khóa ngoại nhà xe.
    @Column(name = "carrier_scope_id", insertable = false, updatable = false)
    private Long carrierScopeId;

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
