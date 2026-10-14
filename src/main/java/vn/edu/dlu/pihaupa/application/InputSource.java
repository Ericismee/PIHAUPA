package vn.edu.dlu.pihaupa.application;

import piHAUPA.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Consumer;

/** Strategy: built-in paper sample, catalog dataset, and uploaded text share one contract. */
public interface InputSource {
    InputData read(double su, double sl, Consumer<String> progress) throws IOException;
    String name();
    default void cleanup() throws IOException {}

    static InputSource paper() {
        return new InputSource() {
            public String name() { return "Bài báo · Tables 2–3"; }
            public InputData read(double su, double sl, Consumer<String> progress) {
                InputData sample = InputParser.parseBuiltInSample();
                return new InputData(su, sl, sample.externalUtilities(), sample.batches());
            }
        };
    }
    static InputSource catalog(DatasetCatalog catalog, String id) {
        String name = catalog.name(id);
        return new InputSource() {
            public String name() { return name; }
            public InputData read(double su, double sl, Consumer<String> progress) throws IOException {
                return catalog.read(id, su, sl, progress);
            }
        };
    }
    static InputSource upload(Path file, String name, boolean transactions) {
        return new InputSource() {
            public String name() { return name; }
            public void cleanup() throws IOException { Files.deleteIfExists(file); }
            public InputData read(double su, double sl, Consumer<String> progress) throws IOException {
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    if (transactions) return ArchiveDatasetReader.readTransactionsText(reader, name, su, sl, progress);
                    InputData parsed = InputParser.parse(reader);
                    return new InputData(su, sl, parsed.externalUtilities(), parsed.batches());
                }
            }
        };
    }
}

