package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.config.integration.MediaSettings;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository.SqlWork;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.media.dto.request.MediaUpdateRequest;
import vn.vietroute.common.media.entity.MediaAsset;
import vn.vietroute.common.media.repository.MediaRepository;
import vn.vietroute.common.media.service.MediaService;

/** Sửa thông tin không đụng provider, nhưng không được sửa ảnh của user khác dù biết ID/version. */
class MediaMetadataTest {

    @Test
    void metadataUpdatePreservesOwnershipAndWorksWhileUploadProviderIsDisabled() {
        var image = MediaAsset.builder().id(7L).createdBy(8L).status("READY").version(2L).build();
        var repository = new MediaRepository(null) {
            @Override
            public MediaAsset find(long id) {
                return image;
            }

            @Override
            public MediaAsset update(
                Connection connection,
                long id,
                MediaUpdateRequest request,
                long actor,
                boolean admin
            ) {
                assertEquals(2, request.version());
                assertEquals(8, actor);
                image.setTitle(request.title());
                image.setAltText(request.altText());
                image.setVersion(3L);
                return image;
            }
        };
        var transactions = new JdbcTransactions(null) {
            @Override
            public <T> T execute(SqlWork<T> action) {
                try {
                    return action.apply(null);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }
        };
        var service = new MediaService(repository, null, transactions, null, new MediaSettings(key -> null), null);
        var body = new MediaUpdateRequest("Ảnh xe", "Mô tả xe", 2);
        var denied = assertThrows(BusinessException.class, () ->
            service.update(7, body, new UserPrincipal(9L, "other", Set.of(Role.OPERATOR), 0))
        );
        assertEquals(ErrorCode.NOT_FOUND, denied.getErrorCode());
        var result = service.update(7, body, new UserPrincipal(8L, "owner", Set.of(Role.OPERATOR), 0));
        assertEquals("Ảnh xe", result.getTitle());
        assertEquals(3, result.getVersion());
        assertEquals(8, result.getCreatedBy());
    }
}
