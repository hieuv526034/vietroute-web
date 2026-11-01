package vn.vietroute.common.web.openapi;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.validation.annotation.Validated;

/** Đọc annotation input riêng để tránh trùng tên Parameter của reflection, Swagger annotation và model. */
final class OpenApiInputs {

    private OpenApiInputs() {}

    record Description(String text, String example, boolean hidden, boolean deprecated, String summary) {}

    static Description operation(AnnotatedElement element) {
        Operation annotation = element.getAnnotation(Operation.class);
        return annotation == null
            ? null
            : new Description(annotation.description(), "", annotation.hidden(), false, annotation.summary());
    }

    static Description description(AnnotatedElement element) {
        Parameter annotation = element.getAnnotation(Parameter.class);
        return annotation == null
            ? new Description("", "", false, false, "")
            : new Description(
                  annotation.description(),
                  annotation.example(),
                  annotation.hidden(),
                  annotation.deprecated(),
                  ""
              );
    }

    /** Giữ các ràng buộc khai báo cho path/query/header, không tự suy quyền hoặc thay validation runtime. */
    static void constraints(AnnotatedElement element, Schema<?> schema) {
        Parameter parameter = element.getAnnotation(Parameter.class);
        if (parameter == null) return;
        if (!parameter.schema().format().isBlank()) schema.setFormat(parameter.schema().format());
        if (!parameter.schema().pattern().isBlank()) schema.setPattern(parameter.schema().pattern());
        if (!parameter.schema().minimum().isBlank()) schema.setMinimum(new BigDecimal(parameter.schema().minimum()));
        if (!parameter.schema().maximum().isBlank()) schema.setMaximum(new BigDecimal(parameter.schema().maximum()));
        if (parameter.schema().minLength() > 0) schema.setMinLength(parameter.schema().minLength());
        if (parameter.schema().maxLength() < Integer.MAX_VALUE) schema.setMaxLength(parameter.schema().maxLength());
    }

    /** Resolve $ref của object query; giữ đủ field kế thừa và schema/validation từ DTO. */
    static Schema<?> resolve(Schema<?> schema, OpenAPI api) {
        return schema.get$ref() == null
            ? schema
            : api
                  .getComponents()
                  .getSchemas()
                  .get(schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1));
    }

    /** Một DTO dùng chung nhưng required phải theo đúng nhóm của handler, không cộng bắt buộc tạo+sửa vào nhau. */
    static Schema<?> requestSchema(AnnotatedElement parameter, Class<?> type, Schema<?> schema, OpenAPI api) {
        Validated validation = parameter.getAnnotation(Validated.class);
        if (validation == null || validation.value().length == 0) return schema;
        Schema<?> definition = resolve(schema, api);
        Schema<?> operationSchema = Json.mapper().convertValue(definition, Schema.class);
        List<String> required = new ArrayList<>();
        BeanWrapperImpl defaults = new BeanWrapperImpl(type);
        for (Class<?> current = type; current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                Object initial = defaults.isReadableProperty(field.getName())
                    ? defaults.getPropertyValue(field.getName())
                    : null;
                NotNull notNull = field.getAnnotation(NotNull.class);
                NotBlank notBlank = field.getAnnotation(NotBlank.class);
                NotEmpty notEmpty = field.getAnnotation(NotEmpty.class);
                Min minimum = field.getAnnotation(Min.class);
                boolean mandatory =
                    (notNull != null && initial == null && includes(notNull.groups(), validation.value())) ||
                    (notBlank != null &&
                        (initial == null || initial.toString().isBlank()) &&
                        includes(notBlank.groups(), validation.value())) ||
                    (notEmpty != null && initial == null && includes(notEmpty.groups(), validation.value()));
                // Primitive mặc định 0 nhưng Min(1) thì client vẫn phải gửi giá trị hợp lệ.
                if (
                    field.getType().isPrimitive() && minimum != null && includes(minimum.groups(), validation.value())
                ) {
                    mandatory |= initial instanceof Number number && number.longValue() < minimum.value();
                }
                if (mandatory) required.add(field.getName());
            }
        }
        operationSchema.setRequired(required.isEmpty() ? null : required);
        operationSchema.addExtension(
            "x-validation-groups",
            Arrays.stream(validation.value()).map(Class::getSimpleName).toList()
        );
        // Component mô tả DTO dùng chung; các operation có bản required độc lập ở trên.
        definition.setRequired(null);
        return operationSchema;
    }

    private static boolean includes(Class<?>[] declared, Class<?>[] requested) {
        Class<?>[] constraints = declared.length == 0 ? new Class<?>[] { Default.class } : declared;
        return Arrays.stream(requested).anyMatch(group ->
            Arrays.stream(constraints).anyMatch(item -> item.isAssignableFrom(group))
        );
    }

    /** ModelAttribute có giá trị mặc định thì caller được bỏ field, kể cả DTO gắn NotNull để từ chối null. */
    static boolean requiredQuery(Class<?> type, String property, List<String> required) {
        if (required == null || !required.contains(property)) return false;
        BeanWrapperImpl bean = new BeanWrapperImpl(type);
        return !bean.isReadableProperty(property) || bean.getPropertyValue(property) == null;
    }
}
