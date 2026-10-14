package piHAUPA;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class InputWriter {
    private InputWriter() {
    }

    public static void writeCombined(InputData input, Path path) throws IOException {
        createParent(path);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writeHeader(input, writer);
            for (Batch batch : input.batches()) {
                writeBatch(batch, writer);
            }
        }
    }

    public static void writeThreeSplitFiles(InputData input, Path outputDirectory) throws IOException {
        if (input.batches().size() != 3) {
            throw new IllegalArgumentException("Three split files require exactly DB0, DB1 and DB2.");
        }
        Files.createDirectories(outputDirectory);
        for (Batch batch : input.batches()) {
            Path path = outputDirectory.resolve(batch.name() + ".txt");
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writeHeader(input, writer);
                writeBatch(batch, writer);
            }
        }
    }

    private static void writeHeader(InputData input, BufferedWriter writer) throws IOException {
        writer.write(String.format(Locale.US, "upper=%.12g%n", input.upperThreshold()));
        writer.write(String.format(Locale.US, "lower=%.12g%n%n", input.lowerThreshold()));
        writer.write("external:\n");
        List<Map.Entry<String, Double>> entries = new ArrayList<>(input.externalUtilities().entrySet());
        entries.sort(Map.Entry.comparingByKey(InputWriter::naturalCompare));
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                writer.write(' ');
            }
            Map.Entry<String, Double> entry = entries.get(i);
            writer.write(entry.getKey() + "=" + format(entry.getValue()));
        }
        writer.write("\n");
    }

    private static void writeBatch(Batch batch, BufferedWriter writer) throws IOException {
        writer.write("\nbatch " + batch.name() + "\n");
        for (Transaction transaction : batch.transactions()) {
            writer.write(transaction.tid() + ":");
            List<Map.Entry<String, Double>> entries = new ArrayList<>(transaction.internalUtilities().entrySet());
            entries.sort(Comparator.comparing(Map.Entry::getKey, InputWriter::naturalCompare));
            for (Map.Entry<String, Double> entry : entries) {
                writer.write(" " + entry.getKey() + ":" + format(entry.getValue()));
            }
            writer.write("\n");
        }
    }

    private static int naturalCompare(String left, String right) {
        return left.compareTo(right);
    }

    private static String format(double value) {
        if (value == Math.rint(value)) {
            return String.format(Locale.US, "%.0f", value);
        }
        return String.format(Locale.US, "%.12g", value);
    }

    private static void createParent(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
