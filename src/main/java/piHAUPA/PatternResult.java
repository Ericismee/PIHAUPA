package piHAUPA;

import java.util.List;

public record PatternResult(List<String> items, double sumUtility, int length, PatternType type) {
    public double averageUtility() {
        return sumUtility / length;
    }

    public String displayName() {
        if (items.stream().anyMatch(PatternResult::isNumericItem)) {
            return "{" + String.join(", ", items) + "}";
        }
        return String.join("", items);
    }
    /** Cùng quy tắc [0-9]+ nhưng không biên dịch regex cho từng tên mẫu.
     * Được gọi nhiều khi sắp xếp và xuất hàng triệu mẫu.
     */
    private static boolean isNumericItem(String item) {
        if (item.isEmpty()) return false;
        for (int i = 0; i < item.length(); i++) {
            char character = item.charAt(i);
            if (character < '0' || character > '9') return false;
        }
        return true;
    }
}

