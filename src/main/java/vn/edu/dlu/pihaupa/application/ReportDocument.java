package vn.edu.dlu.pihaupa.application;

import java.text.*;
import java.util.*;
import piHAUPA.*;

/** Báo cáo offline từ kết quả đã hoàn tất; HTML có giới hạn preview, CSV vẫn đầy đủ. */
public final class ReportDocument {

  private ReportDocument() {}

  public static String html(AnalysisService.Job job, Collection<String> files, boolean bulk) {
    AnalysisReport r = Objects.requireNonNull(job.report);
    var last = r.batches().get(r.batchCount() - 1);
    String patterns = bulk ? "managed-patterns.csv" : "patterns.csv";
    StringBuilder out = new StringBuilder(
      "<!doctype html><html lang='vi'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Báo cáo PIHAUPA</title><style>"
    );
    out
      .append(CSS)
      .append(
        "</style></head><body><header><p class='eyebrow'>PIHAUPA / BÁO CÁO THỰC NGHIỆM</p><h1>"
      )
      .append(h(job.source))
      .append("</h1><p>")
      .append(n(r.transactionCount()))
      .append(" giao dịch · ")
      .append(n(r.batchCount()))
      .append(" batch · ")
      .append(h(r.rescanPolicy()))
      .append(" · ")
      .append(h(job.manifest.getOrDefault("createdAt", job.createdAt)))
      .append("</p></header>");
    out.append(
      "<aside class='start'><h2>File chính</h2><p>Giải nén toàn bộ gói. Đọc HTML theo thứ tự dưới đây; mở CSV khi cần số liệu đầy đủ.</p><div class='file-links'>"
    );
    for (String name : List.of("BAO_CAO.txt", "bao-cao.csv", patterns))
      if (files.contains(name)) out
        .append("<a href='")
        .append(ExportGuide.path(name))
        .append("'>")
        .append(h(name))
        .append("</a>");
    out.append("</div><details><summary>Danh mục file và mục đích</summary>");
    out.append("<div class='files'>");
    tableStart(out, "Danh mục tệp xuất", "Nhóm", "File", "Nội dung / khi sử dụng");
    for (String name : files)
      if (!Set.of("BAO_CAO.html", "README.txt").contains(name)) row(
        out,
        ExportGuide.path(name).equals(name)
          ? "File chính"
          : h(ExportGuide.path(name).split("/")[0]),
        link(name),
        h(ExportGuide.description(name))
      );
    tableEnd(out);
    out.append("</div></details></aside>");
    if (bulk && "FAILED".equals(job.state)) out
      .append(
        "<p class='basis'>Kết quả khai phá được khôi phục từ bộ nhớ sau khi bước xuất/lưu gặp lỗi. Trạng thái lượt gốc vẫn FAILED; xem "
      )
      .append(link("summary.json"))
      .append(". Các CSV chứa toàn bộ mẫu đã tính, không dùng preview để suy ra số lượng.</p>");
    out.append("<nav aria-label='Các bước báo cáo'>");
    for (String[] step : new String[][] {
      { "01", "Nguồn" },
      { "02", "Ngưỡng" },
      { "03", "Re-scan" },
      { "04", "Khai phá" },
      { "05", "Đo lường" },
      { "06", "Kết luận" },
    })
      out
        .append("<a href='#buoc-")
        .append(step[0])
        .append("'>")
        .append(step[0])
        .append(" · ")
        .append(step[1])
        .append("</a>");
    out.append("</nav>");

    section(out, "01", "Nguồn dữ liệu và cấu hình");
    tableStart(out, "Bảng 1 · Cấu hình thực nghiệm", "Thông số", "Giá trị");
    row(out, "Nguồn", h(job.source));
    row(
      out,
      "Dữ liệu sử dụng",
      n(job.manifest.getOrDefault("sizePercent", 100)) +
        "% · " +
        n(r.transactionCount()) +
        " giao dịch"
    );
    row(
      out,
      "Ngưỡng Sᵤ / Sₗ",
      n(r.upperThreshold() * 100) + "% / " + n(r.lowerThreshold() * 100) + "%"
    );
    row(
      out,
      "Số lượt đo / warm-up",
      n(job.manifest.getOrDefault("measuredRuns", job.timingSamples.size())) +
        " / " +
        n(job.manifest.getOrDefault("warmupRuns", 0))
    );
    row(
      out,
      "Java / heap tối đa",
      h(job.manifest.getOrDefault("javaVersion", "Chưa ghi nhận")) +
        " / " +
        n(job.manifest.get("maxHeapMiB")) +
        " MiB"
    );
    row(out, "Thiết bị", h(job.manifest.getOrDefault("cpuDescription", "Chưa ghi nhận")));
    row(
      out,
      "SHA-256 input",
      "<code>" + h(job.manifest.getOrDefault("sha256", "Chưa ghi nhận")) + "</code>"
    );
    tableEnd(out);
    out.append(
      "<p>" + link("manifest.json") + " · Tệp input gốc không được đóng gói.</p></section>"
    );

    section(out, "02", "Utility và ngưỡng theo batch");
    out.append(
      "<p class='basis'>Căn cứ: TU(DB)=Σ TU(T); ngưỡng LARGE=Sᵤ×TU(DB); ngưỡng PRE-LARGE=Sₗ×TU(DB).</p>"
    );
    tableStart(
      out,
      "Bảng 2 · Số giao dịch và ngưỡng tích lũy",
      "Batch",
      "Giao dịch mới",
      "Tích lũy",
      "TU(batch)",
      "MU(batch)",
      "TU(DB)",
      "Ngưỡng LARGE",
      "Ngưỡng PRE-LARGE"
    );
    for (var b : r.batches())
      row(
        out,
        h(b.name()),
        n(b.transactionCount()),
        n(b.cumulativeTransactionCount()),
        n(b.batchTransactionUtility()),
        n(b.batchMaximumUtility()),
        n(b.totalTransactionUtility()),
        n(b.minUtilUpper()),
        n(b.minUtilLower())
      );
    tableEnd(out);
    out.append("<p>" + available(files, "bao-cao.csv", "summary.json") + "</p></section>");

    section(out, "03", "Quyết định quét lại");
    boolean tight = r.rescanPolicy() == RescanPolicy.TIGHT;
    out
      .append("<p class='basis'>")
      .append(
        tight
          ? "Eq. (12): MU(ID) − Sᵤ×TU(ID) ≥ (Sᵤ−Sₗ)×TU(OD)."
          : "Eq. (5): (1−Sᵤ)×TU(ID) ≥ (Sᵤ−Sₗ)×TU(OD)."
      )
      .append(
        " OD là dữ liệu tại lần quét toàn bộ gần nhất; ID tích lũy sau lần đó. Bằng ngưỡng vẫn quét lại.</p>"
      );
    tableStart(
      out,
      "Bảng 3 · Hai vế điều kiện và hành động",
      "Batch",
      "TU(ID)",
      "MU(ID)",
      "Vế trái",
      "Vế phải",
      "Hành động"
    );
    for (var b : r.batches()) {
      double left = tight
        ? b.accumulatedMaximumUtility() - r.upperThreshold() * b.accumulatedTransactionUtility()
        : (1 - r.upperThreshold()) * b.accumulatedTransactionUtility();
      double right =
        (r.upperThreshold() - r.lowerThreshold()) *
        (b.totalTransactionUtility() - b.accumulatedTransactionUtility());
      boolean initial = b.index() == 0;
      row(
        out,
        h(b.name()),
        initial ? "—" : n(b.accumulatedTransactionUtility()),
        initial ? "—" : n(b.accumulatedMaximumUtility()),
        initial ? "—" : n(left),
        initial ? "—" : n(right),
        badge(initial ? "Quét khởi tạo" : b.rescanTriggered() ? "Quét lại" : "Cập nhật PT")
      );
    }
    tableEnd(out);
    out.append(
      "<p>Không re-scan vẫn cập nhật utility của mẫu đang quản lý. DB0 không tính vào số re-scan. " +
        available(files, "performance-by-rows.csv", "summary.json") +
        " · " +
        link("process.log") +
        "</p></section>"
    );

    section(out, "04", "Khai phá và phân loại mẫu");
    out.append(
      "<p class='basis'>AU(P)=U(P)/|P|. LARGE/HAUP: AU≥Sᵤ×TU(DB). PRE-LARGE: Sₗ×TU(DB)≤AU&lt;Sᵤ×TU(DB). SMALL không thuộc kết quả HAUP.</p>"
    );
    tableStart(
      out,
      "Bảng 4 · Mẫu đang quản lý sau mỗi batch",
      "Batch",
      "HAUP / LARGE",
      "PRE-LARGE",
      "Nguồn số liệu"
    );
    for (var b : r.batches())
      row(
        out,
        h(b.name()),
        n(b.largePatterns().size()),
        n(b.preLargePatterns().size()),
        bulk ? link("batch-" + b.index() + "-patterns.csv") : link("report.json")
      );
    tableEnd(out);
    out.append(
      "<p>AUUB dùng để sàng lọc; AUUB cao không chứng minh mẫu là HAUP. " +
        available(files, "upper-bounds.csv", patterns) +
        "</p></section>"
    );

    section(out, "05", "Thời gian và bộ nhớ");
    out.append(
      "<p>Runtime dưới đây thuộc lượt đại diện; median nhiều lượt và tổng CPU có phạm vi khác nhau.</p>"
    );
    tableStart(
      out,
      "Bảng 5 · Runtime và heap của từng batch",
      "Batch",
      "Runtime (s)",
      "Heap trước (MiB)",
      "Heap sau (MiB)",
      "Đỉnh heap quan sát tích lũy (MiB)"
    );
    for (var b : r.batches())
      row(
        out,
        h(b.name()),
        n(b.elapsedMs() / 1000),
        n(b.memoryBeforeMb()),
        n(b.memoryAfterMb()),
        n(b.peakMemoryMb())
      );
    tableEnd(out);
    out
      .append("<div class='chart-grid'>")
      .append(chart(r, true))
      .append(chart(r, false))
      .append("</div>");
    tableStart(out, "Bảng 6 · Phạm vi các số đo tổng hợp", "Đại lượng", "Giá trị", "Phạm vi");
    metric(
      out,
      "Đọc + SHA-256",
      job.measurements.get("importMs"),
      "ms",
      "Một lần nhập tệp, không gồm mining"
    );
    metric(
      out,
      "Khai phá · median",
      job.measurements.getOrDefault("medianMs", r.elapsedNanos() / 1e6),
      "ms",
      "Một lượt đại diện của các lượt đo, không gồm warm-up"
    );
    metric(
      out,
      "Khai phá · tổng",
      job.measurements.get("algorithmTotalMs"),
      "ms",
      "Tổng vùng khai phá, gồm warm-up và các lượt đo; không phải median một lượt"
    );
    metric(
      out,
      "CPU luồng khai phá",
      job.measurements.get("threadCpuMs"),
      "ms",
      "Tổng CPU các lượt và warm-up; không phải wall time một lượt"
    );
    metric(
      out,
      "Tổng xử lý",
      job.measurements.get("processingMs"),
      "ms",
      "Phạm vi của lượt web, có đọc và các công đoạn được ghi nhận"
    );
    tableEnd(out);
    tableStart(out, "Bảng 7 · Runtime từng lượt đo", "Lượt", "Runtime (ms)", "Ghi chú");
    for (int i = 0; i < job.timingSamples.size(); i++) row(
      out,
      Integer.toString(i + 1),
      n(job.timingSamples.get(i)),
      "Không gồm warm-up"
    );
    if (job.timingSamples.isEmpty()) row(out, "—", "Chưa ghi nhận", "Xem tệp đo lường nếu có");
    tableEnd(out);
    resources(out, job.measurements.get("resources"));
    out.append(
      "<p>" +
        available(files, "run-measurements.csv", "measurements.json") +
        " · " +
        available(files, "resource-samples.csv", "measurements.json") +
        "</p></section>"
    );

    section(out, "06", "Kết luận khai phá");
    out
      .append("<div class='conclusion'><strong>")
      .append(n(last.largePatterns().size()))
      .append(" HAUP</strong><span>")
      .append(n(last.preLargePatterns().size()))
      .append(" PRE-LARGE · ")
      .append(n(r.stats().rescanCount()))
      .append(" re-scan, không gồm DB0</span></div>");
    out
      .append("<p>Sau ")
      .append(h(last.name()))
      .append(", ngưỡng HAUP = ")
      .append(n(r.upperThreshold()))
      .append(" × ")
      .append(n(last.totalTransactionUtility()))
      .append(" = <strong>")
      .append(n(last.minUtilUpper()))
      .append(
        " utility</strong>. Mỗi HAUP có AU(P) đạt ngưỡng này; PRE-LARGE chỉ được giữ để hỗ trợ cập nhật.</p>"
      );
    tableStart(
      out,
      "Bảng 8 · Tối đa 20 HAUP có AU cao nhất ở batch cuối",
      "Mẫu P",
      "U(P)",
      "|P|",
      "AU(P)=U(P)/|P|",
      "Đối chiếu ngưỡng"
    );
    // List kết quả đã được miner sắp theo AU; không sort/copy hàng triệu mẫu chỉ để xem 20 dòng.
    for (var p : last.largePatterns().stream().limit(20).toList())
      row(
        out,
        h(p.displayName()),
        n(p.sumUtility()),
        n(p.length()),
        n(p.averageUtility()),
        "≥ " + n(last.minUtilUpper())
      );
    if (last.largePatterns().isEmpty()) row(
      out,
      "Không có HAUP",
      "—",
      "—",
      "—",
      "Không mẫu nào đạt ngưỡng tại kết quả cuối"
    );
    tableEnd(out);
    out
      .append("<p>Bảng HTML là preview; toàn bộ mẫu ở ")
      .append(link(patterns))
      .append(". Lọc <code>type=LARGE</code> để lấy HAUP; không coi PRE-LARGE/SMALL là HAUP.</p>");
    String oracle = String.valueOf(job.oracle.getOrDefault("state", "NOT_CHECKED"));
    out
      .append("<p>Oracle độc lập: <strong>")
      .append(h(oracle))
      .append("</strong>. ")
      .append(
        oracle.equals("NOT_CHECKED")
          ? "Chưa kiểm chứng độc lập; không gán trạng thái ĐẠT."
          : "Xem phạm vi và chi tiết kiểm chứng trong tệp đi kèm."
      )
      .append(" ")
      .append(available(files, "verification.json", "oracle.json"))
      .append("</p>");
    if (files.contains("comparison.csv")) out
      .append("<p>Đã có đối chiếu Original/Tight: ")
      .append(link("comparison.csv"))
      .append(".</p>");
    out.append(
      "</section><footer>Số HTML làm tròn tối đa 6 chữ số thập phân để đọc; CSV/JSON giữ số gốc. Heap quan sát khác RSS/RAM tiến trình; số đo Java trên máy này không phải số đo C++ của tác giả. Không tái chạy thuật toán khi xuất.</footer></body></html>"
    );
    return out.toString();
  }

  private static void resources(StringBuilder out, Object value) {
    if (!(value instanceof Map<?, ?> resources)) return;
    tableStart(out, "Bảng tài nguyên · Đỉnh lấy mẫu", "Loại bộ nhớ", "MiB", "Ý nghĩa");
    row(
      out,
      "JVM heap",
      n(resources.get("heapUsedMiBPeak")),
      "Vùng heap của JVM chung, không phải RSS"
    );
    row(
      out,
      "RAM tiến trình Java",
      n(resources.get("processResidentMiBPeak")),
      "Resident memory nếu thiết bị hỗ trợ đo"
    );
    row(
      out,
      "RAM toàn máy đang dùng",
      n(resources.get("systemUsedMiBPeak")),
      "Gồm các ứng dụng khác"
    );
    tableEnd(out);
    if (!(resources.get("samples") instanceof List<?> samples)) return;
    out.append(
      "<details><summary>Mẫu tài nguyên theo thời gian · hiển thị tối đa 30 dòng đầu</summary>"
    );
    tableStart(
      out,
      "Bảng mẫu tài nguyên · Toàn bộ số liệu ở resource-samples.csv",
      "Thời điểm (ms)",
      "Heap (MiB)",
      "RAM Java (MiB)",
      "RAM máy dùng (MiB)",
      "RAM máy tổng (MiB)",
      "CPU Java (%)",
      "CPU máy (%)"
    );
    for (Object sample : samples.stream().limit(30).toList())
      if (sample instanceof Map<?, ?> map) row(
        out,
        n(map.get("elapsedMs")),
        n(map.get("heapUsedMiB")),
        n(map.get("processResidentMiB")),
        n(map.get("systemUsedMiB")),
        n(map.get("systemTotalMiB")),
        n(map.get("processCpuPercent")),
        n(map.get("systemCpuPercent"))
      );
    tableEnd(out);
    out.append("</details>");
  }

  private static String chart(AnalysisReport r, boolean runtime) {
    String title = runtime ? "Runtime theo batch" : "Heap quan sát theo batch";
    String unit = runtime ? "s" : "MiB";
    double max = r
      .batches()
      .stream()
      .mapToDouble(b -> runtime ? b.elapsedMs() / 1000 : b.peakMemoryMb())
      .max()
      .orElse(1);
    if (max <= 0) max = 1;
    StringBuilder svg = new StringBuilder("<figure><figcaption>")
      .append(title)
      .append("</figcaption><p>X: Batch · Y: ")
      .append(runtime ? "Runtime (s)" : "JVM heap quan sát (MiB)")
      .append("</p><svg role='img' aria-label='")
      .append(title)
      .append("' viewBox='0 0 620 280'>");
    for (int tick = 0; tick <= 4; tick++) {
      double y = 225 - tick * 47.5;
      svg
        .append("<path d='M80 ")
        .append(y)
        .append("H600' stroke='#dce4ed' stroke-dasharray='3 4'/><text x='72' y='")
        .append(y + 4)
        .append("' text-anchor='end'>")
        .append(n((max * tick) / 4))
        .append("</text>");
    }
    StringBuilder points = new StringBuilder();
    for (int i = 0; i < r.batches().size(); i++) {
      var b = r.batches().get(i);
      double x = r.batchCount() == 1 ? 330 : 110 + (450.0 * i) / (r.batchCount() - 1);
      double v = runtime ? b.elapsedMs() / 1000 : b.peakMemoryMb(),
        y = 225 - (v / max) * 190;
      points.append(x).append(',').append(y).append(' ');
      svg
        .append("<text x='")
        .append(x)
        .append("' y='253' text-anchor='middle'>")
        .append(h(b.name()))
        .append("</text>");
      if (runtime) svg
        .append("<circle cx='")
        .append(x)
        .append("' cy='")
        .append(y)
        .append("' r='4' fill='#245bc7'><title>")
        .append(n(v))
        .append(' ')
        .append(unit)
        .append("</title></circle>");
      else svg
        .append("<rect x='")
        .append(x - 18)
        .append("' y='")
        .append(y)
        .append("' width='36' height='")
        .append(225 - y)
        .append("' rx='3' fill='#245bc7'><title>")
        .append(n(v))
        .append(' ')
        .append(unit)
        .append("</title></rect>");
    }
    if (runtime) svg
      .append("<polyline points='")
      .append(points)
      .append("' fill='none' stroke='#245bc7' stroke-width='2'/>");
    return svg
      .append("</svg><p>Số chính xác ở Bảng 5. ")
      .append(
        runtime
          ? "Không gồm đọc/hash."
          : "Đỉnh quan sát tích lũy; không phải đỉnh riêng từng batch hoặc RSS."
      )
      .append("</p></figure>")
      .toString();
  }

  private static void section(StringBuilder out, String id, String title) {
    out
      .append("<section id='buoc-")
      .append(id)
      .append("'><h2><span>")
      .append(id)
      .append("</span> ")
      .append(title)
      .append("</h2>");
  }

  private static void tableStart(StringBuilder out, String caption, String... headers) {
    out
      .append("<div class='table-scroll'><table><caption>")
      .append(h(caption))
      .append("</caption><thead><tr>");
    for (String name : headers) out.append("<th scope='col'>").append(h(name)).append("</th>");
    out.append("</tr></thead><tbody>");
  }

  private static void row(StringBuilder out, String... cells) {
    out.append("<tr>");
    for (String cell : cells) out.append("<td>").append(cell).append("</td>");
    out.append("</tr>");
  }

  private static void tableEnd(StringBuilder out) {
    out.append("</tbody></table></div>");
  }

  private static void metric(
    StringBuilder out,
    String title,
    Object value,
    String unit,
    String scope
  ) {
    row(out, h(title), n(value) + (value instanceof Number ? " " + unit : ""), h(scope));
  }

  private static String badge(String text) {
    return "<span class='badge'>" + h(text) + "</span>";
  }

  private static String link(String name) {
    return "<a href='" + h(ExportGuide.path(name)) + "'>" + h(name) + "</a>";
  }

  private static String available(Collection<String> files, String name, String fallback) {
    return files.contains(name)
      ? link(name)
      : files.contains(fallback)
        ? link(fallback)
        : "Không có tệp chi tiết tương ứng";
  }

  private static String n(Object value) {
    if (
      !(value instanceof Number number) || !Double.isFinite(number.doubleValue())
    ) return "Chưa ghi nhận";
    return new DecimalFormat(
      "#,##0.######",
      DecimalFormatSymbols.getInstance(Locale.forLanguageTag("vi-VN"))
    ).format(number);
  }

  private static String h(Object value) {
    return String.valueOf(value)
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;");
  }

  private static final String CSS = """
  *{box-sizing:border-box}html{scroll-behavior:smooth}body{font:15px/1.6 system-ui,sans-serif;color:#20364e;background:#f4f6f9;max-width:1200px;margin:0 auto;padding:32px 24px}h1{font-size:29px;line-height:1.25;margin:6px 0}h2{font-size:20px;margin:0 0 16px}h2 span,.eyebrow{color:#476384;font-size:12px;letter-spacing:.08em}.eyebrow{font-weight:700}header{margin-bottom:24px}header p,footer,figure p{color:#53667b}section,.start{background:white;border:1px solid #dce4ed;border-radius:12px;padding:24px;margin:20px 0}section{scroll-margin-top:16px}.start{border-left:4px solid #245bc7}nav,.file-links{display:flex;gap:8px;flex-wrap:wrap}nav a,.file-links a{padding:8px 12px;border:1px solid #d7e0eb;border-radius:7px;text-decoration:none;background:white}a{color:#2057b3}a:focus-visible,summary:focus-visible{outline:3px solid #245bc7;outline-offset:3px}details{margin-top:18px}summary{cursor:pointer;font-weight:600}.basis{background:#f0f4fa;padding:14px;border-radius:7px}.table-scroll{overflow-x:auto;margin:16px 0}table{width:100%;border-collapse:collapse;font-size:14px;font-variant-numeric:tabular-nums}caption{text-align:left;font-weight:650;padding:0 0 10px;color:#294564}th,td{padding:11px 14px;text-align:right;border-bottom:1px solid #e5eaf0;white-space:nowrap}th{background:#f1f4f8;color:#48617c}th:first-child,td:first-child{text-align:left}tbody tr:nth-child(even){background:#fafbfd}td:last-child{white-space:normal;min-width:140px}code{overflow-wrap:anywhere;white-space:normal;font-size:12px}.files th,.files td{text-align:left;white-space:normal}.badge{background:#edf3fa;padding:3px 8px;border-radius:5px;white-space:nowrap}.chart-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}figure{margin:0;border:1px solid #dce4ed;border-radius:8px;padding:16px}figcaption{font-weight:650}svg{width:100%;height:auto}svg text{font:12px system-ui;fill:#53667b}.conclusion{display:flex;align-items:baseline;gap:20px;flex-wrap:wrap;background:#edf5f1;padding:18px;border-radius:8px}.conclusion strong{font-size:27px;color:#176448}footer{font-size:13px;padding:12px 0 28px}@media(max-width:740px){body{padding:18px 12px}section,.start{padding:18px}.chart-grid{grid-template-columns:1fr}}@media print{body{background:white;max-width:none;padding:0;font-size:11px}nav,details{display:none}section{break-inside:avoid;padding:12px;margin:12px 0}.table-scroll{overflow:visible}th,td{white-space:normal;padding:5px}a{color:inherit;text-decoration:none}footer{font-size:10px}}
  """;
}
