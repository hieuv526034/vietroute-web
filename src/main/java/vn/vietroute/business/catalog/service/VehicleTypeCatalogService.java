package vn.vietroute.business.catalog.service;

import org.springframework.stereotype.Service;
import vn.vietroute.auth.security.annotation.AccessResource;
import vn.vietroute.business.catalog.repository.CatalogRepository;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.CatalogType;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Quyền loại xe độc lập, tài nguyên cố định trong source thay vì lấy từ request. */
@Service
@AccessResource(Groups.VEHICLE_TYPE)
public class VehicleTypeCatalogService extends TypedCatalogService {

    public VehicleTypeCatalogService(CatalogRepository catalogs, JdbcTransactions transactions) {
        super(CatalogType.VEHICLE_TYPE, catalogs, transactions);
    }
}
