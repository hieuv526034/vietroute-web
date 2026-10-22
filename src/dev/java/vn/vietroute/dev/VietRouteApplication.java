package vn.vietroute.dev;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.tomcat.util.scan.StandardJarScanner;
import vn.vietroute.common.config.ConfigValues;
import vn.vietroute.common.config.WebApplicationInitializer;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/**
 * Điểm Run/Debug cho IntelliJ bằng Tomcat nhúng, vẫn khởi tạo đúng Spring MVC/Security của WAR.
 * Chỉ profile local biên dịch lớp này; production không có launcher hoặc thư viện Tomcat nhúng.
 */
public final class VietRouteApplication {

    private VietRouteApplication() {}

    public static void main(String[] args) throws Exception {
        if ("production".equalsIgnoreCase(new ConfigValues(System::getenv).raw(Env.ENV))) {
            throw new IllegalStateException("Launcher chỉ dùng local; production triển khai bằng WAR/Docker.");
        }
        Path root = Path.of("").toAbsolutePath().normalize();
        Path web = root.resolve("src/main/webapp");
        // Dùng thư mục class thực tế của IDE/Maven, không buộc IntelliJ phải xuất vào target/classes.
        Path classes = Path.of(
            WebApplicationInitializer.class.getProtectionDomain().getCodeSource().getLocation().toURI()
        );
        if (!Files.isDirectory(web) || !Files.isDirectory(classes)) {
            throw new IllegalStateException("Chọn working directory là thư mục dự án và bật Build trước khi Run.");
        }
        int port = new ConfigValues(System::getenv).number(Env.HTTP_PORT, 8080, 1, 65535);
        if (port < 1 || port > 65535) {
            throw new IllegalStateException(Env.HTTP_PORT.key() + " phải từ 1 đến 65535.");
        }
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(root.resolve("target/tomcat-local").toString());
        tomcat.setPort(port);
        // Thymeleaf/MVC phục vụ cả HTML và tài nguyên; không khởi tạo servlet JSP khi không có Jasper.
        tomcat.setAddDefaultWebXmlToWebapp(false);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        tomcat.getConnector().setProperty("maxThreads", "64");
        tomcat.getConnector().setProperty("maxPostSize", "5308416");
        tomcat.getConnector().setProperty("maxSwallowSize", "5308416");
        Context context = tomcat.addWebapp("", web.toString());
        Tomcat.addDefaultMimeTypeMappings(context);
        context.setParentClassLoader(VietRouteApplication.class.getClassLoader());
        StandardRoot resources = new StandardRoot(context);
        resources.addPreResources(new DirResourceSet(resources, "/WEB-INF/classes", classes.toString(), Routes.HOME));
        context.setResources(resources);
        // Tomcat tìm initializer trong WEB-INF/classes; không đăng ký lại vì sẽ tạo dispatcher hai lần.
        StandardJarScanner scanner = new StandardJarScanner();
        scanner.setScanClassPath(false);
        context.setJarScanner(scanner);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(tomcat), "vietroute-local-shutdown"));
        tomcat.start();
        if (!context.getState().isAvailable() || !tomcat.getConnector().getState().isAvailable()) {
            shutdown(tomcat);
            throw new IllegalStateException("Ứng dụng chưa khởi động; xem lỗi cấu hình phía trên.");
        }
        System.out.println("VietRoute đã chạy: http://localhost:" + port + Routes.LOGIN + "?lang=vi");
        tomcat.getServer().await();
    }

    /** Nút Stop/Debug đóng Spring context, pool và worker trước khi giải phóng Tomcat. */
    private static void shutdown(Tomcat tomcat) {
        try {
            tomcat.stop();
            tomcat.destroy();
        } catch (LifecycleException exception) {
            System.err.println("Không thể đóng đầy đủ Tomcat local.");
        }
    }
}
