package vn.vietroute.common.monitoring.service;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

/** Cô lập tên MXBean trùng thư viện; nền tảng không hỗ trợ trả null thay vì số 0 gây hiểu nhầm. */
final class ProcessCpu {

    private ProcessCpu() {}

    static Double ratio() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean bean) {
            double value = bean.getProcessCpuLoad();
            return value >= 0 && Double.isFinite(value) ? value : null;
        }
        return null;
    }
}
