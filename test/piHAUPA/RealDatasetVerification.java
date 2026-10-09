package piHAUPA;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Optional repeatable check against the original six-dataset ZIP. */
public final class RealDatasetVerification {
    private RealDatasetVerification() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: RealDatasetVerification DATA.zip");
        Path archive = Path.of(args[0]);
        Map<String, long[]> expected = Map.of(
                "Accidents", new long[] {340183, 3478342570855L},
                "Chainstore", new long[] {1112949, 2428645460792L},
                "NewChess", new long[] {3196, 36864920448L},
                "NewConnects", new long[] {67557, 996517630255L},
                "NewMushroom", new long[] {8416, 60998108464L},
                "Retail", new long[] {88162, 281010698598L});

        List<ArchiveDatasetReader.DatasetAudit> audits = ArchiveDatasetReader.inspect(archive, ignored -> { });
        check(audits.size() == expected.size(), "Expected six datasets");
        for (var audit : audits) {
            long[] numbers = expected.get(audit.name());
            check(numbers != null, "Unexpected dataset " + audit.name());
            check(audit.rows() == numbers[0], audit.name() + " row count");
            check(audit.totalTransactionUtility() == numbers[1], audit.name() + " TU");
            check(audit.mismatchedTransactionUtilities() == 0, audit.name() + " mismatched TU rows");
            check(audit.missingInvestmentItems() == 0, audit.name() + " missing metadata items");
            System.out.println("PASS " + audit.name() + ": rows=" + audit.rows() + ", TU=" + (long) audit.totalTransactionUtility());
        }

        InputData highInput = ArchiveDatasetReader.read(archive, "NewMushroom", .23, .10, ignored -> { });
        AnalysisReport high = AnalysisReport.run(highInput);
        check(high.batches().get(2).largePatterns().isEmpty(), "NewMushroom default threshold must have no HAUP");

        InputData lowInput = ArchiveDatasetReader.read(archive, "NewMushroom", .05, .02, ignored -> { });
        AnalysisReport low = AnalysisReport.run(lowInput);
        var last = low.batches().get(2);
        Set<String> expectedNames = Set.of("97", "94,97", "36,97", "36,94,97", "90,97");
        check(last.largePatterns().size() == expectedNames.size(), "NewMushroom low-threshold HAUP count");
        check(last.preLargePatterns().size() == 311, "NewMushroom low-threshold PRE-LARGE count");
        check(last.totalTransactionUtility() == 60998108464L, "NewMushroom final TU");
        for (PatternResult pattern : last.largePatterns()) {
            String key = String.join(",", pattern.items());
            check(expectedNames.contains(key), "Unexpected HAUP " + key);
            BigDecimal independentSum = sumPatternFromRawZip(archive, pattern.items());
            check(independentSum.compareTo(BigDecimal.valueOf(pattern.sumUtility())) == 0,
                    "sumU differs from raw transactions for " + key);
            double independentAu = independentSum.doubleValue() / pattern.items().size();
            check(Math.abs(independentAu - pattern.averageUtility()) < 0.001,
                    "AU differs from raw transactions for " + key);
            check(pattern.averageUtility() >= last.minUtilUpper(), "HAUP below Upper: " + key);
            System.out.println("PASS HAUP " + key + ": sumU=" + independentSum + ", AU=" + independentAu);
        }
        System.out.println("Real ZIP validation passed at Su/Sl 0.23/0.10 and 0.05/0.02.");
    }

    private static BigDecimal sumPatternFromRawZip(Path archive, List<String> pattern) throws Exception {
        BigDecimal sum = BigDecimal.ZERO;
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry entry = zip.stream()
                    .filter(item -> item.getName().endsWith("/NewMushroom-Metadata/transactions.txt"))
                    .findFirst().orElseThrow();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    String[] parts = line.split(":", 3);
                    String[] items = parts[0].trim().split("\\s+");
                    String[] values = parts[2].trim().split("\\s+");
                    check(items.length == values.length, "Raw ZIP item/utility mismatch");
                    Map<String, BigDecimal> row = new HashMap<>();
                    for (int i = 0; i < items.length; i++) row.put(items[i], new BigDecimal(values[i]));
                    if (row.keySet().containsAll(pattern)) {
                        for (String item : pattern) sum = sum.add(row.get(item));
                    }
                }
            }
        }
        return sum;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
