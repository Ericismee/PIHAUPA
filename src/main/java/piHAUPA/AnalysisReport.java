package piHAUPA;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

public record AnalysisReport(
        double upperThreshold,
        double lowerThreshold,
        RescanPolicy rescanPolicy,
        int batchCount,
        int transactionCount,
        long elapsedNanos,
        long memoryDeltaBytes,
        long peakMemoryBytes,
        MiningStats stats,
        List<BatchView> batches,
        Map<String, Double> upperBounds,
        List<PatternResult> managedPatterns) {

    public static AnalysisReport run(InputData input) {
        return run(input, RescanPolicy.TIGHT);
    }

    /**
     * DESIGN PATTERN: phương thức này đóng vai trò Facade cho luồng phân tích.
     * Bên gọi chỉ đưa InputData và RescanPolicy, nhận lại AnalysisReport;
     * không cần tự khởi tạo miner, duyệt batch hay thu thập thống kê.
     * Bên trong: tạo miner -> xử lý từng batch -> đo tài nguyên -> tổng hợp báo cáo.
     * Mục đích: Desktop, CLI và Web dùng chung một quy trình, giảm mã lặp.
     * Tối ưu hiện có: giữ một miner xuyên suốt các batch trong cùng lượt để khai phá
     * tăng dần; mỗi lượt độc lập có miner riêng, không chia sẻ trạng thái thay đổi.
     * elapsedNanos đo phần xử lý trong run, không gồm đọc input hay render giao diện.
     * Heap được lấy mẫu trước/sau batch; peakMemoryBytes là đỉnh quan sát được,
     * không phải đỉnh bộ nhớ chính xác của JVM hoặc RSS của toàn tiến trình.
     * Facade giúp tổ chức mã; hiệu quả giảm re-scan thuộc thuật toán PIHAUPA.
     */
    public static AnalysisReport run(InputData input, RescanPolicy policy) {
        return run(input, policy, ignored -> { });
    }

    // DESIGN PATTERN — FACADE: một điểm vào chung cho Web/CLI.
    // Tạo một miner cho cả lượt; các batch dùng cùng miner để giữ đúng OD/ID và cây mẫu.
    // Facade thống nhất điều phối; việc giảm re-scan thuộc thuật toán PIHAUPA.
    public static AnalysisReport run(InputData input, RescanPolicy policy, Consumer<String> progress) {
        PiHaupaMiner miner = new PiHaupaMiner(input.externalUtilities(), input.upperThreshold(),
                input.lowerThreshold(), policy);
        long baselineMemory = usedMemory();
        long peakMemory = baselineMemory;
        long started = System.nanoTime();
        List<BatchView> batchViews = new ArrayList<>();
        int transactionCount = 0;

        int index = 0;
        for (Batch batch : input.batches()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("PIHAUPA analysis cancelled.");
            }
            transactionCount += batch.transactions().size();
            progress.accept(policy + ": bắt đầu " + batch.name() + " (" + transactionCount
                    + " giao dịch tích lũy)");
            long memoryBefore = usedMemory();
            long batchStarted = System.nanoTime();
            miner.setProgress(line -> progress.accept(policy+" · "+batch.name()+" · "+line));
            BatchResult result = miner.processBatch(batch);
            long batchElapsed = System.nanoTime() - batchStarted;
            long memoryAfter = usedMemory();
            peakMemory = Math.max(peakMemory, Math.max(memoryBefore, memoryAfter));
            batchViews.add(BatchView.from(index++, batch, result, batchElapsed, transactionCount,
                    memoryBefore, memoryAfter, peakMemory));
            progress.accept(policy + ": xong " + batch.name() + ", " + result.action()
                    + ", LARGE=" + result.largePatterns().size()
                    + ", PRE-LARGE=" + result.preLargePatterns().size());
        }

        long elapsed = System.nanoTime() - started;
        long finalMemory = usedMemory();
        peakMemory = Math.max(peakMemory, finalMemory);
        long memoryDelta = Math.max(0L, finalMemory - baselineMemory);
        return new AnalysisReport(input.upperThreshold(), input.lowerThreshold(), policy, input.batches().size(),
                transactionCount, elapsed, memoryDelta, peakMemory, miner.stats(), batchViews,
                miner.upperBounds(),
                miner.managedPatterns());
    }

    public String toJson() {
        JsonBuilder json = new JsonBuilder();
        json.beginObject()
                .number("upperThreshold", upperThreshold)
                .number("lowerThreshold", lowerThreshold)
                .string("rescanPolicy", rescanPolicy.name())
                .number("batchCount", batchCount)
                .number("transactionCount", transactionCount)
                .number("elapsedMs", elapsedNanos / 1_000_000.0)
                .number("memoryDeltaMb", memoryDeltaBytes / (1024.0 * 1024.0))
                .number("peakMemoryMb", peakMemoryBytes / (1024.0 * 1024.0))
                .name("stats").beginObject()
                .number("rescanCount", stats.rescanCount())
                .number("patternsVisited", stats.patternsVisited())
                .number("combinedNodes", stats.combinedNodes())
                .number("totalTransactionUtilityProcessed", stats.totalTransactionUtilityProcessed())
                .number("totalMaximumUtilityProcessed", stats.totalMaximumUtilityProcessed())
                .endObject()
                .name("batches").beginArray();
        for (BatchView batch : batches) {
            batch.writeJson(json);
        }
        json.endArray().name("upperBounds").beginArray();
        upperBounds.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue(
                Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> json.beginObject().string("item", entry.getKey())
                        .number("auub", entry.getValue()).endObject());
        json.endArray().name("managedPatterns").beginArray();
        for (PatternResult pattern : managedPatterns) {
            writePattern(json, pattern);
        }
        return json.endArray().endObject().toString();
    }

    private static void writePattern(JsonBuilder json, PatternResult pattern) {
        json.beginObject()
                .string("name", pattern.displayName())
                .string("items", String.join(",", pattern.items()))
                .string("type", pattern.type().name())
                .number("sumUtility", pattern.sumUtility())
                .number("averageUtility", pattern.averageUtility())
                .number("length", pattern.length())
                .endObject();
    }

    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    public record BatchView(
            int index,
            String name,
            int transactionCount,
            int cumulativeTransactionCount,
            String action,
            boolean rescanTriggered,
            Double tightRescanLimit,
            Double tightRescanDenominator,
            Double originalRescanLimit,
            double accumulatedTransactionUtility,
            double accumulatedMaximumUtility,
            double batchTransactionUtility,
            double batchMaximumUtility,
            double totalTransactionUtility,
            double minUtilUpper,
            double minUtilLower,
            double elapsedMs,
            double memoryBeforeMb,
            double memoryAfterMb,
            double peakMemoryMb,
            List<PatternResult> largePatterns,
            List<PatternResult> preLargePatterns) {

        private static BatchView from(int index, Batch batch, BatchResult result, long elapsedNanos,
                                      int cumulativeTransactionCount, long memoryBefore,
                                      long memoryAfter, long peakMemory) {
            return new BatchView(index, batch.name(), batch.transactions().size(), cumulativeTransactionCount,
                    result.action(),
                    result.rescanTriggered(), result.tightRescanLimit(), result.tightRescanDenominator(),
                    result.originalRescanLimit(), result.accumulatedTransactionUtility(),
                    result.accumulatedMaximumUtility(), result.batchTransactionUtility(), result.batchMaximumUtility(),
                    result.totalTransactionUtility(), result.minUtilUpper(), result.minUtilLower(),
                    elapsedNanos / 1_000_000.0, memoryBefore / (1024.0 * 1024.0),
                    memoryAfter / (1024.0 * 1024.0), peakMemory / (1024.0 * 1024.0),
                    result.largePatterns(), result.preLargePatterns());
        }

        private void writeJson(JsonBuilder json) {
            json.beginObject()
                    .number("index", index)
                    .string("name", name)
                    .number("transactionCount", transactionCount)
                    .number("cumulativeTransactionCount", cumulativeTransactionCount)
                    .string("action", action)
                    .bool("rescanTriggered", rescanTriggered)
                    .nullableNumber("tightRescanLimit", tightRescanLimit)
                    .nullableNumber("tightRescanDenominator", tightRescanDenominator)
                    .nullableNumber("originalRescanLimit", originalRescanLimit)
                    .number("accumulatedTransactionUtility", accumulatedTransactionUtility)
                    .number("accumulatedMaximumUtility", accumulatedMaximumUtility)
                    .number("batchTransactionUtility", batchTransactionUtility)
                    .number("batchMaximumUtility", batchMaximumUtility)
                    .number("totalTransactionUtility", totalTransactionUtility)
                    .number("minUtilUpper", minUtilUpper)
                    .number("minUtilLower", minUtilLower)
                    .number("elapsedMs", elapsedMs)
                    .number("memoryBeforeMb", memoryBeforeMb)
                    .number("memoryAfterMb", memoryAfterMb)
                    .number("peakMemoryMb", peakMemoryMb)
                    .name("largePatterns").beginArray();
            for (PatternResult pattern : largePatterns) {
                writePattern(json, pattern);
            }
            json.endArray().name("preLargePatterns").beginArray();
            for (PatternResult pattern : preLargePatterns) {
                writePattern(json, pattern);
            }
            json.endArray().endObject();
        }
    }

    private static final class JsonBuilder {
        private final StringBuilder out = new StringBuilder();
        private final List<Boolean> firstStack = new ArrayList<>();
        private boolean waitingForNamedValue;

        private JsonBuilder beginObject() {
            beforeValue();
            out.append('{');
            firstStack.add(true);
            return this;
        }

        private JsonBuilder endObject() {
            out.append('}');
            firstStack.remove(firstStack.size() - 1);
            return this;
        }

        private JsonBuilder beginArray() {
            beforeValue();
            out.append('[');
            firstStack.add(true);
            return this;
        }

        private JsonBuilder endArray() {
            out.append(']');
            firstStack.remove(firstStack.size() - 1);
            return this;
        }

        private JsonBuilder name(String name) {
            beforeValue();
            out.append('"').append(escape(name)).append("\":");
            waitingForNamedValue = true;
            return this;
        }

        private JsonBuilder string(String name, String value) {
            return name(name).rawString(value);
        }

        private JsonBuilder number(String name, double value) {
            return name(name).rawNumber(value);
        }

        private JsonBuilder number(String name, long value) {
            return name(name).rawNumber(value);
        }

        private JsonBuilder nullableNumber(String name, Double value) {
            name(name);
            if (value == null) {
                beforeValue();
                out.append("null");
            } else {
                rawNumber(value);
            }
            return this;
        }

        private JsonBuilder bool(String name, boolean value) {
            name(name);
            beforeValue();
            out.append(value);
            return this;
        }

        private JsonBuilder rawString(String value) {
            beforeValue();
            out.append('"').append(escape(value)).append('"');
            return this;
        }

        private JsonBuilder rawNumber(double value) {
            beforeValue();
            if (Double.isFinite(value)) {
                // Preserve threshold precision; rounding here changes the configuration
                // reported to clients while minUtil still uses the original double.
                out.append(Double.toString(value));
            } else {
                out.append("null");
            }
            return this;
        }

        private JsonBuilder rawNumber(long value) {
            beforeValue();
            out.append(value);
            return this;
        }

        private void beforeValue() {
            if (waitingForNamedValue) {
                waitingForNamedValue = false;
                return;
            }
            if (firstStack.isEmpty()) {
                return;
            }
            int last = firstStack.size() - 1;
            if (firstStack.get(last)) {
                firstStack.set(last, false);
            } else {
                out.append(',');
            }
        }

        private static String escape(String value) {
            return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }

        @Override
        public String toString() {
            return out.toString();
        }
    }
}
