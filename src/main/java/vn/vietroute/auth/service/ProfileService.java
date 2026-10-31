package vn.vietroute.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.dto.record.account.ProfileUpdateRequest;
import vn.vietroute.auth.dto.response.account.UserResponse;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.media.repository.MediaRepository;
import vn.vietroute.common.validation.BusinessRules;

/** Hồ sơ tự phục vụ không cấp quyền quản trị media; chỉ gắn ảnh của chính principal đã được xác thực. */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository users;

    private final MediaRepository images;

    private final JdbcTransactions transactions;

    /** Khóa ảnh trước user thống nhất với delete media; kiểm owner/READY/token/version trước khi ghi audit. */
    @AccessOperation(authenticated = true)
    public UserResponse update(ProfileUpdateRequest body, UserPrincipal actor) {
        // Lời gọi bean nội bộ cũng chỉ được sửa hồ sơ của chính principal mà guard vừa xác minh.
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> {
            if (body.avatarMediaId() != null) images.requireAvatar(connection, body.avatarMediaId(), actor.id());
            var user = BusinessRules.requireFound(users.findById(connection, actor.id(), true));
            BusinessRules.require(
                user.isActive() && user.getTokenVersion() == actor.tokenVersion(),
                ErrorCode.TOKEN_INVALID
            );
            users.updateProfile(connection, actor.id(), body);
            return UserResponse.from(users.findById(connection, actor.id(), false));
        });
    }
}
