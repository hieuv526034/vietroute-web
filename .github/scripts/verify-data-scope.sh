#!/usr/bin/env bash
# AUTHDB04: ca tương đương đã chuyển vào JUnit/PostgreSQL trước build; HTTP WAR kiểm bằng auth-smoke.py.
# Giữ assertion mỗi nhóm, thiếu report/skip/fail đều dừng CI, không dùng helper action đã bỏ.
python3 .github/scripts/assert-auth-regressions.py data-scope
