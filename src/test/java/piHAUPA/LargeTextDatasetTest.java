package piHAUPA;

import java.io.StringReader;

/** Direct extracted transactions.txt with user-selected thresholds. */
public final class LargeTextDatasetTest {
    private LargeTextDatasetTest() { }

    public static void main(String[] args) throws Exception {
        String transactions = "1 2:9:4 5\n1:2:2\n2:3:3\n";
        InputData input = ArchiveDatasetReader.readTransactionsText(
                new StringReader(transactions), "test", 0.50, 0.25, ignored -> { });
        check(input.upperThreshold() == 0.50 && input.lowerThreshold() == 0.25, "custom thresholds");
        check(input.externalUtilities().get("1") == 1.0, "precomputed utility mapping");
        check(input.batches().stream().mapToInt(b -> b.transactions().size()).sum() == 3, "all rows");
        AnalysisReport report = AnalysisReport.run(input);
        check(report.rescanPolicy() == RescanPolicy.TIGHT, "only Tight policy");
        check(Math.abs(report.batches().get(2).totalTransactionUtility() - 14.0) < 1e-9, "TU sum");
        check(Math.abs(report.batches().get(2).minUtilUpper() - 7.0) < 1e-9, "custom Upper");
        check(Math.abs(report.batches().get(2).minUtilLower() - 3.5) < 1e-9, "custom Lower");
        check(Math.abs(report.upperBounds().get("2") - 8.0) < 1e-9, "AUUB from MU(T)");
        String exported = ExportBundle.reportText(input, report, "test/transactions.txt");
        check(exported.contains("Su = 0.500000") && !exported.contains("Original"), "single-policy export");
        check(ExportBundle.reportCsv(input, report, "test").contains("\"AUUB\",\"2\""), "AUUB CSV");
        System.out.println("Large TXT test passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
