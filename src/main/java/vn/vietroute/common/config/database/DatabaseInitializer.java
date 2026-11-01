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
import java.util.logging.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import vn.vietroute.auth.entity.AccessChange;
import vn.vietroute.auth.entity.AccountChallenge;
import vn.vietroute.auth.entity.Function;
import vn.vietroute.auth.entity.FunctionResource;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.entity.RoleFunction;
import vn.vietroute.auth.entity.UserAccount;
import vn.vietroute.auth.entity.UserRole;
import vn.vietroute.auth.repo.AuthBootstrapRepository;
import vn.vietroute.auth.service.BootstrapAdminInitializer;
import vn.vietroute.auth.service.DefaultAccountsInitializer;
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

    private final AuthBootstrapRepository foundation;

    private final DefaultAccountsInitializer defaults;

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
        migrate(connection, "V023__search_and_report_indexes.sql");
        migrate(connection, "V024__eight_table_authorization.sql");
        JdbcSchemaValidator.verify(
            connection,
            UserAccount.class,
            AccountChallenge.class,
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
            Function.class,
            RoleFunction.class,
            FunctionResource.class,
            UserRole.class,
            ApiJournalRecord.class,
            AccessChange.class,
            VisitorPresence.class,
            ActivityBucket.class
        );
        foundation.seed(connection);
        bootstrap.initialize(connection, SystemActor.ID);
        defaults.initialize(connection);
        return null;
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
}
