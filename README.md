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
