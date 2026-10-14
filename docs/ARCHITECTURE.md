# Kiến trúc PIHAUPA Research Lab

Java 17+ thực hiện toàn bộ tính toán. UI là HTML/CSS và JavaScript ES modules, được phục vụ từ classpath, không cần Node hay CDN khi chạy. HTTP server của JDK bind riêng 127.0.0.1.

## Phân lớp

```text
src/main/java/
  piHAUPA/                         Lõi thuật toán, mô hình, parser, CLI, export được giữ lại
  vn/edu/dlu/pihaupa/
    application/
      InputSource.java             Strategy chọn nguồn dữ liệu
      DatasetCatalog.java          Repository danh mục transactions.txt
      AnalysisService.java         Facade xử lý job, progress, đo và lưu báo cáo
      PaperVerification.java       Oracle giá trị cố định từ ví dụ bài báo
    web/
      LabServer.java               HTTP adapter/controller, static resources, export
    support/
      Json.java                    Mã hóa JSON dùng chung, không phụ thuộc lớp web
src/main/resources/static/         Giao diện hoàn toàn mới
src/test/java/piHAUPA/             Test thuật toán và dịch vụ
scripts/                           Build và kiểm thử tích hợp
```

## Design pattern thực sự sử dụng

- **Strategy:** `InputSource` có ba triển khai nguồn paper, catalog và upload; `AnalysisService` không phụ thuộc cách đọc từng nguồn.
- **Facade:** `AnalysisService` điều phối đọc → khai phá → đo → đối chiếu tùy chọn → báo cáo; `AnalysisReport.run` tiếp tục là facade của lõi miner.
- **Repository:** `DatasetCatalog` quản lý ánh xạ ID sang tệp trong root cấu hình. Client không gửi đường dẫn tùy ý để đọc tệp trên máy.
- **Adapter:** `LabServer` ánh xạ HTTP sang nguồn dữ liệu và service; UI không chứa công thức quyết định khai phá.
- `RescanPolicy` giữ nguyên là enum cấu hình trong lõi, không gọi nhầm nó là GoF Strategy.

Việc giảm re-scan đến từ điều kiện PIHAUPA, không phải lợi ích tốc độ tự động của design pattern.

## Vòng đời và giới hạn

Một worker khai phá cho mỗi server, từ chối job thứ hai khi đang có job chạy. HTTP có 4 worker riêng nên vẫn truy vấn được progress. Upload được chuyển vào tệp tạm rồi đọc bằng BufferedReader; xóa sau xử lý hoặc khi không nhận được job. Miner vẫn giữ dữ liệu trong heap, không phải thuật toán out-of-core. Mặc định script cấp heap tối đa 4 GiB.

Giữ tối đa 8 báo cáo gần nhất trong bộ nhớ server. UI giữ tối đa 8 lượt trong phiên trang, không lưu bền qua reload. Khi server loại báo cáo cũ, yêu cầu xuất trả lỗi rõ ràng; xuất TXT/CSV không chạy lại miner. Không có tài khoản, database hay hosting công khai trong phạm vi bản Java cục bộ này.

## API

- `GET /api/datasets`: danh mục từ `PIHAUPA_DATA_ROOT`.
- `GET /api/verify`: 12 phép đối chiếu mẫu bài báo ở Su/Sl = 0.23/0.10.
- `POST /api/jobs?kind=paper|catalog|upload&su=...&sl=...&compare=true|false`: tạo job; catalog nhận `dataset=ID`; upload nhận raw TXT cùng `format=transactions|quantitative` và `name`.
- `GET /api/jobs/{id}`: RUNNING, COMPLETED, FAILED và nhật ký.
- `GET /api/jobs/{id}/result`: báo cáo, số đo, preview, EU, so sánh tùy chọn.
- `GET /api/jobs/{id}/export?format=csv|txt`: xuất kết quả đã tính.

## Thiết kế UI và tài liệu tham chiếu

Nền sáng, sidebar ổn định, màu xanh lam cho thao tác chính, xanh lục cho LARGE/cập nhật PT, hổ phách cho PRE-LARGE/re-scan. Spacing 8 px, nội dung có focus bàn phím, nhãn biểu mẫu, trạng thái tải/lỗi, reduced-motion và responsive. Biểu đồ SVG không phụ thuộc mạng, có tooltip và bảng số liệu tương đương. Các thang số liệu tách theo đơn vị, có mốc 0 (hoặc mở rộng đến giá trị âm với Eq. 12).

Tham khảo [UI UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) và [quick reference](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill/blob/main/.claude/skills/ui-ux-pro-max/references/quick-reference.md). Bản này áp dụng quy tắc công khai; không tuyên bố đã chạy bộ tìm kiếm design-system cục bộ của skill khi bộ dữ liệu đó chưa được cài.

Tham khảo bố cục card, sidebar và biểu đồ của [shadcn/ui](https://ui.shadcn.com/docs/components/chart). Đây là UI phục vụ bởi Java với ES modules; không tuyên bố sử dụng package React shadcn. Không tạo bản Figma hoặc reusable template khi chưa có yêu cầu đầu ra đó. Sites hosting chạy Cloudflare Workers nên không dùng để host JVM này.

## Chính xác khoa học

Ngưỡng tự điền theo dữ liệu, kiểm chứng mới và cắt tỉa bổ sung được mô tả tại [DATASET-PRESETS.md](DATASET-PRESETS.md). Mục lục báo cáo không phải số bước thuật toán; thiết lập phân tích chỉ xuất hiện trên trang phân tích.

PDF người dùng cung cấp: *Intelligent average utility pattern analysis using pre-large concept in dynamic stream data*, Kim et al., Journal of Big Data (2026), 13:13, DOI [10.1186/s40537-025-01352-x](https://doi.org/10.1186/s40537-025-01352-x). Tables 2–3 là dữ liệu mẫu; MAU theo Eq. (1), phân loại theo Eq. (2)–(4), Original theo Eq. (5)/(13), Tight theo Eq. (6)/(12).

`transactions.txt` chứa utility đã tính. EU=1 bảo toàn utility; không nhân investment_table lần nữa. Kiểm tra TU và thứ tự item/utility trước khi chạy. Chia 80/10/10 là mô phỏng trong ứng dụng, không gán cho tác giả. PRE-LARGE là tập đang quản lý, không phải toàn bộ PRE-LARGE được khai phá lại ở mỗi batch.

Thời gian tường dùng System.nanoTime, CPU dùng ThreadMXBean trên luồng khai phá (không gồm đọc và lượt đối chiếu), heap lấy mẫu trước/sau batch. Heap không phải RSS, peak tuyệt đối hay RAM của máy. So sánh ≤1.000 giao dịch có warm-up và median 3 lượt đan xen; lớn hơn chạy 1 lượt. Heap hai chính sách dùng chung JVM nên không phải benchmark cách ly.
