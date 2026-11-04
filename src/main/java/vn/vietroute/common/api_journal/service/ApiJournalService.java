package vn.vietroute.common.api_journal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.booking.dto.request.BookingRequest;
import vn.vietroute.business.booking.dto.response.BookingEventResponse;
import vn.vietroute.business.booking.dto.response.BookingResponse;
import vn.vietroute.business.carrier.dto.request.CarrierRequest;
import vn.vietroute.business.carrier.dto.request.DriverRequest;
import vn.vietroute.business.carrier.dto.request.VehicleRequest;
import vn.vietroute.business.carrier.dto.response.CarrierResponse;
import vn.vietroute.business.carrier.dto.response.PublicCarrierResponse;
import vn.vietroute.business.carrier.dto.response.VehicleResponse;
import vn.vietroute.business.catalog.dto.request.CategoryRequest;
import vn.vietroute.business.catalog.dto.response.CategoryResponse;
import vn.vietroute.business.report.dto.response.ReportCarrierResponse;
import vn.vietroute.business.report.dto.response.ReportSummaryResponse;
import vn.vietroute.business.trip.dto.request.RouteRequest;
import vn.vietroute.business.trip.dto.request.RouteStopRequest;
import vn.vietroute.business.trip.dto.request.TripRequest;
import vn.vietroute.business.trip.dto.response.JourneyMatchResponse;
import vn.vietroute.business.trip.dto.response.JourneyResponse;
import vn.vietroute.business.trip.dto.response.RouteResponse;
import vn.vietroute.business.trip.dto.response.RouteStopResponse;
import vn.vietroute.business.trip.dto.response.TripChoiceResponse;
import vn.vietroute.business.trip.dto.response.TripResponse;
import vn.vietroute.business.trip.dto.response.TripStopResponse;
import vn.vietroute.common.api_journal.dto.response.ApiJournalDetail;
import vn.vietroute.common.api_journal.dto.response.ApiJournalResponse;
import vn.vietroute.common.api_journal.dto.search.ApiJournalSearch;
import vn.vietroute.common.api_journal.repo.ApiJournalRepo;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.web.response.ApiResponse;

/** Ghi trực tiếp/commit PostgreSQL, không cache/queue lịch sử trong RAM; lỗi log không làm lỗi nghiệp vụ. */
@Service
public class ApiJournalService {

    private static final Logger LOG = Logger.getLogger(ApiJournalService.class.getName());

    private final AppSettings settings;

    private final ApiJournalRepo repository;

    private final ObjectMapper mapper;

    private final String instanceId = UUID.randomUUID().toString();

    private final Semaphore writer = new Semaphore(1);

    private final AtomicLong failedWrites = new AtomicLong();

    private final AtomicLong lastWarning = new AtomicLong();

    private volatile long retryAfter;

    private volatile long cleanupAfter;

    public ApiJournalService(AppSettings settings, ApiJournalRepo repository, ObjectMapper mapper) {
        this.settings = settings;
        this.repository = repository;
        this.mapper = mapper;
    }

    public String instanceId() {
        return instanceId;
    }

    public boolean enabled() {
        return settings.isJournalEnabled();
    }

    /** Không enqueue nếu slot bận/DB lỗi: đếm lỗi, không giữ payload để gửi lại sau. */
    public void add(ApiJournalDetail detail) {
        if (!enabled()) return;
        long now = System.nanoTime();
        if ((retryAfter != 0 && now - retryAfter < 0) || !acquireWriter()) {
            failed(detail, now);
            return;
        }
        try {
            // Request khác có thể vừa mở circuit trong lúc request này chờ slot.
            if (retryAfter != 0 && System.nanoTime() - retryAfter < 0) {
                failed(detail, now);
                return;
            }
            repository.append(detail);
        } catch (RuntimeException exception) {
            retryAfter = now + 30_000_000_000L;
            failed(detail, now);
            return;
        } finally {
            writer.release();
        }
        // Insert commit trước cleanup: timeout dọn không được làm mất bản ghi vừa ghi thành công.
        if ((cleanupAfter == 0 || now - cleanupAfter >= 0) && writer.tryAcquire()) {
            try {
                cleanupAfter = now + 60_000_000_000L;
                repository.cleanup(settings.getJournalRetentionMinutes(), settings.getJournalCapacity());
            } catch (RuntimeException ignored) {
                LOG.warning("event=api_journal_cleanup_unavailable");
            } finally {
                writer.release();
            }
        }
    }

    /** Chỉ chờ slot tối đa 100ms trên request hiện hành; không tạo hàng đợi/batch log riêng. */
    private boolean acquireWriter() {
        try {
            return writer.tryAcquire(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void failed(ApiJournalDetail detail, long now) {
        failedWrites.incrementAndGet();
        long previous = lastWarning.get();
        if ((previous == 0 || now - previous >= 1_000_000_000L) && lastWarning.compareAndSet(previous, now)) {
            LOG.warning("event=api_journal_write_unavailable traceId=" + detail.getEntry().getTraceId());
        }
    }

    /** Đọc DB dùng chung: node khác/restart vẫn thấy record đã commit. */
    public ApiJournalResponse page(ApiJournalSearch search) {
        requireTarget("GET " + Routes.API_JOURNAL);
        return new ApiJournalResponse(
            instanceId,
            enabled(),
            settings.getJournalCapacity(),
            settings.getJournalRetentionMinutes(),
            failedWrites.get(),
            "POSTGRESQL",
            repository.page(search, settings.getJournalRetentionMinutes())
        );
    }

    public ApiJournalDetail find(UUID id) {
        requireTarget("GET " + Routes.API_JOURNAL_DETAIL);
        ApiJournalDetail result = repository.find(id, settings.getJournalRetentionMinutes());
        if (result == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        return result;
    }

    public static final int MAX_SNAPSHOT = 4096;

    private static final Set<Class<?>> REQUEST_TYPES = Set.of(
        CategoryRequest.class,
        BookingRequest.class,
        CarrierRequest.class,
        DriverRequest.class,
        VehicleRequest.class,
        RouteRequest.class,
        RouteStopRequest.class,
        TripRequest.class
    );
    private static final Set<Class<?>> RESPONSE_TYPES = Set.of(
        CategoryResponse.class,
        BookingResponse.class,
        BookingEventResponse.class,
        CarrierResponse.class,
        VehicleResponse.class,
        PublicCarrierResponse.class,
        RouteResponse.class,
        RouteStopResponse.class,
        TripResponse.class,
        TripChoiceResponse.class,
        TripStopResponse.class,
        ReportSummaryResponse.class,
        ReportCarrierResponse.class,
        JourneyResponse.class,
        JourneyMatchResponse.class
    );

    private static final Set<String> NUMBERS = Set.of(
        "id",
        "carrierId",
        "vehicleId",
        "driverId",
        "routeId",
        "tripId",
        "tripVersion",
        "locationId",
        "provinceId",
        "vehicleTypeId",
        "pickupStopId",
        "dropoffStopId",
        "seats",
        "capacity",
        "remainingSeats",
        "sequence",
        "stopOrder",
        "price",
        "amount",
        "totalPrice",
        "bookings",
        "passengers",
        "sortOrder",
        "version",
        "page",
        "size",
        "totalElements",
        "totalPages"
    );
    private static final Set<String> FLAGS = Set.of(
        "active",
        "hasNext",
        "hasPrevious",
        "canUpdate",
        "canAssign",
        "canCancel",
        "published"
    );

    private static final Set<String> ENUMS = Set.of("type", "status", "kind", "source");

    public boolean supports(Class<?> type) {
        return REQUEST_TYPES.contains(type);
    }

    /** Không capture auth/access response hoặc PII, kể cả tên field không giống secret. */
    public String request(Object value) {
        if (value == null || !supports(value.getClass())) return "{\"capture\":\"BODY_OMITTED\"}";
        ObjectNode fields = mapper.valueToTree(value);
        // Gộp DTO không tự mở thêm field capture; active trước đây chỉ được ghi trong response.
        if (value instanceof CategoryRequest) fields.remove("active");
        return encode(project(fields));
    }

    public String response(ApiResponse<?> response, String path) {
        ObjectNode result = mapper.createObjectNode();
        result.put("success", response.isSuccess());
        result.put("code", response.getCode());
        result.put("messageKey", response.getMessageKey());
        result.put("locale", response.getLocale());
        ArrayNode errors = result.putArray("errors");
        response
            .getErrors()
            .stream()
            .limit(10)
            .forEach(error -> {
                ObjectNode field = errors.addObject();
                field.put("field", error.getField().matches(Regex.JOURNAL_FIELD) ? error.getField() : "REDACTED");
                field.put("code", error.getCode());
                field.put("messageKey", error.getMessageKey());
            });
        Object data = response.getData();
        if (data != null && RESPONSE_TYPES.contains(data.getClass())) {
            result.set("data", project(mapper.valueToTree(data)));
        } else if (data instanceof PageResponse<?> page) {
            ObjectNode summary = result.putObject("data");
            summary.put("totalElements", page.getTotalElements());
            summary.put("page", page.getPage());
            summary.put("size", page.getSize());
            ArrayNode content = summary.putArray("content");
            page.getContent()
                .stream()
                .limit(3)
                .filter(item -> item != null && RESPONSE_TYPES.contains(item.getClass()))
                .forEach(item -> content.add(project(mapper.valueToTree(item))));
            summary.put("capture", "FIRST_3_ITEMS_ONLY");
        } else result.put("data", "OMITTED_BY_POLICY");
        return encode(result);
    }

    /** Không giữ chuỗi tự do như tên, địa chỉ, message hay tham số; giữ cấu trúc và enum/số hợp lệ. */
    private JsonNode project(JsonNode node) {
        ObjectNode result = mapper.createObjectNode();
        node.properties()
            .stream()
            .limit(20)
            .forEach(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (NUMBERS.contains(key) && value.isNumber()) result.set(key, value);
                else if (FLAGS.contains(key) && value.isBoolean()) result.set(key, value);
                else if (ENUMS.contains(key)) {
                    if (value.isArray()) {
                        ArrayNode array = result.putArray(key);
                        value
                            .valueStream()
                            .limit(32)
                            .forEach(item -> array.add(enumValue(item)));
                    } else result.put(key, enumValue(value));
                } else if (Set.of("code", "nameVi", "nameEn", "name").contains(key)) result.put(key, "[REDACTED]");
            });
        return result;
    }

    private String enumValue(JsonNode value) {
        return value.isTextual() && value.textValue().matches(Regex.JOURNAL_ENUM) ? value.textValue() : "[REDACTED]";
    }

    private String encode(JsonNode value) {
        String json = value.toString();
        return json.length() <= MAX_SNAPSHOT ? json : "{\"capture\":\"OMITTED_SIZE_LIMIT\"}";
    }

    /** EndpointAccess đã duyệt principal trước controller; lời gọi Java trực tiếp cũng phải đúng target. */
    private void requireTarget(String target) {
        if (!target.equals(UserPrincipal.current().getTarget())) throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
}
