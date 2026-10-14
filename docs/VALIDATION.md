# Kiểm chứng bản web mới

Cập nhật sau góp ý UI: cả 6 profile ngưỡng khởi đầu đã qua `DatasetProfileVerification` (đối chiếu utility toàn bộ HAUP trả về trong các lượt này) và `scripts/verify-web.py --presets --real-data` trên server QA riêng. Số HAUP cuối: Accidents 3, Chainstore 1, NewChess 8, NewConnects 1, NewMushroom 1, Retail 1. Xem [DATASET-PRESETS.md](DATASET-PRESETS.md) cho nguồn ngưỡng, công thức cắt tỉa và giới hạn kiểm chứng. Test thuật toán/vét cạn, mẫu bài báo và test ứng dụng đều qua sau thay đổi.

Thực hiện ngày 14/10/2026, JDK 25.0.2 biên dịch với `--release 17`. Bản Java không cần thư viện bên ngoài khi chạy. Maven chưa có trên máy nên không tuyên bố đã chạy `mvn package`; build và các test dưới đây chạy bằng javac/java.

## Các kiểm tra đã qua

- `run-tests.ps1`: PiHaupaSelfTest, ExportBundleTest, ArchiveDatasetReaderTest, LargeTextDatasetTest, LabApplicationTest.
- Self-test đối chiếu HAUP với vét cạn trên ví dụ bài báo và luồng nhỏ ngẫu nhiên; kiểm tra tích lũy ID, ranh giới re-scan, ngưỡng phân loại và mẫu số không dương.
- Màn hình `/api/verify`: 12/12 đối chiếu đạt. TU tích lũy 141, 173, 240; MU từng batch 73, 24, 40; HAUP cuối E=84, B=75, BE=63.5. Tight re-scan 1 lần, Original 2 lần; DB1 Tight không re-scan, DB2 có re-scan.
- `verify-large.ps1`: 9.000 giao dịch nhân bản mẫu bài báo, AU oracle, ngưỡng và quyết định Eq. (6) đều khớp. Đây là dữ liệu scale tổng hợp, không phải bộ thực nghiệm gốc.
- CLI `run.ps1 --summary`: 9 giao dịch, 3 batch, 1 re-scan, cuối cùng 3 HAUP và 7 PRE-LARGE đang quản lý.
- `scripts/verify-web.py --url http://localhost:8015 --real-data`: HTTP, mẫu bài báo, so sánh Original/Tight, xuất TXT/CSV, upload transactions.txt, từ chối TU sai, ngưỡng sai, ID danh mục không hợp lệ và Origin khác nguồn.
- Qua HTTP: NewChess 3.196 giao dịch, TU=36.864.920.448, Su/Sl=0.23/0.10 cho 0 HAUP; NewMushroom 8.416 giao dịch, TU=60.998.108.464, Su/Sl=0.05/0.02 cho 5 HAUP và 311 PRE-LARGE đang quản lý.
- Browser: chạy mẫu thật, đối chiếu 12/12, lọc LARGE ở DB2, bảng IU/EU/TU/MU, AUUB, phép thế Eq. (12), màn hình đo tài nguyên và điều hướng. Không có error/warn JavaScript trong phiên kiểm tra. Kiểm tra viewport 375 px không có tràn ngang toàn trang; bảng dài có vùng cuộn riêng. Đây chưa phải một audit WCAG đầy đủ.
- Kiểm tra cú pháp ES modules bằng Node và `git diff --check` cho các tệp thay đổi liên quan.

## Dữ liệu gốc: kiểm tra cả sáu bộ trong ZIP

`RealDatasetVerification` đọc ZIP gốc của người dùng; không sử dụng kết quả cũ trong thư mục results làm oracle.

- Accidents: 340.183 dòng; TU = 3.478.342.570.855.
- Chainstore: 1.112.949 dòng; TU = 2.428.645.460.792.
- NewChess: 3.196 dòng; TU = 36.864.920.448.
- NewConnects: 67.557 dòng; TU = 996.517.630.255.
- NewMushroom: 8.416 dòng; TU = 60.998.108.464.
- Retail: 88.162 dòng; TU = 281.010.698.598.

Không có dòng sai TU; metadata item đầy đủ. NewMushroom tại 0.23/0.10 không có HAUP. Tại 0.05/0.02, 5 HAUP được cộng utility độc lập bằng BigDecimal từ giao dịch gốc:

- {97}: U=4.254.357.114; AU=4.254.357.114.
- {94,97}: U=6.941.641.179; AU=3.470.820.589,5.
- {36,97}: U=6.562.861.083; AU=3.281.430.541,5.
- {36,94,97}: U=9.354.687.729; AU=3.118.229.243.
- {90,97}: U=6.213.727.296; AU=3.106.863.648.

Kiểm tra cả sáu bộ ở trên là audit dữ liệu, không phải benchmark khai phá đầy đủ mọi bộ tại mọi ngưỡng. Khai phá thực tế đã chạy thêm NewChess/NewMushroom qua API; test không thay thế chứng minh tính đúng cho mọi dữ liệu. Thời gian và heap phụ thuộc máy, JVM, warm-up, GC và ngưỡng; không dùng số đo một lượt để tuyên bố hiệu năng tổng quát.

Ảnh giao diện: [ui-preview.jpg](ui-preview.jpg).
