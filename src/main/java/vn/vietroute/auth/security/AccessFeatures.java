package vn.vietroute.auth.security;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.AccessConstants.Permissions;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Định nghĩa cây do lập trình viên khai báo; DB chỉ lưu danh bạ và mapping quyền, không tự mở route. */
public final class AccessFeatures {

    private AccessFeatures() {}

    public record Feature(String code, String parent, String nameKey, String path, String viewPermission) {}

    private static final List<Feature> DEFINITIONS = List.of(
        new Feature(Functions.SYSTEM, null, "access.function.SYSTEM", null, null),
        new Feature(Functions.CATALOG, null, "access.function.CATALOG", Routes.ADMIN, Permissions.CATALOG_VIEW),
        new Feature(
            Functions.VEHICLE_TYPE,
            Functions.CATALOG,
            "access.function.VEHICLE_TYPE",
            Routes.CATALOG_VEHICLE_TYPES_PAGE,
            Groups.VEHICLE_TYPE + "_VIEW"
        ),
        new Feature(
            Functions.BOOKING_SOURCE,
            Functions.CATALOG,
            "access.function.BOOKING_SOURCE",
            Routes.CATALOG_BOOKING_SOURCES_PAGE,
            Groups.BOOKING_SOURCE + "_VIEW"
        ),
        new Feature(
            Functions.PROVINCE,
            Functions.CATALOG,
            "access.function.PROVINCE",
            Routes.CATALOG_PROVINCES_PAGE,
            Groups.PROVINCE + "_VIEW"
        ),
        new Feature(
            Functions.LOCATION,
            Functions.CATALOG,
            "access.function.LOCATION",
            Routes.CATALOG_LOCATIONS_PAGE,
            Groups.LOCATION + "_VIEW"
        ),
        new Feature(Functions.BUSINESS, null, "access.function.BUSINESS", null, null),
        new Feature(
            Functions.REPORT,
            Functions.BUSINESS,
            "access.function.REPORT",
            Routes.REPORTS_PAGE,
            Groups.REPORT + "_VIEW"
        ),
        new Feature(
            Functions.CARRIER,
            Functions.BUSINESS,
            "access.function.CARRIER",
            Routes.CARRIERS_PAGE,
            Groups.CARRIER + "_VIEW"
        ),
        new Feature(
            Functions.VEHICLE,
            Functions.BUSINESS,
            "access.function.VEHICLE",
            Routes.VEHICLES_PAGE,
            Groups.VEHICLE + "_VIEW"
        ),
        new Feature(
            Functions.DRIVER,
            Functions.BUSINESS,
            "access.function.DRIVER",
            Routes.DRIVERS_PAGE,
            Groups.DRIVER + "_VIEW"
        ),
        new Feature(Functions.DEMO, Functions.SYSTEM, "access.function.DEMO", Routes.DEMO_PAGE, Groups.DEMO + "_VIEW"),
        new Feature(
            Functions.ROUTE,
            Functions.BUSINESS,
            "access.function.ROUTE",
            Routes.ROUTES_PAGE,
            Groups.ROUTE + "_VIEW"
        ),
        new Feature(
            Functions.TRIP,
            Functions.BUSINESS,
            "access.function.TRIP",
            Routes.TRIPS_PAGE,
            Groups.TRIP + "_VIEW"
        ),
        new Feature(
            Functions.DRIVER_TRIP,
            Functions.BUSINESS,
            "access.function.DRIVER_TRIP",
            Routes.DRIVER_TRIPS_PAGE,
            Groups.DRIVER_TRIP + "_VIEW"
        ),
        new Feature(
            Functions.BOOKING,
            Functions.BUSINESS,
            "access.function.BOOKING",
            Routes.BOOKINGS_PAGE,
            Groups.BOOKING + "_VIEW"
        ),
        new Feature(
            Functions.MY_BOOKING,
            null,
            "access.function.MY_BOOKING",
            Routes.MY_BOOKINGS_PAGE,
            Groups.MY_BOOKING + "_VIEW"
        ),
        new Feature(
            Functions.MEDIA,
            Functions.SYSTEM,
            "access.function.MEDIA",
            Routes.MEDIA_PAGE,
            Permissions.MEDIA_VIEW
        ),
        new Feature(Functions.JOBS, Functions.SYSTEM, "access.function.JOBS", Routes.JOBS_PAGE, Permissions.JOB_VIEW),
        new Feature(
            Functions.MONITORING,
            Functions.SYSTEM,
            "access.function.MONITORING",
            Routes.MONITORING_PAGE,
            Permissions.SYSTEM_VIEW
        ),
        new Feature(
            Functions.CONFIGURATION,
            Functions.SYSTEM,
            "access.function.CONFIGURATION",
            Routes.CONFIGURATION_PAGE,
            Permissions.CONFIGURATION_VIEW
        ),
        new Feature(
            Functions.JOURNAL,
            Functions.SYSTEM,
            "access.function.JOURNAL",
            Routes.API_JOURNAL_PAGE,
            Permissions.JOURNAL_VIEW
        ),
        new Feature(Functions.MAIL, Functions.SYSTEM, "access.function.MAIL", null, null),
        new Feature(Functions.AUTHORIZATION, null, "access.function.AUTHORIZATION", null, null),
        new Feature(
            Functions.ACCOUNTS,
            Functions.AUTHORIZATION,
            "access.function.ACCOUNTS",
            Routes.ACCOUNTS_PAGE,
            Permissions.ACCOUNT_VIEW
        ),
        new Feature(
            Functions.ACCESS,
            Functions.AUTHORIZATION,
            "access.function.ACCESS",
            Routes.ACCESS_PAGE,
            Permissions.ACCESS_VIEW
        )
    );

    public static List<Feature> definitions() {
        return DEFINITIONS;
    }

    /** Bộ quản trị nền tảng để gán nhanh; không gom CRUD danh mục/ảnh hoặc nghiệp vụ tương lai vào quyền hệ thống. */
    public static boolean inSystemBundle(String function) {
        return Set.of(
            Functions.JOBS,
            Functions.MONITORING,
            Functions.CONFIGURATION,
            Functions.JOURNAL,
            Functions.MAIL,
            Functions.ACCESS
        ).contains(function);
    }

    /** Fail trước publish nếu trùng mã, parent sai/chu trình hoặc VIEW không hợp lệ. */
    public static void validate(List<Feature> definitions) {
        Set<String> codes = new HashSet<>();
        for (var feature : definitions) {
            if (
                feature.code() == null ||
                feature.code().isBlank() ||
                feature.code().length() > 40 ||
                feature.nameKey() == null ||
                feature.nameKey().isBlank()
            ) throw new IllegalStateException("Định nghĩa chức năng thiếu mã/tên");
            if (feature.path() != null && feature.viewPermission() == null) throw new IllegalStateException(
                "Trang thiếu quyền VIEW"
            );
            if (!codes.add(feature.code())) throw new IllegalStateException("Trùng mã chức năng: " + feature.code());
            if (
                feature.viewPermission() != null &&
                !Permission.valueOf(feature.viewPermission()).actions().contains(PermissionAction.VIEW)
            ) throw new IllegalStateException("Quyền vào trang phải là VIEW");
        }
        for (var feature : definitions) {
            Set<String> ancestors = new HashSet<>();
            var current = feature;
            while (current.parent() != null) {
                if (!ancestors.add(current.code())) throw new IllegalStateException("Chu trình cây chức năng");
                String parent = current.parent();
                current = definitions
                    .stream()
                    .filter(item -> item.code().equals(parent))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Thiếu chức năng cha: " + parent));
            }
        }
    }

    /** Không nhận code tùy ý từ client để sinh function có quyền. */
    public static Feature require(String code) {
        return DEFINITIONS.stream()
            .filter(item -> item.code().equals(code))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Chức năng chưa khai báo: " + code));
    }
}
