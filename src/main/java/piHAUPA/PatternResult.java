package piHAUPA;

import java.util.List;

public record PatternResult(List<String> items, double sumUtility, int length, PatternType type) {
  public double averageUtility() {
    return sumUtility / length;
  }

  public String displayName() {
    if (
      items.stream().anyMatch(item -> {
        // Cùng [0-9]+, không tạo regex mỗi lần sắp xếp/xuất tên mẫu.
        if (item.isEmpty()) return false;
        for (int i = 0; i < item.length(); i++) {
          char character = item.charAt(i);
          if (character < '0' || character > '9') return false;
        }
        return true;
      })
    ) {
      return "{" + String.join(", ", items) + "}";
    }
    return String.join("", items);
  }
}
