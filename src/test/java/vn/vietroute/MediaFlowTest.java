package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.media.entity.MediaAsset;
import vn.vietroute.business.media.repository.MediaRepository;
import vn.vietroute.business.media.service.MediaService;
import vn.vietroute.common.config.MediaSettings;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository.SqlWork;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.job.JobRepository;
import vn.vietroute.common.media.MediaStorage;
import vn.vietroute.common.traffic.MemoryRateLimitStore;

/** Kiểm thứ tự commit/provider và scope qua fake port; SQL thực chạy bằng PostgreSQL tạm trong CI. */
class MediaFlowTest {

    private final List<String> events = new ArrayList<>();

    private final MediaAsset image = MediaAsset.builder()
        .id(7L)
        .createdBy(2L)
        .status("PENDING")
        .publicId("test/generated")
        .createdAt(Instant.parse("2026-10-22T00:00:00Z"))
        .build();

    private boolean inTransaction;

    private boolean providerFails;

    private boolean metadataFails;

    private final UserPrincipal actor = new UserPrincipal(2L, "operator", Set.of(Role.OPERATOR), 0);

    private MediaService service() {
        var settings = new MediaSettings(
            Map.of(
                "VIETROUTE_MEDIA_ENABLED",
                "true",
                "VIETROUTE_WORKER_ENABLED",
                "true",
                "VIETROUTE_CLOUDINARY_CLOUD_NAME",
                "demo",
                "VIETROUTE_CLOUDINARY_API_KEY",
                "1234",
                "VIETROUTE_CLOUDINARY_API_SECRET",
                "test-only-secret"
            )::get
        );
        var transactions = new JdbcTransactions(null) {
            @Override
            public <T> T execute(SqlWork<T> work) {
                inTransaction = true;
                try {
                    T value = work.apply(null);
                    events.add("commit");
                    return value;
                } catch (SQLException exception) {
                    throw new AssertionError(exception);
                } catch (RuntimeException exception) {
                    events.add("rollback");
                    throw exception;
                } finally {
                    inTransaction = false;
                }
            }
        };
        var repository = new MediaRepository(null) {
            @Override
            public MediaAsset reserve(Connection connection, String publicId, long user) {
                events.add("reserve");
                assertEquals(actor.id(), user);
                return image;
            }

            @Override
            public MediaAsset ready(Connection connection, long id, MediaStorage.StoredImage stored) {
                events.add("metadata");
                if (metadataFails) {
                    throw new BusinessException(ErrorCode.DATABASE_UNAVAILABLE);
                }
                image.setStatus("READY");
                return image;
            }

            @Override
            public MediaAsset find(long id) {
                return image;
            }
        };
        var jobs = new JobRepository(null) {
            @Override
            public UUID enqueueAt(
                Connection connection,
                String type,
                String dedup,
                String payload,
                Long user,
                Instant available
            ) {
                events.add("cleanup");
                assertEquals(image.getCreatedAt().plusSeconds(3600), available);
                assertEquals("MEDIA_CLEANUP", type);
                return UUID.randomUUID();
            }
        };
        MediaStorage provider = new MediaStorage() {
            @Override
            public StoredImage upload(String publicId, byte[] bytes) {
                assertFalse(inTransaction);
                events.add("upload");
                if (providerFails) {
                    throw new BusinessException(ErrorCode.MEDIA_UNAVAILABLE);
                }
                return new StoredImage(
                    "https://res.cloudinary.com/demo/a.png",
                    "https://res.cloudinary.com/demo/b.png",
                    "png",
                    80,
                    2,
                    2
                );
            }

            @Override
            public void delete(String publicId) {
                throw new AssertionError("Không xóa đồng bộ trong request");
            }
        };
        return new MediaService(
            repository,
            jobs,
            transactions,
            provider,
            settings,
            new MemoryRateLimitStore(Clock.systemUTC(), 100)
        );
    }

    private MockMultipartFile file() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("file", output.toByteArray());
    }

    @Test
    void persistsRecoveryBeforeProviderAndPublishesOnlyAfterMetadataCommit() throws Exception {
        assertEquals("READY", service().upload(file(), actor).getStatus());
        assertEquals(List.of("reserve", "cleanup", "commit", "upload", "metadata", "commit"), events);
    }

    @Test
    void providerTimeoutLeavesPendingRecoveryAlreadyCommitted() throws Exception {
        providerFails = true;
        var service = service();
        var file = file();
        assertThrows(BusinessException.class, () -> service.upload(file, actor));
        assertEquals("PENDING", image.getStatus());
        assertEquals(List.of("reserve", "cleanup", "commit", "upload"), events);
    }

    @Test
    void databaseFailureAfterProviderKeepsRecoveryAndNeverReturnsReady() throws Exception {
        metadataFails = true;
        var service = service();
        var file = file();
        assertThrows(BusinessException.class, () -> service.upload(file, actor));
        assertEquals("PENDING", image.getStatus());
        assertEquals(List.of("reserve", "cleanup", "commit", "upload", "metadata", "rollback"), events);
    }

    @Test
    void hidesOtherOwnersAndAllowsAdminOnlyThroughExplicitScope() {
        var stranger = new UserPrincipal(3L, "stranger", Set.of(Role.OPERATOR), 0);
        var service = service();
        assertEquals(
            ErrorCode.NOT_FOUND,
            assertThrows(BusinessException.class, () -> service.find(7, stranger)).getErrorCode()
        );
        assertEquals(
            ErrorCode.NOT_FOUND,
            assertThrows(BusinessException.class, () -> service.delete(7, 0, stranger)).getErrorCode()
        );
        assertEquals(7L, service.find(7, new UserPrincipal(1L, "admin", Set.of(Role.ADMIN), 0)).getId());
    }
}
