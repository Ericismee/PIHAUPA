# Ngưỡng khởi đầu và chẩn đoán kết quả rỗng

Ngưỡng không có một giá trị “chuẩn” dùng chung cho mọi bộ dữ liệu. Su/Sl = 0.23/0.10 thuộc ví dụ nhỏ của bài báo. Khi áp dụng cho các tệp transactions.txt đã cung cấp, max AUUB của cả sáu bộ thấp hơn Su × TU(DB), nên không thể có HAUP ở ngưỡng đó. Đây là kết quả rỗng hợp lệ, không phải dữ liệu bị bỏ qua.

## Cấu hình tự điền

`src/main/resources/dataset-profiles.properties` lưu số dòng, TU, utility của singleton mạnh nhất, item đối chứng, kích thước tệp và hai hệ số. Catalog áp dụng cấu hình khi tên và kích thước tệp khớp bộ tham chiếu. Tệp riêng hoặc tệp thay đổi kích thước dùng ngưỡng tùy chỉnh, không tự nhận cấu hình đã kiểm chứng.

Đặt r = max_i AU({i}) / TU(DB). Su = a × r, Sl = b × r, với 0 < b < a < 1. Do singleton mạnh nhất có AU = r × TU(DB), mẫu này thỏa ngưỡng LARGE ở database cuối. Đây là cấu hình khởi đầu có mẫu đối chứng, không phải thông số benchmark của bài báo hay tối ưu được khuyến nghị cho mọi mục tiêu nghiên cứu.

- Accidents: a/b = 0.9/0.8, Su ≈ 0.0420076974, Sl ≈ 0.0373401754; 3 HAUP, 12 PRE-LARGE đang quản lý.
- Chainstore: a/b = 0.9/0.8, Su ≈ 0.0109181533, Sl ≈ 0.0097050251; 1 HAUP, 0 PRE-LARGE đang quản lý.
- NewChess: a/b = 0.9/0.8, Su ≈ 0.0389814969, Sl ≈ 0.0346502195; 8 HAUP, 56 PRE-LARGE đang quản lý.
- NewConnects: a/b = 0.99/0.98, Su ≈ 0.0358694085, Sl ≈ 0.0355070912; 1 HAUP, 0 PRE-LARGE đang quản lý. Khoảng hẹp được chọn cho lần chạy khởi đầu vì mở rộng ở ngưỡng thấp hơn có chi phí cao.
- NewMushroom: a/b = 0.9/0.8, Su ≈ 0.0627711498, Sl ≈ 0.0557965776; 1 HAUP, 1 PRE-LARGE đang quản lý. Cấu hình 0.05/0.02 vẫn cho 5 HAUP và 311 PRE-LARGE.
- Retail: a/b = 0.9/0.8, Su ≈ 0.0853445280, Sl ≈ 0.0758618027; 1 HAUP, 0 PRE-LARGE đang quản lý.

Các số đếm trên được kiểm tra với giá trị double đầy đủ trong profile và kịch bản chèn 80/10/10 của ứng dụng. Các tỷ lệ gần đúng trong tài liệu không thay thế giá trị profile khi cần tái lập chính xác.

## Kiểm chứng

`DatasetProfileVerification` chạy từng bộ trong JVM riêng, kiểm tra số dòng/TU, singleton đối chứng và cộng U(P) trực tiếp từ raw transactions bằng BigDecimal cho tối đa 10 HAUP. Trong lần kiểm tra này, mỗi bộ có không quá 8 HAUP nên toàn bộ HAUP của sáu lượt đều được đối chiếu. Kiểm tra này xác nhận utility của các mẫu trả về; tính đầy đủ tiếp tục được kiểm tra bằng oracle vét cạn trên luồng nhỏ trong PiHaupaSelfTest, không tuyên bố đã vét cạn dữ liệu lớn.

`python scripts/verify-web.py --url http://localhost:8015 --presets` chạy cả sáu cấu hình qua HTTP, kiểm tra số mẫu, số dòng/TU, trạng thái COMPLETED, thời gian đọc/khai phá, heap và thông báo “Đọc hoàn tất”.

## Cắt tỉa bổ sung trong mã triển khai

Lõi giữ nguyên AU, MAU, phân loại và điều kiện re-scan của bài báo. Thêm hai phép loại nhánh có căn cứ:

1. Khi AUUB(i) < Sl × TU(DB), không dựng entry của item i trong PIHAUP-List. MU(T) vẫn được tính trên giao dịch đầy đủ, không tính lại sau khi loại item.
2. Với prefix P và phần mở rộng S, U(P ∪ S) ≤ U(P) + Σ_{i∈S} U({i}). Ở mỗi độ dài mở rộng k, lấy k singleton utility lớn nhất trong các item còn lại làm cận trên; nếu mọi độ dài đều không thể đạt Sl × TU(DB), không mở rộng nhánh. Đây là cận tối ưu riêng của mã triển khai, không gọi nó là Eq. MAU hoặc công thức mới của bài báo. Có dung sai số học để không loại nhánh ở ranh giới do làm tròn double.

Self-test ngẫu nhiên/vét cạn, ranh giới ngưỡng/re-scan, mẫu bài báo, parser/export và test ứng dụng đều qua sau thay đổi.

## UI và tiến trình

Thiết lập chỉ hiện trên trang Phân tích PIHAUPA. Chọn lại mẫu bài báo khôi phục 0.23/0.10; chọn bộ có profile tự điền ngưỡng riêng. Hiển thị cả tỷ lệ và phần trăm để tránh nhầm 0.042 với 0.42. Đổi thiết lập chưa chạy có thông báo rõ rằng báo cáo bên dưới thuộc lần trước. Mỗi lượt mới reset bộ lọc mẫu và mặc định xem batch cuối.

Mục lục báo cáo không đánh số như các bước thuật toán. Kết quả rỗng vì ngưỡng khác với không khớp bộ lọc, có thông báo riêng và không hiện một trang phân trang giả. Trạng thái chạy gồm READING, MINING, COMPARING khi bật đối chiếu, REPORTING, COMPLETED hoặc FAILED. Thời gian tổng, đọc, thuật toán và JVM heap lấy từ Java; heap tiến trình được lấy mẫu khi truy vấn trạng thái, không phải RSS hoặc đỉnh chính xác.

JSON giữ đầy đủ precision của double thay vì làm tròn 6 chữ số. Ngưỡng báo cáo và phép kiểm tra cấu hình chưa chạy dùng cùng giá trị với miner; định dạng số trên UI không thay đổi dữ liệu tính toán.
