package vn.vietroute.common.media.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.config.integration.MediaSettings;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.job.repository.JobRepository;
import vn.vietroute.common.media.dto.request.MediaUpdateRequest;
import vn.vietroute.common.media.dto.response.MediaResponse;
import vn.vietroute.common.media.entity.MediaAsset;
import vn.vietroute.common.media.repository.MediaRepository;
import vn.vietroute.common.media.storage.MediaStorage;
import vn.vietroute.common.media.validation.ImageValidator;
import vn.vietroute.common.traffic.RateLimitStore;

/** Transaction DB ngắn, gọi mạng bên ngoài; cleanup bền vững xử lý khoảng trống giữa hai hệ thống. */
@Service
@RequiredArgsConstructor
public class MediaService {

    /** Caller chỉ truyền ID từ liên kết business đã công bố, không mở API tra metadata ảnh ẩn danh. */
    public Map<Long, String> publishedUrls(Connection connection, Set<Long> publishedIds) throws SQLException {
        return images.readyPublicUrls(connection, publishedIds);
    }

    /** Caller đã kiểm quyền business và giữ transaction; tham chiếu chỉ nhận ảnh READY của principal, khóa cùng delete media. */
    public void requireReference(Connection connection, long id, UserPrincipal actor) throws SQLException {
        AccessActors.requireCurrent(actor);
        images.requireAvatar(connection, id, actor.id());
    }

    private final MediaRepository images;

    private final JobRepository jobs;

    private final JdbcTransactions transactions;

    private final MediaStorage storage;

    private final MediaSettings settings;

    private final RateLimitStore quotas;

    /** Người có READ chỉ xem thư viện của mình; ADMIN có quyền xem toàn bộ. */
    @AccessOperation(resource = Groups.MEDIA, action = PermissionAction.READ)
    public PageResponse<MediaResponse> page(PageRequest page, UserPrincipal actor) {
        var result = images.page(page, actor.id(), actor.roles().contains(Role.ADMIN));
        return new PageResponse<>(
            result.content().stream().map(MediaResponse::from).toList(),
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages(),
            result.hasNext(),
            result.hasPrevious()
        );
    }

    /** Trả 404 cho ảnh của người khác để không lộ sự tồn tại của bản ghi ngoài phạm vi. */
    @AccessOperation(resource = Groups.MEDIA, action = PermissionAction.READ)
    public MediaResponse find(long id, UserPrincipal actor) {
        return MediaResponse.from(owned(id, actor));
    }

    /** Reserve + job dọn cùng transaction trước mạng; chỉ công bố READY sau khi lưu metadata thành công. */
    @AccessOperation(resource = Groups.MEDIA, action = PermissionAction.CREATE)
    public MediaResponse upload(MultipartFile file, UserPrincipal actor) {
        return uploadOwned(file, actor);
    }

    /** Tự phục vụ avatar chỉ tạo ảnh thuộc chính mình; không mở quyền đọc/sửa/xóa thư viện của người khác. */
    @AccessOperation(authenticated = true)
    public MediaResponse uploadAvatar(MultipartFile file, UserPrincipal actor) {
        return uploadOwned(file, actor);
    }

    private MediaResponse uploadOwned(MultipartFile file, UserPrincipal actor) {
        if (!settings.isEnabled()) {
            throw new BusinessException(ErrorCode.MEDIA_DISABLED);
        }
        consumeUploadQuota(actor.id());
        byte[] pixels = ImageValidator.validate(file);
        MediaAsset reserved = transactions.execute(connection -> {
            MediaAsset image = images.reserve(connection, settings.getFolder() + "/" + UUID.randomUUID(), actor.id());
            jobs.enqueueAt(
                connection,
                "MEDIA_CLEANUP",
                "media-pending:" + image.getId(),
                payload(image.getId()),
                actor.id(),
                image.getCreatedAt().plus(Duration.ofHours(1))
            );
            return image;
        });
        // Upload lỗi/timeout hoặc process chết: PENDING vẫn có job dọn sau một giờ, kể cả CDN đã nhận file.
        var stored = storage.upload(reserved.getPublicId(), pixels);
        return transactions.execute(connection ->
            MediaResponse.from(images.ready(connection, reserved.getId(), stored))
        );
    }

    /** Xóa là yêu cầu bất đồng bộ; HTTP 202 không đồng nghĩa cache CDN đã bị thu hồi ngay lập tức. */
    @AccessOperation(resource = Groups.MEDIA, action = PermissionAction.DELETE)
    public MediaResponse delete(long id, long version, UserPrincipal actor) {
        owned(id, actor);
        return transactions.execute(connection -> {
            MediaAsset image = images.deleting(connection, id, version, actor.id());
            images.requireUnreferenced(connection, id);
            if (!settings.isEnabled()) throw new BusinessException(ErrorCode.MEDIA_DISABLED);
            jobs.enqueue(
                connection,
                "MEDIA_CLEANUP",
                "media-delete:" + id + ":" + image.getVersion(),
                payload(id),
                actor.id()
            );
            return MediaResponse.from(image);
        });
    }

    /** Sửa metadata trong transaction ngắn; không gọi provider, dùng được cả khi upload đang tắt. */
    @AccessOperation(resource = Groups.MEDIA, action = PermissionAction.UPDATE)
    public MediaResponse update(long id, MediaUpdateRequest body, UserPrincipal actor) {
        owned(id, actor);
        return transactions.execute(connection ->
            MediaResponse.from(images.update(connection, id, body, actor.id(), actor.roles().contains(Role.ADMIN)))
        );
    }

    private MediaAsset owned(long id, UserPrincipal actor) {
        MediaAsset image = images.find(id);
        if (!actor.roles().contains(Role.ADMIN) && !image.getCreatedBy().equals(actor.id())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return image;
    }

    private static String payload(long id) {
        return "{\"id\":" + id + "}";
    }

    /** Quota theo user dùng chung Redis khi scale; không để đổi IP vượt giới hạn upload. */
    private void consumeUploadQuota(long actor) {
        RateLimitStore.Decision decision;
        try {
            decision = quotas.consume("media-upload:" + actor, 10, 60);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY);
        }
        if (!decision.allowed()) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
    }
}
