package vn.vietroute.auth.dto.response.scope;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(
    description = "Phạm vi bảo mật; quan hệ nhà xe nghiệp vụ sẽ tham chiếu ID này, không dùng cookie/client role làm scope"
)
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class DataScopeResponse {

    @Schema(description = "ID scope do server cấp")
    private long id;

    @Schema(description = "Mã bất biến")
    private String code;

    @Schema(description = "Tên tiếng Việt")
    private String nameVi;

    @Schema(description = "Tên tiếng Anh")
    private String nameEn;

    @Schema(description = "Phạm vi bảo mật đang bật")
    private boolean active;

    @Schema(description = "Phiên bản metadata")
    private long version;

    @Schema(description = "Thời điểm cập nhật UTC")
    private Instant updatedAt;

    @Schema(description = "Actor cập nhật; 0 là system")
    private long updatedBy;

    public DataScopeResponse(
        long id,
        String code,
        String nameVi,
        String nameEn,
        boolean active,
        long version,
        Instant updatedAt,
        long updatedBy
    ) {
        this.id = id;
        this.code = code;
        this.nameVi = nameVi;
        this.nameEn = nameEn;
        this.active = active;
        this.version = version;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public long id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String nameVi() {
        return nameVi;
    }

    public String nameEn() {
        return nameEn;
    }

    public boolean active() {
        return active;
    }

    public long version() {
        return version;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long updatedBy() {
        return updatedBy;
    }
}
