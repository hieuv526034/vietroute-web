package vn.vietroute.business.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import vn.vietroute.auth.repo.UserRepo;
import vn.vietroute.business.demo.repository.DemoSeedRepository;
import vn.vietroute.business.demo.repository.InitialRouteRepository;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.application.ConfigValues;
import vn.vietroute.common.config.application.ConfigurationEnvironment;
import vn.vietroute.common.config.database.DatabaseInitializer;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Dữ liệu nền opt-in tại startup, sau migration; không thực hiện trong Maven đóng gói hoặc tự bật production. */
@Component
@RequiredArgsConstructor
public class BusinessDataInitializer implements InitializingBean {

    private final DatabaseInitializer database;
    private final AppSettings settings;
    private final ConfigurationEnvironment environment;
    private final DemoSeedRepository seeds;
    private final InitialRouteRepository routes;
    private final UserRepo accounts;
    private final JdbcTransactions transactions;

    /** Seed và gắn đúng account mới vào nhà xe cùng transaction; chạy lại giữ dữ liệu quản trị đã sửa. */
    @Override
    public void afterPropertiesSet() {
        if (
            !settings.isDatabaseInitEnabled() || !new ConfigValues(environment).flag(Env.BUSINESS_INIT_ENABLED, false)
        ) return;
        transactions.execute(connection -> {
            seeds.seed(connection, SystemActor.ID);
            routes.seed(connection);
            accounts.assignInitialScopes(connection);
            return null;
        });
    }
}
