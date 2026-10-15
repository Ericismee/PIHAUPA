package vn.edu.dlu.pihaupa.application;

import piHAUPA.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Consumer;

/** Strategy: built-in paper sample, catalog dataset, and uploaded text share one contract. */
/**
 * DESIGN PATTERN — STRATEGY (chiến lược nguồn dữ liệu).
 * Cùng một contract read/name/cleanup, nhưng paper/catalog/upload đọc theo cách riêng.
 * AnalysisService chỉ gọi contract này, không cần biết file đến từ đâu.
 * Lợi ích: dễ thay/thêm nguồn và kiểm thử; không tự tăng tốc thuật toán khai phá.
 * Các phương thức paper/catalog/upload là static factory, không phải GoF Factory Method.
 */
public interface InputSource {
    InputData read(double su, double sl, Consumer<String> progress) throws IOException;
    String name();
    default String kind(){return "custom";}
    default String format(){return "quantitative";}
    default Path file(){return null;}
    default void cleanup() throws IOException {}

    /** Kịch bản kích thước: lấy prefix theo thứ tự gốc, chia lại 80/10/10. Vẫn đọc và kiểm tra toàn tệp. */
    static InputSource fraction(InputSource original, int percent) {
        if (!java.util.Set.of(25,50,75,100).contains(percent)) throw new IllegalArgumentException("Kích thước cần là 25, 50, 75 hoặc 100%.");
        if(percent==100)return original;
        return new InputSource() {
            public String name(){return original.name();}
            public String kind(){return "size-experiment";}
            public String format(){return original.format();}
            public Path file(){return original.file();}
            public int sizePercent(){return percent;}
            public void cleanup()throws IOException{original.cleanup();}
            public InputData read(double su,double sl,Consumer<String> progress)throws IOException{
                InputData full=original.read(su,sl,progress);
                var all=full.batches().stream().flatMap(b->b.transactions().stream()).toList();
                int count=Math.max(1,(int)((long)all.size()*percent/100));
                var rows=all.subList(0,count);
                int first=Math.max(1,count*80/100), second=first+(count-first)/2;
                progress.accept("Kịch bản kích thước: "+percent+"% · "+count+"/"+all.size()+" giao dịch đầu; chia lại 80/10/10.");
                return new InputData(su,sl,full.externalUtilities(),java.util.List.of(
                    new Batch("DB0",java.util.List.copyOf(rows.subList(0,first))),
                    new Batch("DB1",java.util.List.copyOf(rows.subList(first,second))),
                    new Batch("DB2",java.util.List.copyOf(rows.subList(second,count)))));
            }
        };
    }
    default int sizePercent(){return 100;}

    static InputSource paper() {
        return new InputSource() {
            public String name() { return "Bài báo · Tables 2–3"; }
            public String kind(){return "paper";}
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
            public Path file(){return catalog.path(id);}
            public String kind(){return "catalog";}
            public String format(){return "transactions";}
            public InputData read(double su, double sl, Consumer<String> progress) throws IOException {
                return catalog.read(id, su, sl, progress);
            }
        };
    }
    static InputSource upload(Path file, String name, boolean transactions) {
        return new InputSource() {
            public String name() { return name; }
            public Path file(){return file;}
            public String kind(){return "upload";}
            public String format(){return transactions?"transactions":"quantitative";}
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

