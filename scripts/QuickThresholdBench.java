import piHAUPA.*;
import vn.edu.dlu.pihaupa.application.DatasetProfile;
import vn.edu.dlu.pihaupa.support.Json;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.security.*;
import java.util.*;

/** Đo toàn tệp, không lấy mẫu giao dịch, không giới hạn số kết quả. */
public final class QuickThresholdBench {
    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]);
        String name = file.getParent().getFileName().toString();
        DatasetProfile profile = DatasetProfile.find(name, Files.size(file));
        if (profile == null) throw new IllegalArgumentException("Tệp chưa có profile đã kiểm tra: " + name);
        double ratio = profile.singletonAU() / profile.totalTU();
        double upperFraction = args.length > 1 ? Double.parseDouble(args[1]) : 0.99;
        double lowerFraction = args.length > 2 ? Double.parseDouble(args[2]) : 0.98;
        if (!(0 < lowerFraction && lowerFraction < upperFraction && upperFraction <= 1))
            throw new IllegalArgumentException("Cần 0 < lowerFraction < upperFraction ≤ 1");
        double su = ratio * upperFraction, sl = ratio * lowerFraction;
        long start = System.nanoTime();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputData input;
        try (var reader = new BufferedReader(new InputStreamReader(
                new DigestInputStream(Files.newInputStream(file), digest), StandardCharsets.UTF_8), 65536)) {
            input = ArchiveDatasetReader.readTransactionsText(reader, name, su, sl, ignored -> {});
        }
        double readMs = (System.nanoTime() - start) / 1e6;
        AnalysisReport report = AnalysisReport.run(input);
        var last = report.batches().get(report.batchCount() - 1);
        if (report.transactionCount() != profile.rows() || last.totalTransactionUtility() != profile.totalTU())
            throw new AssertionError("Profile không khớp dữ liệu: " + name);
        if (last.largePatterns().stream().noneMatch(p -> p.items().equals(List.of(profile.strongestItem()))
                && p.averageUtility() == profile.singletonAU()))
            throw new AssertionError("Thiếu item mạnh nhất ở kết quả cuối: " + name);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("sha256", HexFormat.of().formatHex(digest.digest()));
        result.put("rows", report.transactionCount());
        result.put("su", su);
        result.put("sl", sl);
        result.put("readMs", readMs);
        result.put("algorithmMs", report.elapsedNanos() / 1e6);
        result.put("totalMs", (System.nanoTime() - start) / 1e6);
        result.put("haups", last.largePatterns().size());
        result.put("preLarge", last.preLargePatterns().size());
        result.put("visited", report.stats().patternsVisited());
        result.put("rescan", report.stats().rescanCount());
        System.out.println(Json.encode(result));
    }
}
