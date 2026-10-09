package piHAUPA;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Human-readable TXT plus flat CSV; shared by desktop, CLI and web. */
public final class ExportBundle {
    private ExportBundle() { }

    /** Báo cáo PIHAUPA Tight duy nhất; giữ các overload cũ để đọc báo cáo lịch sử. */
    public static void writeDirectory(InputData input, AnalysisReport report,
            Path directory, String sourceName) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("BAO_CAO.txt"), reportText(input, report, sourceName),
                StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("bao-cao.csv"), reportCsv(input, report, sourceName),
                StandardCharsets.UTF_8);
        InputWriter.writeCombined(input, directory.resolve("pihaupa-input.txt"));
        Files.writeString(directory.resolve("patterns.csv"), ReportWriter.patternsCsv(report),
                StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("performance-by-rows.csv"), ReportWriter.performanceCsv(report),
                StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("upper-bounds.csv"), upperBoundsCsv(report), StandardCharsets.UTF_8);
    }

    public static String reportText(InputData input, AnalysisReport report, String sourceName) {
        return reportText(input.externalUtilities(), report, sourceName);
    }

    /** Chỉ cần EU và kết quả đã tính; không giữ lại toàn bộ giao dịch khi xuất từ web. */
    static String reportText(Map<String, Double> externalUtilities, AnalysisReport report, String sourceName) {
        AnalysisReport.BatchView last = report.batches().get(report.batches().size() - 1);
        Map.Entry<String, Double> strongest = report.upperBounds().entrySet().stream()
                .max(Map.Entry.comparingByValue()).orElse(null);
        StringBuilder out = new StringBuilder("BÁO CÁO PIHAUPA TIGHT\n==============================\n");
        out.append("Nguồn: ").append(safeSource(sourceName)).append('\n')
                .append("Giao dịch: ").append(report.transactionCount()).append(" | Batch: ")
                .append(report.batchCount()).append(" | Item: ").append(externalUtilities.size()).append('\n')
                .append("Su = ").append(n(report.upperThreshold())).append(" | Sl = ")
                .append(n(report.lowerThreshold())).append("\n\n")
                .append("1. KẾT LUẬN SAU BATCH CUỐI\n")
                .append("- TU(DB) = ").append(n(last.totalTransactionUtility())).append('\n')
                .append("- Upper = Su × TU(DB) = ").append(n(last.minUtilUpper())).append('\n')
                .append("- Lower = Sl × TU(DB) = ").append(n(last.minUtilLower())).append('\n')
                .append("- HAUP/LARGE: ").append(last.largePatterns().size())
                .append("; PRE-LARGE đang quản lý: ").append(last.preLargePatterns().size())
                .append("; re-scan: ").append(report.stats().rescanCount()).append(" lần.\n")
                .append("- DB0 là quét khởi tạo, không tính là re-scan.\n");
        if (strongest != null) {
            out.append("- AUUB lớn nhất: item ").append(strongest.getKey()).append(" = ")
                    .append(n(strongest.getValue())).append(". ");
            if (strongest.getValue() < last.minUtilLower())
                out.append("Thấp hơn Lower: cận trên loại cả LARGE và PRE-LARGE.\n");
            else if (strongest.getValue() < last.minUtilUpper())
                out.append("Thấp hơn Upper: không thể có HAUP; PRE-LARGE vẫn cần xét AU.\n");
            else out.append("Cận trên chưa kết luận mẫu đạt ngưỡng; phải tính AU thực.\n");
        }
        out.append("\n2. CÁCH ĐỌC CÁC BƯỚC\n")
                .append("- Bước 1: đọc giao dịch, tính utility item, TU và MU.\n")
                .append("- Bước 2: tính TU(DB), Upper và Lower từ Su/Sl của lượt chạy.\n")
                .append("- Bước 3: với batch mới, so hai vế Eq.(12) để quyết định re-scan.\n")
                .append("- Bước 4: khởi tạo/quét lại và mở rộng mẫu, hoặc chỉ cập nhật cây mẫu đang quản lý.\n")
                .append("- Bước 5: tính AU(P)=sumU(P)/|P| và phân loại LARGE, PRE-LARGE, SMALL.\n")
                .append("- OD: dữ liệu ở lần quét toàn bộ gần nhất; ID: dữ liệu mới tích lũy từ lần đó.\n")
                .append("- Không re-scan KHÔNG có nghĩa là bỏ qua batch mới: chương trình vẫn đọc batch và cập nhật cây.\n")
                .append("\n3. PHÉP TÍNH VÀ QUYẾT ĐỊNH THEO TỪNG BATCH\n");
        for (AnalysisReport.BatchView b : report.batches()) {
            double oldTu = b.totalTransactionUtility() - b.accumulatedTransactionUtility();
            double left = b.accumulatedMaximumUtility() - report.upperThreshold() * b.accumulatedTransactionUtility();
            double right = (report.upperThreshold() - report.lowerThreshold()) * oldTu;
            out.append("\n[").append(b.name()).append("] ").append(b.index() == 0 ? "DỮ LIỆU GỐC" : "BATCH MỚI").append('\n')
                    .append("Bước 1 - Đọc dữ liệu\n")
                    .append("- Giao dịch trong batch: ").append(b.transactionCount())
                    .append("; giao dịch tích lũy: ").append(b.cumulativeTransactionCount()).append(".\n")
                    .append("- U(i,T)=IU(i,T)×EU(i); TU(T)=tổng U(i,T); MU(T)=U(i,T) lớn nhất.\n")
                    .append("- TU(batch)=Σ TU(T)=").append(n(b.batchTransactionUtility()))
                    .append("; MU(batch)=Σ MU(T)=").append(n(b.batchMaximumUtility())).append(".\n")
                    .append("Bước 2 - Tính ngưỡng\n")
                    .append("- TU(DB)=").append(n(b.totalTransactionUtility())).append(".\n")
                    .append("- Upper=").append(n(report.upperThreshold())).append(" × ")
                    .append(n(b.totalTransactionUtility())).append(" = ").append(n(b.minUtilUpper())).append(".\n")
                    .append("- Lower=").append(n(report.lowerThreshold())).append(" × ")
                    .append(n(b.totalTransactionUtility())).append(" = ").append(n(b.minUtilLower())).append(".\n")
                    .append("Bước 3 - Quyết định re-scan\n");
            if (b.index() == 0) out.append("- DB0 là quét khởi tạo; Eq.(12) chưa áp dụng.\n");
            else out.append("- TU(ID)=").append(n(b.accumulatedTransactionUtility()))
                    .append("; MU(ID)=").append(n(b.accumulatedMaximumUtility()))
                    .append("; TU(OD)=").append(n(oldTu)).append(".\n")
                    .append("- Vế trái = MU(ID) − Su×TU(ID) = ").append(n(left)).append(".\n")
                    .append("- Vế phải = (Su−Sl)×TU(OD) = ").append(n(right)).append(".\n")
                    .append("- So sánh: ").append(n(left)).append(b.rescanTriggered() ? " ≥ " : " < ")
                    .append(n(right)).append(" → ")
                    .append(b.rescanTriggered() ? "CÓ re-scan toàn bộ dữ liệu." : "KHÔNG re-scan dữ liệu cũ.")
                    .append("\n");
            out.append("Bước 4 - Khai phá/cập nhật\n")
                    .append("- ").append(b.index() == 0 ? "Quét DB0, dựng PIHAUP-List và mở rộng mẫu ban đầu."
                            : b.rescanTriggered() ? "Quét lại toàn bộ dữ liệu tích lũy, dựng lại PIHAUP-List và mở rộng mẫu."
                            : "Chỉ quét batch mới để cập nhật các mẫu đã quản lý; không quét lại OD.")
                    .append('\n')
                    .append("Bước 5 - Phân loại mẫu\n")
                    .append("- LARGE/HAUP nếu AU≥Upper; PRE-LARGE nếu Lower≤AU<Upper; còn lại SMALL.\n")
                    .append("- LARGE/HAUP (").append(b.largePatterns().size()).append("):\n");
            patternList(out, b.largePatterns());
            out.append("- PRE-LARGE đang quản lý (").append(b.preLargePatterns().size()).append("):\n");
            patternList(out, b.preLargePatterns());
            out.append("- Đo lường batch: ").append(n(b.elapsedMs())).append(" ms; heap sau batch=")
                    .append(n(b.memoryAfterMb())).append(" MiB; peak quan sát=")
                    .append(n(b.peakMemoryMb())).append(" MiB.\n");
        }
        out.append("\n4. ĐO LƯỜNG TOÀN LƯỢT\n- Thời gian thuật toán: ")
                .append(n(report.elapsedNanos() / 1e6)).append(" ms\n- JVM heap peak quan sát: ")
                .append(n(report.peakMemoryBytes() / 1048576.0)).append(" MiB\n- Re-scan: ")
                .append(report.stats().rescanCount()).append("; mẫu duyệt: ")
                .append(report.stats().patternsVisited()).append("; node kết hợp: ")
                .append(report.stats().combinedNodes()).append('\n')
                .append("Lưu ý: thời gian không gồm đọc tệp/render UI; heap là mẫu quan sát, không phải RSS/đỉnh chính xác.\n")
                .append("PRE-LARGE là ứng viên đang quản lý; khi không re-scan có thể không đầy đủ theo vét cạn.\n")
                .append("\n5. PHỤ LỤC: EXTERNAL UTILITY\n");
        externalUtilities.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.append("- ").append(e.getKey()).append(" = ").append(n(e.getValue())).append('\n'));
        out.append("\n6. PHỤ LỤC: AUUB TỪNG ITEM (20 GIÁ TRỊ LỚN NHẤT; xem CSV để có đủ)\n");
        report.upperBounds().entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue(
                java.util.Comparator.reverseOrder())).limit(20)
                .forEach(e -> out.append("- ").append(e.getKey()).append(" = ").append(n(e.getValue())).append('\n'));
        return out.toString();
    }

    public static String reportCsv(InputData input, AnalysisReport report, String sourceName) {
        return reportCsv(input.externalUtilities(), report, sourceName);
    }

    /** CSV dùng cùng số liệu đã phân tích; không cần parse hay mine lại input. */
    static String reportCsv(Map<String, Double> externalUtilities, AnalysisReport report, String sourceName) {
        StringBuilder csv = new StringBuilder("step,batch,metric,pattern,type,value,unit,description\n");
        AnalysisReport.BatchView last = report.batches().get(report.batches().size() - 1);
        stepRow(csv, "SUMMARY", "ALL", "source", "", "", safeSource(sourceName), "", "Tên tệp đầu vào");
        stepRow(csv, "SUMMARY", "ALL", "Su", "", "", n(report.upperThreshold()), "ratio", "Ngưỡng trên do người dùng chọn");
        stepRow(csv, "SUMMARY", "ALL", "Sl", "", "", n(report.lowerThreshold()), "ratio", "Ngưỡng dưới do người dùng chọn");
        stepRow(csv, "SUMMARY", "ALL", "transactionCount", "", "", "" + report.transactionCount(), "rows", "Tổng giao dịch");
        stepRow(csv, "SUMMARY", "ALL", "itemCount", "", "", "" + externalUtilities.size(), "items", "Số item có external utility");
        stepRow(csv, "SUMMARY", "ALL", "finalTotalTU", "", "", n(last.totalTransactionUtility()), "utility", "TU(DB) sau batch cuối");
        stepRow(csv, "SUMMARY", "ALL", "finalUpper", "", "", n(last.minUtilUpper()), "utility", "Su × TU(DB) sau batch cuối");
        stepRow(csv, "SUMMARY", "ALL", "finalLower", "", "", n(last.minUtilLower()), "utility", "Sl × TU(DB) sau batch cuối");
        stepRow(csv, "SUMMARY", "ALL", "finalLargeCount", "", "", "" + last.largePatterns().size(), "patterns", "Số HAUP/LARGE cuối");
        stepRow(csv, "SUMMARY", "ALL", "finalPreLargeCount", "", "", "" + last.preLargePatterns().size(), "patterns", "Số PRE-LARGE đang quản lý cuối");
        stepRow(csv, "SUMMARY", "ALL", "rescanCount", "", "", "" + report.stats().rescanCount(), "times", "Không tính lần quét khởi tạo DB0");
        stepRow(csv, "SUMMARY", "ALL", "algorithmElapsedMs", "", "", n(report.elapsedNanos() / 1e6), "ms", "Thời gian thuật toán; không gồm đọc tệp và render giao diện");
        stepRow(csv, "SUMMARY", "ALL", "heapPeakMiB", "", "", n(report.peakMemoryBytes() / 1048576.0), "MiB", "JVM heap peak quan sát; không phải RSS");
        for (AnalysisReport.BatchView b : report.batches()) {
            String batch = b.name();
            stepRow(csv, "Bước 1", batch, "stepLabel", "", "", "Bước 1 - Đọc dữ liệu", "", "U(i,T)=IU(i,T)×EU(i); TU(T)=tổng U; MU(T)=U lớn nhất");
            stepRow(csv, "Bước 1", batch, "transactionCount", "", "", "" + b.transactionCount(), "rows", "Số giao dịch trong batch");
            stepRow(csv, "Bước 1", batch, "cumulativeTransactionCount", "", "", "" + b.cumulativeTransactionCount(), "rows", "Số giao dịch tích lũy");
            stepRow(csv, "Bước 1", batch, "batchTU", "", "", n(b.batchTransactionUtility()), "utility", "TU(batch)=Σ TU(T)");
            stepRow(csv, "Bước 1", batch, "batchMU", "", "", n(b.batchMaximumUtility()), "utility", "MU(batch)=Σ MU(T)");
            stepRow(csv, "Bước 2", batch, "stepLabel", "", "", "Bước 2 - Tính ngưỡng", "", "Upper=Su×TU(DB); Lower=Sl×TU(DB)");
            stepRow(csv, "Bước 2", batch, "totalTU", "", "", n(b.totalTransactionUtility()), "utility", "TU(DB) tích lũy");
            stepRow(csv, "Bước 2", batch, "upper", "", "", n(b.minUtilUpper()), "utility", n(report.upperThreshold()) + " × " + n(b.totalTransactionUtility()));
            stepRow(csv, "Bước 2", batch, "lower", "", "", n(b.minUtilLower()), "utility", n(report.lowerThreshold()) + " × " + n(b.totalTransactionUtility()));
            stepRow(csv, "Bước 3", batch, "stepLabel", "", "", "Bước 3 - Quyết định re-scan", "", "Eq.(12): MU(ID)-Su×TU(ID) ≥ (Su-Sl)×TU(OD)");
            stepRow(csv, "Bước 3", batch, "TU(ID)", "", "", n(b.accumulatedTransactionUtility()), "utility", "Dữ liệu mới tích lũy từ lần quét toàn bộ gần nhất");
            stepRow(csv, "Bước 3", batch, "MU(ID)", "", "", n(b.accumulatedMaximumUtility()), "utility", "Dữ liệu mới tích lũy từ lần quét toàn bộ gần nhất");
            if (b.index() > 0) {
                double oldTu = b.totalTransactionUtility() - b.accumulatedTransactionUtility();
                double left = b.accumulatedMaximumUtility() - report.upperThreshold() * b.accumulatedTransactionUtility();
                double right = (report.upperThreshold() - report.lowerThreshold()) * oldTu;
                stepRow(csv, "Bước 3", batch, "TU(OD)", "", "", n(oldTu), "utility", "Dữ liệu ở lần quét toàn bộ gần nhất");
                stepRow(csv, "Bước 3", batch, "Eq12Left", "", "", n(left), "utility", n(b.accumulatedMaximumUtility()) + " − " + n(report.upperThreshold()) + " × " + n(b.accumulatedTransactionUtility()));
                stepRow(csv, "Bước 3", batch, "Eq12Right", "", "", n(right), "utility", "(" + n(report.upperThreshold()) + " − " + n(report.lowerThreshold()) + ") × " + n(oldTu));
                stepRow(csv, "Bước 3", batch, "Eq12Comparison", "", "", b.rescanTriggered() ? ">=" : "<", "", n(left) + (b.rescanTriggered() ? " ≥ " : " < ") + n(right));
            }
            stepRow(csv, "Bước 3", batch, "rescan", "", "", "" + b.rescanTriggered(), "", b.action());
            stepRow(csv, "Bước 3", batch, "rescanDecision", "", "", b.index() == 0 ? "KHỞI TẠO DB0"
                    : b.rescanTriggered() ? "CÓ re-scan toàn bộ dữ liệu" : "KHÔNG re-scan dữ liệu cũ", "",
                    b.index() == 0 ? "Eq.(12) chưa áp dụng" : "Không re-scan vẫn đọc và xử lý batch mới");
            stepRow(csv, "Bước 4", batch, "stepLabel", "", "", "Bước 4 - Khai phá/cập nhật", "", "DB0 quét khởi tạo; batch mới có thể quét lại hoặc chỉ cập nhật mẫu đã quản lý");
            stepRow(csv, "Bước 4", batch, "action", "", "", b.action(), "", b.index() == 0
                    ? "Quét khởi tạo DB0, không tính là re-scan" : b.rescanTriggered()
                    ? "Quét lại toàn bộ dữ liệu tích lũy" : "Vẫn đọc batch mới; không quét lại dữ liệu cũ OD");
            stepRow(csv, "Bước 5", batch, "stepLabel", "", "", "Bước 5 - Phân loại mẫu", "", "AU(P)=sumU(P)/|P|; LARGE nếu AU≥Upper; PRE-LARGE nếu Lower≤AU<Upper; còn lại SMALL");
            stepRow(csv, "Bước 5", batch, "largeCount", "", "", "" + b.largePatterns().size(), "patterns", "Số HAUP/LARGE sau batch");
            stepRow(csv, "Bước 5", batch, "preLargeCount", "", "", "" + b.preLargePatterns().size(), "patterns", "Số PRE-LARGE đang quản lý sau batch");
            for (PatternResult p : b.largePatterns())
                stepRow(csv, "Bước 5", batch, "AU", p.displayName(), "LARGE", n(p.averageUtility()), "utility", "AU=sumU/|P|; sumU=" + n(p.sumUtility()) + "; |P|=" + p.length());
            for (PatternResult p : b.preLargePatterns())
                stepRow(csv, "Bước 5", batch, "AU", p.displayName(), "PRE-LARGE", n(p.averageUtility()), "utility", "AU=sumU/|P|; sumU=" + n(p.sumUtility()) + "; |P|=" + p.length());
            stepRow(csv, "Bước 5", batch, "elapsedMs", "", "", n(b.elapsedMs()), "ms", "Thời gian thuật toán của batch");
            stepRow(csv, "Bước 5", batch, "heapAfterMb", "", "", n(b.memoryAfterMb()), "MiB", "JVM heap quan sát sau batch");
            stepRow(csv, "Bước 5", batch, "peakHeapMiB", "", "", n(b.peakMemoryMb()), "MiB", "JVM heap peak quan sát đến batch này");
        }
        stepRow(csv, "SUMMARY", "ALL", "patternsVisited", "", "", "" + report.stats().patternsVisited(), "patterns", "Số mẫu đã duyệt khi khai phá");
        stepRow(csv, "SUMMARY", "ALL", "combinedNodes", "", "", "" + report.stats().combinedNodes(), "nodes", "Số node kết hợp");
        for (Map.Entry<String, Double> e : externalUtilities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList())
            stepRow(csv, "APPENDIX", "ALL", "EU", e.getKey(), "", n(e.getValue()), "utility", "External utility của item");
        report.upperBounds().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> stepRow(csv, "APPENDIX", "ALL", "AUUB", e.getKey(), "",
                        n(e.getValue()), "utility", "Σ MU(T) của giao dịch chứa item; cận trên, không phải AU"));
        return csv.toString();
    }

    private static void stepRow(StringBuilder out, String... cells) { row(out, cells); }

    private static String upperBoundsCsv(AnalysisReport report) {
        StringBuilder csv = new StringBuilder("item,auub,upper,lower\n");
        AnalysisReport.BatchView last = report.batches().get(report.batches().size() - 1);
        report.upperBounds().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                row(csv, e.getKey(), n(e.getValue()), n(last.minUtilUpper()), n(last.minUtilLower())));
        return csv.toString();
    }

    /**
     * DESIGN PATTERN: phương thức này đóng vai trò Facade cho xuất báo cáo.
     * Bên gọi đưa dữ liệu, báo cáo, thư mục và tên nguồn; phương thức phối hợp
     * các bộ ghi để xuất TXT, CSV, input chuẩn hóa và bảng thống kê.
     * Mục đích: Desktop/CLI/Web không phải lặp lại các bước xuất từng loại file.
     * Tối ưu hiện có: các định dạng đọc cùng ComparisonReport đã tính, không chạy
     * mining lại. Khi gọi từ Desktop, I/O được thực hiện trong SwingWorker.
     * Lưu ý: ghi tuần tự, chưa bảo đảm cả bộ file được thay thế một cách nguyên tử;
     * lỗi I/O giữa chừng có thể để lại một số file đã ghi. Chưa đo tốc độ xuất riêng.
     */
    public static void writeDirectory(InputData input, ComparisonReport comparison,
            Path directory, String sourceName) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("BAO_CAO.txt"), reportText(input, comparison, sourceName),
                StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("bao-cao.csv"), reportCsv(input, comparison, sourceName),
                StandardCharsets.UTF_8);
        InputWriter.writeCombined(input, directory.resolve("pihaupa-input.txt"));
        Files.writeString(directory.resolve("patterns.csv"), ReportWriter.patternsCsv(comparison.tight()),
                StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("performance-by-rows.csv"),
                ReportWriter.performanceCsv(comparison.tight()), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("comparison.csv"), ReportWriter.comparisonCsv(comparison),
                StandardCharsets.UTF_8);
    }

    public static String reportText(InputData input, ComparisonReport comparison, String sourceName) {
        AnalysisReport tight = comparison.tight();
        AnalysisReport original = comparison.original();
        AnalysisReport.BatchView last = tight.batches().get(tight.batches().size() - 1);
        StringBuilder out = new StringBuilder();
        out.append("BÁO CÁO PIHAUPA\n==============================\n")
                .append("Nguồn: ").append(safeSource(sourceName)).append('\n')
                .append("Giao dịch: ").append(tight.transactionCount()).append(" | Batch: ")
                .append(tight.batchCount()).append(" | Item: ").append(input.externalUtilities().size()).append('\n')
                .append("Su = ").append(n(input.upperThreshold())).append(" | Sl = ")
                .append(n(input.lowerThreshold())).append("\n\n")
                .append("1. KẾT LUẬN\n")
                .append("HAUP cuối Original/Tight: ").append(comparison.sameHaups() ? "TRÙNG KHỚP" : "KHÁC NHAU")
                .append("\nLARGE / HAUP cuối: ").append(last.largePatterns().size())
                .append(" | PRE-LARGE đang quản lý: ").append(last.preLargePatterns().size()).append('\n');
        patternList(out, last.largePatterns());
        out.append("\n2. CÔNG THỨC VÀ NGƯỠNG\n")
                .append("U(P,T) = tổng IU(i,T) × EU(i); AU(P) = sumU(P) / |P|.\n")
                .append("Upper = Su × TU(DB); Lower = Sl × TU(DB).\n")
                .append("LARGE nếu AU >= Upper; PRE-LARGE nếu Lower <= AU < Upper; còn lại SMALL.\n")
                .append("Tight re-scan: MU(ID) - Su×TU(ID) >= (Su-Sl)×TU(OD).\n")
                .append("OD: dữ liệu tại lần full scan gần nhất; ID: dữ liệu mới tích lũy.\n\n")
                .append("3. SO SÁNH THUẬT TOÁN\n");
        compare(out, "Re-scan", original.stats().rescanCount(), tight.stats().rescanCount(), "lần");
        compare(out, "Pattern duyệt", original.stats().patternsVisited(), tight.stats().patternsVisited(), "mẫu");
        compare(out, "Node kết hợp", original.stats().combinedNodes(), tight.stats().combinedNodes(), "node");
        compare(out, "Thời gian", original.elapsedNanos() / 1e6, tight.elapsedNanos() / 1e6, "ms");
        compare(out, "JVM heap peak", original.peakMemoryBytes() / 1048576.0,
                tight.peakMemoryBytes() / 1048576.0, "MB");
        out.append("\n4. EXTERNAL UTILITY\n");
        input.externalUtilities().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.append("  ").append(e.getKey()).append(" = ").append(n(e.getValue())).append('\n'));
        out.append("\n5. DIỄN TIẾN THEO BATCH (TIGHT)\n");
        for (AnalysisReport.BatchView batch : tight.batches()) {
            out.append("\n[").append(batch.name()).append("] ").append(batch.action()).append('\n')
                    .append("Giao dịch: ").append(batch.transactionCount())
                    .append(" | TU batch: ").append(n(batch.batchTransactionUtility()))
                    .append(" | MU batch: ").append(n(batch.batchMaximumUtility())).append('\n')
                    .append("TU tích lũy: ").append(n(batch.totalTransactionUtility()))
                    .append(" | Upper: ").append(n(batch.minUtilUpper()))
                    .append(" | Lower: ").append(n(batch.minUtilLower())).append('\n');
            if (batch.tightRescanLimit() != null) {
                double oldTu = batch.totalTransactionUtility() - batch.accumulatedTransactionUtility();
                double left = batch.accumulatedMaximumUtility()
                        - input.upperThreshold() * batch.accumulatedTransactionUtility();
                double right = (input.upperThreshold() - input.lowerThreshold()) * oldTu;
                out.append("Re-scan Eq.(12): ").append(n(left))
                        .append(batch.rescanTriggered() ? " >= " : " < ").append(n(right))
                        .append(" | TU(ID): ").append(n(batch.accumulatedTransactionUtility()))
                        .append(" | MU(ID): ").append(n(batch.accumulatedMaximumUtility()))
                        .append(" | TU(OD): ").append(n(oldTu)).append('\n');
            }
            out.append("Thời gian batch: ").append(n(batch.elapsedMs())).append(" ms")
                    .append(" | Heap sau batch: ").append(n(batch.memoryAfterMb())).append(" MB\n")
                    .append("LARGE (mẫu, AU, sumU, |P|):\n");
            patternList(out, batch.largePatterns());
            out.append("PRE-LARGE đang quản lý (mẫu, AU, sumU, |P|):\n");
            patternList(out, batch.preLargePatterns());
        }
        out.append("\nGHI CHÚ\n")
                .append("PRE-LARGE là ứng viên trong pattern tree; khi không re-scan có thể thiếu tập pre-large theo vét cạn.\n")
                .append("Thời gian và heap là phép đo lần chạy, có thể thay đổi giữa các lần chạy.\n")
                .append("pihaupa-input.txt chứa toàn bộ giao dịch và IU; các CSV chứa dữ liệu dạng bảng.\n");
        return out.toString();
    }

    public static String reportCsv(InputData input, ComparisonReport comparison, String sourceName) {
        AnalysisReport tight = comparison.tight();
        AnalysisReport original = comparison.original();
        StringBuilder csv = new StringBuilder("section,batch,metric,pattern,type,value,unit,originalEq5,tightEq6,description\n");
        row(csv, "input", "", "source", "", "", safeSource(sourceName), "", "", "", "Input file name");
        row(csv, "input", "", "Su", "", "", n(input.upperThreshold()), "ratio", "", "", "Upper threshold");
        row(csv, "input", "", "Sl", "", "", n(input.lowerThreshold()), "ratio", "", "", "Lower threshold");
        row(csv, "input", "", "transactionCount", "", "", Integer.toString(tight.transactionCount()), "rows", "", "", "All batches");
        for (Map.Entry<String, Double> e : input.externalUtilities().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            row(csv, "externalUtility", "", "EU", e.getKey(), "", n(e.getValue()), "utility", "", "", "Per item");
        }
        row(csv, "comparison", "", "sameFinalHaups", "", "", Boolean.toString(comparison.sameHaups()), "", "", "", "Final LARGE sets match");
        compareCsv(csv, "rescanCount", original.stats().rescanCount(), tight.stats().rescanCount(), "times");
        compareCsv(csv, "patternsVisited", original.stats().patternsVisited(), tight.stats().patternsVisited(), "patterns");
        compareCsv(csv, "combinedNodes", original.stats().combinedNodes(), tight.stats().combinedNodes(), "nodes");
        compareCsv(csv, "elapsedMs", original.elapsedNanos() / 1e6, tight.elapsedNanos() / 1e6, "ms");
        compareCsv(csv, "peakMemoryMb", original.peakMemoryBytes() / 1048576.0,
                tight.peakMemoryBytes() / 1048576.0, "MB");
        for (AnalysisReport.BatchView batch : tight.batches()) {
            String b = batch.name();
            row(csv, "batch", b, "transactionCount", "", "", Integer.toString(batch.transactionCount()), "rows", "", "", "Rows in batch");
            row(csv, "batch", b, "batchTU", "", "", n(batch.batchTransactionUtility()), "utility", "", "", "TU of batch");
            row(csv, "batch", b, "batchMU", "", "", n(batch.batchMaximumUtility()), "utility", "", "", "MU of batch");
            row(csv, "batch", b, "totalTU", "", "", n(batch.totalTransactionUtility()), "utility", "", "", "Cumulative TU");
            row(csv, "batch", b, "upper", "", "", n(batch.minUtilUpper()), "utility", "", "", "Su times total TU");
            row(csv, "batch", b, "lower", "", "", n(batch.minUtilLower()), "utility", "", "", "Sl times total TU");
            row(csv, "batch", b, "rescanTriggered", "", "", Boolean.toString(batch.rescanTriggered()), "", "", "", batch.action());
            if (batch.tightRescanLimit() != null) {
                double oldTu = batch.totalTransactionUtility() - batch.accumulatedTransactionUtility();
                double left = batch.accumulatedMaximumUtility()
                        - input.upperThreshold() * batch.accumulatedTransactionUtility();
                double right = (input.upperThreshold() - input.lowerThreshold()) * oldTu;
                row(csv, "batch", b, "tightEq12Left", "", "", n(left), "utility", "", "", "MU(ID)-Su*TU(ID)");
                row(csv, "batch", b, "tightEq12Right", "", "", n(right), "utility", "", "", "(Su-Sl)*TU(OD)");
            }
            row(csv, "batch", b, "elapsedMs", "", "", n(batch.elapsedMs()), "ms", "", "", "Observed batch time");
            row(csv, "batch", b, "memoryAfterMb", "", "", n(batch.memoryAfterMb()), "MB", "", "", "JVM heap after batch");
            patternsCsv(csv, b, "LARGE", batch.largePatterns());
            patternsCsv(csv, b, "PRE-LARGE", batch.preLargePatterns());
        }
        return csv.toString();
    }

    private static void patternsCsv(StringBuilder csv, String batch, String type, List<PatternResult> patterns) {
        for (PatternResult pattern : patterns) {
            String description = "sumU=" + n(pattern.sumUtility()) + "; length=" + pattern.length();
            row(csv, "pattern", batch, "AU", pattern.displayName(), type,
                    n(pattern.averageUtility()), "utility", "", "", description);
        }
    }

    private static void compareCsv(StringBuilder csv, String metric, double oldValue, double tightValue, String unit) {
        row(csv, "comparison", "", metric, "", "", "", unit, n(oldValue), n(tightValue), "Original Eq.(5) vs Tight Eq.(6)");
    }

    private static void row(StringBuilder out, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) out.append(',');
            out.append('"').append(cells[i].replace("\"", "\"\"")).append('"');
        }
        out.append('\n');
    }

    private static void patternList(StringBuilder out, List<PatternResult> patterns) {
        if (patterns.isEmpty()) {
            out.append("  (không có)\n");
            return;
        }
        for (PatternResult p : patterns) {
            out.append("  ").append(p.displayName()).append(" | AU=").append(n(p.averageUtility()))
                    .append(" | sumU=").append(n(p.sumUtility())).append(" | |P|=")
                    .append(p.length()).append('\n');
        }
    }

    private static void compare(StringBuilder out, String name, double oldValue, double tightValue, String unit) {
        out.append(name).append(": Original=").append(n(oldValue)).append(' ').append(unit)
                .append(" | Tight=").append(n(tightValue)).append(' ').append(unit).append('\n');
    }

    private static String n(double value) { return String.format(Locale.US, "%.6f", value); }

    private static String safeSource(String source) {
        String name = source == null ? "Dữ liệu chưa đặt tên" : source.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", " ").trim();
        return name.isEmpty() ? "Dữ liệu chưa đặt tên" : name.substring(0, Math.min(120, name.length()));
    }
}
