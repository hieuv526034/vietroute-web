package vn.vietroute.common.validation;

import jakarta.validation.groups.Default;

/** Nhóm validation dùng chung để một request phục vụ nhiều thao tác, không nhân bản DTO tạo/sửa. */
public final class RequestGroups {

    private RequestGroups() {}

    public interface Create extends Default {}

    public interface Update extends Default {}

    public interface Delete {}

    public interface State {}

    public interface Payment {}

    public interface Notice {}

    public interface Progress {}
}
