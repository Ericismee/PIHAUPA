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
    default String rawSha256(){return null;}
    default void cleanup() throws IOException {}

    /** Kịch bản kích thước: lấy prefix theo thứ tự gốc, chia lại 80/10/10. Vẫn đọc và kiểm tra toàn tệp. */
    static InputSource fraction(InputSource original, int percent) {
        if (!java.util.Set.of(20,25,40,50,60,75,80,100).contains(percent)) throw new IllegalArgumentException("Kích thước cần là 20, 25, 40, 50, 60, 75, 80 hoặc 100%.");
        if(percent==100)return original;
        return new InputSource() {
            public String name(){return original.name();}
            public String kind(){return "size-experiment";}
            public String format(){return original.format();}
            public Path file(){return original.file();}
            public int sizePercent(){return percent;}
            public String rawSha256(){return original.rawSha256();}
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

    default int insertionSteps(){return 0;}
    default double originalRatio(){return 0;}
    /** Kịch bản Fig.8–9: OR ban đầu và 9 lần chèn; không đổi ví dụ Tables 2–3 mặc định. */
    static InputSource insertion(InputSource original,int steps,double ratio) {
        if(steps==0)return original;
        if(steps!=9 || !(ratio>0 && ratio<1))throw new IllegalArgumentException("Kịch bản chèn cần 9 lần và 0 < OR < 1.");
        return new InputSource(){
            public String name(){return original.name();}
            public String kind(){return "insertion-experiment";}
            public String format(){return original.format();}
            public Path file(){return original.file();}
            public String rawSha256(){return original.rawSha256();}
            public int sizePercent(){return original.sizePercent();}
            public int insertionSteps(){return steps;}
            public double originalRatio(){return ratio;}
            public void cleanup()throws IOException{original.cleanup();}
            public InputData read(double su,double sl,Consumer<String> progress)throws IOException{
                InputData input=original.read(su,sl,progress);
                var rows=input.batches().stream().flatMap(b->b.transactions().stream()).toList();
                int first=(int)Math.ceil(rows.size()*ratio);
                if(first<1 || rows.size()-first<steps)throw new IllegalArgumentException("Không đủ giao dịch cho 9 lần chèn. Giảm OR hoặc dùng chia batch mặc định.");
                var batches=new java.util.ArrayList<Batch>();
                batches.add(new Batch("DB0",java.util.List.copyOf(rows.subList(0,first))));
                int from=first;
                for(int i=1;i<=steps;i++){
                    int to=first+(int)Math.ceil((rows.size()-first)*(double)i/steps);
                    batches.add(new Batch("DB"+i,java.util.List.copyOf(rows.subList(from,to))));from=to;
                }
                progress.accept("Kịch bản chèn: OR="+(100.*first/rows.size())+"% · 9 lần chèn đến 100%.");
                return new InputData(su,sl,input.externalUtilities(),java.util.List.copyOf(batches));
            }
        };
    }

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
    record FileRead(InputData input, String sha256) {}

    /** Một lượt đọc byte cho cả parse và checksum; decoder báo lỗi UTF-8 như Files.newBufferedReader. */
    static FileRead readFile(Path file, String name, boolean transactions, double su, double sl,
                             Consumer<String> progress) throws IOException {
        var before = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class);
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            InputData input;
            var decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
            try (var stream = new java.security.DigestInputStream(
                    new BufferedInputStream(Files.newInputStream(file), 65536), digest);
                 var reader = new BufferedReader(new InputStreamReader(stream, decoder), 65536)) {
                if (transactions) input = ArchiveDatasetReader.readTransactionsText(reader, name, su, sl, progress);
                else {
                    InputData parsed = InputParser.parse(reader);
                    input = new InputData(su, sl, parsed.externalUtilities(), parsed.batches());
                }
            }
            var after = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class);
            if (before.size() != after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())
                    || !java.util.Objects.equals(before.fileKey(), after.fileKey()))
                throw new IOException("Tệp đã thay đổi trong lúc đọc; hãy chạy lại với tệp ổn định.");
            progress.accept("Đọc/kiểm tra và SHA-256 hoàn tất trong một lượt tệp.");
            return new FileRead(input, java.util.HexFormat.of().formatHex(digest.digest()));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("Không khởi tạo được SHA-256.", e);
        }
    }

    static InputSource catalog(DatasetCatalog catalog, String id) {
        String name = catalog.name(id);
        return new InputSource() {
            private String sha256;
            public String name() { return name; }
            public Path file(){return catalog.path(id);}
            public String rawSha256(){return sha256;}
            public String kind(){return "catalog";}
            public String format(){return "transactions";}
            public InputData read(double su, double sl, Consumer<String> progress) throws IOException {
                FileRead read = readFile(file(), name, true, su, sl, progress);
                sha256 = read.sha256();
                return read.input();
            }
        };
    }
    static InputSource upload(Path file, String name, boolean transactions) {
        return new InputSource() {
            private String sha256;
            public String name() { return name; }
            public Path file(){return file;}
            public String rawSha256(){return sha256;}
            public String kind(){return "upload";}
            public String format(){return transactions?"transactions":"quantitative";}
            public void cleanup() throws IOException { Files.deleteIfExists(file); }
            public InputData read(double su, double sl, Consumer<String> progress) throws IOException {
                FileRead read = readFile(file, name, transactions, su, sl, progress);
                sha256 = read.sha256();
                return read.input();
            }
        };
    }
}

