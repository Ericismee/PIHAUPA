# PIHAUPA

*Khai phá mẫu tiện ích trung bình trên dữ liệu giao dịch tăng dần.*

Ứng dụng Java này triển khai **PIHAUPA** theo bài báo *Intelligent average utility pattern analysis using pre-large concept in dynamic stream data* (Kim et al., *Journal of Big Data*). Mục tiêu không chỉ là đưa ra tập mẫu HAUP cuối cùng: người dùng còn có thể theo dõi các phép tính, ngưỡng phân loại và quyết định **Tight Re-Scan Condition** sau từng batch.

| Đầu vào | Phân tích | Đầu ra |
|---|---|---|
| Dữ liệu Tables 2–3 hoặc tệp TXT định lượng; `Su` và `Sl` do người dùng chọn | PIHAUP-List, AUUB/MAU, LARGE/PRE-LARGE và cập nhật tăng dần | Báo cáo theo từng batch, số đo thời gian/heap, tệp TXT/CSV |

Desktop, Web cục bộ và dòng lệnh dùng **cùng lõi tính toán Java**. Giao diện được thiết kế để đọc kết quả theo thứ tự: *dữ liệu → công thức → quyết định re-scan → mẫu cuối → chi phí xử lý*.

**Đi tới:** [Bắt đầu nhanh](#bắt-đầu-nhanh) · [Cách đọc kết quả](#cách-đọc-kết-quả) · [Công thức](#công-thức-cốt-lõi) · [Dữ liệu lớn](#thực-nghiệm-với-dữ-liệu-lớn) · [Đối chứng](#đối-chứng-và-kiểm-thử) · [Kiến trúc](#kiến-trúc-và-design-pattern)

## Bắt đầu nhanh

Yêu cầu **JDK 17 trở lên**. Chạy một trong các lệnh sau tại thư mục gốc dự án:

```powershell
.\run-desktop.ps1  # Ứng dụng Desktop
.\run-web.ps1      # Web cục bộ; tự mở trình duyệt
.\run.ps1          # CLI với Tables 2–3, Su=0.23 và Sl=0.10
```

Desktop tự chạy ví dụ bài báo khi mở. Trên Web, chọn **Dữ liệu bài báo** hoặc một tệp `.txt`, điều chỉnh `Su`/`Sl` và bấm **Chạy PIHAUPA**. Không cần đóng gói EXE.

## Phạm vi triển khai

| Thành phần | Chương trình thực hiện |
|---|---|
| Đọc dữ liệu | Nhận giao dịch định lượng theo batch; hỗ trợ tệp `transactions.txt` của các bộ thực nghiệm lớn. |
| Khai phá | Tính `U`, `TU`, `MU`, `AU`, `AUUB`, `MRU`, `MRN`, `MAU`; dựng PIHAUP-List, mở rộng DFS và cắt tỉa. |
| Cập nhật | Dùng `Su`/`Sl` phân loại LARGE, PRE-LARGE, SMALL. Eq. (12) quyết định cập nhật pattern tree hiện có hay re-scan và dựng lại. |
| Giải thích | Trình bày Bước 1–5, số thế vào công thức, mẫu theo batch, sơ đồ re-scan và các mốc thời gian/heap quan sát. |
| Xuất kết quả | TXT/CSV dễ đọc; CLI/Desktop có thể ghi thêm input chuẩn hóa và bảng chi tiết khi chọn thư mục xuất. |

## Công thức cốt lõi

Với `u(i,T) = IU(i,T) * EU(i)`:

```text
TU(T)   = sum u(i,T)
MU(T)   = max u(i,T)
U(P,T)  = sum u(i,T), i thuộc P và P thuộc T
AU(P,T) = U(P,T) / |P|
AU(P)   = sum AU(P,T) trên các T chứa P

LARGE:     AU(P) >= Su * TU(DB)
PRE-LARGE: Sl * TU(DB) <= AU(P) < Su * TU(DB)
SMALL:     AU(P) < Sl * TU(DB)
```

Tight re-scan Eq. (6):

```text
MU(ID) >= ((Su - Sl) / (1 - TU(ID)/MU(ID) * Su)) * TU(OD)
```

Chương trình so sánh bằng dạng ổn định đại số của bài báo (Eq. 12), tránh phép chia:

```text
MU(ID) - Su * TU(ID) >= (Su - Sl) * TU(OD)
```

`OD` là dữ liệu ở lần full scan gần nhất; `ID` là toàn bộ dữ liệu được thêm sau lần đó. Khi bằng ngưỡng (`>=`) vẫn re-scan.

## Cách đọc kết quả

| Khu vực Web | Câu hỏi được trả lời |
|---|---|
| **Tổng quan** | Kết luận cuối là gì? Dữ liệu, ngưỡng và số mẫu nào tạo ra kết luận ấy? |
| **Từng bước** | TU, AU, cận trên và Eq. (12) được tính như thế nào ở DB0–DB2? |
| **Sơ đồ** | Batch mới dẫn tới *update existing PT* hay *re-scan*? Mẫu nào đổi trạng thái? |
| **Kết quả** | Những mẫu LARGE/HAUP và PRE-LARGE được quản lý có AU bao nhiêu? |
| **Tài nguyên** | Thời gian đọc tệp, thời gian thuật toán, CPU luồng và JVM heap thay đổi ra sao? |
| **Dữ liệu lớn** | Một tệp `transactions.txt` chạy với ngưỡng đã chọn cho kết quả và log nào? |
| **Thuật ngữ** | Ký hiệu trong bài báo có nghĩa gì và xuất hiện ở bước nào? |

Sơ đồ re-scan cho chọn DB0/DB1/DB2, nhấn mạnh nhánh được thực hiện và mặc định hiển thị các mẫu đã thay đổi. Có thể mở phép tính AU hoặc tra cứu theo nhóm item. **Sơ đồ tra cứu này được dựng từ báo cáo, không phải pattern tree nội bộ**; nó chỉ vẽ tối đa 60 mẫu có AU cao nhất và ghi rõ tổng số mẫu.

Màu sắc là một lớp hỗ trợ đọc: xanh lam cho điều hướng và công thức; xanh lục cho LARGE hoặc nhánh không re-scan; hổ phách cho PRE-LARGE hoặc nhánh re-scan; đỏ cho lỗi. **Nhãn chữ và số liệu luôn đi kèm màu**, nên kết luận không phụ thuộc vào khả năng phân biệt màu sắc.

> **Giới hạn số đo:** “thời gian thuật toán” không bao gồm đọc tệp hay dựng giao diện. Tổng thời gian phía server gồm nhận và xử lý request nhưng không gồm gửi response/dựng trang. Heap là bộ nhớ JVM được lấy mẫu, không phải RAM/RSS toàn máy; CPU luồng không phải phần trăm CPU hệ thống. Ứng dụng không đo điện năng tiêu thụ.

### Trải nghiệm chờ kết quả: Skeleton Screen

Một báo cáo PIHAUPA có nhiều lớp thông tin: kết luận, phép tính theo batch, sơ
đồ re-scan, danh sách mẫu và tài nguyên. Để người dùng không phải nhìn vào
một trang trống trước khi phân tích, Web hiển thị **skeleton** mô phỏng vị trí
các khối nội dung ấy. Mỗi tab có khung xem trước tương ứng; tab Dữ liệu lớn
có khung riêng cho giao dịch, mẫu, thời gian và heap.

Khi bắt đầu chạy, nhãn **ĐANG PHÂN TÍCH** và hiệu ứng chuyển động nhẹ cho biết
ứng dụng đang chờ kết quả. Sau khi nhận báo cáo, skeleton được thay bằng số
liệu thật; nếu có lỗi, thông báo lỗi xuất hiện thay vì một kết quả giả. Hiệu
ứng được tắt khi hệ điều hành yêu cầu giảm chuyển động. Skeleton là **mẫu
thiết kế giao diện**, giúp định hướng và truyền đạt trạng thái; nó không tham
gia tính AU, quyết định re-scan hay tăng tốc thuật toán.

## Thực nghiệm với dữ liệu lớn

Trong tab **Dữ liệu lớn**, chọn thư mục `Data` đã giải nén hoặc chọn trực tiếp một hay nhiều tệp `transactions.txt`. Chương trình liệt kê từng bộ để chạy **độc lập**, không ghép sáu bộ vào cùng một database. Người dùng đặt `Su`/`Sl` cho từng lượt; `0,23/0,10` chỉ là giá trị khởi đầu, không phải ngưỡng cố định của dữ liệu lớn.

| Quy tắc xử lý | Ý nghĩa |
|---|---|
| `items:TU:itemUtilities` | Utility từng item đã nằm trong tệp; chương trình dùng `EU=1` và giữ giá trị utility trong `IU`, không nhân thêm lần nữa. |
| `investment_table.txt` | Metadata của bộ dữ liệu, không bị dùng nhầm làm external utility; đọc `transactions.txt` không cần tệp này. |
| DB0 / DB1 / DB2 | Giữ thứ tự giao dịch gốc và chia khoảng 80% / 10% / 10% để mô phỏng chèn batch; ZIP gốc không định nghĩa các batch này. |
| Mỗi lượt chạy | Lưu ngưỡng, số giao dịch, HAUP/PRE-LARGE, số re-scan, thời gian và heap trong phiên. |

Sau khi chạy, mở **Từng bước** hoặc **Sơ đồ** để xem phép tính DB0–DB2. Nếu `max AUUB(item) < Su × TU(DB)`, cận trên chứng minh không có HAUP ở ngưỡng đó; điều này không thay cho báo cáo PRE-LARGE và thống kê re-scan. Web không đặt giới hạn tệp 20 MB cố định, nhưng quy mô xử lý thực tế vẫn phụ thuộc RAM/heap và độ phức tạp của dữ liệu.

### Kiểm tra bộ dữ liệu gốc

Có thể kiểm tra toàn bộ ZIP hoặc chạy một bộ từ dòng lệnh:

```powershell
.\run.ps1 --audit-archive "C:\Users\Mera\Downloads\Data-20261007T013338Z-1-001.zip"
.\run.ps1 --input "C:\Users\Mera\Downloads\Data-20261007T013338Z-1-001.zip" --dataset NewMushroom --summary
.\run.ps1 --input "C:\Users\Mera\Downloads\Data\NewChess-Metadata\transactions.txt" --su 0.12 --sl 0.05 --summary
```

Để lặp lại phép kiểm chứng với ZIP gốc (không tạo tệp dữ liệu mới):

```powershell
.\run-tests.ps1
java -ea -Xmx3g -cp out piHAUPA.RealDatasetVerification "C:\Users\Mera\Downloads\Data-20261007T013338Z-1-001.zip"
```

Bài kiểm chứng đối chiếu số dòng, tổng TU và lỗi TU của cả sáu bộ; sau đó chạy NewMushroom với hai cặp ngưỡng `0,23/0,10` và `0,05/0,02`. Ở ngưỡng thấp, phép kiểm chứng đọc lại giao dịch gốc trong ZIP để tính độc lập `sumU` và `AU` cho năm mẫu HAUP cuối. **Phạm vi này không chứng minh mọi mẫu ở mọi ngưỡng.**

### Các lệnh CLI thường dùng

```powershell
.\run.ps1 --input examples\paper-example.txt
.\run.ps1 --input examples\paper-example.txt --su 0.23 --sl 0.10 --output-dir results\paper
.\run.ps1 --help
```

Dòng đầu dùng ngưỡng ghi trong tệp; dòng thứ hai ghi đè ngưỡng và xuất báo cáo. Muốn chạy dữ liệu mặc định, dùng `.\run.ps1` như phần [Bắt đầu nhanh](#bắt-đầu-nhanh).

## Định dạng dữ liệu đầu vào

Với tệp PIHAUPA thông thường, khai báo hai ngưỡng, bảng external utility và các batch theo thứ tự thời gian:

```text
upper=0.23
lower=0.10

external:
A=2 B=5 C=1 D=3 E=6 F=4

batch DB0
T1: A:2 B:1 C:3
T2: C:1 D:2

batch DB1
T3: A:3 F:3
```

Giá trị sau item là **internal utility**; bảng `external` chứa **external utility**. Mỗi transaction cần mã duy nhất trên toàn bộ batch, không lặp item trong cùng giao dịch và có utility dương.

## Đối chứng và kiểm thử

### Kết quả chuẩn của ví dụ bài báo

| Batch | TU batch | MU batch | TU tích luỹ | Tight re-scan | LARGE/HAUP |
|---|---:|---:|---:|---|---|
| DB0 | 141 | 73 | 141 | khởi tạo | E=60, BE=45, B=35, AE=34 |
| DB1 | 32 | 24 | 173 | không (`24 < 26.438`) | E=72, BE=45 |
| DB2 | 67 | 40 | 240 | có (ID tích luỹ DB1+DB2) | E=84, B=75, BE=63.5 |

Sau DB1, `AE=34` và `B=35` chuyển thành PRE-LARGE; `CAE=15.333` thành SMALL và bị loại khỏi pattern tree. DB2 làm điều kiện tight đạt, nên chương trình full scan và mở rộng mẫu lại.

Lưu ý: khi **không** re-scan, PIHAUPA chỉ cập nhật các ứng viên LARGE/PRE-LARGE
đã có trong pattern tree. Do đó danh sách PRE-LARGE hiển thị là tập **được quản
lý**, không nhất thiết chứa mọi tập thỏa bất đẳng thức lower/upper nếu tính lại
vét cạn. Ví dụ `F` đạt ngưỡng PRE-LARGE ở DB1 nhưng không nằm trong cây từ DB0;
điều kiện tight vẫn bảo đảm nó chưa thể thành LARGE trước lần re-scan kế tiếp.

### Kiểm thử tự động

```powershell
.\run-tests.ps1
java -ea -cp out piHAUPA.DesktopLayoutSmokeTest
```

Bộ test kiểm tra số liệu Tables 2-5, ranh giới phân loại, dấu `>=` của re-scan, tích luỹ ID qua nhiều batch, đối chiếu kết quả bằng vét cạn trên dữ liệu ngẫu nhiên nhỏ và dữ liệu input không hợp lệ.
Bài smoke test desktop kiểm tra kích thước nút ở cột trái và hộp chọn thư mục xuất.

### Bộ dữ liệu lớn có đáp án đối chiếu

[`examples/paper-scaled-9000.txt`](examples/paper-scaled-9000.txt) có 9.000 giao dịch:
mỗi giao dịch trong Tables 2–3 được lặp 1.000 lần với TID duy nhất. File giữ nguyên
`Su=0.23`, `Sl=0.10`. Vì vậy utility và các ngưỡng phải gấp đúng 1.000 lần ví dụ
bài báo; cấu trúc mẫu và quyết định re-scan phải giữ nguyên.

| Batch | Số giao dịch tích luỹ | TU tích luỹ | Upper | Lower | Số LARGE |
|---|---:|---:|---:|---:|---:|
| DB0 | 5.000 | 141.000 | 32.430 | 14.100 | 4 |
| DB1 | 7.000 | 173.000 | 39.790 | 17.300 | 2 |
| DB2 | 9.000 | 240.000 | 55.200 | 24.000 | 3 |

AU cuối: `E=84.000`, `B=75.000`, `BE=63.500`. Tight re-scan một lần ở
DB2. Chạy bộ kiểm chứng độc lập theo định nghĩa
`AU(P)` (vét cạn mọi tập con của A–F). Bộ test so khớp toàn bộ LARGE/HAUP và
kiểm tra AU, phân loại của từng PRE-LARGE được quản lý:

```powershell
.\verify-large.ps1
```

Tạo lại file với hệ số lặp khác, ví dụ 2.000 lần (18.000 giao dịch):

```powershell
.\generate-paper-scaled.ps1 2000
.\verify-large.ps1 examples\paper-scaled-18000.txt
```

## Xuất báo cáo và thao tác tệp

Trên Desktop, **Chọn tệp** dùng hộp chọn tệp của Windows; có thể kéo thả tệp `.txt` vào vùng dữ liệu. Thư mục xuất dùng hộp chọn thư mục của hệ điều hành. Với tệp lớn, màn hình chỉ mở sẵn 25 giao dịch đầu và có nút xem thêm; **thuật toán vẫn tính trên toàn bộ tệp**. Báo cáo cũ được ẩn trong lúc chạy lượt mới để tránh nhầm kết quả.

| Tệp xuất từ Desktop/CLI | Nội dung |
|---|---|
| `BAO_CAO.txt` | Bản đọc trước: công thức Bước 1–5, quyết định và kết quả theo batch. |
| `bao-cao.csv` | Số liệu báo cáo ở dạng bảng. |
| `patterns.csv`, `upper-bounds.csv` | Chi tiết mẫu và cận trên. |
| `performance-by-rows.csv` | Số đo chi tiết theo dòng. |
| `pihaupa-input.txt` | Dữ liệu đầu vào đã chuẩn hóa. |

Web có nút tải TXT và CSV riêng, không xuất HTML hoặc ZIP. Giao diện hiện tại xuất từ **báo cáo của lượt chạy đã lưu tạm trên server**, không khai phá lại; nếu bản lưu hết hạn, hãy chạy phân tích rồi xuất lại.

> **Không phải dữ liệu giao dịch:** `PIPA_dataset/all_data.txt` mô tả metadata ảnh (album, ảnh, bounding box, identity, subset), không chứa định nghĩa item, IU, EU hay batch cho PIHAUPA. Chương trình báo lỗi nếu chọn trực tiếp; muốn sử dụng phải định nghĩa cách chuyển đổi dữ liệu trước.

## Kiến trúc và design pattern

Điểm quan trọng trong thiết kế là **một lõi PIHAUPA phục vụ ba cách sử dụng**:
Desktop, Web và dòng lệnh. Nhờ vậy, khi thay đổi cách hiển thị hoặc xuất báo
cáo, các công thức tính tiện ích và quyết định re-scan vẫn đi qua cùng một
quy trình. Những cơ chế dưới đây giải quyết bài toán tổ chức và tương tác của
phần mềm; chúng không thay thế thuật toán của bài báo.

| Cơ chế | Cốt lõi | Vai trò trong chương trình |
|---|---|---|
| Observer / event listener của Swing | Nút phát sự kiện, listener phản ứng | Nối thao tác chọn tệp, chạy và xuất với handler tương ứng trong Desktop; bản thân listener không chạy thuật toán. |
| Template Method của `SwingWorker` | Thư viện định sẵn vòng đời `doInBackground()` → `done()` | Chạy phân tích/xuất file ngoài luồng giao diện rồi đưa kết quả về EDT, giúp cửa sổ không bị đứng. |
| Facade (vai trò kiến trúc) | Một cửa vào che trình tự nhiều bước | `AnalysisReport.run` gom xử lý batch và đo đạc; `ExportBundle.writeDirectory` gom các file TXT/CSV từ kết quả đã tính. Desktop, Web, CLI dùng chung lõi. |
| Static factory của Swing | Phương thức `BorderFactory.create...` tạo viền | Dựng giao diện nhất quán; đây là API có sẵn, **không** phải Factory Method do dự án tự cài đặt. |
| Skeleton Screen (UI pattern) | Hiện khung có hình dạng gần kết quả trước khi số liệu sẵn sàng | Web giữ bố cục dễ nhận biết khi chưa chạy/đang chạy; thay khung bằng báo cáo thật sau khi phân tích. Không tham gia tính AU hoặc re-scan. |

Trong phần trình bày kiến trúc, cần phân biệt **cơ chế được sử dụng** với
**pattern do dự án tự triển khai**: `SwingWorker` và `BorderFactory` là API
của Swing; `AnalysisReport.run` và `ExportBundle.writeDirectory` đảm nhiệm vai
trò Facade. `RescanPolicy` hiện là enum được kiểm tra bằng nhánh điều kiện,
chưa phải GoF Strategy. PIHAUP-List, pattern tree, MAU pruning và Tight
re-scan là thuật toán/cấu trúc dữ liệu của bài báo, không phải design pattern
GoF. [Tài liệu kiến trúc và design pattern](README-DESIGN-PATTERNS.md) trình
bày chi tiết cách từng cơ chế hoạt động, nơi áp dụng và giới hạn của nó.
