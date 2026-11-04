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

/** Model đầy đủ cột bảng auth_function_resources; chỉ DTO mới được nhận/trả ở API. */
@Entity
@Table(name = "auth_function_resources")
@Getter
@Setter
@NoArgsConstructor
public class FunctionResource {

    // Khóa định danh bản ghi.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Mã chức năng trong auth_functions.
    @Column(name = "function_code", length = 80)
    private String functionCode;

    // Loại tài nguyên MENU hoặc ENDPOINT (API).
    @Column(name = "kind", length = 12)
    private String kind;

    // Định danh đích được cấp quyền theo loại tài nguyên.
    @Column(name = "target", length = 240)
    private String target;

    // HTTP method của endpoint API.
    @Column(name = "http_method", length = 10)
    private String httpMethod;

    // Mẫu đường dẫn MVC, giữ biến như {id}.
    @Column(name = "path_template", length = 240)
    private String pathTemplate;

    // Controller/method thu thập từ MVC để tra cứu endpoint.
    @Column(name = "handler", length = 240)
    private String handler;

    // Mô tả bổ sung.
    @Column(name = "description", length = 500)
    private String description;

    // Tên hiển thị tiếng Việt.
    @Column(name = "name_vi", length = 500)
    private String nameVi;

    // Tên hiển thị tiếng Anh.
    @Column(name = "name_en", length = 500)
    private String nameEn;

    // Biểu tượng của mục menu.
    @Column(name = "icon", length = 50)
    private String icon;

    // Thứ tự hiển thị tăng dần.
    @Column(name = "sort_order")
    private int sortOrder;

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
