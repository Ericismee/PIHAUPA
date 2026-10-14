package piHAUPA;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;

public final class InputParser {
    private InputParser() {
    }

    public static InputData parse(Path path) throws IOException {
        try (Stream<String> lines = Files.lines(path)) {
            return parseLines(lines::iterator);
        }
    }

    public static InputData parse(Reader reader) {
        BufferedReader buffered = reader instanceof BufferedReader
                ? (BufferedReader) reader : new BufferedReader(reader);
        return parseLines(buffered.lines()::iterator);
    }

    public static InputData parseText(String text) {
        return parseLines(text.lines().toList());
    }

    public static InputData parseBuiltInSample() {
        return parseLines("""
                upper=0.23
                lower=0.10

                external:
                A=2 B=5 C=1 D=3 E=6 F=4

                batch DB0
                T1: A:2 B:1 C:3
                T2: C:1 D:2
                T3: A:6 B:1 C:4 E:5
                T4: A:4 B:4 E:3
                T5: B:1 E:2 F:2

                batch DB1
                T6: A:3 F:3
                T7: C:2 E:2

                batch DB2
                T8: B:5 C:1 D:3 E:2
                T9: A:1 B:3 D:1
                """.lines().toList());
    }

    private static InputData parseLines(Iterable<String> lines) {
        double upper = Double.NaN;
        double lower = Double.NaN;
        Map<String, Double> external = new LinkedHashMap<>();
        List<Batch> batches = new ArrayList<>();
        String currentBatchName = null;
        List<Transaction> currentTransactions = new ArrayList<>();
        boolean readingExternal = false;

        for (String rawLine : lines) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Input import cancelled.");
            }
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }
            String lowerLine = line.toLowerCase();
            if (lowerLine.startsWith("upper=") || lowerLine.startsWith("su=")) {
                upper = Double.parseDouble(valueAfterEquals(line));
                readingExternal = false;
            } else if (lowerLine.startsWith("lower=") || lowerLine.startsWith("sl=")) {
                lower = Double.parseDouble(valueAfterEquals(line));
                readingExternal = false;
            } else if (lowerLine.equals("external:") || lowerLine.equals("external")) {
                readingExternal = true;
            } else if (lowerLine.startsWith("batch ")) {
                if (currentBatchName != null) {
                    batches.add(new Batch(currentBatchName, List.copyOf(currentTransactions)));
                    currentTransactions.clear();
                }
                currentBatchName = line.substring("batch".length()).trim();
                readingExternal = false;
            } else if (readingExternal) {
                parseExternalUtilities(line, external);
            } else if (line.contains(":")) {
                if (currentBatchName == null) {
                    throw new IllegalArgumentException("Transaction appears before any batch: " + line);
                }
                currentTransactions.add(parseTransaction(line));
            } else if (Double.isNaN(upper) && Double.isNaN(lower) && external.isEmpty()
                    && batches.isEmpty() && currentBatchName == null
                    && line.matches("[0-9]+(?:\\s+[0-9]+){7}")) {
                throw new IllegalArgumentException("Tệp này giống PIPA all_data.txt (8 cột metadata ảnh), "
                        + "không phải dữ liệu giao dịch PIHAUPA. Thiếu external utility, "
                        + "batch và internal utility; không thể suy ra các giá trị đó từ tọa độ ảnh.");
            } else {
                throw new IllegalArgumentException("Cannot parse line: " + line);
            }
        }

        if (currentBatchName != null) {
            batches.add(new Batch(currentBatchName, List.copyOf(currentTransactions)));
        }
        if (!Double.isFinite(upper) || !Double.isFinite(lower)
                || !(lower > 0.0 && lower < upper && upper <= 1.0)) {
            throw new IllegalArgumentException("Require thresholds 0 < Sl < Su <= 1.");
        }
        if (external.isEmpty()) {
            throw new IllegalArgumentException("Require external utilities.");
        }
        if (batches.isEmpty()) {
            throw new IllegalArgumentException("Require at least one batch.");
        }
        return new InputData(upper, lower, Map.copyOf(external), List.copyOf(batches));
    }

    private static void parseExternalUtilities(String line, Map<String, Double> external) {
        for (String token : line.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            String[] parts = token.split("=");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Bad external utility token: " + token);
            }
            String item = parts[0].trim();
            double value = Double.parseDouble(parts[1].trim());
            if (external.putIfAbsent(item, value) != null) {
                throw new IllegalArgumentException("Duplicate external utility item: " + item);
            }
        }
    }

    private static Transaction parseTransaction(String line) {
        String[] pair = line.split(":", 2);
        String tid = pair[0].trim();
        Map<String, Double> items = new LinkedHashMap<>();
        for (String token : pair[1].trim().split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            String[] parts = token.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Bad item token: " + token);
            }
            String item = parts[0].trim();
            double value = Double.parseDouble(parts[1].trim());
            if (items.putIfAbsent(item, value) != null) {
                throw new IllegalArgumentException("Duplicate item " + item + " in transaction " + tid);
            }
        }
        if (tid.isEmpty() || items.isEmpty()) {
            throw new IllegalArgumentException("Transaction id and at least one item are required: " + line);
        }
        return new Transaction(tid, items);
    }

    private static String valueAfterEquals(String line) {
        int idx = line.indexOf('=');
        if (idx < 0) {
            throw new IllegalArgumentException("Missing '=' in " + line);
        }
        return line.substring(idx + 1).trim();
    }

    private static String stripComment(String line) {
        int idx = line.indexOf('#');
        return idx >= 0 ? line.substring(0, idx) : line;
    }
}
