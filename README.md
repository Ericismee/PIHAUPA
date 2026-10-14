# PIHAUPA Research Lab

Web Java hoàn toàn mới để khai phá mẫu tiện ích trung bình cao trên dữ liệu tăng dần. Giữ lõi PIHAUPA, điều kiện re-scan, parser, CLI và test thuật toán; loại bỏ Swing Desktop và web cũ.

## Chạy nhanh

Yêu cầu JDK 17+. Tại thư mục dự án:

```powershell
.\run-web.ps1
# Cổng khác và thư mục dữ liệu tùy chọn:
.\run-web.ps1 -Port 8015 -DataRoot "C:\Users\Mera\Downloads\Data-20261007T013338Z-1-001\Data"
```

Mở http://localhost:8000. Script tự tìm folder Data theo đường dẫn Downloads đã cung cấp nếu chưa đặt PIHAUPA_DATA_ROOT. Có thể tải TXT qua giao diện nếu chưa cấu hình thư mục. Chọn dữ liệu, đặt Su/Sl, bấm Chạy phân tích. Không tự chạy dữ liệu lớn khi mở trang.

Giao diện nền sáng gộp Tổng quan, Phân tích từng bước, Quyết định re-scan và Kết quả khai phá thành một trang Phân tích PIHAUPA. Ba phần tổng quan → từng batch kèm re-scan → mẫu khai phá hiện sẵn, có liên kết cuộn nhanh. Đo lường & biểu đồ, Bộ dữ liệu, Kiểm chứng bài báo và Công thức & thuật ngữ được giữ riêng. Biểu đồ lấy dữ liệu thật từ Java, có đơn vị và bảng giá trị. Kết quả có tìm kiếm, lọc, phân trang; xuất CSV/TXT từ báo cáo đã tính.

## Dữ liệu và công thức

Khi chọn một trong 6 bộ dữ liệu gốc, ứng dụng tự điền ngưỡng khởi đầu đã kiểm chứng trên tệp đó, hiển thị tỷ lệ và phần trăm. Các cấu hình này được suy ra từ singleton utility thực tế, được ghi nhãn riêng với ngưỡng bài báo. Xem [docs/DATASET-PRESETS.md](docs/DATASET-PRESETS.md) cho công thức, số mẫu và cách chạy lại kiểm chứng. Thiết lập chỉ hiện trên trang phân tích; trạng thái đọc/khai phá và nhãn thời gian/heap hiển thị ngay dưới biểu mẫu.

Bài báo gốc: Kim et al., *Intelligent average utility pattern analysis using pre-large concept in dynamic stream data*, Journal of Big Data (2026), 13:13, DOI: https://doi.org/10.1186/s40537-025-01352-x.

```text
u(i,T) = IU(i,T) × EU(i)
TU(T) = Σ u(i,T); MU(T) = max u(i,T)
AU(P) = U(P) / |P|
LARGE: AU(P) ≥ Su × TU(DB)
PRE-LARGE: Sl × TU(DB) ≤ AU(P) < Su × TU(DB)
Tight Eq. (12): MU(ID) − Su × TU(ID) ≥ (Su − Sl) × TU(OD)
```

OD là database tại lần full scan gần nhất; ID cộng dồn các batch thêm sau lần đó. Initial scan DB0 không tính vào số re-scan. Xem đầy đủ MAU, AUUB, MRU, MRN và công thức trong UI.

Các tệp transactions.txt đọc đúng `items:TU:itemUtilities`: utility đã tính sẵn, EU=1, không nhân investment_table. Kiểm tra TU bằng tổng utility. Giữ thứ tự giao dịch, chia DB0/DB1/DB2 khoảng 80/10/10 để mô phỏng dữ liệu tăng dần. Mỗi bộ thực nghiệm chạy riêng.

## Build và test

Trên web có mục **Test case & kiểm thử** với 20 case chạy trực tiếp, mã TC-P/TC-E/TC-A/TC-D và bảng kỳ vọng/thực tế. Các bộ test đầy đủ trong mã nguồn được liệt kê riêng. Xem [docs/TESTCASES.md](docs/TESTCASES.md) cho phạm vi, lệnh chạy và mã lỗi.

```powershell
.\scripts\build.ps1
.\run-tests.ps1
.\run.ps1 --summary
.\verify-large.ps1
# Sau khi server đang chạy:
python .\scripts\verify-web.py --url http://localhost:8000 --real-data
# Đối chiếu cả 6 bộ gốc và U/AU trên NewMushroom:
java -Xmx4g -ea -cp "target/classes;target/test-classes" piHAUPA.RealDatasetVerification "C:\Users\Mera\Downloads\Data-20261007T013338Z-1-001.zip"
```

Có pom.xml với cấu trúc Maven chuẩn. Có thể `mvn package` và `java -Xmx4g -jar target/pihaupa-research-lab-2.0.0.jar 8000`. Bộ test Java hiện dùng main/assertions; lệnh run-tests.ps1 là lệnh chạy test bắt buộc, không xem mvn package là bằng chứng các test đã chạy.

Lõi CLI vẫn hỗ trợ input định lượng, transactions.txt, ZIP, ngưỡng và xuất kết quả. Thư mục examples và results được giữ lại.

## Kiến trúc và đo lường

Xem [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) cho phân lớp, Strategy/Facade/Repository/Adapter, API, nguồn thiết kế và giới hạn phép đo. Xem [docs/VALIDATION.md](docs/VALIDATION.md) cho kiểm chứng đã thực hiện.

Số đo thời gian thuật toán không gồm đọc tệp hoặc UI. CPU là CPU luồng khai phá, không phải % toàn máy. Heap là MiB lấy mẫu, không phải RSS hay peak tuyệt đối. Tối đa 8 báo cáo trong bộ nhớ; lịch sử không lưu bền sau reload. Dữ liệu lớn phụ thuộc heap và độ phức tạp của tập mẫu; mặc định cấp tối đa 4 GiB. Ít re-scan hơn không tự động chứng minh thời gian luôn nhanh hơn.
