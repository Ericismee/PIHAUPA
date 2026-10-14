package piHAUPA;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws IOException {
        CliOptions options = CliOptions.parse(args);
        if (options.help) {
            printHelp();
            return;
        }

        if (options.auditArchive != null) {
            double su = Double.isNaN(options.upperThreshold) ? 0.23 : options.upperThreshold;
            double sl = Double.isNaN(options.lowerThreshold) ? 0.10 : options.lowerThreshold;
            if (!(sl > 0 && sl < su && su <= 1))
                throw new IllegalArgumentException("Cần 0 < Sl < Su ≤ 1.");
            System.out.println("Kiểm tra 6 bộ dữ liệu trong " + options.auditArchive);
            for (ArchiveDatasetReader.DatasetAudit audit : ArchiveDatasetReader.inspect(
                    options.auditArchive, System.out::println)) {
                System.out.printf(Locale.US,
                        "%s: rows=%d items=%d investmentRows=%d missingInvestment=%d "
                                + "TU-mismatch=%d TU=%.2f MU=%.2f maxItems/row=%d "
                                + "maxAUUB(%s)=%.2f upper=%.2f lower=%.2f%n",
                        audit.name(), audit.rows(), audit.items(), audit.investmentRows(),
                        audit.missingInvestmentItems(), audit.mismatchedTransactionUtilities(),
                        audit.totalTransactionUtility(), audit.totalMaximumUtility(),
                        audit.maximumTransactionLength(), audit.strongestUpperBoundItem(),
                        audit.strongestUpperBound(), su * audit.totalTransactionUtility(),
                        sl * audit.totalTransactionUtility());
            }
            return;
        }

        InputData input = loadInput(options);
        AnalysisReport report = AnalysisReport.run(input);
        printReport(input, report, options.summaryOnly);

        if (options.outputDirectory != null) {
            String source = options.inputPath == null ? "Tables 2–3 · bài báo"
                    : options.inputPath.getFileName().toString();
            ExportBundle.writeDirectory(input, report, options.outputDirectory, source);
            System.out.println("Da xuat ket qua: " + options.outputDirectory.toAbsolutePath());
        }
    }

    private static InputData loadInput(CliOptions options) throws IOException {
        if (options.inputPath == null) {
            return overrideThresholds(InputParser.parseBuiltInSample(), options);
        }

        if (options.inputPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            if (options.dataset == null) {
                throw new IllegalArgumentException("ZIP có 6 bộ dữ liệu độc lập; hãy dùng --dataset NAME "
                        + "hoặc --audit-archive ZIP để kiểm tra cả 6 bộ.");
            }
            double su = Double.isNaN(options.upperThreshold) ? 0.23 : options.upperThreshold;
            double sl = Double.isNaN(options.lowerThreshold) ? 0.10 : options.lowerThreshold;
            return ArchiveDatasetReader.read(options.inputPath, options.dataset, su, sl, System.out::println);
        }
        if (options.inputPath.getFileName().toString().equalsIgnoreCase("transactions.txt")) {
            double su = Double.isNaN(options.upperThreshold) ? 0.23 : options.upperThreshold;
            double sl = Double.isNaN(options.lowerThreshold) ? 0.10 : options.lowerThreshold;
            try (var reader = java.nio.file.Files.newBufferedReader(options.inputPath)) {
                Path parent = options.inputPath.toAbsolutePath().getParent();
                return ArchiveDatasetReader.readTransactionsText(reader,
                        parent == null ? "transactions" : parent.getFileName().toString(),
                        su, sl, System.out::println);
            }
        }
        return overrideThresholds(InputParser.parse(options.inputPath), options);
    }

    private static InputData overrideThresholds(InputData input, CliOptions options) {
        double upper = Double.isNaN(options.upperThreshold) ? input.upperThreshold() : options.upperThreshold;
        double lower = Double.isNaN(options.lowerThreshold) ? input.lowerThreshold() : options.lowerThreshold;
        if (!(lower > 0.0 && lower < upper && upper <= 1.0)) {
            throw new IllegalArgumentException("Thresholds must satisfy 0 < Sl < Su <= 1.");
        }
        return new InputData(upper, lower, input.externalUtilities(), input.batches());
    }

    private static void printReport(InputData input, AnalysisReport report, boolean summaryOnly) {
        System.out.printf(Locale.US, "PIHAUPA - Su=%.6f, Sl=%.6f%n",
                report.upperThreshold(), report.lowerThreshold());
        System.out.printf("Batches: %d, transactions: %d, external items: %d%n%n",
                report.batchCount(), report.transactionCount(), input.externalUtilities().size());

        for (AnalysisReport.BatchView batch : report.batches()) {
            if (summaryOnly) {
                printBatchSummary(batch);
            } else {
                printBatch(batch, report.upperThreshold(), report.lowerThreshold());
            }
        }

        MiningStats stats = report.stats();
        System.out.println("=".repeat(88));
        System.out.println("TONG KET");
        System.out.printf(Locale.US, "Tong thoi gian: %.3f ms%n", report.elapsedNanos() / 1_000_000.0);
        System.out.printf(Locale.US, "Heap tang them: %.3f MB, heap dinh quan sat: %.3f MB%n",
                report.memoryDeltaBytes() / (1024.0 * 1024.0), report.peakMemoryBytes() / (1024.0 * 1024.0));
        System.out.printf(Locale.US, "Tong TU xu ly: %.3f, tong MU xu ly: %.3f%n",
                stats.totalTransactionUtilityProcessed(), stats.totalMaximumUtilityProcessed());
        System.out.printf("So lan re-scan: %d, pattern duyet: %d, node ket hop: %d%n",
                stats.rescanCount(), stats.patternsVisited(), stats.combinedNodes());
        System.out.printf("Pattern-tree entries: %d%n", report.managedPatterns().size());
    }

    private static void printBatch(AnalysisReport.BatchView batch, double upperRatio, double lowerRatio) {
        System.out.println("=".repeat(88));
        System.out.printf("Batch %d - %s%n", batch.index(), batch.name());
        System.out.printf(Locale.US, "Rows: %d (cumulative=%d), batchTU=%.3f, batchMU=%.3f%n",
                batch.transactionCount(), batch.cumulativeTransactionCount(),
                batch.batchTransactionUtility(), batch.batchMaximumUtility());
        System.out.printf(Locale.US,
                "Pattern thresholds: LARGE AU >= %.3f; PRE-LARGE %.3f <= AU < %.3f%n",
                batch.minUtilUpper(), batch.minUtilLower(), batch.minUtilUpper());
        System.out.println("Re-scan condition:");
        if (batch.tightRescanLimit() != null) {
            double originalTU = batch.totalTransactionUtility() - batch.accumulatedTransactionUtility();
            double left = batch.accumulatedMaximumUtility() - upperRatio * batch.accumulatedTransactionUtility();
            double right = (upperRatio - lowerRatio) * originalTU;
            System.out.printf(Locale.US,
                    "  ID since last full scan: TU=%.3f, MU=%.3f; OD at last full scan: TU=%.3f%n",
                    batch.accumulatedTransactionUtility(), batch.accumulatedMaximumUtility(), originalTU);
            System.out.printf(Locale.US,
                    "  Tight Eq.(6), stable form: MU(ID) - Su*TU(ID) >= (Su-Sl)*TU(OD)%n"
                            + "  %.6f %s %.6f -> %s%n",
                    left, batch.rescanTriggered() ? ">=" : "<", right,
                    batch.rescanTriggered() ? "RE-SCAN" : "UPDATE EXISTING TREE");
            if (batch.tightRescanDenominator() > 0.0) {
                System.out.printf(Locale.US, "  Divided form: MU(ID)=%.3f, limit=%.3f%n",
                        batch.accumulatedMaximumUtility(), batch.tightRescanLimit());
            } else {
                System.out.println("  Divided form unavailable (denominator <= 0); stable comparison used.");
            }
        } else {
            System.out.println("  Initial DB0 scan; re-scan condition is not applied.");
        }
        System.out.printf("Action: %s%n", batch.action());
        System.out.printf(Locale.US, "TotalTU=%.3f, minUtilUpper=%.3f, minUtilLower=%.3f%n",
                batch.totalTransactionUtility(), batch.minUtilUpper(), batch.minUtilLower());
        System.out.printf(Locale.US, "Time=%.3f ms, heap before/after/peak=%.3f/%.3f/%.3f MB%n",
                batch.elapsedMs(), batch.memoryBeforeMb(), batch.memoryAfterMb(), batch.peakMemoryMb());
        printPatterns("HAUP/LARGE", batch.largePatterns());
        printPatterns("PRE-LARGE", batch.preLargePatterns());
    }

    private static void printBatchSummary(AnalysisReport.BatchView batch) {
        System.out.printf(Locale.US,
                "Batch %d %-8s rows=%d cumulative=%d action=%s time=%.3fms heap=%.3fMB peak=%.3fMB "
                        + "TU=%.3f MU=%.3f large=%d preLarge=%d%n",
                batch.index(), batch.name(), batch.transactionCount(), batch.cumulativeTransactionCount(),
                batch.action(), batch.elapsedMs(), batch.memoryAfterMb(), batch.peakMemoryMb(),
                batch.batchTransactionUtility(), batch.batchMaximumUtility(),
                batch.largePatterns().size(), batch.preLargePatterns().size());
    }

    private static void printPatterns(String title, List<PatternResult> patterns) {
        System.out.printf("%s (%d)%n", title, patterns.size());
        if (patterns.isEmpty()) {
            System.out.println("  (none)");
            return;
        }
        for (PatternResult pattern : patterns) {
            System.out.printf(Locale.US, "  %-24s AU=%12.3f sumU=%12.3f length=%d%n",
                    pattern.displayName(), pattern.averageUtility(), pattern.sumUtility(), pattern.length());
        }
    }

    private static void printHelp() {
        System.out.println("""
                PIHAUPA Java
                  java -cp out piHAUPA.Main [options] [input-file]

                Options:
                  --input FILE          Read PIHAUPA text input
                  --dataset NAME        Select one dataset from a Data ZIP
                  --audit-archive ZIP   Validate all datasets in the ZIP without mining
                  --su VALUE            Override upper threshold ratio
                  --sl VALUE            Override lower threshold ratio
                  --output-dir DIR      Export input, DB0-DB2, JSON and CSV reports
                  --summary             Print compact per-row-count performance statistics
                  --help                Show this help

                With no input file, the program runs Tables 2-3 of the paper with
                Su=0.23 and Sl=0.10. Command-line --su/--sl override file values.
                """);
    }

    private static final class CliOptions {
        private Path inputPath;
        private Path auditArchive;
        private String dataset;
        private Path outputDirectory;
        private boolean summaryOnly;
        private boolean help;
        private double upperThreshold = Double.NaN;
        private double lowerThreshold = Double.NaN;

        private static CliOptions parse(String[] args) {
            CliOptions options = new CliOptions();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--summary" -> options.summaryOnly = true;
                    case "--help", "-h" -> options.help = true;
                    case "--input" -> options.inputPath = Path.of(requireValue(args, ++i, arg));
                    case "--audit-archive" -> options.auditArchive = Path.of(requireValue(args, ++i, arg));
                    case "--dataset" -> options.dataset = requireValue(args, ++i, arg);
                    case "--output-dir" -> options.outputDirectory = Path.of(requireValue(args, ++i, arg));
                    case "--su" -> options.upperThreshold = Double.parseDouble(requireValue(args, ++i, arg));
                    case "--sl" -> options.lowerThreshold = Double.parseDouble(requireValue(args, ++i, arg));
                    default -> {
                        if (arg.startsWith("--")) {
                            throw new IllegalArgumentException("Unknown option: " + arg);
                        }
                        if (options.inputPath != null) {
                            throw new IllegalArgumentException("Only one input file is allowed.");
                        }
                        options.inputPath = Path.of(arg);
                    }
                }
            }
            return options;
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            return args[index];
        }
    }
}
