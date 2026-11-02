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

/** Model đầy đủ cột bảng auth_functions; chỉ DTO mới được nhận/trả ở API. */
@Entity
@Table(name = "auth_functions")
@JdbcTable(name = "auth_functions", primaryKey = { "code" })
@Getter
@Setter
@NoArgsConstructor
public class Function {

    // Mã chức năng, đồng thời là khóa chính.
    @Id
    @Column(name = "code", length = 80)
    private String code;

    // Tên hiển thị tiếng Việt.
    @Column(name = "name_vi", length = 500)
    private String nameVi;

    // Tên hiển thị tiếng Anh.
    @Column(name = "name_en", length = 500)
    private String nameEn;

    // Mô tả bổ sung.
    @Column(name = "description", length = 1000)
    private String description = "";

    // Mã chức năng cha để tổ chức cây menu.
    @Column(name = "parent_code", length = 80)
    private String parentCode;

    // Tên biểu tượng giao diện.
    @Column(name = "icon", length = 50)
    private String icon = "menu";

    // Thứ tự hiển thị tăng dần.
    @Column(name = "sort_order")
    private int sortOrder;

    // Trạng thái cho phép sử dụng bản ghi.
    @Column(name = "active")
    private boolean active;

    // Đánh dấu xóa mềm, không xóa lịch sử liên quan.
    @Column(name = "deleted")
    private boolean deleted;

    // Đã khởi tạo tài nguyên; restart không tự phục hồi gán đã thu hồi.
    @Column(name = "resources_initialized")
    private boolean resourcesInitialized;

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
