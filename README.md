# PIHAUPA Research Lab

Ứng dụng Web Java để khai phá **mẫu tiện ích trung bình cao (HAUP)** trên dữ liệu tăng dần, kiểm chứng ví dụ bài báo và quan sát chi phí thực thi. Giao diện nền sáng trình bày cấu hình, kết quả, từng batch, quyết định re-scan và biểu đồ trong cùng một luồng phân tích.

Lõi Java được dùng chung cho Web và CLI. Bản hiện tại không có giao diện Swing/Desktop; không cần Node.js để chạy Web và không dùng Spring Boot hoặc React.

- [Cách chạy](#2-chạy-ứng-dụng)
- [Dữ liệu và thuật toán](#4-dữ-liệu-đầu-vào)
- [Kiểm thử](#7-kiểm-thử-và-kiểm-chứng)
- [Xuất báo cáo](#8-xuất-báo-cáo)
- [Tài liệu design pattern](README-DESIGN-PATTERNS.md)

## 1. Công nghệ và yêu cầu

- JDK **17 trở lên**, có cả `java` và `javac`.
- PowerShell để dùng các script `.ps1` trên Windows.
- Trình duyệt hiện đại hỗ trợ SVG, `dialog`, ES modules và Fetch API.
- Maven là tùy chọn để build JAR; Python 3 chỉ cần cho các script kiểm tra HTTP.
- Backend dùng `jdk.httpserver`, Java collections, executor, ZIP và quản lý JVM có sẵn trong JDK.
- Server chỉ bind `127.0.0.1`, phục vụ cho thực nghiệm cục bộ. Đây không phải cấu hình triển khai công khai nhiều người dùng.

Kiểm tra môi trường:

```powershell
java -version
javac -version
```

`scripts/java-environment.ps1` ưu tiên JDK từ `JAVA_HOME` hoặc JDK đã cài, tránh launcher shim `javapath` khi cần.

## 2. Chạy ứng dụng

Tại thư mục chứa `pom.xml`:

```powershell
# Build, chạy và mở trình duyệt; cổng mặc định 8000
.\run-web.ps1

# Cổng tùy chọn, thư mục chứa các bộ dữ liệu
.\run-web.ps1 -Port 8015 -DataRoot "D:\Data" -NoBrowser
```

Mở [http://localhost:8000](http://localhost:8000), hoặc cổng đã chỉ định. Dừng server bằng `Ctrl+C` trong terminal chạy server.

Cấu trúc dữ liệu gợi ý:

```text
D:\Data\
  Accidents-metadata\transactions.txt
  Chainstore-metadata\transactions.txt
  NewChess-Metadata\transactions.txt
  NewConnects-Metadata\transactions.txt
  NewMushroom-Metadata\transactions.txt
  Retail-metadata\transactions.txt
```

Catalog tìm các tệp `transactions.txt` trong tối đa 3 cấp dưới thư mục gốc. Dùng `-DataRoot` hoặc biến môi trường:

```powershell
$env:PIHAUPA_DATA_ROOT = 'D:\Data'
.\run-web.ps1 -Port 8015
```

Nếu chưa cấu hình, script thử đường dẫn `Downloads/Data-20261007T013338Z-1-001/Data` trong thư mục người dùng. Nếu không có dữ liệu lớn, ví dụ bài báo và chức năng tải TXT vẫn dùng được. Dữ liệu thực nghiệm lớn không đi kèm source.

### Build riêng và chạy JAR

```powershell
.\scripts\build.ps1
java -Xmx4g -cp target/classes vn.edu.dlu.pihaupa.web.LabServer 8015

# Tùy chọn nếu đã có Maven
mvn package
java -Xmx4g -jar target/pihaupa-research-lab-2.0.0.jar 8015
```

`-Xmx4g` là giới hạn heap, không phải lượng bộ nhớ luôn sử dụng. Cần điều chỉnh theo máy và dữ liệu. `mvn package` không thay thế lệnh chạy bộ test main/assertions ở mục 7.

## 3. Sử dụng giao diện

1. Chọn dữ liệu: mẫu bài báo, catalog hoặc tải tệp TXT.
2. Kiểm tra `Su`, `Sl` và phần trăm tương ứng. Cần `0 < Sl < Su ≤ 1`.
3. Mở **Tùy chọn & giải thích** khi cần xem định dạng, căn cứ ngưỡng, nhật ký hoặc bật đối chiếu Original/Tight.
4. Bấm **Chạy phân tích**. Nút bị khóa khi đang chạy và đổi nhãn theo trạng thái xử lý.
5. Xem tổng quan, từng batch kèm điều kiện re-scan, rồi tra cứu mẫu bằng bộ lọc/tìm kiếm/phân trang.
6. Mở **Đo lường & biểu đồ** hoặc xuất báo cáo của lượt đã hoàn tất.

Các mục riêng:

- **Bộ dữ liệu:** chọn dataset và xem dung lượng/cấu hình tương ứng.
- **Test case & kiểm thử:** chạy 20 case, xem kỳ vọng/thực tế và xuất bằng chứng.
- **Kiểm chứng bài báo:** đối chiếu giá trị cố định của ví dụ gốc.
- **Công thức & thuật ngữ:** định nghĩa utility, AUUB, MAU và re-scan.

Sidebar có thể thu gọn còn icon; tooltip và tên truy cập vẫn giữ. Trạng thái thu gọn được lưu trong trình duyệt. Mọi biểu đồ SVG trên Web có nút **Phóng to**, nhãn X/Y, đơn vị và bảng số liệu. Nhãn dài được giãn ở biểu đồ nhỏ; popup cho phép xem đầy đủ các mốc bằng cuộn ngang.

**Hồ sơ thực nghiệm** lưu các lượt hoàn tất trên máy trong `results/runs` (hoặc `PIHAUPA_RUN_ROOT`), có thể mở lại sau reload/restart và tải ZIP. Dữ liệu lưu cục bộ có preview tối đa 30 giao dịch; thư mục được Git ignore.

Góc **Local workspace** có lịch sử thông báo trong phiên trang: bắt đầu, đọc hoàn tất, khai phá, hoàn tất, lỗi, kiểm thử và chuẩn bị tải tệp. Thông báo này không phải log lưu bền trên server. Lượt rất nhanh có thể hoàn thành giữa hai lần polling; giao diện không cố tình kéo dài thời gian để minh họa tiến độ.

## 4. Dữ liệu đầu vào

### 4.1. `transactions.txt`: utility đã tính sẵn

Mỗi dòng gồm ba phần, cùng thứ tự item và utility:

```text
1 2 3:17:4 5 8
```

- Items: `1 2 3`.
- Transaction utility: `17`.
- Utility từng item: `4 5 8`; tổng phải bằng `17`.

Parser kiểm tra cấu trúc, số lượng item/utility, giá trị không hợp lệ, item trùng và TU không khớp. Các utility này đã là `u(i,T)`. Chương trình biểu diễn bằng `EU=1`; **không nhân lại `investment_table.txt`**.

Thứ tự dòng được giữ và chia thành DB0/DB1/DB2 khoảng **80%/10%/10%** để mô phỏng dữ liệu tăng dần; quy tắc làm tròn do reader xử lý. Đây là cách chia của ứng dụng, không khẳng định mọi thực nghiệm trong bài báo dùng cấu hình này. Các bộ dữ liệu chạy độc lập, không ghép thành một database.

### 4.2. Định dạng định lượng

```text
upper=0.23
lower=0.10
external:
A=2 B=5 C=1 D=3 E=6 F=4

batch DB0
T1: A:2 B:1 C:3
T2: C:1 D:2

batch DB1
T3: A:1 E:2
```

Giá trị trong giao dịch là `IU`; `external` chứa `EU`. Ví dụ trên minh họa cú pháp, không phải toàn bộ dữ liệu mẫu bài báo. Khi tải qua Web, chọn đúng định dạng; ngưỡng trên biểu mẫu được dùng cho lượt chạy. Mẫu bài báo đầy đủ đã được nhúng trong `InputParser.parseBuiltInSample()`.

### 4.3. Ngưỡng cho dữ liệu lớn

Đặt `r = max AU(singleton) / TU(DB)`. Trên web, mỗi tệp trong danh mục có thể chọn:

- **Tham chiếu:** cấu hình cũ `0.90r / 0.80r`; NewConnects `0.99r / 0.98r`.
- **Tổng quan (mặc định dữ liệu lớn):** `Su=0.70r`, `Sl=0.50r`.
- **Mở rộng:** `Su=0.50r`, `Sl=0.30r`.
- **Ngưỡng thấp:** `Su=0.25r`, `Sl=0.10r`.
- **Tự nhập:** giữ chính xác Su/Sl người dùng nhập, không tự nâng ngưỡng hoặc cắt kết quả để chạy nhanh.

Các mức này là cấu hình khảo sát của ứng dụng, **không phải benchmark bài báo**, không bảo đảm số HAUP hay thời gian cụ thể. Hạ ngưỡng có thể tăng mạnh không gian tìm kiếm, thời gian và bộ nhớ; dùng nút Hủy nếu muốn dừng. Tệp tải lên chưa có thống kê singleton dùng ngưỡng tự nhập. Mẫu bài báo vẫn mặc định 0,23/0,10; nếu chọn mức khảo sát thì dùng r=84/240, và kết quả không còn là lượt kiểm chứng ngưỡng gốc.

Mỗi lượt lưu ngưỡng thực tế, HAUP, PRE-LARGE, re-scan, thời gian và bộ nhớ. Xem **Đo lường & biểu đồ** để so sánh các lượt; **Hồ sơ thực nghiệm** giữ kết quả sau khi khởi động lại. So sánh ngưỡng trên cùng tệp và cùng môi trường; số mẫu nhiều hơn không tự chứng minh thuật toán đúng. Oracle độc lập chỉ áp dụng dữ liệu nhỏ trong phạm vi hỗ trợ.

## 5. Công thức và luồng thuật toán

Nguồn: Kim et al., *Intelligent average utility pattern analysis using pre-large concept in dynamic stream data*, **Journal of Big Data (2026), 13:13**, [DOI 10.1186/s40537-025-01352-x](https://doi.org/10.1186/s40537-025-01352-x). Năm trong nội dung bài là 2026 dù DOI chứa `025`.

Với mẫu `P` và giao dịch `T` chứa `P`:

```text
u(i,T) = IU(i,T) × EU(i)
TU(T) = Σ u(i,T)
MU(T) = max u(i,T)
TU(DB) = Σ TU(T)
MU(DB) = Σ MU(T)
U(P,T) = Σ(i thuộc P) u(i,T)
U(P) = Σ(T chứa P) U(P,T)
AU(P) = U(P) / |P|
AUUB(P) = Σ(T chứa P) MU(T)
```

AU chia cho **số item của mẫu**, không phải số giao dịch. MU(DB) là tổng MU từng giao dịch.

```text
LARGE / HAUP: AU(P) ≥ Su × TU(DB)
PRE-LARGE:    Sl × TU(DB) ≤ AU(P) < Su × TU(DB)
SMALL:        AU(P) < Sl × TU(DB)
```

`MRU(P,T)` là utility lớn nhất của item còn lại theo thứ tự mở rộng; `RN(P,T)` là số item còn lại; `MRN(P)=max RN(P,T)`. MAU được cộng trên các giao dịch chứa P:

```text
Nếu MRU(P,T) > AU(P,T):
  MAU(P,T) = [U(P,T) + MRN(P) × MRU(P,T)] / [|P| + MRN(P)]
Nếu 0 < MRU(P,T) ≤ AU(P,T):
  MAU(P,T) = [U(P,T) + MRU(P,T)] / [|P| + 1]
Nếu MRU(P,T) = 0:
  MAU(P,T) = 0
```

### Re-scan

- `OD`: database tại lần full scan gần nhất.
- `ID`: dữ liệu mới tích lũy sau lần đó, không chỉ batch vừa thêm.
- `DB = OD ∪ ID`.

```text
Tight — Eq. (12):
MU(ID) − Su × TU(ID) ≥ (Su − Sl) × TU(OD)

Original — Eq. (13), tương ứng Eq. (5):
TU(ID) × (1 − Su) ≥ (Su − Sl) × TU(OD)
```

Lõi dùng dạng Tight không chia để tránh mẫu số không dương của Eq. (6). **Bằng ngưỡng vẫn re-scan**. DB0 là initial scan, không tính vào số re-scan. Sau re-scan, cập nhật mốc OD và reset ID; nếu không re-scan, vẫn đọc batch và cập nhật mẫu đang quản lý trong PT. PRE-LARGE hiển thị giữa hai lần re-scan không phải kết quả khai phá lại toàn bộ PRE-LARGE từ đầu.

Luồng thực thi: đọc/kiểm tra → TU/MU và cận trên → PIHAUP-List → kiểm tra re-scan → mở rộng hoặc cập nhật PT → tính AU/phân loại → đo lường/xuất.

## 6. Chạy CLI

```powershell
# Mẫu bài báo nhúng sẵn
.\run.ps1 --summary
.\run.ps1 --summary --output-dir results/paper

# Tệp utility; CLI nhận định dạng này qua tên transactions.txt
.\run.ps1 --input "D:\Data\NewMushroom-Metadata\transactions.txt" --su 0.05 --sl 0.02 --summary --output-dir results/mushroom

# Tệp định lượng
.\run.ps1 --input "D:\Data\quantitative.txt" --summary

# ZIP: chọn một dataset; hoặc chỉ audit toàn bộ
.\run.ps1 --input "D:\Data.zip" --dataset NewChess --su 0.03 --sl 0.02 --summary
.\run.ps1 --audit-archive "D:\Data.zip"
.\run.ps1 --help
```

Các ngưỡng trong lệnh dataset là ví dụ, không phải cam kết tối ưu hiệu năng. `--summary` rút gọn nội dung console, không cắt dữ liệu hay thay thuật toán.

## 7. Kiểm thử và kiểm chứng

### Chạy bộ test mã nguồn

```powershell
.\run-tests.ps1
```

Script build source, compile test và chạy các lớp sau bằng `java -ea`:

- `PiHaupaSelfTest`: mẫu gốc, oracle vét cạn trên dữ liệu nhỏ/ngẫu nhiên và các tình huống re-scan.
- `ExportBundleTest`: định dạng input và bộ tệp xuất.
- `ArchiveDatasetReaderTest`: đọc archive và kiểm tra TU.
- `LargeTextDatasetTest`: dữ liệu TXT lớn được tạo cho kiểm thử reader.
- `LabApplicationTest`: catalog, job bất đồng bộ, comparison, JSON, mã lỗi, test runner và ZIP.

### 20 case chạy trên Web

- `TC-P01–12`: TU, MU, tập HAUP/AU qua DB0–DB2, số re-scan Original/Tight và cận Tight DB1; kỳ vọng cố định từ ví dụ bài báo.
- `TC-E01–05`: ngưỡng sai, TU sai, số item/utility lệch, item trùng, utility âm.
- `TC-A01–02`: re-scan đúng tại ranh giới và JSON giữ độ chính xác ngưỡng.
- `TC-D01`: utility gốc của `transactions.txt` được bảo toàn.

Case chưa chạy phải hiển thị CHƯA CHẠY. TC-P dùng chung một lượt chạy ví dụ; thời gian từng assertion không dùng để benchmark miner. Các case E/A/D là kiểm thử bổ sung của ứng dụng, không được mô tả là test do bài báo cung cấp.

Mốc kiểm chứng mẫu: TU tích lũy `141 → 173 → 240`; MU batch `73, 24, 40`; HAUP cuối `{E}:84`, `{B}:75`, `{B,E}:63.5`; Tight re-scan `1`, Original `2`; cận Tight DB1 `26.4375` (bài báo làm tròn `26.438`). Đạt các case này không đồng nghĩa tái lập toàn bộ benchmark hoặc chứng minh mọi đầu vào.

### Kiểm tra HTTP và dữ liệu thực

Sau khi server chạy và DataRoot đã cấu hình:

```powershell
python scripts/verify-web.py --url http://localhost:8015
python scripts/verify-web.py --url http://localhost:8015 --real-data --presets
python scripts/verify-exports.py
```

`verify-exports.py` hiện dùng cố định cổng 8015. Kiểm tra dữ liệu archive riêng sau khi `run-tests.ps1` compile các lớp test:

```powershell
java -Xmx4g -ea -cp "target/classes;target/test-classes" piHAUPA.RealDatasetVerification "D:\Data.zip"
java -Xmx4g -ea -cp "target/classes;target/test-classes" piHAUPA.DatasetProfileVerification "D:\Data\NewMushroom-Metadata\transactions.txt"
```

Các kiểm tra dữ liệu thực có thể cần nhiều heap và thời gian hơn test mặc định; xem tham số trong lớp tương ứng trước khi thay đường dẫn. Không sử dụng số đo của máy khác làm kết quả của máy đang chạy.

## 8. Xuất báo cáo

Xuất sử dụng kết quả đã hoàn tất, không mining lại.

**Gói báo cáo ZIP trên Web** gồm:

- `BAO_CAO.html`: báo cáo offline có SVG, đơn vị, nhãn trục và các bước tính.
- `BAO_CAO.txt`: diễn giải phép tính và quyết định theo từng batch.
- `bao-cao.csv`: báo cáo phẳng có bước/căn cứ.
- `patterns.csv`: toàn bộ mẫu LARGE/PRE-LARGE đang quản lý trong báo cáo.
- `performance-by-rows.csv`: thời gian, heap, TU/MU, số mẫu và điều kiện re-scan theo batch.
- `upper-bounds.csv`: AUUB item cùng ngưỡng trên/dưới.
- `measurements.csv`: import, thuật toán, CPU luồng, heap quan sát.
- `report.json`: kết quả, cấu hình, preview tối đa 30 giao dịch và comparison nếu có.
- `comparison.csv`: từng lượt Original/Tight trong JVM riêng, chỉ có nếu bật đối chiếu.
- `manifest.json`: SHA-256, định dạng, ngưỡng, batch, JDK/OS/heap và cấu hình đo.
- `verification.json`: phạm vi/kết quả oracle độc lập.
- `timing-samples.csv`: thời gian từng lượt đo Tight.
- `README.txt`: mô tả các tệp và giới hạn số đo.

Có thể tải báo cáo CSV/TXT riêng. ZIP không nhân bản raw dataset lớn và không phải bản sao lưu đầy đủ dữ liệu đầu vào.

**Xuất kiểm thử ZIP** gồm `TESTCASES.html`, `testcases.csv`, `testcases.json`. Tệp giữ trạng thái hiện tại, kỳ vọng/thực tế, thời điểm và thời gian case; xuất không tự chạy lại kiểm thử.

**CLI `--output-dir`** ghi `BAO_CAO.txt`, `bao-cao.csv`, `pihaupa-input.txt`, `patterns.csv`, `performance-by-rows.csv`, `upper-bounds.csv`. Bộ xuất CLI và gói ZIP Web có phạm vi khác nhau.

## 9. Đo lường và giới hạn

- Thời gian thuật toán là wall time của phần phân tích, không gồm đọc tệp và render UI.
- Import được đo riêng; tổng thời gian trạng thái có thể gồm chờ/đối chiếu/tổng hợp nên không nhất thiết bằng import + thuật toán.
- CPU là thời gian CPU của luồng thực thi, không phải phần trăm CPU toàn máy. Nếu JVM không hỗ trợ thì số đo không có.
- Heap dùng MiB (`bytes / 1,048,576`), lấy mẫu trong JVM chung; không phải RSS hoặc peak tuyệt đối. GC, các kết quả giữ trong bộ nhớ và thứ tự lượt chạy ảnh hưởng phép đo.
- Chọn 1, 3 hoặc 5 lượt đo; 3/5 lượt có 1 warm-up. Báo cáo chính lấy lượt có wall time median và lưu min–max/các mẫu đo. CPU là tổng của warm-up và các lượt trong JVM chính.
- Original/Tight chạy mỗi chính sách trong JVM con riêng với cùng số lượt. Heap vẫn là sampled JVM heap, không phải RSS. Hai JVM con chạy tuần tự; server và một JVM con có thể cùng sử dụng bộ nhớ. Không suy ra ít re-scan luôn nhanh hơn.
- Một phép phân tích hoặc kiểm thử chạy tại một thời điểm. Cache giữ tối đa 8 job, lịch sử phiên trang tối đa 8 kết quả. Hồ sơ hoàn tất được lưu trên disk; trang Hồ sơ hiển thị 20 lượt gần nhất. Chưa có chính sách tự dọn hồ sơ cũ.
- Nút Hủy gửi yêu cầu ngắt hợp tác trong reader/miner/oracle và dừng JVM con nếu có. Job CANCELLED không xuất/lưu kết quả dở dang. Khi bắt đầu ghi hồ sơ hoàn tất, nút hủy bị khóa để giữ tính nhất quán.
- Đọc tệp theo dòng không có nghĩa miner hoạt động với bộ nhớ O(1): giao dịch, PI-list và cây mẫu vẫn cần bộ nhớ.

## 10. Cấu trúc source và API

```text
src/main/java/
  piHAUPA/                         # lõi miner, model, parser, CLI, writer
  vn/edu/dlu/pihaupa/
    application/                   # service, nguồn dữ liệu, catalog, kiểm chứng, ZIP
    support/                       # JSON, lỗi có cấu trúc
    web/LabServer.java             # HTTP controller, static files
src/main/resources/
  static/                          # HTML/CSS và các module JS
    app.js                         # điều phối view/job
    api.js                         # Fetch, timeout, error contract
    ui-components.js               # bảng, card, icon dùng chung
    report-ui.js                   # kết luận, mức kiểm chứng, manifest
    notifications.js               # toast và lịch sử trạng thái
    charts.js                      # SVG, bảng số liệu, popup
    report.css                     # bố cục báo cáo tiết chế
  dataset-profiles.properties       # preset có đối chứng
src/test/java/piHAUPA/              # test main/assertions và kiểm chứng dữ liệu
scripts/                           # build, môi trường Java, kiểm tra HTTP
pom.xml
run-web.ps1
run.ps1
run-tests.ps1
```

API chính (localhost):

- `GET /api/datasets`: danh mục và profile.
- `GET /api/verify`: chạy kiểm chứng ví dụ bài báo.
- `GET /api/testcases`: đặc tả/kết quả gần nhất.
- `POST /api/testcases/run`: chạy 20 case.
- `GET /api/testcases/export`: tải ZIP kiểm thử.
- `POST /api/jobs?kind=paper&su=0.23&sl=0.10&compare=false&repeats=1`: tạo job (repeats: 1/3/5).
- `kind=catalog&dataset=<id>`: dùng ID từ catalog; `kind=upload&format=transactions`: gửi body TXT, hoặc `format=quantitative`.
- `GET /api/jobs/<id>`: trạng thái, nhật ký, số đo trực tiếp.
- `POST /api/jobs/<id>/cancel`: hủy hợp tác lượt đang chạy.
- `GET /api/runs`: 20 hồ sơ gần nhất.
- `GET /api/runs/<id>`: mở kết quả đã lưu.
- `GET /api/runs/<id>/export`: ZIP đã lưu, không mining lại.
- `GET /api/jobs/<id>/result`: báo cáo khi hoàn tất.
- `GET /api/jobs/<id>/export?format=csv|txt|zip`: xuất kết quả.

Job có các phase `READING`, `MINING`, `REPORTING`, `COMPARING` (tùy chọn), `COMPLETED`, `CANCELLING`, `CANCELLED`, `FAILED`. UI có thêm trạng thái gửi request/kết nối lại. API kiểm tra Origin của yêu cầu trình duyệt; ID catalog không nhận đường dẫn tùy ý.

## 11. Xử lý sự cố

- **Không có dataset:** kiểm tra `-DataRoot`, biến môi trường và tên `transactions.txt`; khởi động lại server để lập lại catalog.
- **INVALID_THRESHOLDS:** nhập tỷ lệ, ví dụ `0.03=3%`; cần `0<Sl<Su≤1`.
- **TU_MISMATCH / DUPLICATE_ITEM / INVALID_INPUT:** sửa đúng dòng/định dạng được báo; không bỏ qua lỗi để sinh kết quả.
- **BUSY:** chờ lượt phân tích hoặc kiểm thử hiện tại hoàn tất.
- **OUT_OF_MEMORY:** tăng heap nếu máy đáp ứng, tăng ngưỡng hoặc giảm quy mô thử nghiệm; không coi kết quả dở dang là hoàn tất.
- **JOB_EXPIRED:** job đã bị loại khỏi cache hoặc server khởi động lại; chạy lại trước khi xuất.
- **NETWORK_UNAVAILABLE / REQUEST_TIMEOUT:** kiểm tra terminal Java và cổng; đây là mã do frontend sinh.
- **Port đã dùng:** chọn cổng khác bằng `-Port`.
- **Script bị chặn:** chạy PowerShell với chính sách phù hợp của máy; không cần đổi chính sách toàn hệ thống để dùng các lệnh Java trực tiếp.

## 12. Tài liệu liên quan

[Tài liệu design pattern và kiến trúc](README-DESIGN-PATTERNS.md) mô tả lớp cụ thể, lý do áp dụng, luồng xử lý và giới hạn. Các công thức có thể tra trực tiếp trong mục Công thức & thuật ngữ của ứng dụng. Báo cáo thực nghiệm nên đính kèm cấu hình ngưỡng, dataset, JDK, heap, cách đo và file xuất của chính lượt chạy đó.


## 13. Hồ sơ và mức kiểm chứng

Kết luận nằm đầu báo cáo; batch và phép tính được bung khi cần. Ba nhãn kiểm chứng phân biệt rõ:

- **Ví dụ bài báo:** áp dụng cho đúng mẫu 9 giao dịch và ngưỡng 0.23/0.10; DB2 là giá trị tính từ dữ liệu mẫu, Table 5 in trực tiếp kết quả DB1.
- **Original/Tight:** so tập HAUP và AU giữa hai chính sách của cùng implementation, không phải xác nhận độc lập của tác giả.
- **Oracle độc lập:** vét cạn các tập con giao dịch, không dùng PI-list/pruning; tự chạy khi ≤12 item và ≤1.000 giao dịch. Ngoài giới hạn ghi chưa đối chứng; không gán ĐẠT.

Chạy kiểm tra luồng thực nghiệm:

```powershell
python scripts/verify-experiments.py --url http://localhost:8016
```

Bộ kiểm tra bao gồm phép đo lặp, median/range, JVM riêng, hủy và chạy lại, hồ sơ/ZIP và NewChess nếu có. `scripts/check-ui.cjs` kiểm tra module render bằng JSON tạo trong `tmp` bởi script tích hợp; đây không thay thế kiểm tra hình ảnh trong trình duyệt.

SHA-256 dataset lấy từ byte file gốc; mẫu nhúng dùng biểu diễn chuẩn hóa `canonical-input-sha256-v1`. Ngưỡng lưu riêng, không trộn vào dataset hash. Cấu hình trong hồ sơ giúp chạy lại, nhưng hồ sơ không chứa toàn bộ raw dataset. Cần giữ file gốc khớp hash.

Build bằng `scripts/build.ps1` ghi revision Git và trạng thái working tree vào manifest. Build bằng công cụ không tạo build-info sẽ ghi unavailable/unknown; không gán giả một commit cho phép đo.


### Thực nghiệm kích thước và tài nguyên

Trong **Tùy chọn & giải thích**, chọn 25/50/75/100% hoặc bấm **Thực nghiệm kích thước** để chạy tuần tự bốn mức. Giữ cùng Sᵤ/Sₗ và repetitions; không tự nâng ngưỡng hoặc dừng theo timeout. Hủy/lỗi dừng cả chuỗi, kết quả hoàn tất trước đó vẫn giữ. Mẫu nhỏ là prefix theo thứ tự gốc, chia lại DB0/DB1/DB2 khoảng 80/10/10; 100% giữ nguyên các batch của nguồn. Vẫn đọc/kiểm tra và hash toàn tệp, nên thời gian đọc không đại diện đọc riêng phần mẫu. Tỷ lệ trong manifest phân biệt các prefix có cùng hash tệp gốc. Với cùng tỷ lệ Su, ngưỡng tuyệt đối thay đổi theo TU của phần dữ liệu.

- Runtime: median/min/max và runtime/heap từng lượt đo; 3/5 lượt có 1 warm-up.
- JVM heap: bộ nhớ Java đang dùng, bộ nhớ đã cấp cho JVM; số đo trước/sau batch và lấy mẫu khoảng 1 giây.
- RAM tiến trình: working set trên Windows (`tasklist`), RSS trên Linux (`/proc/self/status`); không hỗ trợ thì null, không giả 0.
- RAM toàn máy: total và used từ OS MXBean, gồm mọi chương trình khác. Không phải mức RAM riêng của miner.
- Các đỉnh là đỉnh quan sát, có thể bỏ lỡ spike; các lượt dùng chung JVM và bộ nhớ còn lại từ lượt trước. JVM con Original/Tight không nằm trong RAM tiến trình server. Lấy mẫu tài nguyên có overhead; testcase heap chỉ chụp trước/sau action, không phải allocation hay peak riêng.

**Đo lường & biểu đồ** hiển thị RAM Java/RAM toàn máy ngay trong dãy thẻ số liệu, bảng runtime/heap từng lượt và từng kích thước, cùng CSV so sánh. Phần tài nguyên/kích thước bổ sung dùng con số và bảng, không dùng biểu đồ. ZIP từng lượt thêm `resources.json`, `resource-samples.csv`, `run-measurements.json/csv`; testcase ZIP chứa memoryBefore/memoryAfter trong JSON và các cột heap/system trong CSV. API nhận `sizePercent=25|50|75|100` (mặc định 100). Kiểm tra bằng `python scripts/verify-resources.py --url http://localhost:8017` trên server thử nghiệm. Thực nghiệm kích thước đánh giá hành vi/hiệu năng, không tự xác nhận tính đúng của tập dữ liệu lớn; oracle độc lập vẫn chỉ áp dụng phạm vi dữ liệu nhỏ được hỗ trợ.


### Nhật ký quy trình

Nhật ký hiển thị ngay dưới cấu hình, tự mở khi bắt đầu và thu gọn khi hoàn tất. Mỗi dòng có thời gian tương đối từ đầu job, chính sách và batch: kiểm tra TU/MU → quyết định re-scan (hai vế điều kiện) → xây PIHAUP-List → tái cấu trúc AUUB/MRU/MRN → mở rộng/cắt tỉa → xây hoặc cập nhật PT → oracle → đối chiếu → lưu báo cáo. Khi mở rộng lâu, cập nhật số mẫu đã duyệt/nút kết hợp khoảng 2 giây, kiểm tra tại các mốc 1.024 mẫu; đây không phải % hoàn thành hay ETA. Giữ 500 dòng gần nhất để giới hạn bộ nhớ. ZIP có `process.log`; report.json có `processLog` chụp trước khi lưu hồ sơ, nên không chứa sự kiện sau thời điểm lưu.


### Tối ưu pipeline dữ liệu lớn (15/10/2026)

Giữ nguyên Sᵤ/Sₗ, công thức AU/AUUB/MAU, tập LARGE/PRE-LARGE và điều kiện re-scan:

- Parser dùng chung Pattern khoảng trắng, kiểm tra mã item không tạo regex cho từng item, tái dùng set kiểm tra trùng từng dòng và pool String mã item theo mỗi lần đọc. Kiểm tra TU/utility/item trùng vẫn giữ nguyên; không intern hoặc cache toàn cục.
- Xây PIHAUP-List chỉ tạo node vượt cận dưới, tái sử dụng AUUB đã tích lũy từ toàn bộ giao dịch thay vì tính MU/AUUB lại. Không thay TU/MU bằng giá trị từ giao dịch đã cắt item.
- Tái cấu trúc dùng mảng theo vị trí giao dịch thay HashMap TID. Ghép conditional list bằng hai con trỏ, không tạo HashMap ở mỗi phép ghép. Thứ tự là vị trí giao dịch gốc, không phụ thuộc TID số, chuỗi hoặc thứ tự từ điển. Có kiểm tra hủy trong vòng ghép dài.

Đo trong hai JVM riêng trước/sau, heap tối đa 3 GiB, 1 warm-up + median 3 lượt, cùng máy/tệp/ngưỡng; không gồm UI, resource sampler, SHA-256 hay export:

- NewConnects, 67.557 giao dịch, Su=0,0358694 / Sl=0,0355071: đọc 1.552,476 → 1.136,556 ms; mining 1.671,239 → 870,593 ms. Tổng hai median giảm khoảng 38%. Kết quả giữ nguyên 1 HAUP, 0 PRE-LARGE, 2 re-scan.
- NewMushroom, 8.416 giao dịch, Su=0,05 / Sl=0,02: đọc 101,230 → 82,726 ms; mining 1.085,462 → 438,976 ms. Tổng hai median giảm khoảng 56%. Kết quả giữ nguyên 5 HAUP, 311 PRE-LARGE, 0 re-scan.
- Byte cấp phát median của luồng benchmark (đọc/mining và tạo chữ ký) giảm từ 5.110.818.920 → 1.921.346.576 ở NewConnects và 2.171.344.536 → 1.003.670.816 ở NewMushroom. Đây là tổng byte cấp phát trong pipeline, không phải RAM đang dùng hoặc peak heap.

Chữ ký SHA-256 của action, LARGE/PRE-LARGE và AU **ở mọi batch** trùng trước/sau trên hai cấu hình trên. Test oracle vét cạn/ngẫu nhiên, ranh giới, parser và TID không theo thứ tự vẫn đạt. Các mức tăng tốc là số đo máy hiện tại, không cam kết cho mọi bộ dữ liệu/ngưỡng. Không thêm cache input để tránh giữ thêm dữ liệu lớn trong RAM hoặc làm sai phạm vi đo đọc tệp.

Công cụ tái đo: `PipelineBenchmark` trong `src/test/java/piHAUPA/PipelineBenchmark.java`, nhận `transactions.txt Su Sl`; dùng classpath `target/classes;target/test-classes` sau khi biên dịch test. In median đọc/mining, byte cấp phát và chữ ký kết quả để kiểm tra trước/sau.


### Lưu giao dịch bằng mã item và mảng nguyên thủy

Parser `transactions.txt` lưu item thành `int[]`, utility thành `double[]`, dùng từ điển tên/mã chung theo từng tệp. Mảng `long[]` chỉ mục cho lookup nhị phân, trong khi thứ tự duyệt item gốc vẫn giữ nguyên. API `internalUtilities()` là Map view bất biến để giữ tương thích export/oracle/UI; miner duyệt primitive trực tiếp, không cần tạo Map.Entry/Double cho các vòng đọc chính. Mẫu nhúng và định dạng định lượng vẫn hỗ trợ Map như trước. Không thay đổi thứ tự cộng TU để tránh đổi kết quả dấu phẩy động.

Đo bổ sung cùng cấu hình benchmark bên trên: NewConnects đọc/mining median 1.144,002/807,574 ms; NewMushroom 66,725/409,943 ms. Chữ ký mọi batch trùng bản trước. Lợi ích chính là giảm heap giữ dữ liệu: với NewConnects, phép đo chẩn đoán sau GC trong JVM riêng giữ InputData sống cho thấy 216.134.144 → 72.930.152 bytes (khoảng 206,1 → 69,6 MiB, giảm 66%). Đây là heap giữ input sau GC, không phải RSS hoặc peak của toàn pipeline; GC này chỉ nằm trong công cụ benchmark, không thêm vào web.

30 luồng dữ liệu nhỏ ngẫu nhiên kiểm tra cả Original/Tight: array và Map có cùng LARGE/PRE-LARGE ở mọi batch, cùng re-scan và khớp oracle vét cạn. Kiểm tra Map view/entry không sửa được. Tái đo heap giữ input bằng tham số thứ tư `retained` của PipelineBenchmark. Chưa bật cache toàn bộ input hoặc song song miner: chúng có thể tăng RAM/overhead và cần thực nghiệm riêng trước khi áp dụng.


### Thanh tiến độ

Thanh % trong Nhật ký tính theo mốc thực sự đã hoàn tất: 1 mốc đọc/kiểm tra/hash, mỗi batch của mỗi lượt Tight (gồm warm-up), 1 mốc oracle, 1 mốc đối chiếu nếu bật, 1 mốc hoàn tất báo cáo. Trước khi biết số batch, thanh chưa xác định %. Trong lúc mở rộng mẫu, % có thể đứng yên và log vẫn cập nhật số mẫu đã duyệt; không suy ra % thời gian còn lại hoặc ETA. Chỉ job COMPLETED mới đạt 100%; lỗi/hủy giữ mức đã đạt và ghi trạng thái rõ ràng.


### Khôi phục lượt chạy và xử lý BUSY

`GET /api/jobs/active` trả lượt đang chạy trên server. Khi mở/tải lại trang hoặc gặp BUSY/timeout, web kết nối lại job đó, tiếp tục log/% và nhận kết quả thay vì gửi thêm lượt đo. Job ID gần nhất được lưu localStorage; nếu đã hoàn tất và còn trong cache, tải lại sẽ mở kết quả. ID hết hạn được xóa, không khóa UI. Nút **Hủy lượt đang chạy** nằm ngay trong nhật ký, dùng được cả ở trang Đo lường. Sau hủy, phải đợi worker dọn tài nguyên xong mới chạy lượt mới; không reset khóa để cho hai miner chạy đồng thời. Cấu hình mới không tự được chạy khi đang theo dõi lượt cũ. Chuỗi kích thước dừng nếu yêu cầu được thay bằng việc theo dõi một job đang có.

BUSY vẫn là phản hồi hợp lệ của API cho các client gửi đồng thời; web tự xử lý để nối lại tác vụ. Kiểm chứng/testcase dùng cùng worker gate, được chờ đến hoàn tất. ResourceMonitor khởi tạo trong try/finally để lỗi khởi tạo không bỏ lại khóa active. API trả HTTP status trong lỗi frontend để phân biệt ID hết hạn với lỗi mạng.


### Giảm bộ nhớ sống trong tìm kiếm DFS (16/10/2026)

**Dừng ghép theo cận còn lại — thử nghiệm, mặc định tắt:** `-Dpihaupa.lookAheadJoin=true` áp dụng trong `combine` cho mọi nhánh và mọi batch/tệp đủ điều kiện EU=1, utility nguyên, TU<2^52. Khi giao dịch của left/right chắc chắn không khớp, trừ MU(T) khỏi cận tương ứng; nếu cận còn lại dưới Sₗ×TU, dừng ghép. Nếu binary seek bỏ nhiều entry, chỉ trừ entry đã biết, giữ phần còn lại trong cận để không đánh giá thấp. Kết thúc ghép, tính AUUB chính xác trên giao dịch chung để truyền cho các mức con. Không dựng mảng suffix riêng cho mỗi node; chỉ dùng một mảng MU theo vị trí giao dịch cho mỗi lần initial scan/re-scan. Ngoài điều kiện cộng chính xác, tự dùng merge cũ.

Đối chứng có bật/tắt cận, toàn NewConnects tại 3,5%/3%, heap 512 MiB, 1 warm-up + 3 lượt: median bật 3.456,50 ms, tắt 3.301,45 ms — **chậm thêm khoảng 4,7%**, kết quả SHA từng batch khớp. Fixture 16 item/3.000 giao dịch, 2%/1%, heap 256 MiB, 5 lượt: số mẫu xét 19.781 → 18.000 nhưng thời gian 228,97 → 266,06 ms — **chậm khoảng 16,2%**. Vì vậy không bật mặc định và không tuyên bố đây là tăng tốc. `run-tests.ps1` chạy thêm vét cạn khi ép bật cận này/tắt lọc cặp; kiểm thử tổ hợp với earlyJoin cũ cũng đã qua. Các số đo có lượt nền cùng máy.

**Nghiên cứu cận suffix — thử nghiệm, mặc định tắt:** bật JVM bằng `-Dpihaupa.suffixRemainingBound=true`. PIHAUPA trang 14 mô tả MRU của node ghép bằng max của hai MRU; chế độ mặc định giữ đúng quy tắc này. Chế độ thử nghiệm dùng maximum utility của suffix sau item cuối, bởi DFS chỉ nối item nằm sau node đó. Với U=utility mẫu trong giao dịch, l=độ dài mẫu, r=maximum suffix, m=cận trên số item còn lại: mọi mở rộng có AU giao dịch ≤(U+m×r)/(l+m) nếu r>U/l; ≤(U+r)/(l+1) nếu 0<r≤U/l; bằng 0 nếu không còn suffix. Tổng các cận giao dịch chặn mọi mở rộng. Không thay U(P), ngưỡng, phân loại hay re-scan; không gọi đây là công thức MAU mới của tác giả.

Fixture 3.000 giao dịch/16 item, 2%/1%, heap 256 MiB, 1 warm-up + 5 lượt: số gọi ghép 19.733 → 17.329 (giảm 12,2%), median 234,54 → 215,85 ms (giảm 8,0%); SHA kết quả từng batch khớp. Các kiểm thử vét cạn số nguyên/số thực và EU khác 1 đều qua khi bật. Trên NewConnects 3,5%/3%, chỉ bỏ thêm 1 phép ghép và chưa chứng minh giảm thời gian; vì vậy **không bật mặc định**. Số đo có lượt nền cùng máy, không suy rộng sang ngưỡng chạy nhiều giờ.

Tài liệu đã đối chiếu: PIHAUPA, định nghĩa MRU/MAU và phép ghép trang 8/14; [HAUIM-GMU (Song et al., 2021)](https://www.philippe-fournier-viger.com/spmf/HAUIM_GMU_the_paper.pdf) về generalized maximal utility/cắt tỉa theo support; [HAUI-Miner](https://www.philippe-fournier-viger.com/spmf/hauiminer.pdf) về AUUB/list; [MPM/damped window (2018)](https://www.sciencedirect.com/science/article/abs/pii/S095070511730607X) về khai phá mẫu gần đây. Time fading đổi trọng số utility theo tuổi giao dịch, nên chưa tích hợp vào chế độ PIHAUPA đối chứng: cần định nghĩa lại TU, AU, các cận và chứng minh lại điều kiện re-scan. GAUUB/critical support của HAUIM-GMU là hướng nghiên cứu tiếp theo, chưa được triển khai đầy đủ trong dự án.

**Bộ lọc cặp item:** `C(a,b)=Σ MU(T)` trên các giao dịch chứa cả a,b. Với mọi mẫu P chứa cặp a,b, AU(P)≤C(a,b), vì trung bình utility của P trong mỗi giao dịch không vượt MU(T). Khi C(a,b)<Sₗ×TU(DB), bỏ phép ghép trước khi tạo PIHAUP-List con. Cận được xây lại trên toàn dữ liệu mỗi lần initial scan/re-scan; không làm thay đổi điều kiện quét lại hay cập nhật PT.

Chế độ mặc định `-Dpihaupa.pairFilter=auto`: chỉ bật cho 32–256 item ứng viên, EU=1, utility nguyên dương, TU<2^52 (cộng chính xác), và ước lượng không quá 100 triệu lượt cập nhật cặp mỗi lần dựng list. Ngoài phạm vi này dùng thuật toán cũ; đó là lựa chọn đường thực thi, không cắt dữ liệu/kết quả. `false` tắt để đối chứng; `true` cho phép thử cả tập dưới 32 item nhưng vẫn giữ giới hạn an toàn còn lại. Bảng double tối đa khoảng 512 KiB, không lưu giao dịch theo từng cặp.

Đối chứng toàn tệp NewConnects, Sᵤ=0,035/Sₗ=0,03, JVM heap 512 MiB, 1 warm-up + 3 lượt, cùng source với bộ lọc bật/tắt: median 4.594,43 → 3.820,45 ms (giảm 16,8%, gồm cả xây bảng cận). Số mẫu đã xét 1.966 → 1.899; số lần gọi ghép 1.890 giữ nguyên, nhưng một số lần trả về sớm trước khi duyệt entry. SHA-256 toàn bộ HAUP/PRE-LARGE từng batch khớp. Fixture 16 item/3.000 giao dịch ở 2%/1% cho chênh lệch gần bằng không khi ép bật; không suy rộng mức giảm sang lượt ngưỡng thấp nhiều giờ. Kiểm thử vét cạn thêm 20 bộ utility nguyên, đối chiếu cả HAUP và PRE-LARGE.

**Kiểm tra cận theo chi phí:** kiểm tra `ExtensionPlan.canReach` (duyệt item) trước MAU (duyệt entry giao dịch). MAU chỉ quyết định có mở rộng hay không; các số hạng không âm, nên khi tổng đã đạt Sₗ×TU có thể dừng kiểm tra. Kiểm tra theo khối 64 entry để giảm chi phí nhánh; khi chưa đạt ngưỡng vẫn cộng đến hết, giữ nguyên thứ tự tính.

Đo bổ sung so với commit `0364ad5`, cùng quy trình 1 warm-up + 3 lượt: NewConnects 3,5%/3%, heap 512 MiB, median 4.797,31 → 4.593,53 ms (giảm khoảng 4,2%); fixture utility số thực 3.000 giao dịch/16 item, 2%/1%, heap 256 MiB: 256,21 → 241,90 ms (giảm khoảng 5,6%). SHA-256 kết quả từng batch khớp. Các mức chênh lệch nhỏ chịu ảnh hưởng lượt nền; không cộng phần trăm giữa các lần benchmark và không suy ra thời gian của ngưỡng đang chạy. Kiểm thử bổ sung vét cạn 24 bộ dữ liệu số thực đối chiếu cả HAUP và PRE-LARGE, EU=1 và EU=1,7.

- Sau khi ghép xong các node con, giải phóng danh sách entry của node cha **trước khi** đệ quy. Các node con sở hữu entry riêng; các vòng tiếp theo chỉ ghép với node phía sau, nên không cần giữ lại danh sách đã xử lý.
- Node cuối không còn phần mở rộng: bỏ lượt quét tính MAU. Trước phép ghép, kiểm tra cận AUUB bảo thủ để tránh cấp phát khi mọi siêu mẫu đều dưới ngưỡng PRE-LARGE.
- Không đổi Sᵤ/Sₗ, điều kiện re-scan, tập HAUP/PRE-LARGE; không giới hạn số mẫu. Đây là kỹ thuật quản lý vòng đời dữ liệu, không phải design pattern GoF mới.

Đối chứng bằng `scripts/OptimizationBench.java`, 1 warm-up + 3 lượt đo, JVM riêng: toàn tệp NewConnects tại Sᵤ=0,035/Sₗ=0,03, heap 512 MiB, median 5.543,34 → 5.011,36 ms (giảm 9,6%). Fixture seed 71352, 3.000 giao dịch/16 item, Sᵤ=0,02/Sₗ=0,01, heap 256 MiB: 353,71 → 232,46 ms (giảm 34,3%). SHA-256 của toàn bộ kết quả từng batch khớp trước/sau. Đây là đối chứng với source ngay trước thay đổi này; có lượt nền cùng máy, chưa suy rộng sang cấu hình NewConnects chạy nhiều giờ.

Lõi mới cần JVM mới. Không hot-swap thuật toán vào lượt đang khai phá; khi người dùng chọn giữ lượt hiện tại, chỉ nạp tối ưu sau khi lượt đó kết thúc và server được khởi động lại.

### Tối ưu bước 1: đọc, kiểm tra và SHA-256

Khảo sát nguồn chính thức: [BufferedReader JDK 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html), [DigestInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/DigestInputStream.html), [Double.parseDouble](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html#parseDouble(java.lang.String)), [FileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html), [Oracle JFR](https://docs.oracle.com/en/java/javase/17/troubleshoot/troubleshoot-performance-issues-using-jfr.html), [OpenJDK JMH](https://github.com/openjdk/jmh), [FastDoubleParser của tác giả](https://github.com/wrandelshofer/FastDoubleParser). Đây là các nguồn được chọn theo vấn đề, không tuyên bố đã đọc mọi tài liệu trên Internet.

Áp dụng trong mã:

- Tách token bằng vị trí trong dòng, không tạo mảng String hoặc chuỗi utility ở đường số nguyên thường gặp. Số nguyên tối đa 15 chữ số được chuyển chính xác bằng long→double; số dài, thập phân, số mũ, dạng hex/suffix vẫn dùng Double.parseDouble của JDK để giữ cách làm tròn.
- Catalog/upload dùng BufferedInputStream + DigestInputStream + UTF-8 decoder báo lỗi + BufferedReader 64 KiB. Đọc/parse/kiểm tra và SHA-256 dùng cùng một lượt byte; ExperimentProfile tái dùng digest vừa tính. Source tùy chỉnh không cung cấp digest vẫn có fallback đọc/hash riêng. Không cache tệp cũ giữa các lượt.
- Tránh tính AUUB audit tạm khi đã xây InputData; miner vẫn tích lũy AUUB đúng theo từng batch. Chế độ inspect ZIP độc lập vẫn tính đầy đủ AUUB audit.
- Bước kiểm tra giao dịch trong miner duyệt primitive thay Map.Entry/Double cho storage compact; cộng TU vẫn giữ thứ tự cũ. Không bỏ kiểm tra item trùng, utility dương/hữu hạn, TU, EU hoặc TID.
- So sánh size/mtime/fileKey trước/sau đọc; nếu tệp thay đổi, báo lỗi thay vì lưu hồ sơ không nhất quán. Hash raw giữ nguyên CRLF/LF và toàn bộ byte đầu vào. Manifest có hashReadMode=single-pass-read-and-hash.

ReadStageBenchmark đo đúng read + validation + SHA-256 + manifest trong JVM riêng, 3 GiB heap, 1 warm-up + median 3 lượt, không mining/sampler/UI. Trên máy hiện tại:

- NewConnects: 1.054,045 → 839,025 ms, giảm khoảng 20%; 67.557 giao dịch, TU=996.517.630.255, SHA-256 trùng.
- Accidents: 4.240,708 → 3.191,474 ms, giảm khoảng 25%; 340.183 giao dịch, TU=3.478.342.570.855, SHA-256 trùng.

Số đo nhanh có ảnh hưởng JIT/GC/OS cache, không phải benchmark JMH nhiều fork và không cam kết tốc độ trên mọi máy. Tests gồm numeric bit-for-bit với JDK (leading zero, dấu cộng, số thực/mũ/hex, số nguyên lớn), whitespace, raw hash CRLF/quantitative, tệp bị đổi khi đọc và UTF-8 lỗi. Test API 6 profile vẫn giữ HAUP và TU. Chưa thêm thư viện parser ngoài, mmap hoặc parse song song vì chưa có số đo cho thấy lợi ích trên pipeline này.

Tái đo: `piHAUPA.ReadStageBenchmark <DataRoot> <TênThưMụcDataset>` với classpath `target/classes;target/test-classes` sau `run-tests.ps1`. Dùng cùng tham số và JVM riêng cho bản trước/sau.


### Hiệu năng và thực nghiệm

- Thời gian chạy: biểu đồ đường, đơn vị giây; bộ nhớ: biểu đồ cột, MB (1.000.000 bytes), tương ứng cách trình bày Fig. 8–14.
- Tiến trình chèn: chọn **OR + 9 lần chèn** trong tùy chọn phân tích. DB0 chứa OR của dữ liệu; chín batch sau chia đều phần còn lại. OR 82%, 95,5%, 99,8% tương ứng các cấu hình dữ liệu trong bài báo; dữ liệu quá nhỏ được báo lỗi. Ví dụ Tables 2–3 mặc định vẫn giữ ba batch gốc.
- Theo ngưỡng trên: giữ Sₗ và số giao dịch; theo kích thước: giữ hai ngưỡng; độ nhạy: cùng số giao dịch, thay hai ngưỡng. Chỉ các lượt có cùng nguồn, cấu hình chèn và số lần đo được ghép. Dãy kích thước dùng prefix 20/40/60/80/100%, không phải bộ dữ liệu tổng hợp 200K–1000K của tác giả.
- Bảng thiết bị hiển thị thời gian đọc, CPU luồng, RAM Java, RAM máy và CPU lấy mẫu. Không gọi sampled heap là đỉnh RAM tuyệt đối; kết quả Java không đồng nhất phép đo C++ của tác giả. Các thuật toán đối chứng chưa cài đặt không xuất hiện dưới dạng số liệu giả.
- ZIP thêm `insertion-performance.csv`: tiến trình chèn, số giao dịch tích lũy, thời gian mỗi batch, heap và quyết định re-scan; `resource-samples.csv` bổ sung CPU tiến trình/toàn máy.


### Báo cáo lớn và phục hồi khi hết heap

Khi tổng số bản ghi mẫu trong báo cáo vượt 50.000, web dùng luồng ghi CSV/ZIP tuần tự xuống đĩa. JSON gửi lên trình duyệt chỉ có tối đa 100 mẫu mỗi lớp/batch, kèm **số đếm đầy đủ** `largePatternCount`, `preLargePatternCount`; giao diện đọc 25 mẫu/trang từ CSV. Đây là giới hạn preview truyền tải, không giới hạn kết quả khai phá. Tìm kiếm báo cáo lớn thực hiện khi nhấn Enter/rời ô tìm kiếm, có thể phải quét CSV; các trang đầu không tìm kiếm dừng đọc khi đủ dòng.

- Tải ZIP dùng stream file, tránh nạp toàn bộ ZIP vào byte[].
- Mẫu đầy đủ, SMALL còn quản lý nếu có, công thức, số đo và nhật ký nằm trong gói xuất. Có HTML gọn với bảng tổng và biểu đồ runtime/bộ nhớ; số liệu lớn nằm trong CSV.
- Sau khi lưu thành công, job giải phóng tham chiếu tới report mẫu lớn; bản đầy đủ nằm trên đĩa. Metadata giao diện giữ tối đa 600 mẫu thiết bị gần nhất và ghi tổng số mẫu; ZIP giữ toàn bộ số đo.
- CLI mặc định vẫn xuất JSON đầy đủ. Chế độ web bật `pihaupa.diskReports`; `pihaupa.bulkThreshold` mặc định 50.000 chỉ dùng chọn cách xuất, không thay ngưỡng khai phá.
- `RecoveryWriter` có thể ghi report đã hoàn tất từ job FAILED sang `results/runs/recovered/<id>` bằng stream, không mining lại. Việc cứu hộ cần report vẫn còn trong JVM; không phải checkpoint để tiếp tục khai phá sau khi JVM đã mất.

Sự cố ngày 16/10/2026 đã cứu đầy đủ: DB0 3.846.405 dòng, DB1 2.945.507 dòng, DB2 2.307.057 dòng (7.971 LARGE/HAUP, 2.299.086 PRE-LARGE). Đã đối chiếu số dòng và đọc kiểm tra CRC của ZIP 199.103.738 byte. Không tải dữ liệu thực nghiệm này lên Git.

Kiểm tra stress ghi 2 triệu **bản ghi fixture** và đọc phân trang/ZIP dưới `-Xmx128m`; không có nghĩa khai phá 2 triệu mẫu thực chỉ cần 128MiB. Chạy: `java -Xmx128m -ea -cp "target/classes;target/test-classes" piHAUPA.RecoveryWriterTest stress` sau build/tests, với PIHAUPA_RUN_ROOT trỏ thư mục test.

`scripts/RecoveryAttach.java` và `scripts/ReportStorageAttach.java` là công cụ cứu hộ/cập nhật phần báo cáo một lần qua JDK Attach. Chỉ dùng với JVM của dự án và class schema tương thích; không đổi thuật toán đang chạy. Không cần dùng trong vận hành bình thường sau khi khởi động bằng bản sửa.
