package piHAUPA;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipFile;

/** Reads the six independent utility datasets without extracting the archive. */
public final class ArchiveDatasetReader {
    private static final java.util.regex.Pattern WHITESPACE = java.util.regex.Pattern.compile("\\s+");
    private static boolean numericItem(String value) {
        if (value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') return false;
        }
        return true;
    }
    private ArchiveDatasetReader() { }

    /** Read an extracted transactions.txt directly; no ZIP or investment file is required. */
    /**
     * DESIGN PATTERN — ADAPTER dữ liệu (chuyển biểu diễn đầu vào).
     * Đổi items:TU:itemUtilities thành InputData mà miner hiểu, kiểm tra TU/utility.
     * Utility đã tính sẵn nên dùng EU=1 để bảo toàn; không nhân investment metadata lần nữa.
     * Parsing/validation vẫn có chi phí; Adapter giúp tích hợp, không tự làm mining nhanh hơn.
     */
    public static InputData readTransactionsText(Reader source, String dataset, double su, double sl,
            Consumer<String> progress) throws IOException {
        if (!(sl > 0 && sl < su && su <= 1)) throw new IllegalArgumentException("Cần 0 < Sl < Su ≤ 1.");
        MutableAudit audit = new MutableAudit(dataset == null || dataset.isBlank() ? "Dataset" : dataset);
        List<Transaction> transactions = new ArrayList<>();
        readTransactions(source instanceof BufferedReader br ? br : new BufferedReader(source),
                audit, transactions, progress);
        if (audit.tuMismatches != 0)
            throw new IllegalArgumentException(audit.name + " có " + audit.tuMismatches + " giao dịch sai TU.");
        Map<String, Double> external = new LinkedHashMap<>();
        audit.seenItems.stream().sorted(Comparator.comparingInt(Integer::parseInt))
                .forEach(item -> external.put(item, 1.0));
        progress.accept("Đã đọc " + audit.rows + " giao dịch; TU=" + audit.totalTU
                + "; MU=" + audit.totalMU + "; item=" + external.size());
        return splitBatches(transactions, external, su, sl);
    }

    private static InputData splitBatches(List<Transaction> transactions, Map<String, Double> external,
            double su, double sl) {
        int first = (int) Math.ceil(transactions.size() * 0.8);
        int second = (int) Math.ceil(transactions.size() * 0.9);
        if (transactions.size() >= 3) {
            first = Math.min(first, transactions.size() - 2);
            second = Math.max(first + 1, Math.min(second, transactions.size() - 1));
        }
        return new InputData(su, sl, Map.copyOf(external), List.of(
                new Batch("DB0", transactions.subList(0, first)),
                new Batch("DB1", transactions.subList(first, second)),
                new Batch("DB2", transactions.subList(second, transactions.size()))));
    }

    public record DatasetAudit(String name, long rows, int items, int investmentRows,
            long missingInvestmentItems, long mismatchedTransactionUtilities,
            double totalTransactionUtility, double totalMaximumUtility,
            double totalInvestment, int maximumTransactionLength,
            String strongestUpperBoundItem, double strongestUpperBound) {
        public String toJson() {
            return "{\"name\":\"" + name + "\",\"rows\":" + rows
                    + ",\"items\":" + items + ",\"investmentRows\":" + investmentRows
                    + ",\"missingInvestmentItems\":" + missingInvestmentItems
                    + ",\"mismatchedTransactionUtilities\":" + mismatchedTransactionUtilities
                    + ",\"totalTransactionUtility\":" + totalTransactionUtility
                    + ",\"totalMaximumUtility\":" + totalMaximumUtility
                    + ",\"totalInvestment\":" + totalInvestment
                    + ",\"maximumTransactionLength\":" + maximumTransactionLength
                    + ",\"strongestUpperBoundItem\":\"" + strongestUpperBoundItem
                    + "\",\"strongestUpperBound\":" + strongestUpperBound + "}";
        }
    }

    public static List<DatasetAudit> inspect(Path archive, Consumer<String> progress) throws IOException {
        try (InputStream stream = Files.newInputStream(archive)) {
            return inspect(stream, progress);
        }
    }

    public static List<String> datasetNames(Path archive) throws IOException {
        Map<String, Set<String>> files = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                EntryName name = entryName(entries.nextElement().getName());
                if (name != null) files.computeIfAbsent(name.dataset, ignored -> new HashSet<>())
                        .add(name.file);
            }
        }
        return files.entrySet().stream()
                .filter(entry -> entry.getValue().contains("transactions.txt")
                        && entry.getValue().contains("investment_table.txt"))
                .map(Map.Entry::getKey).sorted().toList();
    }

    public static List<DatasetAudit> inspect(InputStream archive, Consumer<String> progress) throws IOException {
        Map<String, MutableAudit> audits = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(archive, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                EntryName entryName = entryName(entry.getName());
                if (entryName == null) continue;
                MutableAudit audit = audits.computeIfAbsent(entryName.dataset, MutableAudit::new);
                progress.accept("Đang kiểm tra " + entryName.dataset + "/" + entryName.file);
                BufferedReader reader = new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8));
                if (entryName.file.equals("transactions.txt")) {
                    if (audit.hasTransactions) throw new IllegalArgumentException("Trùng transactions.txt: " + audit.name);
                    audit.hasTransactions = true;
                    readTransactions(reader, audit, null, progress);
                } else {
                    if (audit.hasInvestment) throw new IllegalArgumentException("Trùng investment_table.txt: " + audit.name);
                    audit.hasInvestment = true;
                    readInvestments(reader, audit);
                }
                zip.closeEntry();
            }
        }
        if (audits.isEmpty()) throw new IllegalArgumentException("ZIP không có thư mục dữ liệu hợp lệ.");
        List<DatasetAudit> result = new ArrayList<>();
        for (MutableAudit audit : audits.values()) {
            if (!audit.hasTransactions || !audit.hasInvestment)
                throw new IllegalArgumentException("Thiếu transactions.txt hoặc investment_table.txt: " + audit.name);
            long missing = audit.seenItems.stream().filter(item -> !audit.investments.containsKey(item)).count();
            Map.Entry<String, Double> strongest = audit.upperBounds.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).orElseThrow();
            result.add(new DatasetAudit(audit.name, audit.rows, audit.seenItems.size(),
                    audit.investments.size(), missing, audit.tuMismatches, audit.totalTU,
                    audit.totalMU, audit.totalInvestment, audit.maxLength,
                    strongest.getKey(), strongest.getValue()));
            progress.accept(audit.name + ": " + audit.rows + " giao dịch, " + audit.seenItems.size()
                    + " item, " + audit.tuMismatches + " dòng TU không khớp");
        }
        result.sort(Comparator.comparing(DatasetAudit::name));
        return result;
    }

    /** Item utilities in transactions are already final U(i,T); EU=1 preserves them exactly. */
    public static InputData read(Path archive, String dataset, double su, double sl,
            Consumer<String> progress) throws IOException {
        try (InputStream stream = Files.newInputStream(archive)) {
            return read(stream, dataset, su, sl, progress);
        }
    }

    public static InputData read(InputStream archive, String dataset, double su, double sl,
            Consumer<String> progress) throws IOException {
        if (!(sl > 0 && sl < su && su <= 1)) throw new IllegalArgumentException("Cần 0 < Sl < Su ≤ 1.");
        if (dataset == null || dataset.isBlank()) throw new IllegalArgumentException("Cần chọn một bộ dữ liệu trong ZIP.");
        MutableAudit audit = new MutableAudit(dataset);
        List<Transaction> transactions = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(archive, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                EntryName name = entryName(entry.getName());
                if (name == null || !name.dataset.equalsIgnoreCase(dataset)) continue;
                BufferedReader reader = new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8));
                if (name.file.equals("transactions.txt")) {
                    if (audit.hasTransactions) throw new IllegalArgumentException("Trùng transactions.txt: " + dataset);
                    audit.hasTransactions = true;
                    progress.accept("Đang nạp giao dịch " + dataset);
                    readTransactions(reader, audit, transactions, progress);
                } else {
                    if (audit.hasInvestment) throw new IllegalArgumentException("Trùng investment_table.txt: " + dataset);
                    audit.hasInvestment = true;
                    readInvestments(reader, audit);
                }
                zip.closeEntry();
            }
        }
        if (!audit.hasTransactions || !audit.hasInvestment)
            throw new IllegalArgumentException("Không tìm thấy đủ hai tệp dữ liệu cho " + dataset);
        if (audit.tuMismatches != 0)
            throw new IllegalArgumentException(dataset + " có " + audit.tuMismatches + " giao dịch sai TU; không thể khẳng định kết quả chính xác.");
        if (!audit.investments.keySet().containsAll(audit.seenItems))
            throw new IllegalArgumentException(dataset + " có item chưa có trong investment_table.txt.");
        Map<String, Double> external = new LinkedHashMap<>();
        audit.seenItems.stream().sorted(Comparator.comparingInt(Integer::parseInt))
                .forEach(item -> external.put(item, 1.0));
        progress.accept("Đã nạp " + dataset + ": " + transactions.size()
                + " giao dịch, TU=" + audit.totalTU + ", MU=" + audit.totalMU);
        return splitBatches(transactions, external, su, sl);
    }

    private static void readTransactions(BufferedReader reader, MutableAudit audit,
            List<Transaction> transactions, Consumer<String> progress) throws IOException {
        String line;
        // Pool theo từng lần đọc, không intern toàn cục: dùng chung String item giữa các giao dịch.
        Map<String, String> itemPool = new HashMap<>();
        Set<String> rowItems = new HashSet<>();
        while ((line = reader.readLine()) != null) {
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Đã hủy đọc dữ liệu.");
            if (line.isBlank()) continue;
            long row = ++audit.rows;
            String[] sections = line.split(":", -1);
            if (sections.length != 3) throw badRow(audit.name, row, "cần items:TU:itemUtilities");
            String[] items = WHITESPACE.split(sections[0].trim());
            String[] utilities = WHITESPACE.split(sections[2].trim());
            if (items.length == 0 || items.length != utilities.length)
                throw badRow(audit.name, row, "số item và số utility không bằng nhau");
            double declaredTU = number(sections[1], audit.name, row);
            if (declaredTU <= 0) throw badRow(audit.name, row, "TU phải dương");
            double calculatedTU = 0;
            double maximum = 0;
            Map<String, Double> values = transactions == null ? null : new LinkedHashMap<>();
            rowItems.clear();
            for (int i = 0; i < items.length; i++) {
                String item = itemPool.computeIfAbsent(items[i], key -> key);
                if (!numericItem(item) || !rowItems.add(item))
                    throw badRow(audit.name, row, "item không hợp lệ hoặc bị lặp: " + item);
                double utility = number(utilities[i], audit.name, row);
                if (utility <= 0) throw badRow(audit.name, row, "utility phải dương");
                calculatedTU += utility;
                maximum = Math.max(maximum, utility);
                audit.seenItems.add(item);
                if (values != null) values.put(item, utility);
            }
            if (Math.abs(declaredTU - calculatedTU) > Math.max(0.01, declaredTU * 1e-9))
                audit.tuMismatches++;
            audit.totalTU += calculatedTU;
            audit.totalMU += maximum;
            for (String item : rowItems) audit.upperBounds.merge(item, maximum, Double::sum);
            audit.maxLength = Math.max(audit.maxLength, items.length);
            if (transactions != null) transactions.add(new Transaction("T" + row, values));
            if (row % 10_000 == 0) progress.accept(audit.name + ": đã đọc " + row + " giao dịch");
        }
        if (audit.rows == 0) throw new IllegalArgumentException(audit.name + " không có giao dịch.");
    }

    private static void readInvestments(BufferedReader reader, MutableAudit audit) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Đã hủy đọc dữ liệu.");
            line = line.trim();
            if (line.isEmpty() || line.startsWith("ItemID") || line.startsWith("=")) continue;
            String[] columns = line.split("\\s+");
            if (columns.length != 2 || !columns[0].matches("[0-9]+"))
                throw new IllegalArgumentException("investment_table.txt không hợp lệ: " + audit.name + " / " + line);
            double value = number(columns[1], audit.name, 0);
            if (value < 0 || audit.investments.putIfAbsent(columns[0], value) != null)
                throw new IllegalArgumentException("Investment âm hoặc item lặp: " + audit.name + " / " + columns[0]);
            audit.totalInvestment += value;
        }
    }

    private static double number(String text, String dataset, long row) {
        try {
            double value = Double.parseDouble(text.trim());
            if (Double.isFinite(value)) return value;
        } catch (NumberFormatException ignored) { }
        throw badRow(dataset, row, "số không hợp lệ: " + text);
    }

    private static IllegalArgumentException badRow(String dataset, long row, String reason) {
        return new IllegalArgumentException(dataset + (row > 0 ? " dòng " + row : "") + ": " + reason);
    }

    private static EntryName entryName(String path) {
        String[] parts = path.replace('\\', '/').split("/");
        if (parts.length != 3 || !parts[0].equalsIgnoreCase("Data")) return null;
        String folder = parts[1];
        String lower = folder.toLowerCase(Locale.ROOT);
        if (!lower.endsWith("-metadata")) return null;
        if (!folder.matches("[A-Za-z0-9_-]+")) return null;
        String file = parts[2].toLowerCase(Locale.ROOT);
        if (!file.equals("transactions.txt") && !file.equals("investment_table.txt")) return null;
        return new EntryName(folder.substring(0, folder.length() - "-metadata".length()), file);
    }

    private record EntryName(String dataset, String file) { }

    private static final class MutableAudit {
        final String name;
        final Set<String> seenItems = new HashSet<>();
        final Map<String, Double> investments = new HashMap<>();
        final Map<String, Double> upperBounds = new HashMap<>();
        long rows;
        long tuMismatches;
        double totalTU;
        double totalMU;
        double totalInvestment;
        int maxLength;
        boolean hasTransactions;
        boolean hasInvestment;

        MutableAudit(String name) { this.name = name; }
    }
}
