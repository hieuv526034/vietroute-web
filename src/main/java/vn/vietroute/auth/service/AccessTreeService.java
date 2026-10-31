package vn.vietroute.auth.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.dto.record.capability.AccessCapabilityResponse;
import vn.vietroute.auth.dto.record.capability.EffectiveAccessResponse;
import vn.vietroute.auth.dto.record.policy.AccessPolicyResponse;
import vn.vietroute.auth.dto.record.registry.PermissionResponse;
import vn.vietroute.auth.dto.record.resource.AccessActionResponse;
import vn.vietroute.auth.dto.record.resource.AccessEndpointResponse;
import vn.vietroute.auth.dto.record.resource.AccessFeatureResponse;
import vn.vietroute.auth.dto.record.resource.AccessResourceResponse;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.repository.AccessFeatureRepository;
import vn.vietroute.auth.security.AccessCapabilities;
import vn.vietroute.auth.security.AccessFeatures;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.constant.AccessConstants.Groups;

/** Danh bạ cây chỉ dùng mapping hiện tại; DB giữ nguồn cấp quyền và metadata đã sync, không kích hoạt route cũ. */
@Service
@RequiredArgsConstructor
public class AccessTreeService {

    private final MessageSource messages;
    private final AccessFeatureRepository repository;
    private final AccessService access;

    /** Lọc permission active từ registry DB, rồi ghép function/action/endpoint đang có trên node. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.READ)
    public List<AccessFeatureResponse> tree(List<AccessResourceResponse> inventory, Locale locale) {
        Map<String, String> names = access
            .permissions(locale)
            .stream()
            .collect(Collectors.toMap(item -> item.code().name(), PermissionResponse::name));
        Set<String> active = names.keySet();
        List<String> order = Arrays.stream(PermissionAction.values()).map(Enum::name).toList();
        List<AccessFeatureResponse> result = new ArrayList<>();
        for (var feature : AccessFeatures.definitions()) {
            Map<String, List<AccessEndpointResponse>> bindings = new LinkedHashMap<>();
            for (var endpoint : inventory) {
                if (!endpoint.function().equals(feature.code()) || endpoint.publicRoute()) continue;
                for (String code : endpoint.declaredPermissions()) {
                    if (active.contains(code)) bindings
                        .computeIfAbsent(code, key -> new ArrayList<>())
                        .add(
                            new AccessEndpointResponse(
                                endpoint.target(),
                                endpoint.description(),
                                endpoint.declaredPermissions(),
                                endpoint.match(),
                                endpoint.adminOnly()
                            )
                        );
                }
            }
            List<AccessActionResponse> actions = bindings
                .entrySet()
                .stream()
                .map(entry ->
                    new AccessActionResponse(
                        entry.getKey(),
                        names.get(entry.getKey()),
                        entry.getKey().substring(entry.getKey().lastIndexOf('_') + 1),
                        List.copyOf(entry.getValue())
                    )
                )
                .sorted(
                    Comparator.comparingInt(item -> {
                        int index = order.indexOf(item.action());
                        return index < 0 ? Integer.MAX_VALUE : index;
                    })
                )
                .toList();
            result.add(
                new AccessFeatureResponse(
                    feature.code(),
                    feature.parent(),
                    messages.getMessage(feature.nameKey(), null, locale),
                    feature.path(),
                    feature.viewPermission(),
                    actions,
                    AccessFeatures.inSystemBundle(feature.code())
                )
            );
        }
        return List.copyOf(result);
    }

    /** Chỉ capabilities của principal hiện tại; không nhận user/role từ request. */
    @AccessOperation(authenticated = true)
    public List<AccessCapabilityResponse> own(
        List<AccessResourceResponse> inventory,
        List<AccessPolicyResponse> policies
    ) {
        return AccessCapabilities.compute(inventory, SecurityContextHolder.getContext().getAuthentication(), policies);
    }

    /** Nguồn role chỉ người có ACCESS_READ xem được; không trả dữ liệu credential/PII ngoài username quản trị. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.READ)
    public EffectiveAccessResponse effective(long id) {
        return repository.effective(id);
    }
}
