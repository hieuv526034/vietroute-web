package vn.vietroute.common.base;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** Audit do server/SQL quản lý; entity JDBC không có annotation JPA và không trả trực tiếp ra API. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public abstract class BaseEntity {

    private Long id;

    private Instant createdAt;

    private Instant updatedAt;

    private Long createdBy;

    private Long updatedBy;

    private long version;
}
