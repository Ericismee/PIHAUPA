# Test case và xử lý lỗi

## Test chạy trực tiếp trên web

Mở **Test case & kiểm thử** ở sidebar và bấm **Chạy 20 test case**. Mỗi case có mã, đầu vào, kỳ vọng, kết quả thực tế và trạng thái ĐẠT/KHÔNG ĐẠT. Chưa bấm chạy thì hiển thị CHƯA CHẠY; kết quả có thời điểm thực thi. Đây là test thực thi bằng Java, không phải dữ liệu mẫu gán sẵn kết quả.

- TC-P01–TC-P12: TU, MU, HAUP của DB0–DB2, số re-scan Original/Tight và cận Tight DB1; oracle cố định từ ví dụ bài báo.
- TC-E01: Sl bằng Su bị từ chối với INVALID_THRESHOLDS.
- TC-E02: TU khác tổng utility bị từ chối với TU_MISMATCH.
- TC-E03: số item/utility không khớp bị từ chối với INVALID_INPUT.
- TC-E04: item trùng bị từ chối với DUPLICATE_ITEM.
- TC-E05: utility/TU âm bị từ chối với INVALID_INPUT.
- TC-A01: tại ranh giới Eq. (12), re-scan phải xảy ra.
- TC-A02: JSON phải giữ đầy đủ ngưỡng double cấu hình.
- TC-D01: transactions.txt bảo toàn utility gốc và TU; không nhân metadata lại.

API: `GET /api/testcases` đọc danh mục/kết quả gần nhất; `POST /api/testcases/run` chạy test. Một lượt khai phá và một lượt test không chạy đồng thời để tránh làm sai lệch phép đo; yêu cầu cạnh tranh trả BUSY.

## Bộ kiểm thử mã nguồn

```powershell
./run-tests.ps1
python scripts/verify-web.py --url http://localhost:8015 --real-data
# Khi cần chạy cả 6 cấu hình dữ liệu lớn:
python scripts/verify-web.py --url http://localhost:8015 --presets --real-data
```

`run-tests.ps1` chạy PiHaupaSelfTest (vét cạn/ngẫu nhiên/ranh giới), các test export/parser và LabApplicationTest. LabApplicationTest kiểm tra 20 case thực thi, phân loại lỗi, bất đồng bộ, catalog và precision. Kiểm chứng từng file lớn bằng DatasetProfileVerification được mô tả tại DATASET-PRESETS.md. Trang web liệt kê các bộ test terminal riêng, không tự hiển thị ĐẠT cho các bộ chưa được chạy trong lượt đó.

## Lỗi báo rõ và cách xử lý

Hợp đồng lỗi HTTP/job có message (`error`), `code` và `action`. UI luôn hiện mã, nguyên nhân và cách xử lý ở ngoài phần giải thích thu gọn.

- INVALID_THRESHOLDS / INVALID_NUMBER: sửa tỷ lệ ngưỡng và các số hữu hạn.
- TU_MISMATCH / INVALID_INPUT / DUPLICATE_ITEM / DUPLICATE_TID: sửa dữ liệu tại dòng được parser báo.
- DATASET_NOT_FOUND / JOB_EXPIRED: chọn lại danh mục hoặc chạy lại nếu server đã restart/báo cáo bị loại khỏi cache.
- BUSY / REPORT_NOT_READY: đợi lượt đang chạy hoàn tất.
- OUT_OF_MEMORY / DATA_IO / INTERNAL_ERROR: báo cáo mới chưa hoàn tất; thông báo chỉ rõ heap, quyền/tệp hoặc nhật ký cần kiểm tra.
- NETWORK_UNAVAILABLE / REQUEST_TIMEOUT / INVALID_RESPONSE: lỗi phía kết nối. Polling thử kết nối lại tối đa hai lần trước khi báo thất bại; không gán kết quả rỗng thay cho lỗi.

Phép đo và báo cáo cũ được giữ khi lượt mới lỗi nhưng có thông báo rõ. Cảnh báo cấu hình thay đổi so sánh với cấu hình đã submit (nguồn, Su/Sl và tệp), tách khỏi cách định dạng số của báo cáo; chọn lại đúng cấu hình không bị báo nhầm.

## UI thu gọn

Mặc định chỉ hiện nguồn, Su/Sl, nút chạy và dòng trạng thái/tổng thời gian/heap. **Tùy chọn & giải thích** chứa nguồn ngưỡng, đối chiếu Original/Tight, thời gian đọc/thuật toán, đỉnh heap và nhật ký. Lỗi và cảnh báo chưa chạy cấu hình mới luôn hiện trực tiếp.

## Biểu đồ và xuất bằng chứng
TC-P là kiểm chứng ví dụ bài báo (Tables 2–3, Table 5, Eq. 5/6/12), TC-E/A/D là kiểm thử bổ sung. Không phải tái lập toàn bộ benchmark. TC-P dùng chung một lượt miner, nên thời gian từng assertion không dùng để so sánh hiệu năng.
Trang kiểm thử đánh dấu 01–05, biểu đồ trạng thái theo nhóm, TU/MU kỳ vọng/thực tế, AU HAUP cuối, re-scan, cận Tight và thời gian case. Xuất kiểm thử ZIP gồm TESTCASES.html, testcases.csv, testcases.json; xuất giữ trạng thái hiện tại và không chạy lại.
Gói báo cáo ZIP của lượt phân tích gồm BAO_CAO.html (biểu đồ offline), BAO_CAO.txt, bao-cao.csv, patterns.csv, performance-by-rows.csv, upper-bounds.csv, measurements.csv, report.json, README.txt; có comparison.csv nếu đã bật đối chiếu. Tệp raw lớn không được nhân bản vào ZIP; JSON chứa preview tối đa 30 giao dịch. CSV và JSON giữ số đo của lượt đã chạy, không sinh số liệu minh họa.
