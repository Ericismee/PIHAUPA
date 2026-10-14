package vn.edu.dlu.pihaupa.support;

import java.io.IOException;
import java.util.*;

/** Shared error contract for synchronous HTTP errors and asynchronous jobs. */
public record AppError(String code, String message, String action) {
    public static AppError from(Throwable error) {
        String message=error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();
        String lower=message.toLowerCase(Locale.ROOT);
        if(error instanceof OutOfMemoryError)return new AppError("OUT_OF_MEMORY","JVM không đủ heap để hoàn tất lượt phân tích.","Tăng ngưỡng hoặc chạy Java với heap lớn hơn. Báo cáo lượt này chưa hoàn tất.");
        if(error instanceof NoSuchElementException)return new AppError("JOB_EXPIRED",message,"Máy chủ có thể đã khởi động lại hoặc báo cáo đã hết hạn. Chạy lại phân tích.");
        if(error instanceof NumberFormatException||lower.contains("số không hợp lệ"))return new AppError("INVALID_NUMBER",message,"Nhập số hữu hạn; ngưỡng dùng tỷ lệ, ví dụ 0.03 = 3%.");
        if(error instanceof IllegalStateException)return new AppError(lower.contains("chưa sẵn sàng")?"REPORT_NOT_READY":"BUSY",message,"Đợi lượt đang chạy hoàn tất rồi thử lại.");
        if(error instanceof IOException)return new AppError("DATA_IO",message,"Kiểm tra tệp tồn tại, quyền đọc và dung lượng ổ đĩa.");
        if(lower.contains("sl")&& (lower.contains("su")||lower.contains("threshold")))return new AppError("INVALID_THRESHOLDS",message,"Ngưỡng phải thỏa 0 < Sl < Su ≤ 1; 0.03 tương ứng 3%.");
        if(lower.contains("giao dịch sai tu")||lower.contains("transaction utility"))return new AppError("TU_MISMATCH",message,"Sửa TU của dòng được báo để bằng tổng itemUtilities. Không dùng kết quả từ tệp sai TU.");
        if(lower.contains("duplicate transaction")||lower.contains("transaction id")&&lower.contains("duplicate"))return new AppError("DUPLICATE_TID",message,"Mỗi giao dịch định lượng phải có một TID duy nhất.");
        if(lower.contains("bị lặp")||lower.contains("duplicate item"))return new AppError("DUPLICATE_ITEM",message,"Mỗi item chỉ xuất hiện một lần trong một giao dịch.");
        if(lower.contains("danh mục"))return new AppError("DATASET_NOT_FOUND",message,"Chọn lại bộ dữ liệu từ danh mục hiện tại.");
        if(error instanceof IllegalArgumentException)return new AppError("INVALID_INPUT",message,"Kiểm tra định dạng TXT, số item/utility và các giá trị dương tại dòng được báo.");
        return new AppError("INTERNAL_ERROR",message,"Ghi lại mã lỗi và nhật ký xử lý để kiểm tra; không xem lượt này là đã hoàn tất.");
    }
    public Map<String,Object> toMap(){return Map.of("error",message,"code",code,"action",action);}
}
