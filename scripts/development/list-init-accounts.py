"""Liệt kê tài khoản khởi tạo; chỉ đọc/in mật khẩu khi người vận hành chọn --show-passwords.

Không kết nối DB và không xác nhận mật khẩu hiện tại nếu người dùng đã đổi sau init.
"""
import argparse
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--show-passwords", action="store_true", help="Hiển thị mật khẩu khởi tạo trên terminal cá nhân, không ghi file/log")
    args = parser.parse_args()
    data = json.loads((ROOT / "src/main/resources/auth/default-access.json").read_text(encoding="utf-8"))
    business = json.loads((ROOT / "src/main/resources/bootstrap/foundation.json").read_text(encoding="utf-8"))
    extra = json.loads((ROOT / "src/main/resources/bootstrap/operations.json").read_text(encoding="utf-8"))
    carriers = {item["code"]: item["name"] for item in business["carriers"] + extra["carriers"]}
    values = {}
    if args.show_passwords:
        file = ROOT / ".env"
        if file.is_file():
            for line in file.read_text(encoding="utf-8-sig").splitlines():
                entry = line.strip()
                if not entry or entry.startswith("#") or "=" not in entry:
                    continue
                key, value = entry.split("=", 1)
                values[key.strip()] = value.strip().strip('"').strip("'")
        print("Chỉ là mật khẩu khởi tạo trong OS/.env; mật khẩu đã đổi trong DB được giữ nguyên. Không chia sẻ bản terminal này.")
    def configured(key):
        return os.environ.get(key, values.get(key, "[chưa cấu hình]"))
    rows = [{"username": configured("VIETROUTE_BOOTSTRAP_ADMIN_USERNAME") if args.show_passwords else "admin",
        "role": data["bootstrapRole"], "passwordEnv": "VIETROUTE_BOOTSTRAP_ADMIN_PASSWORD"}, *data["accounts"]]
    print("USERNAME | ROLE | PHẠM VI | " + ("MẬT KHẨU KHỞI TẠO" if args.show_passwords else "KEY MẬT KHẨU"))
    for account in rows:
        scope = carriers.get(account.get("carrierCode"), "Tất cả theo grant" if account["role"] == data["bootstrapRole"] else "Đơn của chính mình")
        credential = configured(account["passwordEnv"]) if args.show_passwords else account["passwordEnv"]
        print(" | ".join([account["username"], account["role"], scope, credential]))
    print("Số tài khoản: " + str(len(rows)) + ". Chỉ tạo phần thiếu khi bật init; không reset password hoặc phục hồi quyền đã thu hồi.")


if __name__ == "__main__":
    main()
