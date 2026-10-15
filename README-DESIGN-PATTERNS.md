# Design pattern trong PIHAUPA Research Lab

> **Cách đọc:** mỗi mẫu được trình bày theo cùng thứ tự: hiểu đơn giản → lý thuyết → dùng ở đâu → mục đích/tác dụng → thời gian và giới hạn.
>
> Nhấn liên kết **Code áp dụng** để đến đúng dòng source trên GitHub. Ngay trong code có chú thích tiếng Việt bắt đầu bằng `DESIGN PATTERN` hoặc `UI DESIGN PATTERN` để tìm nhanh.

[Hướng dẫn chạy và công thức](README.md)

## 1. Dự án sử dụng những mẫu nào?

Có **5 mẫu/vai trò thiết kế được mô tả**, thuộc các phạm vi khác nhau:

1. **Strategy:** đổi cách lấy dữ liệu mà giữ nguyên cách gọi.
2. **Facade:** một điểm vào điều phối quy trình nhiều bước.
3. **Adapter:** nối HTTP/định dạng bên ngoài với model Java.
4. **Repository:** truy cập dataset qua danh mục và ID.
5. **Skeleton Screen:** khung giữ chỗ khi giao diện đang chờ dữ liệu.

**Ba mẫu trọng tâm ở phần xử lý là Strategy, Facade và Adapter.** Repository bổ sung ở tầng dữ liệu; Skeleton Screen bổ sung ở UI.

Strategy và Facade thể hiện các mẫu GoF; Adapter trong dự án chủ yếu là vai trò kiến trúc tại ranh giới, không phải mọi lớp đều là GoF Object Adapter đầy đủ. Repository là pattern kiến trúc. Skeleton Screen là **UI/UX design pattern**, không thuộc 23 mẫu GoF nhưng vẫn là design pattern.

## 2. Nhìn nhanh luồng hoạt động

```text
Web / CLI
   |
   +-- HTTP hoặc file -> Adapter chuyển sang model Java
   |
   +-- Strategy chọn nguồn: mẫu bài báo / catalog / upload
   |                       |
   |                       +-- Repository tìm dataset theo ID
   v
Facade điều phối đọc -> khai phá -> đo -> đối chiếu -> báo cáo
   |
   v
PiHaupaMiner xử lý DB0 -> DB1 -> DB2
   |
   +-- Report -> JSON / biểu đồ / TXT / CSV / ZIP

Trong lúc chờ: UI hiển thị Skeleton Screen khi chưa có nội dung.
```

**Ranh giới quan trọng:** pattern tổ chức chương trình; AU, MAU, pre-large và Tight re-scan mới là nội dung thuật toán từ bài báo. Không khẳng định bài báo gốc sử dụng các GoF pattern này.

## 3. Strategy — chọn cách lấy dữ liệu

### Hiểu đơn giản

“Cùng yêu cầu đọc dữ liệu, nhưng có thể đổi người thực hiện mà bên gọi không phải đổi quy trình.”

### Lý thuyết cơ bản

Strategy là mẫu hành vi, đóng gói các cách thực hiện một nhiệm vụ sau cùng một interface. Client chọn implementation; Context chỉ gọi interface.

### Code áp dụng

- [InputSource — contract Strategy](src/main/java/vn/edu/dlu/pihaupa/application/InputSource.java#L17)
- [AnalysisService.submit — Context và Facade](src/main/java/vn/edu/dlu/pihaupa/application/AnalysisService.java#L23)

Các vai trò trong dự án:

- **Strategy:** interface `InputSource`, có `read()`, `name()`, `cleanup()`.
- **Implementation:** anonymous class do `paper()`, `catalog()` và `upload()` tạo.
- **Context:** `AnalysisService` gọi `source.read(...)` và cleanup trong `finally`.
- **Client:** `LabServer` chọn nguồn từ request người dùng.

```java
// Bên gọi chọn nguồn; service dùng chung quy trình phân tích.
InputSource source = InputSource.paper();
service.submit(source, 0.23, 0.10, false);
```

### Mục đích và tác dụng trong đề tài

Ứng dụng cần chạy mẫu bài báo để kiểm chứng và dataset lớn để thực nghiệm. Strategy giúp thay nguồn mà không sửa công thức hoặc viết lại miner.

- Thêm nguồn mới ở một implementation thay vì rải điều kiện vào service.
- Kiểm tra lỗi đọc/cleanup của từng nguồn độc lập.
- Tách cách lấy dữ liệu khỏi cách xử lý dữ liệu.

### Thời gian và phần được tối ưu

**Tối ưu chính:** khả năng mở rộng, coupling và phạm vi sửa mã.

Gọi strategy không tự giảm thời gian đọc, số re-scan hoặc lượng mẫu. Chưa đo overhead interface riêng nên không gán số ms/tỷ lệ tăng tốc. Reader đọc theo dòng là kỹ thuật I/O, không phải lợi ích mặc định của Strategy; miner vẫn cần bộ nhớ cho giao dịch/cấu trúc khai phá.

> **Ghi nhớ:** Strategy giúp đổi nguồn thuận tiện, không làm PIHAUPA tự chạy nhanh hơn. Các phương thức tạo nguồn là static factory, không phải GoF Factory Method qua kế thừa.

## 4. Facade — điều phối một quy trình nhiều bước

### Hiểu đơn giản

“Bên ngoài gọi một chức năng; bên trong phối hợp nhiều thành phần.”

### Lý thuyết cơ bản

Facade là mẫu cấu trúc cung cấp điểm vào đơn giản cho subsystem phức tạp. Client không phải tự biết thứ tự gọi reader, miner, bộ đo và writer. Facade không thay thế trách nhiệm nghiệp vụ của những thành phần này.

### Code áp dụng

- [AnalysisService.submit — Context và Facade](src/main/java/vn/edu/dlu/pihaupa/application/AnalysisService.java#L23)
- [AnalysisReport.run — điều phối các batch](src/main/java/piHAUPA/AnalysisReport.java#L49)
- [ReportArchive.analysis — xuất từ kết quả đã có](src/main/java/vn/edu/dlu/pihaupa/application/ReportArchive.java#L19)

```java
AnalysisReport report = AnalysisReport.run(input, RescanPolicy.TIGHT);
ExportBundle.writeDirectory(input, report, outputDirectory, sourceName);
```

- `AnalysisService.submit()` quản lý job, đọc, mining, đo, đối chiếu tùy chọn và cleanup.
- `AnalysisReport.run()` tạo miner và cho các batch đi qua theo thứ tự.
- `ExportBundle`/`ReportArchive` phối hợp các writer thành bộ báo cáo.

### Mục đích và tác dụng trong đề tài

Web và CLI phải dùng cùng cách khai phá/đo kết quả. Facade giúp tránh mỗi giao diện tự điều phối một phiên bản khác nhau.

- Giảm mã lặp tại bên gọi.
- Tập trung thứ tự xử lý để dễ kiểm tra.
- Một miner cho cả lượt giữ đúng trạng thái tăng dần OD/ID; lượt độc lập dùng miner mới.
- Xuất dùng report đã tính, không mining lại chỉ để tạo file.

### Thời gian và phần được tối ưu

**Tối ưu chính:** nhất quán quy trình và tránh công việc thừa khi tái sử dụng report.

```text
Phân tích: đọc + mining + tổng hợp + đối chiếu nếu bật
Xuất lại: tạo nội dung + nén/ghi + truyền file
          không cộng thêm một lượt mining
```

Tránh mining lại là lợi ích cụ thể của implementation hiện tại, không phải mọi Facade đều tăng tốc. Chưa có benchmark A/B riêng để công bố tỷ lệ tiết kiệm.

Giữ report hỗ trợ xuất lại nhưng chiếm heap. Cache tối đa 8 job và nội dung ZIP vẫn sử dụng bộ nhớ. Single worker là chính sách chạy tuần tự, không phải GoF Singleton.

> **Ghi nhớ:** Facade làm cách gọi đơn giản và nhất quán; công thức/tốc độ khai phá vẫn thuộc miner.

## 5. Adapter — chuyển giao tiếp và biểu diễn dữ liệu

### Hiểu đơn giản

“Mỗi bên nói một định dạng; adapter chuyển để cùng dùng được một lõi xử lý.”

### Lý thuyết cơ bản

Adapter là mẫu cấu trúc chuyển giao diện/biểu diễn có sẵn sang dạng bên sử dụng cần. GoF Object Adapter thường có Target interface và một đối tượng được bọc. Dự án này chủ yếu áp dụng vai trò adapter tại ranh giới HTTP và dữ liệu, không gán mọi parser/writer là Object Adapter thuần túy.

### Code áp dụng

- [LabServer — HTTP Adapter](src/main/java/vn/edu/dlu/pihaupa/web/LabServer.java#L22)
- [ArchiveDatasetReader.readTransactionsText — Input Adapter](src/main/java/piHAUPA/ArchiveDatasetReader.java#L36)
- [ReportArchive.analysis — xuất từ kết quả đã có](src/main/java/vn/edu/dlu/pihaupa/application/ReportArchive.java#L19)

```text
HTTP query/body -> LabServer -> InputSource / AnalysisService
items:TU:itemUtilities -> reader -> InputData -> miner
AnalysisReport -> writer -> JSON / CSV / TXT / HTML / ZIP
```

### Mục đích và tác dụng trong đề tài

Miner nhận model Java; nó không nên biết URL, DOM hoặc cú pháp file. Adapter giữ phần chuyển đổi bên ngoài lõi.

- Dùng chung miner cho Web và CLI.
- Tập trung validation TU, item/utility và dữ liệu sai ở reader.
- Đổi giao thức/định dạng không buộc viết lại AU/MAU/re-scan.
- Giữ utility đúng khi chuyển biểu diễn.

Ví dụ `1 2 3:17:4 5 8`: reader kiểm tra `4+5+8=17`, rồi giữ utility đã tính qua biểu diễn `EU=1`. **Không nhân investment metadata lần nữa.**

### Thời gian và phần được tối ưu

**Tối ưu chính:** khả năng tích hợp, tính nhất quán và phạm vi thay đổi.

Parsing, validation, serialization và nén ZIP đều có chi phí. Adapter không tự giảm độ phức tạp mining. Validation có thể thêm thời gian nhưng ngăn xử lý dữ liệu sai; chưa có phép đo riêng để công bố mức tăng tốc do pattern.

> **Ghi nhớ:** Adapter giúp bên ngoài và lõi Java phối hợp đúng, không phải mẹo tăng tốc thuật toán.

## 6. Repository — danh mục dataset theo ID

### Hiểu đơn giản

“Muốn đọc dataset thì hỏi danh mục bằng ID, không tự đi tìm đường dẫn ở mọi nơi.”

### Lý thuyết cơ bản

Repository là pattern kiến trúc đóng gói truy cập dữ liệu sau các thao tác mức cao. Bên sử dụng không cần biết cách dữ liệu được tìm/mở/lưu bên dưới.

### Code áp dụng

- [DatasetCatalog — Repository](src/main/java/vn/edu/dlu/pihaupa/application/DatasetCatalog.java#L17)

`DatasetCatalog` cung cấp `list()`, `name(id)`, `read(id,...)`. Nó duyệt thư mục cấu hình, chỉ đăng ký real path nằm dưới root và trả ID catalog cho UI.

### Mục đích, tác dụng và thời gian

- Tập trung việc khám phá/mở `transactions.txt`.
- Tránh cho client gửi đường dẫn filesystem tùy ý.
- Phối hợp với Strategy catalog để cung cấp cùng `InputData`.

**Tối ưu chính:** tổ chức truy cập dữ liệu; không có bằng chứng pattern làm mining nhanh hơn. Danh mục được lập lúc server khởi động; ID không phải định danh bền và cần khởi động lại để khám phá file mới. Đây là filesystem repository, không phải ORM/database transaction.

## 7. Skeleton Screen — khung nội dung khi đang chờ

### Hiểu đơn giản

“Chưa có kết quả thì hiện khung giữ chỗ, có dữ liệu thật mới thay nội dung vào.”

### Lý thuyết cơ bản

Skeleton Screen là **UI/UX design pattern** dùng placeholder mô phỏng vùng nội dung sắp xuất hiện. Nó không thuộc GoF nhưng vẫn là design pattern ở phạm vi giao diện.

### Code áp dụng

- [app.js — skeleton cho lượt phân tích đầu](src/main/resources/static/app.js#L168)
- [app.js — skeleton kiểm chứng](src/main/resources/static/app.js#L342)
- [app.js — skeleton danh mục test](src/main/resources/static/app.js#L356)
- [styles.css — khối placeholder](src/main/resources/static/styles.css#L5)

Đoạn tạo bốn khối chờ khi phân tích lần đầu:

```javascript
$('content').innerHTML = '<div class="kpi-grid">'
  + Array(4).fill('<div class="loading-block"></div>').join('')
  + '</div>';
```

```text
Bắt đầu request -> Skeleton -> Kết quả thật
                           -> Hoặc thông báo lỗi nếu thất bại
```

### Mục đích và tác dụng trong đề tài

Dữ liệu lớn có khoảng chờ đọc/khai phá. Skeleton cho phản hồi trực quan thay vì vùng nội dung trống.

- Cho biết khu vực đang chờ tải.
- Giữ chỗ một phần bố cục; không đảm bảo loại bỏ mọi layout shift.
- Không gán sẵn số đo hay trạng thái ĐẠT.
- Nếu đã có report, lượt mới giữ report cũ và nêu rõ đó là lượt trước; không che toàn bộ bằng skeleton.

Khối `.loading-block` hiện tại là placeholder đơn giản, không mô phỏng chi tiết mọi bảng. Spinner trên nút chạy là thành phần bổ sung, không phải chính Skeleton Screen.

### Thời gian và phần được tối ưu

**Tối ưu chính:** trải nghiệm và cách nhận biết thời gian chờ.

Skeleton không giảm thời gian Java, CPU/heap miner hoặc số re-scan. Placeholder/animation còn có chi phí DOM/CSS/render; chưa đo riêng và chưa có khảo sát người dùng để công bố tỷ lệ cải thiện trải nghiệm. Skeleton không phải thanh phần trăm tiến độ.

> **Ghi nhớ:** giao diện phản hồi rõ hơn, thuật toán không vì vậy mà tính nhanh hơn.

## 8. Phần nào thực sự giảm công việc khai phá?

Đó là **pre-large, Tight re-scan và pruning** trong thuật toán, không phải bản thân các pattern trên.

```text
Tight Eq. (12):
MU(ID) − Su × TU(ID) ≥ (Su − Sl) × TU(OD)

Original Eq. (13):
TU(ID) × (1 − Su) ≥ (Su − Sl) × TU(OD)
```

Khi không cần re-scan, chương trình vẫn đọc batch mới và cập nhật PT, nhưng tránh dựng lại toàn bộ cấu trúc khai phá ở lượt đó. AUUB/MAU giúp cắt nhánh không cần xét.

Ví dụ bài báo: HAUP cuối `{E}=84`, `{B}=75`, `{B,E}=63.5`; Tight re-scan **1 lần**, Original **2 lần**, không tính initial scan DB0. Giảm số re-scan không đồng nghĩa thời gian nhanh gấp hai.

Các tối ưu implementation bổ sung phải mô tả riêng, không gắn nhãn là MAU mới của bài báo hoặc GoF pattern.

## 9. Đọc số đo và kiểm chứng

- **Import (ms):** đọc và validation.
- **Thuật toán (ms):** wall time phần phân tích; không gồm đọc file/UI.
- **CPU luồng (ms):** CPU của luồng xử lý nếu JVM hỗ trợ, không phải % CPU toàn máy.
- **Heap (MiB):** bộ nhớ JVM quan sát, không phải RSS/peak tuyệt đối.
- **Re-scan, mẫu duyệt, nút kết hợp:** lượng công việc của miner.

Chưa có benchmark A/B cô lập từng design pattern, nên không đưa tỷ lệ giảm thời gian/RAM thiếu căn cứ. Muốn đánh giá phải giữ nguyên dataset, ngưỡng, kết quả, thuật toán, máy/JDK/heap và chạy nhiều lượt có warm-up phù hợp. Thời gian assertion testcase không phải benchmark miner.

```powershell
.\run-tests.ps1
```

`PiHaupaSelfTest` bảo vệ tính đúng; test reader bảo vệ chuyển đổi dữ liệu; `ExportBundleTest` và `LabApplicationTest` bảo vệ pipeline, precision và bộ xuất. 20 case trên Web chỉ xác nhận phạm vi đã kiểm tra, không chứng minh mọi đầu vào hoặc tái lập toàn bộ benchmark bài báo.

## 10. Tìm nhanh trong source

Tìm các chú thích tiếng Việt đã đánh dấu:

```powershell
rg -n "DESIGN PATTERN|UI DESIGN PATTERN" src/main
```

Các comment mô tả **pattern, trách nhiệm, lợi ích và giới hạn** ngay tại code áp dụng. Tham chiếu `#L...` khớp phiên bản hiện tại; khi thêm/xóa dòng source cần cập nhật lại số dòng trong tài liệu.


## 11. Cập nhật luồng thực nghiệm

Frontend tách thành `api.js`, `ui-components.js`, `report-ui.js`, `notifications.js` và `charts.js`; `app.js` điều phối các view. Skeleton vẫn nằm trong handler/view tải dữ liệu của app; CSS giữ vai trò placeholder.

`AnalysisService` bổ sung hủy hợp tác, đo lặp và `RunStore` lưu hồ sơ trên máy. `ExperimentProfile` ghi SHA-256 và môi trường. `SmallDataOracle` kiểm tra độc lập dữ liệu nhỏ. `IsolatedBenchmark` chạy mỗi chính sách trong JVM con riêng, tuần tự. Các lớp này hỗ trợ tái lập phép đo và tính đúng; không làm thay đổi công thức PIHAUPA hoặc tự chứng minh tăng tốc.

Kết quả chính dùng median một số lẻ lượt (1/3/5); 3/5 có warm-up. So sánh heap đã tách JVM theo chính sách nhưng vẫn dùng heap quan sát, không phải RSS. Hồ sơ hoàn tất lưu ở `results/runs`, có thể mở lại khi cache/job ID đã hết hạn. Hủy trước công bố không lưu kết quả dở dang.


## Tối ưu lõi khai phá: bước chuẩn bị trung gian

Đây là **kỹ thuật tối ưu triển khai**, không bổ sung tên GoF vào số lượng design pattern. Strategy/Facade giúp thay nguồn và điều phối; tốc độ ở đây đến từ giảm cấp phát và tránh sắp xếp lặp.

1. **Chuẩn bị thứ tự utility dùng chung:** `ExtensionPlan` sắp xếp utility singleton một lần cho một danh sách anh em. Khi xét một nhánh, chỉ lấy các vị trí ở phía sau. Trước đây mỗi nhánh tạo mảng và sort riêng. Chi phí chuẩn bị từ nhiều lần sort suffix giảm thành một lần O(n log n), sau đó mỗi lần kiểm tra O(n). Đây là cận tối ưu bổ sung của implementation, không đổi công thức MAU của bài báo.
2. **Mảng trạng thái MRU/MRN:** `restructure` dùng `double[]`/`int[]` theo chỉ số giao dịch, thay đối tượng `TempRemaining` riêng từng giao dịch. Giảm số object và áp lực GC; vẫn O(số phần tử PIHAUP-List), vẫn cần bộ nhớ O(số giao dịch).
3. **Duyệt cây với đường đi dùng lại:** `patternExpansion` thêm item trước khi đệ quy, bỏ item khi quay lui. Chỉ mẫu được lưu mới có bản sao bất biến. Tránh sao chép prefix cho mọi mẫu đã duyệt.
4. **Bỏ phép ghép chắc chắn rỗng:** `combine` kiểm tra khoảng chỉ số giao dịch trước merge. Nếu hai khoảng không giao nhau, trả list rỗng ngay. Nếu giao nhau vẫn merge chính xác; không dùng khoảng thay phép kiểm tra support.

Code: [chuẩn bị và mở rộng](src/main/java/piHAUPA/PiHaupaMiner.java), [benchmark tái chạy](scripts/OptimizationBench.java).

### Bằng chứng đo ngày 15/10/2026

JDK 25, heap tối đa 2 GiB; mỗi phiên JVM riêng, 2 warm-up và 5 lượt đo, lấy median. Không tính đọc dữ liệu trong runtime. Thứ tự baseline trước, optimized sau; có nhiễu JIT/OS, không phải benchmark của tác giả.

- Tổng hợp seed 71352, 3.000 giao dịch/16 item, Su=3%, Sl=1,5%: 179,53 → 162,26 ms (giảm 9,6%).
- Cùng dữ liệu, Su=4%, Sl=2,5%: 142,64 → 129,02 ms (giảm 9,5%).
- NewChess, Su=4,1%, Sl=3,5%: 289,87 → 273,82 ms (giảm 5,5%).
- SHA-256 của toàn bộ mẫu, utility và phân loại theo từng batch khớp baseline trong cả ba trường hợp. Số mẫu duyệt/phép ghép giữ nguyên. Bộ kiểm thử Java gồm ví dụ bài báo và đối chiếu oracle ngẫu nhiên đã qua.

Chạy benchmark sau build: `javac -encoding UTF-8 -cp target/classes -d target/bench scripts/OptimizationBench.java`, rồi `java -Xmx2g -cp "target/classes;target/bench" OptimizationBench .04 .025`. Tham số thứ ba tùy chọn là đường dẫn transactions.txt. Để so trước/sau, chạy cùng harness trên classpath của từng revision trong JVM riêng.

Không tự tăng ngưỡng, cắt số kết quả, lấy mẫu dữ liệu hoặc thay điều kiện re-scan để làm runtime trông tốt hơn. Song song hóa, cache kết quả và thay cấu trúc list cần phép đo riêng: chúng có thể tăng RAM, trả kết quả cũ hoặc thay đổi thứ tự cộng số thực. Không áp dụng chỉ vì mang tên một pattern.


### Ghép list thích nghi và thử nghiệm bị loại

`combine` trong [PiHaupaMiner.java](src/main/java/piHAUPA/PiHaupaMiner.java) vẫn merge hai con trỏ khi kích thước list cân bằng. Khi phần còn lại của một list dài ít nhất 8 lần list kia, `seekPosition` tìm nhị phân vị trí đầu tiên ≥ TID-position đang cần. Chỉ bỏ các vị trí nhỏ hơn mục tiêu; giữ nguyên thứ tự giao dịch khớp và thứ tự cộng utility. Đây là kỹ thuật thuật toán, không tăng số mẫu GoF của dự án. Ngưỡng tỷ lệ 8 là heuristic triển khai, không phải tham số bài báo.

- Mục đích: tránh quét từng phần tử của list dài khi list còn lại rất thưa. Trong trường hợp lệch mạnh, chi phí tìm kiếm tiến gần O(m log n), với m là list ngắn. Không hứa tăng tốc list cân bằng.
- JFR profile trên dữ liệu tổng hợp chỉ ra `combine` chiếm phần lớn mẫu CPU; đây là bằng chứng trên workload thử, không đại diện mọi dataset.
- Benchmark seed 71352, 3.000 giao dịch, 16 item; 3 item xuất hiện với xác suất 80%, các item còn lại 3%; Su=0,2%, Sl=0,1%; heap 2 GiB; 10 warm-up + 9 lượt: median 9,84 → 7,60 ms, giảm 22,7%. Cùng SHA-256 mẫu/utility/phân loại, số mẫu duyệt 575 và ghép 565.
- Dữ liệu cân bằng, Su=3%, Sl=1,5%: 164,21 → 161,70 ms; chênh lệch nhỏ trong vùng dao động, chưa kết luận có tăng tốc.
- Kiểm thử thêm 20 seed có support lệch, cả hai chính sách re-scan, đối chiếu oracle exhaustive HAUP cuối; bộ kiểm thử Java còn lại vẫn chạy.
- Đã thử lưu toàn bộ PIHAUP-List bằng mảng và gộp tính MAU vào join. Kết quả khớp nhưng runtime không cải thiện đồng đều, nên **không giữ các thử nghiệm này trong lõi chạy**.

Benchmark bật dữ liệu lệch bằng JVM option `-Dbench.skew=true`; đổi warm-up/lượt đo với `-Dbench.warmups=10 -Dbench.repetitions=9`. PowerShell cần đặt từng JVM option có dấu chấm trong dấu nháy. JFR có thể bật bằng `-XX:StartFlightRecording=filename=target/mining.jfr,settings=profile,dumponexit=true`; phép đo có profiler phải tách khỏi benchmark runtime.
