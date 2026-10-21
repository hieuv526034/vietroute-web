package vn.vietroute.business.catalog.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.constant.Constants.CatalogType;

/** Danh mục mẫu có tên vi/en và audit, không chứa logic giao diện hoặc JSON. */
@Getter @Setter @SuperBuilder @NoArgsConstructor
public class CatalogItem extends BaseEntity {
    private CatalogType type;
    private String code;
    private String nameVi;
    private String nameEn;
    private int sortOrder;
    private boolean active;
}
