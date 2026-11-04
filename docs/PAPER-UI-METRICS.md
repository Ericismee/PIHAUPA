# Chỉ số trên web và căn cứ bài báo

Nguồn: Kim et al., *Intelligent average utility pattern analysis using pre-large concept in dynamic stream data*, Journal of Big Data 13:13 (2026), DOI https://doi.org/10.1186/s40537-025-01352-x. Đối chiếu PDF 34 trang và bản HTML ngày 04/11/2026.

## Runtime và median

Phần Performance evaluation, trang 21–30, trình bày runtime (s), memory usage (MB), số re-scan và kiểm tra độ chính xác tập mẫu. Bài không nêu median, warm-up hoặc số lượt đo lặp để tổng hợp runtime. Không suy ra tác giả chạy đúng một lần; chỉ có thể nói bài không công bố quy trình tổng hợp này.

Web mới chạy một lượt cho mỗi cấu hình, hiển thị runtime thực đo, không thêm warm-up. Đây là cách đo minh bạch của ứng dụng, không tuyên bố là cách tác giả tổng hợp số đo. Median là trung vị: sắp thời gian rồi lấy phần tử giữa; không dùng cho lượt web mới. API và công cụ benchmark phát triển vẫn đọc được hồ sơ nhiều lượt cũ, giữ nguyên kết quả và đánh dấu phạm vi, không đổi tên số cũ thành thời gian một lượt.

## Các phép thử

- **Tiến trình chèn — Fig. 8–9, trang 22–25:** X là insertion progress (%); Y là runtime (s) dạng đường hoặc memory usage (MB) dạng cột. Dữ liệu thực dùng OR riêng từng tập và 9 lần chèn bằng nhau. Điểm DB0 trên web là quét ban đầu; không tính là re-scan.
- **Ngưỡng trên — Fig. 10–11, Table 7, trang 25–27:** thay Sᵤ, giữ Sₗ, dữ liệu, OR và cách chia batch. Table 7 liệt kê runtime, memory, #re-scans. Web chỉ vẽ lượt đã đo.
- **Khả năng mở rộng — Fig. 12, trang 27:** dữ liệu tổng hợp T10I4DxK, 200K–1000K giao dịch, 100 item, chiều dài trung bình 10; OR 91%, Sᵤ 1%, Sₗ 0,2%, 19 lần chèn. Giảm tỷ lệ một tệp bất kỳ trên web chỉ là khảo sát kích thước, không tái lập thí nghiệm này.
- **Độ nhạy — Fig. 13–14, trang 28–29:** giảm đều Sᵤ và Sₗ, giữ khoảng cách hai ngưỡng; đo total runtime và peak memory. Web lọc cùng số giao dịch, cấu hình chèn và cùng khoảng cách Sᵤ−Sₗ.
- **Độ chính xác — phần Analysis of statistical tests, trang 28–30:** đối chiếu kết quả khai phá. Trên web, bảng ví dụ so HAUP/AU và quyết định re-scan với giá trị cố định từ Tables 2–3, Table 5; oracle vét cạn là kiểm tra bổ sung của dự án.

## Phạm vi số liệu

- Bài dùng C++ và thiết bị cố định; ứng dụng dùng Java. Heap JVM lấy mẫu không tương đương trực tiếp bộ nhớ của C++. MB = byte / 1.000.000; MiB = byte / 1.048.576. Không ghi RSS là heap hoặc coi số Java là số bài báo.
- Các tên NewChess/NewMushroom và các tệp metadata không tự chứng minh cùng bản dữ liệu trong Table 6. NewConnects/Retail là đầu vào bổ sung. Ngưỡng khảo sát theo r không phải ngưỡng thực nghiệm của tác giả.
- Original/Tight là hai chính sách trên cùng lõi Java, không phải đầy đủ PRE-HAUIMI, PIHUP*, PIHUPM*, UMPD*, PULM*. Không tạo số đo hoặc đường biểu đồ cho thuật toán chưa triển khai.
- Table 5 in ví dụ sau DB1. HAUP sau DB2 được tính lại từ Tables 2–3; không gọi là bảng DB2 in trực tiếp trong bài.
- CPU, RAM tiến trình, RAM máy và SHA-256 là chẩn đoán bổ sung, nằm trong mục thu gọn. Re-scan không gồm initial scan DB0.

## Cấu trúc giao diện

Ưu tiên: Phân tích & kết quả → Hiệu năng → Đối chiếu bài báo. Sau đó là Bộ dữ liệu, Lượt chạy đã lưu, Công thức & thuật ngữ, Kiểm thử phần mềm. Mỗi biểu đồ ghi trục X/Y và đơn vị; bảng số liệu, căn cứ và phép tính mở khi cần. Kết luận cuối nằm cuối báo cáo, không phụ thuộc bộ lọc mẫu đang xem.
