package vn.vietroute.common.config.database;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import vn.vietroute.auth.entity.AccessChange;
import vn.vietroute.auth.entity.AccessFeature;
import vn.vietroute.auth.entity.AccessPolicy;
import vn.vietroute.auth.entity.AccessResource;
import vn.vietroute.auth.entity.AccountChallenge;
import vn.vietroute.auth.entity.DataScope;
import vn.vietroute.auth.entity.PasswordHistory;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.entity.RoleResource;
import vn.vietroute.auth.entity.RoleResourceSet;
import vn.vietroute.auth.entity.ScopeMembership;
import vn.vietroute.auth.entity.ScopeRole;
import vn.vietroute.auth.entity.UserAccount;
import vn.vietroute.auth.service.BootstrapAdminInitializer;
import vn.vietroute.business.booking.entity.Booking;
import vn.vietroute.business.booking.entity.BookingEvent;
import vn.vietroute.business.carrier.entity.Carrier;
import vn.vietroute.business.carrier.entity.Driver;
import vn.vietroute.business.carrier.entity.Vehicle;
import vn.vietroute.business.catalog.entity.CatalogItem;
import vn.vietroute.business.location.entity.Location;
import vn.vietroute.business.location.entity.Province;
import vn.vietroute.business.route.entity.Route;
import vn.vietroute.business.route.entity.RouteStop;
import vn.vietroute.business.tracking.entity.TripLocation;
import vn.vietroute.business.tracking.entity.TripTracking;
import vn.vietroute.business.trip.entity.Trip;
import vn.vietroute.business.trip.entity.TripEvent;
import vn.vietroute.business.trip.entity.TripSegment;
import vn.vietroute.business.trip.entity.TripStop;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.jdbc.JdbcSchema;
import vn.vietroute.common.jdbc.JdbcSchemaValidator;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.jdbc.LegacyMigrationSql;
import vn.vietroute.common.jdbc.SchemaBootstrap;
import vn.vietroute.common.job.model.Job;
import vn.vietroute.common.media.entity.MediaAsset;
import vn.vietroute.common.monitoring.model.ActivityBucket;
import vn.vietroute.common.monitoring.model.ApiJournalRecord;
import vn.vietroute.common.monitoring.model.VisitorPresence;

/** Migration/seed chạy theo cờ môi trường; tuyệt đối không chạy từ bước đóng gói Maven. */
@Component
@RequiredArgsConstructor
public class DatabaseInitializer implements InitializingBean {

    private static final Logger LOG = Logger.getLogger(DatabaseInitializer.class.getName());

    private final AppSettings settings;

    private final JdbcTransactions transactions;

    private final BootstrapAdminInitializer bootstrap;

    private final JdbcSchema schema;

    @Override
    public void afterPropertiesSet() {
        if (!settings.isDatabaseInitEnabled()) {
            return;
        }
        transactions.execute(this::initialize);
        LOG.info("VietRoute database migration and bootstrap completed");
    }

    /** Một transaction bao trọn chuyển schema, migration, kiểm tra index và seed; lỗi ở đâu rollback ở đó. */
    private Void initialize(Connection connection) throws SQLException {
        // PostgreSQL tự giải phóng advisory lock khi commit/rollback hoặc mất kết nối.
        try (Statement statement = connection.createStatement()) {
            statement.execute("SELECT pg_advisory_xact_lock(781205610)");
        }
        new SchemaBootstrap(schema).prepare(connection);
        migrate(connection, "V001__auth_and_catalog.sql");
        migrate(connection, "V002__vietroute_schema_and_indexes.sql");
        migrate(connection, "V003__permissions_and_jobs.sql");
        migrate(connection, "V004__media_assets.sql");
        migrate(connection, "V005__api_request_journal.sql");
        migrate(connection, "V006__system_audit.sql");
        migrate(connection, "V007__configurable_access.sql");
        migrate(connection, "V008__media_metadata.sql");
        migrate(connection, "V009__activity_presence.sql");
        migrate(connection, "V010__access_resource_inventory.sql");
        migrate(connection, "V011__account_and_request_actor.sql");
        migrate(connection, "V012__roles_profile_resources.sql");
        migrate(connection, "V013__function_tree_and_view.sql");
        migrate(connection, "V014__access_change_history.sql");
        migrate(connection, "V015__data_scope_memberships.sql");
        migrate(connection, "V016__locations.sql");
        migrate(connection, "V017__carriers_and_fleet.sql");
        migrate(connection, "V018__routes_and_stops.sql");
        migrate(connection, "V019__trips_and_capacity.sql");
        migrate(connection, "V020__bookings_and_events.sql");
        migrate(connection, "V021__trip_location_sharing.sql");
        migrate(connection, "V022__role_resource_grants.sql");
        JdbcSchemaValidator.verify(
            connection,
            UserAccount.class,
            AccountChallenge.class,
            PasswordHistory.class,
            CatalogItem.class,
            Carrier.class,
            Vehicle.class,
            Driver.class,
            Province.class,
            Location.class,
            Route.class,
            RouteStop.class,
            Trip.class,
            TripStop.class,
            TripSegment.class,
            TripEvent.class,
            Booking.class,
            BookingEvent.class,
            TripTracking.class,
            TripLocation.class,
            MediaAsset.class,
            Job.class,
            Role.class,
            RoleResource.class,
            RoleResourceSet.class,
            Permission.class,
            ApiJournalRecord.class,
            AccessPolicy.class,
            AccessResource.class,
            AccessFeature.class,
            AccessChange.class,
            DataScope.class,
            ScopeMembership.class,
            ScopeRole.class,
            VisitorPresence.class,
            ActivityBucket.class
        );
        seedRoles(connection);
        seedPermissions(connection);
        bootstrap.initialize(connection, SystemActor.ID);
        seedCatalogs(connection);
        return null;
    }

    /** Seed registry quyền; role thường chỉ nhận mặc định một lần, không phục hồi quyền quản trị đã thu hồi. */
    private void seedPermissions(Connection connection) throws SQLException {
        var vi = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("vi"));
        var en = ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH);
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO auth_permissions(code,name_vi,name_en) VALUES (?,?,?) ON CONFLICT (code) DO NOTHING"
            )
        ) {
            for (Permission permission : Permission.values()) {
                insert.setString(1, permission.name());
                insert.setString(2, vi.getString(permission.messageKey()));
                insert.setString(3, en.getString(permission.messageKey()));
                insert.addBatch();
            }
            insert.executeBatch();
        }
        for (Role role : Role.values()) {
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT permissions_initialized OR deleted FROM auth_roles WHERE code=?"
                )
            ) {
                query.setString(1, role.name());
                try (ResultSet row = query.executeQuery()) {
                    row.next();
                    if (row.getBoolean(1) && role != Role.ADMIN) {
                        continue;
                    }
                }
            }
            try (
                PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO auth_role_permissions(role_code,permission_code) VALUES (?,?) ON CONFLICT DO NOTHING"
                )
            ) {
                for (Permission permission : Permission.values()) {
                    if (permission.grantedInitially(role)) {
                        insert.setString(1, role.name());
                        insert.setString(2, permission.name());
                        insert.addBatch();
                    }
                }
                insert.executeBatch();
            }
            try (
                PreparedStatement update = connection.prepareStatement(
                    "UPDATE auth_roles SET permissions_initialized=true,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE code=? AND NOT permissions_initialized"
                )
            ) {
                update.setLong(1, SystemActor.ID);
                update.setString(2, role.name());
                update.executeUpdate();
            }
        }
    }

    /** Checksum ngăn sửa âm thầm migration đã chạy; thay schema phải tạo version mới. */
    private void migrate(Connection connection, String version) throws SQLException {
        byte[] script;
        String checksum;
        try (InputStream input = getClass().getResourceAsStream("/db/migration/" + version)) {
            if (input == null) {
                throw new IllegalStateException("Missing migration " + version);
            }
            script = input.readAllBytes();
            checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(script));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load migration " + version, exception);
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT checksum FROM vietroute_schema_history WHERE version=?"
            )
        ) {
            query.setString(1, version);
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) {
                    if (!checksum.equals(result.getString(1))) {
                        throw new IllegalStateException("Applied migration checksum changed: " + version);
                    }
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(LegacyMigrationSql.render(version, new String(script, StandardCharsets.UTF_8), schema));
        }
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO vietroute_schema_history(version, checksum) VALUES (?,?)"
            )
        ) {
            insert.setString(1, version);
            insert.setString(2, checksum);
            insert.executeUpdate();
        }
    }

    /** Seed quyền ổn định theo code; chạy lại không ghi đè tên hay dữ liệu người dùng đã chỉnh. */
    private void seedRoles(Connection connection) throws SQLException {
        String[][] roles = {
            { "ADMIN", "Quản trị viên", "Administrator" },
            { "OPERATOR", "Quản lý nhà xe", "Operator" },
            { "DRIVER", "Tài xế", "Driver" },
            { "CUSTOMER", "Khách hàng", "Customer" },
        };
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO auth_roles(code,name_vi,name_en) VALUES (?,?,?) ON CONFLICT (code) DO NOTHING"
            )
        ) {
            for (String[] role : roles) {
                for (int i = 0; i < role.length; i++) {
                    insert.setString(i + 1, role[i]);
                }
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    /** Seed danh mục nền bằng UNIQUE(type, code); không thêm dữ liệu chuyến/đơn giả. */
    private void seedCatalogs(Connection connection) throws SQLException {
        String[][] values = {
            { "VEHICLE_TYPE", "SEAT", "Ghế ngồi", "Seated coach" },
            { "VEHICLE_TYPE", "SLEEPER", "Giường nằm", "Sleeper coach" },
            { "VEHICLE_TYPE", "LIMOUSINE", "Limousine", "Limousine" },
            { "BOOKING_SOURCE", "WEB", "Đặt trên website", "Website" },
            { "BOOKING_SOURCE", "PHONE", "Đặt qua điện thoại", "Phone" },
        };
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO catalog_items(type,code,name_vi,name_en,sort_order) VALUES (?,?,?,?,?) ON CONFLICT (type,code) DO NOTHING"
            )
        ) {
            for (int i = 0; i < values.length; i++) {
                for (int j = 0; j < values[i].length; j++) {
                    insert.setString(j + 1, values[i][j]);
                }
                insert.setInt(5, i);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
