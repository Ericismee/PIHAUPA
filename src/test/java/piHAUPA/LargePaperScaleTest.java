package piHAUPA;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Đối chiếu bộ dữ liệu lớn bằng phép cộng AU độc lập với PIHAUPA. */
public final class LargePaperScaleTest {
    private static final double EPSILON = 1.0e-6;

    private LargePaperScaleTest() { }

    public static void main(String[] args) throws Exception {
        Path file = args.length == 0 ? Path.of("examples", "paper-scaled-9000.txt") : Path.of(args[0]);
        InputData input = InputParser.parse(file);
        int copies = input.batches().get(0).transactions().size() / 5;
        require(copies > 0 && input.batches().size() == 3, "Expected three scaled paper batches.");
        require(input.batches().get(0).transactions().size() == 5 * copies
                && input.batches().get(1).transactions().size() == 2 * copies
                && input.batches().get(2).transactions().size() == 2 * copies,
                "Batch sizes do not match the paper scaling factor.");

        AnalysisReport tight = AnalysisReport.run(input, RescanPolicy.TIGHT);
        AnalysisReport original = AnalysisReport.run(input, RescanPolicy.ORIGINAL);
        require(tight.stats().rescanCount() == 1, "Tight must re-scan only DB2.");
        require(original.stats().rescanCount() == 2, "Original must re-scan DB1 and DB2.");

        List<Transaction> accumulated = new ArrayList<>();
        double[] expectedTu = {141.0, 173.0, 240.0};
        double[] expectedMu = {73.0, 24.0, 40.0};
        for (int index = 0; index < input.batches().size(); index++) {
            Batch batch = input.batches().get(index);
            accumulated.addAll(batch.transactions());
            AnalysisReport.BatchView actual = tight.batches().get(index);
            require(close(actual.totalTransactionUtility(), expectedTu[index] * copies),
                    "TU mismatch at " + batch.name());
            require(close(actual.batchMaximumUtility(), expectedMu[index] * copies),
                    "MU mismatch at " + batch.name());
            require(close(actual.minUtilUpper(), input.upperThreshold() * expectedTu[index] * copies),
                    "Upper threshold mismatch at " + batch.name());
            require(close(actual.minUtilLower(), input.lowerThreshold() * expectedTu[index] * copies),
                    "Lower threshold mismatch at " + batch.name());

            Map<String, Double> oracle = calculateByDefinition(accumulated, input, false);
            Map<String, Double> observed = new HashMap<>();
            for (PatternResult pattern : actual.largePatterns()) {
                observed.put(pattern.displayName(), pattern.averageUtility());
            }
            require(oracle.keySet().equals(observed.keySet()), "LARGE set mismatch at " + batch.name()
                    + ": expected=" + oracle.keySet() + ", actual=" + observed.keySet());
            oracle.forEach((pattern, au) -> require(close(au, observed.get(pattern)),
                    "AU mismatch for " + pattern + " at " + batch.name()));
            Map<String, Double> preLargeOracle = calculateByDefinition(accumulated, input, true);
            Map<String, Double> preLargeObserved = new HashMap<>();
            for (PatternResult pattern : actual.preLargePatterns()) {
                preLargeObserved.put(pattern.displayName(), pattern.averageUtility());
            }
            require(preLargeOracle.keySet().containsAll(preLargeObserved.keySet()),
                    "Managed PRE-LARGE contains an incorrectly classified pattern at " + batch.name());
            preLargeObserved.forEach((pattern, au) -> require(close(au, preLargeOracle.get(pattern)),
                    "PRE-LARGE AU mismatch for " + pattern + " at " + batch.name()));
            System.out.println(batch.name() + " OK: TU=" + actual.totalTransactionUtility()
                    + ", upper=" + actual.minUtilUpper() + ", LARGE=" + observed.size());
        }

        AnalysisReport.BatchView finalBatch = tight.batches().get(2);
        require(close(findAu(finalBatch, "E"), 84.0 * copies), "Final AU(E) mismatch.");
        require(close(findAu(finalBatch, "B"), 75.0 * copies), "Final AU(B) mismatch.");
        require(close(findAu(finalBatch, "BE"), 63.5 * copies), "Final AU(BE) mismatch.");
        require(!tight.batches().get(1).rescanTriggered() && finalBatch.rescanTriggered(),
                "Tight Eq.(6) decision mismatch.");
        System.out.println("Large paper scale verified: " + (9 * copies)
                + " transactions, AU oracle, thresholds, and Eq.(6) decisions.");
    }

    private static Map<String, Double> calculateByDefinition(
            List<Transaction> transactions, InputData input, boolean preLarge) {
        List<String> items = new ArrayList<>(new TreeSet<>(input.externalUtilities().keySet()));
        double totalTu = 0.0;
        for (Transaction transaction : transactions) {
            for (Map.Entry<String, Double> entry : transaction.internalUtilities().entrySet()) {
                totalTu += entry.getValue() * input.externalUtilities().get(entry.getKey());
            }
        }
        double upper = input.upperThreshold() * totalTu;
        double lower = input.lowerThreshold() * totalTu;
        Map<String, Double> matching = new HashMap<>();
        for (int mask = 1; mask < (1 << items.size()); mask++) {
            List<String> pattern = new ArrayList<>();
            for (int bit = 0; bit < items.size(); bit++) {
                if ((mask & (1 << bit)) != 0) pattern.add(items.get(bit));
            }
            double sum = 0.0;
            for (Transaction transaction : transactions) {
                if (!transaction.internalUtilities().keySet().containsAll(pattern)) continue;
                for (String item : pattern) {
                    sum += transaction.internalUtilities().get(item) * input.externalUtilities().get(item);
                }
            }
            double au = sum / pattern.size();
            if (preLarge ? au + EPSILON >= lower && au < upper - EPSILON
                    : au + EPSILON >= upper) matching.put(String.join("", pattern), au);
        }
        return matching;
    }

    private static double findAu(AnalysisReport.BatchView batch, String name) {
        return batch.largePatterns().stream().filter(pattern -> pattern.displayName().equals(name))
                .findFirst().orElseThrow().averageUtility();
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) <= EPSILON;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
