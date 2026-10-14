package piHAUPA;

/**
 * Chọn chính sách kiểm tra quét lại: TIGHT hoặc ORIGINAL.
 * Đây là enum cấu hình, chưa phải GoF Strategy đa hình: enum không chứa
 * phương thức quyết định riêng cho từng chính sách; PiHaupaMiner vẫn rẽ nhánh.
 * Mục đích: dùng tên chế độ rõ ràng thay vì chuỗi hoặc số khó hiểu.
 * Nếu bổ sung nhiều chính sách, có thể tách một giao diện Strategy và các lớp
 * triển khai; nhiệm vụ ghi chú này giữ nguyên cách chọn và kết quả thuật toán.
 */
public enum RescanPolicy {
    TIGHT("PIHAUPA mới - Tight Eq.(6)"),
    ORIGINAL("Thuật toán cũ - Original Eq.(5)");

    private final String label;

    RescanPolicy(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
