package piHAUPA;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ReportWriter {
    private ReportWriter() {
    }

    public static void writeAll(AnalysisReport report, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve("report.json"), report.toJson(), StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("patterns.csv"), patternsCsv(report), StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("performance-by-rows.csv"), performanceCsv(report),
                StandardCharsets.UTF_8);
    }

    public static void writeComparison(ComparisonReport comparison, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve("comparison.json"), comparison.toJson(), StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("comparison.csv"), comparisonCsv(comparison), StandardCharsets.UTF_8);
    }

    public static String comparisonCsv(ComparisonReport comparison) {
        AnalysisReport oldReport = comparison.original();
        AnalysisReport newReport = comparison.tight();
        return "metric,originalEq5,tightEq6\n"
                + "rescanCount," + oldReport.stats().rescanCount() + "," + newReport.stats().rescanCount() + "\n"
                + "elapsedMs," + number(oldReport.elapsedNanos() / 1_000_000.0) + ","
                + number(newReport.elapsedNanos() / 1_000_000.0) + "\n"
                + "memoryDeltaMb," + number(oldReport.memoryDeltaBytes() / 1048576.0) + ","
                + number(newReport.memoryDeltaBytes() / 1048576.0) + "\n"
                + "peakMemoryMb," + number(oldReport.peakMemoryBytes() / 1048576.0) + ","
                + number(newReport.peakMemoryBytes() / 1048576.0) + "\n"
                + "patternsVisited," + oldReport.stats().patternsVisited() + ","
                + newReport.stats().patternsVisited() + "\n"
                + "combinedNodes," + oldReport.stats().combinedNodes() + ","
                + newReport.stats().combinedNodes() + "\n"
                + "sameFinalHaups," + comparison.sameHaups() + "," + comparison.sameHaups() + "\n";
    }

    public static String performanceCsv(AnalysisReport report) {
        List<String> rows = new ArrayList<>();
        rows.add("batch,batchRows,cumulativeRows,elapsedMs,memoryBeforeMb,memoryAfterMb,peakMemoryMb,"
                + "batchTU,batchMU,totalTU,rescanTriggered,tightRescanDenominator,tightRescanLimit,"
                + "originalRescanLimit,largeCount,preLargeCount,"
                + "tightLeftMUminusSuTU,tightRightSuMinusSlTimesOldTU,decision,originalWouldRescan");
        for (AnalysisReport.BatchView batch : report.batches()) {
            boolean initial = batch.tightRescanLimit() == null;
            double oldTu = batch.totalTransactionUtility() - batch.accumulatedTransactionUtility();
            double tightLeft = batch.accumulatedMaximumUtility()
                    - report.upperThreshold() * batch.accumulatedTransactionUtility();
            double tightRight = (report.upperThreshold() - report.lowerThreshold()) * oldTu;
            rows.add(csv(batch.name()) + "," + batch.transactionCount() + ","
                    + batch.cumulativeTransactionCount() + "," + number(batch.elapsedMs()) + ","
                    + number(batch.memoryBeforeMb()) + "," + number(batch.memoryAfterMb()) + ","
                    + number(batch.peakMemoryMb()) + "," + number(batch.batchTransactionUtility()) + ","
                    + number(batch.batchMaximumUtility()) + "," + number(batch.totalTransactionUtility()) + ","
                    + batch.rescanTriggered() + ","
                    + (batch.tightRescanDenominator() == null ? "" : number(batch.tightRescanDenominator())) + ","
                    + (batch.tightRescanLimit() == null ? "" : number(batch.tightRescanLimit())) + ","
                    + (batch.originalRescanLimit() == null ? "" : number(batch.originalRescanLimit())) + ","
                    + batch.largePatterns().size() + "," + batch.preLargePatterns().size() + ","
                    + (initial ? "" : number(tightLeft)) + ","
                    + (initial ? "" : number(tightRight)) + ","
                    + csv(initial ? "INITIAL SCAN" : batch.rescanTriggered() ? "RE-SCAN" : "UPDATE TREE") + ","
                    + (initial ? "" : batch.accumulatedTransactionUtility() >= batch.originalRescanLimit()));
        }
        return String.join(System.lineSeparator(), rows) + System.lineSeparator();
    }

    public static String patternsCsv(AnalysisReport report) {
        List<String> rows = new ArrayList<>();
        rows.add("batch,type,pattern,items,averageUtility,sumUtility,length");
        for (AnalysisReport.BatchView batch : report.batches()) {
            for (PatternResult pattern : concat(batch.largePatterns(), batch.preLargePatterns())) {
                rows.add(csv(batch.name()) + "," + pattern.type() + "," + csv(pattern.displayName()) + ","
                        + csv(String.join(" ", pattern.items())) + "," + number(pattern.averageUtility()) + ","
                        + number(pattern.sumUtility()) + "," + pattern.length());
            }
        }
        return String.join(System.lineSeparator(), rows) + System.lineSeparator();
    }

    private static List<PatternResult> concat(List<PatternResult> left, List<PatternResult> right) {
        List<PatternResult> result = new ArrayList<>(left.size() + right.size());
        result.addAll(left);
        result.addAll(right);
        return result;
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String number(double value) {
        return String.format(Locale.US, "%.6f", value);
    }
}
