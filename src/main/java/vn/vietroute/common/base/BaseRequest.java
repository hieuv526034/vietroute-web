package vn.vietroute.common.base;

import io.swagger.v3.oas.annotations.media.Schema;

/** Điểm mở rộng request; cố ý không chứa audit để tránh client ghi đè người tạo/thời gian. */
@Schema(description = "Request cơ sở; audit và phạm vi dữ liệu được lấy từ server")
public abstract class BaseRequest {}
