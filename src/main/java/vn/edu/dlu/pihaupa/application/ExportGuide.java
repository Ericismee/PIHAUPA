package vn.edu.dlu.pihaupa.application;

import java.util.*;

/** Một danh mục chung cho ZIP, liên kết HTML và hướng dẫn đọc; không nhân đôi dữ liệu. */
public final class ExportGuide {

  private ExportGuide() {}

  public static String path(String name) {
    if (name.contains("/")) return name;
    return switch (name) {
      case "manifest.json" -> "01-cau-hinh/" + name;
      case
        "performance-by-rows.csv",
        "insertion-performance.csv",
        "upper-bounds.csv",
        "process.log" -> "02-tung-batch/" + name;
      case
        "measurements.csv",
        "measurements.json",
        "run-measurements.csv",
        "run-measurements.json",
        "timing-samples.csv",
        "resource-samples.csv",
        "resources.json" -> "03-do-luong/" + name;
      case "verification.json", "oracle.json", "comparison.csv" -> "04-kiem-chung/" + name;
      case
        "report.json",
        "summary.json",
        "counts.properties",
        "web-result.json" -> "05-du-lieu-ky-thuat/" + name;
      default -> name.startsWith("batch-") && name.endsWith("-patterns.csv")
        ? "02-tung-batch/" + name
        : name;
    };
  }

  public static String description(String name) {
    return switch (name) {
      case "BAO_CAO.html" -> "Mở đầu tiên: báo cáo theo bước, bảng, biểu đồ và kết luận.";
      case "BAO_CAO.txt" -> "Bản thuyết minh và phép tính để đọc/in hoặc trích vào báo cáo.";
      case "bao-cao.csv" -> "Các phép tính theo bước và batch; mở bằng Excel.";
      case "patterns.csv" -> "Toàn bộ mẫu PT cuối; lọc type=LARGE để lấy HAUP, PRE_LARGE là vùng đệm.";
      case "managed-patterns.csv" -> "Toàn bộ PT cuối, kể cả SMALL còn quản lý; không coi tất cả là HAUP.";
      case "manifest.json" -> "Nguồn dữ liệu, SHA-256, ngưỡng, JVM, thiết bị và cấu hình đo.";
      case "performance-by-rows.csv" -> "Số giao dịch, thời gian, ngưỡng và quyết định từng batch.";
      case "insertion-performance.csv" -> "Runtime và heap theo tiến độ chèn (%), dùng dựng biểu đồ thực nghiệm.";
      case "upper-bounds.csv" -> "AUUB từng item; cận sàng lọc, không phải AU/kết quả HAUP.";
      case "process.log" -> "Nhật ký các bước đã chạy, kèm thời điểm tương đối.";
      case
        "measurements.csv",
        "measurements.json" -> "Số đo tổng hợp: đọc/hash, runtime, CPU và bộ nhớ.";
      case
        "run-measurements.csv",
        "run-measurements.json" -> "Runtime và heap của từng lượt đo; đối chiếu độ dao động.";
      case "timing-samples.csv" -> "Runtime từng lượt dùng tính median; cùng lượt đo với run-measurements.";
      case
        "resource-samples.csv",
        "resources.json" -> "Mẫu tài nguyên theo thời gian: heap, RAM Java/toàn máy, CPU; không phải đỉnh tuyệt đối.";
      case
        "verification.json",
        "oracle.json" -> "Kết quả oracle độc lập. NOT_CHECKED nghĩa là chưa kiểm chứng, không phải ĐẠT.";
      case "comparison.csv" -> "Đối chiếu Original/Tight, chỉ có khi đã bật đo đối chiếu.";
      case "report.json" -> "Kết quả và metadata dạng máy đọc; preview giao dịch không phải toàn tệp input.";
      case "summary.json", "counts.properties" -> "Tổng hợp/số đếm chính xác cho gói CSV lớn.";
      case "web-result.json" -> "Preview cho giao diện; không thay thế CSV kết quả đầy đủ.";
      default -> name.startsWith("batch-")
        ? "Toàn bộ LARGE/PRE-LARGE đang quản lý ở batch tương ứng."
        : "Tệp hỗ trợ của lượt xuất.";
    };
  }

  public static Map<String, String> organize(Map<String, String> files) {
    Map<String, String> out = new LinkedHashMap<>();
    files.forEach((name, content) -> out.put(path(name), content));
    return out;
  }

  public static String readme(Collection<String> files) {
    StringBuilder out = new StringBuilder("BÁO CÁO PIHAUPA — BẮT ĐẦU TẠI ĐÂY\n\n");
    out
      .append(
        "Giải nén toàn bộ ZIP trước khi mở BAO_CAO.html để các liên kết tới CSV/JSON hoạt động.\n"
      )
      .append(
        "Tệp gốc giao dịch không nằm trong gói; SHA-256 và cấu hình ở 01-cau-hinh/manifest.json.\n"
      )
      .append("Không chạy lại khai phá khi xuất hoặc sắp xếp báo cáo.\n\n")
      .append("FILE CHÍNH\n");
    for (String name : List.of(
      "BAO_CAO.html",
      "BAO_CAO.txt",
      "bao-cao.csv",
      "patterns.csv",
      "managed-patterns.csv"
    ))
      if (files.contains(name)) out
        .append("- ")
        .append(name)
        .append(": ")
        .append(description(name))
        .append('\n');
    out.append(
      "\nTRÌNH TỰ ĐỌC\n01 Nguồn & cấu hình → 02 TU/ngưỡng → 03 Re-scan → 04 Khai phá/PT → 05 Đo lường → 06 Kết luận.\n\nFILE CHI TIẾT\n"
    );
    files
      .stream()
      .filter(name -> !path(name).equals(name))
      .sorted(Comparator.comparing(ExportGuide::path))
      .forEach(name ->
        out.append("- ").append(path(name)).append(": ").append(description(name)).append('\n')
      );
    return out
      .append("\nĐƠN VỊ VÀ GIỚI HẠN\n")
      .append("- AU(P)=U(P)/|P|; LARGE/HAUP khi AU≥Sᵤ×TU(DB). PRE-LARGE không phải HAUP cuối.\n")
      .append("- DB0 là quét khởi tạo, không tính là re-scan.\n")
      .append("- Median là runtime một lượt; tổng CPU/warm-up/nhiều lượt có cơ sở khác nhau.\n")
      .append("- Heap JVM khác RSS/RAM tiến trình và RAM toàn máy; MiB=2^20 byte, MB=10^6 byte.\n")
      .append("- Số trong HTML làm tròn để đọc; CSV/JSON giữ số liệu xuất gốc.\n")
      .toString();
  }
}
