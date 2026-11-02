package vn.vietroute.business.bootstrap.initializer;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import vn.vietroute.auth.initializer.AuthInitializer;
import vn.vietroute.auth.repo.UserRepo;
import vn.vietroute.business.bootstrap.repo.BusinessDataRepo;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.application.ConfigValues;
import vn.vietroute.common.config.application.ConfigurationEnvironment;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.initializer.DatabaseInitializer;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Dữ liệu nền opt-in tại startup, sau migration; không thực hiện trong Maven đóng gói hoặc tự bật production. */
@Component
@RequiredArgsConstructor
public class BusinessDataInitializer implements InitializingBean {

    private final DatabaseInitializer database;
    private final AppSettings settings;
    private final ConfigurationEnvironment environment;
    private final BusinessDataRepo seeds;
    private final UserRepo accounts;
    private final AuthInitializer auth;
    private final JdbcTransactions transactions;

    /** Seed và gắn đúng account mới vào nhà xe cùng transaction; chạy lại giữ dữ liệu quản trị đã sửa. */
    @Override
    public void afterPropertiesSet() {
        if (
            !settings.isDatabaseInitEnabled() || !new ConfigValues(environment).flag(Env.BUSINESS_INIT_ENABLED, false)
        ) return;
        transactions.execute(connection -> {
            seeds.seed(connection, SystemActor.ID);
            seeds.seedAdditionalFoundation(connection);
            seeds.seedRoutes(connection);
            accounts.assignInitialScopes(connection, auth.getData().path("accounts"));
            if (new ConfigValues(environment).flag(Env.DEFAULT_ACCOUNTS_ENABLED, false)) {
                seeds.seedOperations(connection);
            }
            return null;
        });
    }
}
