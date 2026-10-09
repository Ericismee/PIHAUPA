package piHAUPA;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public record ComparisonReport(AnalysisReport original, AnalysisReport tight, boolean sameHaups) {
    public static ComparisonReport run(InputData input) {
        return run(input, ignored -> { });
    }

    public static ComparisonReport run(InputData input, Consumer<String> progress) {
        int rows = input.batches().stream().mapToInt(batch -> batch.transactions().size()).sum();
        if (rows <= 1_000) {
            // Warm up both code paths, then use median wall-clock time from interleaved runs.
            AnalysisReport.run(input, RescanPolicy.ORIGINAL);
            AnalysisReport.run(input, RescanPolicy.TIGHT);
        }
        int repetitions = rows <= 1_000 ? 3 : 1;
        List<AnalysisReport> originals = new ArrayList<>();
        List<AnalysisReport> tights = new ArrayList<>();
        for (int i = 0; i < repetitions; i++) {
            if ((i & 1) == 0) {
                originals.add(AnalysisReport.run(input, RescanPolicy.ORIGINAL, progress));
                tights.add(AnalysisReport.run(input, RescanPolicy.TIGHT, progress));
            } else {
                tights.add(AnalysisReport.run(input, RescanPolicy.TIGHT, progress));
                originals.add(AnalysisReport.run(input, RescanPolicy.ORIGINAL, progress));
            }
        }
        AnalysisReport original = median(originals);
        AnalysisReport tight = median(tights);
        return new ComparisonReport(original, tight, sameFinalHaups(original, tight));
    }

    private static AnalysisReport median(List<AnalysisReport> reports) {
        reports.sort(Comparator.comparingLong(AnalysisReport::elapsedNanos));
        return reports.get(reports.size() / 2);
    }

    public String toJson() {
        return "{\"sameHaups\":" + sameHaups
                + ",\"original\":" + original.toJson()
                + ",\"tight\":" + tight.toJson() + "}";
    }

    private static boolean sameFinalHaups(AnalysisReport left, AnalysisReport right) {
        return largeMap(left).equals(largeMap(right));
    }

    private static Map<String, Double> largeMap(AnalysisReport report) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (report.batches().isEmpty()) {
            return result;
        }
        for (PatternResult pattern : report.batches().get(report.batches().size() - 1).largePatterns()) {
            result.put(pattern.displayName(), pattern.averageUtility());
        }
        return result;
    }
}
