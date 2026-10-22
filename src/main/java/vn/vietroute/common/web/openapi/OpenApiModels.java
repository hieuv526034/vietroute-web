package vn.vietroute.common.web.openapi;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;

/**
 * Dựng model Swagger riêng để không trùng tên với annotation Operation/RequestBody và envelope ApiResponse.
 * Mỗi file chỉ import một class cho từng tên, không viết tên package dài trong thân code.
 */
final class OpenApiModels {

    private OpenApiModels() {}

    /** Tạo thao tác với ID ổn định theo controller/method và nhóm hiển thị trên Swagger UI. */
    static Operation operation(String summary, String description, String id, String tag) {
        return new Operation()
            .summary(summary)
            .description(description)
            .operationId(id)
            .tags(List.of(tag))
            .responses(new ApiResponses());
    }

    /** Schema body lấy từ DTO Java đã gắn @Schema; cờ required lấy từ @RequestBody của Spring. */
    static void jsonBody(Operation operation, boolean required, Schema<?> schema) {
        operation.requestBody(new RequestBody().required(required).content(json(schema, null)));
    }

    /** Swagger hiển thị bộ chọn file thay vì ép file nhị phân thành JSON/query string. */
    static void multipartBody(Operation operation, Schema<?> schema) {
        operation.requestBody(
            new RequestBody()
                .required(true)
                .content(new Content().addMediaType("multipart/form-data", new MediaType().schema(schema)))
        );
    }

    /** Cùng cấu trúc cho response thành công/lỗi; ví dụ lỗi được gắn riêng, không sửa schema dùng chung. */
    static void response(Operation operation, String status, String description, Schema<?> schema, Object example) {
        operation
            .getResponses()
            .addApiResponse(status, new ApiResponse().description(description).content(json(schema, example)));
    }

    private static Content json(Schema<?> schema, Object example) {
        MediaType media = new MediaType().schema(schema);
        if (example != null) {
            media.example(example);
        }
        return new Content().addMediaType("application/json", media);
    }
}
