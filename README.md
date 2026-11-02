# VietRoute

Web quản lý nhà xe và đặt chỗ theo chặng: danh mục/địa điểm → xe/tài xế → tuyến/chuyến → đặt/xác nhận/thu–hoàn → tiến độ tài xế → báo cáo. Java17+/JDK21, Spring MVC6/Security/Thymeleaf, JPA/Hibernate + JDBC, PostgreSQL, WAR/Tomcat10.1; không Spring Boot.

## Bắt đầu và học phần của mình

- [Bộ tài liệu12 file, đọc theo5 bước](docs/README.md).
- [Từng thành viên phụ trách gì, đi từ UI/API xuống bảng ra sao](docs/05-ke-hoach/PHAN_CONG.md).
- [Kịch bản15 phút và45 câu hỏi–trả lời để demo](docs/05-ke-hoach/DEMO_VA_HOI_DAP.md).
- [Trạng thái source/PR/main/Render và giới hạn](docs/05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md).

Auth40 file/8 bảng; business121 file/14 bảng; common61 file, journal1 bảng và history1: tổng24 bảng, baseline8 SQL. Bộ giả lập trên DB mới có46 account,7 nhà xe,25 xe/tài xế,28 mẫu tuyến,646 chuyến và3866 đơn; [Duy Long Hải Phòng ↔ Hà Nội](docs/01-bat-dau/CHAY_LOCAL.md#nhà-xe-duy-long) có bốn lượt/ngày. Không có GPS/mobile, mail/reset qua thư, upload hoặc payment gateway. Oracle26ai mới chuẩn bị, chưa chạy nghiệp vụ.

## Chạy và phát triển

Theo [chạy IntelliJ và tài khoản/key](docs/01-bat-dau/CHAY_LOCAL.md), [formatter](docs/01-bat-dau/FORMAT_INTELLIJ.md), [quy ước backend](docs/02-phat-trien/QUY_UOC_PHAT_TRIEN.md) và [schema/quyền](docs/02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md). .env có secret chỉ local, không Git/WAR; Render dùng env. Địa chỉ DB mẫu không là ủy quyền ghi dữ liệu thật.

```powershell
npm ci
npm run format
npm run format:check
npm run check:rules
npm run check:docs
.\mvnw.cmd verify
node --test src/test/resources/dev/*.test.cjs
```

Maven mặc định không thay toàn bộ regression PostgreSQL; xem điều kiện/DB riêng trong [hướng dẫn demo](docs/05-ke-hoach/DEMO_VA_HOI_DAP.md#kiểm-tra-trước-buổi-demo). Không đọc root .env hoặc seed/cleanup DB thật để kiểm build. Vận hành/journal/Render ở [một tài liệu chung](docs/04-van-hanh/VAN_HANH.md).

Làm nhóm theo [CONTRIBUTING](CONTRIBUTING.md): nhánh thành viên → dev; main/production chỉ theo lệnh phát hành mới. AI hoặc đổi máy đọc [AGENTS](AGENTS.md) và [bàn giao](docs/06-ai-va-ban-giao/AI_VA_BAN_GIAO.md). Phân công không là bằng chứng tác giả; chỉ ghi đóng góp có commit/PR/review/test thật.
