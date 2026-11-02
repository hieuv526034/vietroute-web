package vn.vietroute.common.base;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** ID/audit/version dùng chung; JPA và JDBC cùng ghi theo transaction server, entity không trả trực tiếp qua API. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@MappedSuperclass
public abstract class BaseEntity {

    // Khóa định danh bản ghi.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Thời điểm tạo, lưu theo UTC.
    @Column(name = "created_at")
    private Instant createdAt;

    // Thời điểm cập nhật gần nhất, lưu theo UTC.
    @Column(name = "updated_at")
    private Instant updatedAt;

    // ID người tạo; tác vụ hệ thống dùng tài khoản system (0).
    @Column(name = "created_by")
    private Long createdBy;

    // ID người sửa gần nhất; tác vụ hệ thống dùng system (0).
    @Column(name = "updated_by")
    private Long updatedBy;

    // Phiên bản khóa lạc quan; từ chối cập nhật từ dữ liệu đã cũ.
    @Version
    @Column(name = "version")
    private long version;
}
