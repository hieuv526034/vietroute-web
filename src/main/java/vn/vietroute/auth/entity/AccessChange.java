package vn.vietroute.auth.entity;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Model đầy đủ cột bảng auth_access_changes; chỉ DTO mới được nhận/trả ở API. */
@Entity
@Table(name = "auth_access_changes")
@Getter
@Setter
@NoArgsConstructor
public class AccessChange {

    // Khóa định danh bản ghi.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Loại sự kiện nghiệp vụ.
    @Column(name = "event_type", length = 64)
    private String eventType;

    // Loại đối tượng bị thay đổi quyền.
    @Column(name = "target_kind", length = 20)
    private String targetKind;

    // Khóa đối tượng bị thay đổi quyền.
    @Column(name = "target_key", length = 240)
    private String targetKey;

    // ID người thực hiện thay đổi quyền.
    @Column(name = "actor_id")
    private long actorId;

    // Mã đối chiếu request với nhật ký và phản hồi HTTP.
    @Column(name = "trace_id", length = 64)
    private String traceId;

    // Trạng thái trước thay đổi, chỉ giữ field được phép.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "jsonb")
    private JsonNode beforeState;

    // Trạng thái sau thay đổi, chỉ giữ field được phép.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "jsonb")
    private JsonNode afterState;

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
