package vn.edu.dlu.pihaupa.application;

import piHAUPA.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Repository: catalog IDs resolve only to transactions.txt beneath the configured root. */
/**
 * DESIGN PATTERN — REPOSITORY (danh mục dữ liệu trên filesystem).
 * Che việc tìm/mở transactions.txt sau các thao tác list/name/read theo ID.
 * Chỉ đăng ký real path nằm dưới root; client không chọn đường dẫn tùy ý.
 * Không phải ORM/database repository; danh mục được lập lại khi khởi động server.
 */
public final class DatasetCatalog {
    private final Map<String, Path> files = new LinkedHashMap<>();
    public DatasetCatalog(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root)) return;
        Path realRoot = root.toRealPath();
        try (var paths = Files.walk(realRoot, 3)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().equals("transactions.txt")).sorted().toList()) {
                Path real = path.toRealPath();
                if (real.startsWith(realRoot)) files.put("dataset-" + files.size(), real);
            }
        }
    }
    public List<Map<String, Object>> list() throws IOException {
        List<Map<String,Object>> result = new ArrayList<>();
        for (var entry : files.entrySet()) {
            String name = entry.getValue().getParent().getFileName().toString();
            long bytes = Files.size(entry.getValue());
            DatasetProfile profile = DatasetProfile.find(name,bytes);
            Map<String,Object> dataset = new LinkedHashMap<>();
            dataset.put("id",entry.getKey()); dataset.put("name",name); dataset.put("bytes",bytes);
            dataset.put("profile",profile == null ? null : profile.toMap());
            result.add(dataset);
        }
        return result;
    }
    public String name(String id) { return path(id).getParent().getFileName().toString(); }
    public Path path(String id) {
        Path path = files.get(id);
        if (path == null) throw new IllegalArgumentException("Bộ dữ liệu không tồn tại trong danh mục.");
        return path;
    }
    public InputData read(String id, double su, double sl, Consumer<String> progress) throws IOException {
        try (var reader = Files.newBufferedReader(path(id), StandardCharsets.UTF_8)) {
            return ArchiveDatasetReader.readTransactionsText(reader, name(id), su, sl, progress);
        }
    }
}

