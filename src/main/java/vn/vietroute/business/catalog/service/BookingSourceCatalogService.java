package vn.vietroute.business.catalog.service;

import org.springframework.stereotype.Service;
import vn.vietroute.auth.security.annotation.AccessResource;
import vn.vietroute.business.catalog.repository.CatalogRepository;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.CatalogType;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Quyền nguồn đặt chỗ độc lập, tài nguyên cố định trong source thay vì lấy từ request. */
@Service
@AccessResource(Groups.BOOKING_SOURCE)
public class BookingSourceCatalogService extends TypedCatalogService {

    public BookingSourceCatalogService(CatalogRepository catalogs, JdbcTransactions transactions) {
        super(CatalogType.BOOKING_SOURCE, catalogs, transactions);
    }
}
