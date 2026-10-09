# Design patterns trong PIHAUPA

*Từ thao tác của người dùng đến một báo cáo có thể đối chiếu.*

PIHAUPA có ba cách sử dụng—Desktop, Web cục bộ và CLI—nhưng chỉ có **một lõi khai phá Java**. Các design pattern và UI pattern trong tài liệu này giải quyết những vấn đề của ứng dụng: nhận tương tác, giữ giao diện phản hồi, điều phối nhiều bước tính toán và cho người dùng thấy trạng thái chờ. **Chúng không thay thế công thức hoặc điều kiện re-scan của bài báo.**

[README chính](README.md) · [Mã nguồn Java](src/piHAUPA) · [Giao diện Web](web) · [Dữ liệu phép đo](design-pattern-measurements.csv)

## 1. Bức tranh tổng thể

```text
Desktop / Web / CLI
       │
       ▼
InputParser hoặc ArchiveDatasetReader
       │  InputData: ngưỡng + các batch + external utility
       ▼
AnalysisReport.run(...)
       │  tạo một PiHaupaMiner cho một lượt chạy
       │  xử lý lần lượt DB0, DB1, DB2...
       ▼
AnalysisReport: mẫu, quyết định re-scan, thời gian, heap
       ├──► Desktop / Web: trình bày kết quả
       └──► ExportBundle / ReportWriter: TXT và CSV
```

Mỗi lượt phân tích có miner riêng để không chia sẻ trạng thái thay đổi giữa các lượt. Ngược lại, **các batch trong cùng lượt phải đi qua cùng miner** để giữ trạng thái tăng dần. Đây là quyết định kiến trúc của chương trình, không phải một GoF pattern độc lập.

| Cơ chế | Vấn đề được giải quyết | Nơi áp dụng | Phân loại chính xác |
|---|---|---|---|
| Observer / listener | Nối thao tác trên cửa sổ với hành động tương ứng | `DesktopApp`, `ModernPathDialog` | Cơ chế sự kiện do Swing cung cấp |
| Template Method qua `SwingWorker` | Tính toán/xuất tệp lâu nhưng cửa sổ vẫn phản hồi | `DesktopApp` | Vòng đời công việc do Swing cung cấp |
| Facade | Tránh lặp lại quy trình phân tích hoặc xuất ở từng giao diện | `AnalysisReport`, `ExportBundle` | Vai trò kiến trúc của mã dự án |
| Static factory | Tạo border nhất quán khi dựng giao diện | `BorderFactory` trong Desktop | API Swing; không phải Factory Method tự triển khai |
| Skeleton Screen | Tránh màn hình trắng trước và trong khi phân tích | Web | UI pattern; không thuộc GoF |

## 2. Observer: giao diện phản ứng với người dùng

**Vấn đề.** Cửa sổ có nhiều hành động—chọn dữ liệu, nạp ví dụ, chạy, xuất báo cáo. Nếu logic của tất cả nút bị trộn vào một luồng xử lý, việc bảo trì và kiểm tra từng hành động sẽ khó hơn.

**Cách áp dụng.** Trong [DesktopApp.java](src/piHAUPA/DesktopApp.java), mỗi `JButton` đăng ký một `ActionListener` đúng với nhiệm vụ của nó: `runButton` gọi `runAnalysis()`, `exportButton` gọi `exportLastRun()`, các nút chọn tệp/thư mục gọi hộp chọn tương ứng. [ModernPathDialog.java](src/piHAUPA/ModernPathDialog.java) dùng listener cho điều hướng, chấp nhận và hủy lựa chọn.

**Giá trị.** Người đọc mã có thể đi từ nút → listener → handler, biết rõ tương tác nào khởi động chức năng nào. Listener chỉ nhận sự kiện trên **Event Dispatch Thread (EDT)**; nó không tự khai phá dữ liệu. Công việc nặng được chuyển tiếp sang `SwingWorker`.

**Giới hạn.** Đây là Observer theo mô hình sự kiện **có sẵn của Swing**, không phải hệ thống publish/subscribe tự viết. Web dùng sự kiện DOM trong [app.js](web/app.js), không dùng đối tượng `ActionListener` của Swing.

## 3. Template Method: công việc nền có vòng đời rõ ràng

**Vấn đề.** Đọc tệp lớn, chạy PIHAUPA hoặc ghi báo cáo trên EDT sẽ khiến cửa sổ ngừng phản hồi cho đến khi hoàn tất.

**Cách áp dụng.** `runAnalysis()` và `exportLastRun()` trong [DesktopApp.java](src/piHAUPA/DesktopApp.java) tạo `SwingWorker`:

```text
execute()
   ├─ doInBackground()  → đọc / khai phá / xuất ở luồng nền
   ├─ publish/process   → cập nhật thông báo tiến độ
   └─ done()            → nhận kết quả và cập nhật cửa sổ trên EDT
```

Swing quyết định thứ tự vòng đời; ứng dụng cung cấp nội dung cho các phương thức mở rộng. Trước khi khởi động worker, chương trình chụp giá trị đầu vào và đường dẫn đầu ra trên EDT. Worker dùng bản chụp đó, tránh đọc trực tiếp ô nhập Swing từ luồng nền hoặc bị thay đổi dữ liệu giữa chừng. Khi chỉ **xuất lại**, worker dùng `AnalysisReport` đã có thay vì mining thêm một lần.

**Giá trị và giới hạn.** Cửa sổ phản hồi tốt hơn và việc cập nhật UI đúng luồng. `SwingWorker` không làm công thức PIHAUPA tính nhanh hơn; đây là Template Method của **thư viện Swing**, không phải lớp Template Method do dự án tự định nghĩa.

## 4. Facade: một cửa vào cho một quy trình nhiều bước

**Vấn đề.** Desktop, Web và CLI đều cần cùng trình tự: tạo miner, xử lý batch, đo đạc và đóng gói kết quả. Nếu mỗi giao diện tự thực hiện, quy trình dễ lệch nhau.

| Cửa vào | Trình tự được che bên trong | Người gọi nhận được |
|---|---|---|
| [`AnalysisReport.run(input, policy, progress)`](src/piHAUPA/AnalysisReport.java) | Tạo `PiHaupaMiner` → xử lý batch theo thứ tự → ghi tiến độ → lấy số đo và mẫu | Một `AnalysisReport` nhất quán cho Desktop, Web, CLI |
| [`ExportBundle.writeDirectory(...)`](src/piHAUPA/ExportBundle.java) | Dùng báo cáo đã tính → tạo TXT, CSV, input chuẩn hóa và các bảng chi tiết | Bộ tệp xuất trong một thư mục |

**Giá trị.** Mỗi giao diện gọi một điểm vào ngắn gọn và không cần biết chi tiết phối hợp của miner hoặc từng writer. Desktop/CLI dùng `writeDirectory` cho cả bộ tệp; Web gọi `reportText`/`reportCsv` trên báo cáo đã lưu tạm để tải từng định dạng. Xuất Web hiện tại **không chạy lại thuật toán**.

**Giới hạn.** Đây là *vai trò Facade* do các phương thức tĩnh đảm nhận, không phải một đối tượng Facade có trạng thái riêng. `PiHaupaMiner` vẫn chịu trách nhiệm về AU, MAU, PRE-LARGE và Tight Re-Scan Condition.

## 5. Static factory: tạo thành phần giao diện nhất quán

Trong [DesktopApp.java](src/piHAUPA/DesktopApp.java) và [ModernPathDialog.java](src/piHAUPA/ModernPathDialog.java), `BorderFactory.create...` tạo các đường viền Swing; `createCompoundBorder` kết hợp viền ngoài và khoảng đệm trong. Việc này giúp mã dựng giao diện ngắn và nhất quán hơn so với cấu hình border lặp lại ở nhiều vị trí.

**Phân loại:** dự án **sử dụng static factory của Swing**, không tự triển khai GoF Factory Method. Việc ghép border cũng không đủ để khẳng định dự án tự triển khai Decorator. Cơ chế này thuộc trình bày cửa sổ, không tác động đến thời gian mining.

## 6. Skeleton Screen: cho thấy cấu trúc trước khi có số liệu

**Vấn đề.** Báo cáo có nhiều tab; một vùng trắng hoặc dòng “đang tải” không cho người dùng hình dung nơi sẽ xuất hiện kết luận, bảng mẫu và các phép đo.

**Cách áp dụng.** [index.html](web/index.html) đặt `#emptyState` cho các tab báo cáo thường và `#largePlaceholder` cho Dữ liệu lớn. [app.js](web/app.js) chọn khung phù hợp với tab và đổi `data-loading` khi bắt đầu chạy. [styles.css](web/styles.css) vẽ các khối giữ chỗ và chỉ bật hiệu ứng nhẹ trong lúc thực sự phân tích; `prefers-reduced-motion` tắt hiệu ứng khi người dùng yêu cầu.

**Ba trạng thái phải phân biệt:**

1. **Chưa chạy:** skeleton là bản xem trước bố cục, không phải tín hiệu hệ thống đang tính.
2. **Đang chạy:** skeleton kèm nhãn tiến độ; chưa có số liệu thật để kết luận.
3. **Hoàn tất hoặc lỗi:** kết quả thật thay khung; nếu lỗi, hiển thị lỗi và không dựng số liệu giả.

Skeleton giúp định hướng, không giảm thời gian đọc TXT, thời gian PIHAUPA hoặc bộ nhớ. Đây là **UI pattern**, không phải GoF design pattern.

## 7. Ranh giới thuật ngữ khi thuyết trình

- [`RescanPolicy`](src/piHAUPA/RescanPolicy.java) hiện là `enum` TIGHT/ORIGINAL; [PiHaupaMiner.java](src/piHAUPA/PiHaupaMiner.java) rẽ nhánh theo enum. Chưa có interface và các chính sách đa hình, nên **chưa phải GoF Strategy**. Giao diện thông thường chạy Tight; Original còn phục vụ kiểm thử/đối chiếu.
- PIHAUP-List, pattern tree, PatternExpansion, AUUB/MAU pruning, PRE-LARGE và Eq. (12) là **thuật toán hoặc cấu trúc dữ liệu của bài báo**, không phải GoF pattern.
- `record` như `InputData`/`AnalysisReport` là cách mang dữ liệu giữa các lớp. Trường `final` không làm `Map`/`List` bên trong bất biến sâu và không tự biến record thành một GoF pattern.
- Số re-scan giảm là hệ quả của điều kiện Tight, **không phải** lợi ích tự động của Observer, SwingWorker hay Facade.

## Phụ lục: phép đo và giới hạn kết luận

Phép đo dưới đây được lưu từ một đợt kiểm tra trước, **không phải phép đo hiệu năng riêng của từng design pattern**. [PatternBenchmark.java](test/piHAUPA/PatternBenchmark.java) chạy ba lượt warm-up, bảy lượt đo, rồi lấy trung vị thời gian `ComparisonReport.run` và byte cấp phát của thread hiện tại. Parsing nằm ngoài vùng đo; `ComparisonReport` còn có warm-up/lặp nội bộ với dữ liệu không quá 1.000 giao dịch. Hai trung vị có thể đến từ hai lượt khác nhau.

| Dữ liệu | Giao dịch | Trước (ms) | Sau (ms) | Byte cấp phát trước / sau | HAUP Original/Tight |
|---|---:|---:|---:|---:|---|
| [paper-scaled-9000.txt](examples/paper-scaled-9000.txt) | 9.000 | 48.695 | 74.878 | 51.500.240 / 51.500.240 | Trùng khớp |
| [synthetic-1000.txt](examples/synthetic-1000.txt) | 1.000 | 107.486 | 109.762 | 105.182.264 / 105.182.264 | Trùng khớp |

| Dữ liệu | Re-scan Original / Tight | Node kết hợp Original / Tight |
|---|---:|---:|
| paper-scaled-9000 | 2 / 1 | 37 / 23 |
| synthetic-1000 | 4 / 4 | 3.488 / 3.488 |

Trên bộ 9.000 giao dịch, Tight có một re-scan thay vì hai và 23 node kết hợp thay vì 37. Đó là **so sánh hai điều kiện thuật toán trên bộ dữ liệu này**, không chứng minh các design pattern làm chương trình nhanh hơn. Thời gian “sau” trong phép đo trên còn cao hơn “trước”; không nên tuyên bố tăng tốc từ bảng này. Byte cấp phát không phải peak heap hoặc RSS, và harness không đo render, tương tác nút, duyệt thư mục hay I/O xuất tệp.

Để lặp lại phép đo từ thư mục dự án, dùng JDK hỗ trợ `com.sun.management.ThreadMXBean`:

```powershell
javac -encoding UTF-8 -d out src\piHAUPA\*.java test\piHAUPA\*.java
java -cp out piHAUPA.PatternBenchmark examples\paper-scaled-9000.txt
java -cp out piHAUPA.PatternBenchmark examples\synthetic-1000.txt
```

Các phép kiểm thử trong dự án gồm `PiHaupaSelfTest` và `ExportBundleTest`; bảng benchmark lưu tại [design-pattern-measurements.csv](design-pattern-measurements.csv). Phần đo này không thay cho kiểm thử thao tác giao diện thủ công.
