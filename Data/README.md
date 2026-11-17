# Dữ liệu thực nghiệm PIHAUPA

Thư mục chứa sáu bộ từ gói dữ liệu thực nghiệm của dự án: Accidents, Chainstore, NewChess, NewConnects, NewMushroom và Retail. Đây là các tệp đầu vào của dự án; không giả định mọi bộ trùng phiên bản hoặc cấu hình nêu trong bài báo.

Chạy `./run-web.ps1` tại thư mục dự án rồi chọn bộ dữ liệu trong giao diện. Cả Web và CLI đọc trực tiếp `transactions.txt.gz` của Accidents và Chainstore. Đây là gzip một thành phần, dưới 4 GiB sau giải nén. Bốn bộ còn lại lưu TXT. Không cần Git LFS.

`transactions.txt` dùng cấu trúc `items:TU:itemUtilities`. `investment_table.txt` được giữ để tham chiếu; không nhân lại vào utility giao dịch. `SHA256SUMS` ghi SHA-256 của các tệp TXT gốc (sau giải nén nếu có).
