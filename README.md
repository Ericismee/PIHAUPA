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

`dataset-profiles.properties` chứa cấu hình khởi đầu cho 6 dataset đã đối chứng. `DatasetProfile` chỉ áp dụng cấu hình khi tên và dung lượng tệp khớp; đây không phải kiểm tra checksum nội dung.

Đặt `r = max AU(singleton) / TU(DB)`. Các preset dùng `Su=0.90r`, `Sl=0.80r`; NewConnects dùng `0.99r` và `0.98r` để hạn chế không gian mẫu lúc bắt đầu. Các ngưỡng này là **preset của ứng dụng**, không phải bộ ngưỡng benchmark do bài báo công bố. Người dùng có thể chỉnh lại; hạ ngưỡng có thể tăng mạnh thời gian và bộ nhớ.

Không có HAUP có thể là kết quả đúng ở ngưỡng đã chọn. Nếu `max AUUB(item) < Su × TU(DB)`, cận trên đã loại mọi HAUP. Cần phân biệt trường hợp này với lỗi parser, lỗi xử lý hoặc bộ lọc đang loại hết mẫu.

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
- `comparison.csv`: chỉ xuất nếu đã bật đối chiếu Original/Tight.
- `README.txt`: mô tả các tệp và giới hạn số đo.

Có thể tải báo cáo CSV/TXT riêng. ZIP không nhân bản raw dataset lớn và không phải bản sao lưu đầy đủ dữ liệu đầu vào.

**Xuất kiểm thử ZIP** gồm `TESTCASES.html`, `testcases.csv`, `testcases.json`. Tệp giữ trạng thái hiện tại, kỳ vọng/thực tế, thời điểm và thời gian case; xuất không tự chạy lại kiểm thử.

**CLI `--output-dir`** ghi `BAO_CAO.txt`, `bao-cao.csv`, `pihaupa-input.txt`, `patterns.csv`, `performance-by-rows.csv`, `upper-bounds.csv`. Bộ xuất CLI và gói ZIP Web có phạm vi khác nhau.

## 9. Đo lường và giới hạn

- Thời gian thuật toán là wall time của phần phân tích, không gồm đọc tệp và render UI.
- Import được đo riêng; tổng thời gian trạng thái có thể gồm chờ/đối chiếu/tổng hợp nên không nhất thiết bằng import + thuật toán.
- CPU là thời gian CPU của luồng thực thi, không phải phần trăm CPU toàn máy. Nếu JVM không hỗ trợ thì số đo không có.
- Heap dùng MiB (`bytes / 1,048,576`), lấy mẫu trong JVM chung; không phải RSS hoặc peak tuyệt đối. GC, các kết quả giữ trong bộ nhớ và thứ tự lượt chạy ảnh hưởng phép đo.
- Comparison dùng warm-up và median 3 lượt cho dữ liệu nhỏ (≤1.000 giao dịch), một lượt cho dữ liệu lớn; không được suy ra ít re-scan luôn nhanh hơn.
- Một phép phân tích hoặc kiểm thử chạy tại một thời điểm. Server giữ tối đa 8 job; lịch sử Web tối đa 8 kết quả trong phiên trang, mất khi reload. Không có database lưu bền.
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
  static/                          # index.html, styles.css, app.js, charts.js
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
- `POST /api/jobs?kind=paper&su=0.23&sl=0.10&compare=false`: tạo job.
- `kind=catalog&dataset=<id>`: dùng ID từ catalog; `kind=upload&format=transactions`: gửi body TXT, hoặc `format=quantitative`.
- `GET /api/jobs/<id>`: trạng thái, nhật ký, số đo trực tiếp.
- `GET /api/jobs/<id>/result`: báo cáo khi hoàn tất.
- `GET /api/jobs/<id>/export?format=csv|txt|zip`: xuất kết quả.

Job có các phase `READING`, `MINING`, `REPORTING`, `COMPARING` (tùy chọn), `COMPLETED`, `FAILED`. UI có thêm trạng thái gửi request/kết nối lại. API kiểm tra Origin của yêu cầu trình duyệt; ID catalog không nhận đường dẫn tùy ý.

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
