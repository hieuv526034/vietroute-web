package vn.vietroute.common.web.openapi;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.AnnotatedElement;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.BeanWrapperImpl;

/** Đọc annotation input riêng để tránh trùng tên Parameter của reflection, Swagger annotation và model. */
final class OpenApiInputs {

    private OpenApiInputs() {}

    record Description(String text, String example, boolean hidden, boolean deprecated) {}

    static Description description(AnnotatedElement element) {
        var annotation = element.getAnnotation(Parameter.class);
        return annotation == null
            ? new Description("", "", false, false)
            : new Description(
                  annotation.description(),
                  annotation.example(),
                  annotation.hidden(),
                  annotation.deprecated()
              );
    }

    /** Giữ các ràng buộc khai báo cho path/query/header, không tự suy quyền hoặc thay validation runtime. */
    static void constraints(AnnotatedElement element, Schema<?> schema) {
        var parameter = element.getAnnotation(Parameter.class);
        if (parameter == null) return;
        var declaration = parameter.schema();
        if (!declaration.format().isBlank()) schema.setFormat(declaration.format());
        if (!declaration.pattern().isBlank()) schema.setPattern(declaration.pattern());
        if (!declaration.minimum().isBlank()) schema.setMinimum(new BigDecimal(declaration.minimum()));
        if (!declaration.maximum().isBlank()) schema.setMaximum(new BigDecimal(declaration.maximum()));
        if (declaration.minLength() > 0) schema.setMinLength(declaration.minLength());
        if (declaration.maxLength() < Integer.MAX_VALUE) schema.setMaxLength(declaration.maxLength());
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

    /** ModelAttribute có giá trị mặc định thì caller được bỏ field, kể cả DTO gắn NotNull để từ chối null. */
    static boolean requiredQuery(Class<?> type, String property, List<String> required) {
        if (required == null || !required.contains(property)) return false;
        var bean = new BeanWrapperImpl(type);
        return !bean.isReadableProperty(property) || bean.getPropertyValue(property) == null;
    }
}
