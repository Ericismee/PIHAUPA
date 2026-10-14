package piHAUPA;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

public final class PiHaupaSelfTest {
    private static final double EPSILON = 1.0e-8;

    private PiHaupaSelfTest() {
    }

    public static void main(String[] args) {
        verifiesPaperExampleAndBruteForceCompleteness();
        verifiesOriginalAndTightComparison();
        verifiesRandomSmallStreamsAgainstBruteForce();
        verifiesManagedTrieOnWideIncrement();
        verifiesTightRescanBoundaryAndAccumulation();
        verifiesPatternClassificationAtThresholds();
        verifiesNonPositiveDenominatorDoesNotReverseCondition();
        verifiesInvalidThresholdsAndDuplicateIds();
        System.out.println("PIHAUPA self-tests passed.");
    }

    private static void verifiesOriginalAndTightComparison() {
        ComparisonReport comparison = ComparisonReport.run(InputParser.parseBuiltInSample());
        require(comparison.sameHaups(), "Original and tight policies must return identical final HAUPs.");
        require(comparison.original().stats().rescanCount() == 2,
                "Original Eq.(5) must re-scan DB1 and DB2 in the paper example.");
        require(comparison.tight().stats().rescanCount() == 1,
                "Tight Eq.(6) must avoid the redundant DB1 re-scan.");
        require(comparison.tight().stats().patternsVisited()
                        < comparison.original().stats().patternsVisited(),
                "Tight policy must visit fewer patterns in the paper example.");
    }

    private static void verifiesPaperExampleAndBruteForceCompleteness() {
        InputData input = InputParser.parseBuiltInSample();
        PiHaupaMiner miner = new PiHaupaMiner(input.externalUtilities(), input.upperThreshold(),
                input.lowerThreshold());
        List<Transaction> accumulated = new ArrayList<>();

        for (int i = 0; i < input.batches().size(); i++) {
            Batch batch = input.batches().get(i);
            accumulated.addAll(batch.transactions());
            BatchResult actual = miner.processBatch(batch);
            Map<String, Double> expected = bruteForceLarge(accumulated, input.externalUtilities(),
                    input.upperThreshold());
            Map<String, Double> observed = toMap(actual.largePatterns());
            require(expected.keySet().equals(observed.keySet()),
                    "HAUP set mismatch after " + batch.name() + ": expected=" + expected.keySet()
                            + ", actual=" + observed.keySet());
            expected.forEach((pattern, utility) -> require(close(utility, observed.get(pattern)),
                    "AU mismatch for " + pattern + " after " + batch.name()));

            if (i == 0) {
                require(close(141.0, actual.totalTransactionUtility()), "Paper DB0 TU must be 141.");
                require(close(73.0, actual.batchMaximumUtility()), "Paper DB0 MU must be 73.");
                require(observed.keySet().equals(Set.of("E", "BE", "B", "AE")),
                        "Paper Table 5 DB0 LARGE set mismatch.");
                require(close(60.0, observed.get("E")), "Paper DB0 AU(E) must be 60.");
                require(close(45.0, observed.get("BE")), "Paper DB0 AU(BE) must be 45.");
            }

            if (i == 1) {
                require(!actual.rescanTriggered(), "DB1 must not trigger the tight re-scan.");
                require(close(32.0, actual.batchTransactionUtility()), "Paper DB1 TU must be 32.");
                require(close(24.0, actual.batchMaximumUtility()), "Paper DB1 MU must be 24.");
                require(close(26.4375, actual.tightRescanLimit()), "Unexpected DB1 re-scan limit.");
                require(close(1.0 - (32.0 / 24.0) * 0.23, actual.tightRescanDenominator()),
                        "Unexpected DB1 tight denominator.");
                require(actual.accumulatedTransactionUtility() >= actual.originalRescanLimit(),
                        "The original loose condition should re-scan DB1.");
            }
            if (i == 2) {
                require(actual.rescanTriggered(), "DB2 must trigger the cumulative tight re-scan.");
                require(close(99.0, actual.accumulatedTransactionUtility()),
                        "ID TU must accumulate DB1 and DB2.");
                require(close(64.0, actual.accumulatedMaximumUtility()),
                        "ID MU must accumulate DB1 and DB2.");
                require(observed.keySet().equals(Set.of("E", "B", "BE")),
                        "Paper DB2 LARGE set mismatch.");
            }
        }
    }

    private static void verifiesManagedTrieOnWideIncrement() {
        Map<String, Double> external = new LinkedHashMap<>();
        List<Transaction> original = new ArrayList<>();
        Map<String, Double> wideItems = new LinkedHashMap<>();
        for (int i = 1; i <= 20; i++) {
            String item = "I" + i;
            external.put(item, 1.0);
            original.add(new Transaction("O" + i, Map.of(item, 100.0)));
            wideItems.put(item, 1.0);
        }
        PiHaupaMiner miner = new PiHaupaMiner(external, 0.02, 0.01);
        miner.processBatch(new Batch("DB0", original));
        BatchResult incremental = miner.processBatch(new Batch("DB1",
                List.of(new Transaction("WIDE", wideItems))));
        require(!incremental.rescanTriggered(), "Wide incremental transaction should update the trie only.");
        require(incremental.largePatterns().size() == 20, "All managed singleton patterns must remain large.");
    }

    private static void verifiesRandomSmallStreamsAgainstBruteForce() {
        Random random = new Random(20260920L);
        for (int trial = 0; trial < 40; trial++) {
            Map<String, Double> external = new LinkedHashMap<>();
            for (int item = 0; item < 5; item++) {
                external.put("R" + item, (double) (1 + random.nextInt(3)));
            }
            double upper = 0.12 + (trial % 5) * 0.04;
            double lower = upper / 2.0;
            PiHaupaMiner miner = new PiHaupaMiner(external, upper, lower);
            List<Transaction> accumulated = new ArrayList<>();
            for (int batchIndex = 0; batchIndex < 3; batchIndex++) {
                List<Transaction> batchTransactions = new ArrayList<>();
                for (int transactionIndex = 0; transactionIndex < 4; transactionIndex++) {
                    Map<String, Double> items = new LinkedHashMap<>();
                    for (String item : external.keySet()) {
                        if (random.nextBoolean()) {
                            items.put(item, (double) (1 + random.nextInt(5)));
                        }
                    }
                    if (items.isEmpty()) {
                        items.put("R0", 1.0);
                    }
                    batchTransactions.add(new Transaction("R-" + trial + "-" + batchIndex + "-"
                            + transactionIndex, items));
                }
                accumulated.addAll(batchTransactions);
                BatchResult actual = miner.processBatch(new Batch("DB" + batchIndex, batchTransactions));
                Map<String, Double> expected = bruteForceLarge(accumulated, external, upper);
                Map<String, Double> observed = toMap(actual.largePatterns());
                require(expected.keySet().equals(observed.keySet()),
                        "Random trial " + trial + " batch " + batchIndex + " mismatch: expected="
                                + expected + ", actual=" + observed);
                for (Map.Entry<String, Double> entry : expected.entrySet()) {
                    require(close(entry.getValue(), observed.get(entry.getKey())),
                            "Random trial " + trial + " AU mismatch for " + entry.getKey());
                }
            }
        }
    }

    private static void verifiesInvalidThresholdsAndDuplicateIds() {
        expectFailure(() -> new PiHaupaMiner(Map.of("A", 1.0), 0.1, 0.1));
        PiHaupaMiner miner = new PiHaupaMiner(Map.of("A", 1.0), 0.5, 0.1);
        miner.processBatch(new Batch("DB0", List.of(new Transaction("T1", Map.of("A", 1.0)))));
        expectFailure(() -> miner.processBatch(
                new Batch("DB1", List.of(new Transaction("T1", Map.of("A", 1.0))))));
    }

    private static void verifiesNonPositiveDenominatorDoesNotReverseCondition() {
        Map<String, Double> external = new LinkedHashMap<>();
        Map<String, Double> wideItems = new LinkedHashMap<>();
        List<Transaction> original = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String item = "N" + i;
            external.put(item, 1.0);
            wideItems.put(item, 1.0);
            original.add(new Transaction("N0-" + i, Map.of(item, 100.0)));
        }
        PiHaupaMiner miner = new PiHaupaMiner(external, 0.20, 0.10);
        miner.processBatch(new Batch("DB0", original));
        BatchResult result = miner.processBatch(new Batch("DB1",
                List.of(new Transaction("N-WIDE", wideItems))));
        require(result.tightRescanDenominator() < 0.0, "Test setup requires a negative denominator.");
        require(!result.rescanTriggered(), "A negative stable-form left side cannot satisfy re-scan.");
        require(Double.isInfinite(result.tightRescanLimit()), "The divided threshold must be reported as infinite.");
    }

    private static void verifiesTightRescanBoundaryAndAccumulation() {
        Map<String, Double> external = Map.of("A", 1.0);
        PiHaupaMiner boundary = new PiHaupaMiner(external, 0.5, 0.25);
        boundary.processBatch(new Batch("DB0", List.of(new Transaction("B0", Map.of("A", 100.0)))));
        BatchResult below = boundary.processBatch(new Batch("DB1", List.of(
                new Transaction("B1", Map.of("A", 20.0)))));
        require(!below.rescanTriggered(), "Below the tight boundary must only update the tree.");
        require(close(50.0, below.tightRescanLimit()), "Unexpected tight limit below boundary.");
        BatchResult equal = boundary.processBatch(new Batch("DB2", List.of(
                new Transaction("B2", Map.of("A", 30.0)))));
        require(equal.rescanTriggered(), "Equality must trigger a re-scan according to >= in Eq.(6).");
        require(close(50.0, equal.accumulatedMaximumUtility()), "Inserted MU must span DB1 and DB2.");
        BatchResult afterReset = boundary.processBatch(new Batch("DB3", List.of(
                new Transaction("B3", Map.of("A", 1.0)))));
        require(!afterReset.rescanTriggered(), "Inserted counters must reset after the full re-scan.");
        require(close(1.0, afterReset.accumulatedMaximumUtility()), "MU(ID) must restart after a re-scan.");

        PiHaupaMiner justBelow = new PiHaupaMiner(external, 0.5, 0.25);
        justBelow.processBatch(new Batch("DB0", List.of(new Transaction("J0", Map.of("A", 100.0)))));
        require(!justBelow.processBatch(new Batch("DB1", List.of(
                new Transaction("J1", Map.of("A", 49.0))))).rescanTriggered(),
                "MU(ID) just below the limit must not re-scan.");
    }

    private static void verifiesPatternClassificationAtThresholds() {
        PiHaupaMiner miner = new PiHaupaMiner(Map.of("A", 1.0, "B", 1.0, "C", 1.0), 0.5, 0.25);
        BatchResult result = miner.processBatch(new Batch("DB0", List.of(
                new Transaction("C0", Map.of("A", 50.0, "B", 25.0, "C", 25.0)))));
        require(toMap(result.largePatterns()).containsKey("A"), "AU equal to Su*TU must be LARGE.");
        require(toMap(result.preLargePatterns()).containsKey("B"), "AU equal to Sl*TU must be PRE-LARGE.");
        require(!toMap(result.largePatterns()).containsKey("B"), "PRE-LARGE item must not be LARGE.");
    }

    private static Map<String, Double> bruteForceLarge(List<Transaction> transactions,
                                                        Map<String, Double> external,
                                                        double upperThreshold) {
        List<String> items = new ArrayList<>(new TreeSet<>(external.keySet()));
        double totalUtility = transactions.stream().mapToDouble(t -> t.transactionUtility(external)).sum();
        double threshold = upperThreshold * totalUtility;
        Map<String, Double> result = new HashMap<>();
        enumerate(items, 0, new ArrayList<>(), pattern -> {
            double sumUtility = 0.0;
            for (Transaction transaction : transactions) {
                if (transaction.internalUtilities().keySet().containsAll(pattern)) {
                    for (String item : pattern) {
                        sumUtility += transaction.utilityOf(item, external);
                    }
                }
            }
            double averageUtility = sumUtility / pattern.size();
            if (averageUtility + EPSILON >= threshold) {
                result.put(String.join("", pattern), averageUtility);
            }
        });
        return result;
    }

    private static void enumerate(List<String> items, int index, List<String> selected,
                                  java.util.function.Consumer<List<String>> consumer) {
        if (index == items.size()) {
            if (!selected.isEmpty()) {
                consumer.accept(List.copyOf(selected));
            }
            return;
        }
        enumerate(items, index + 1, selected, consumer);
        selected.add(items.get(index));
        enumerate(items, index + 1, selected, consumer);
        selected.remove(selected.size() - 1);
    }

    private static Map<String, Double> toMap(List<PatternResult> patterns) {
        Map<String, Double> result = new HashMap<>();
        for (PatternResult pattern : patterns) {
            result.put(pattern.displayName(), pattern.averageUtility());
        }
        return result;
    }

    private static boolean close(double expected, Double actual) {
        return actual != null && Math.abs(expected - actual) <= EPSILON;
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
