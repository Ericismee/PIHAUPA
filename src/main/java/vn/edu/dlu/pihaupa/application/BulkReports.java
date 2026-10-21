package vn.edu.dlu.pihaupa.application;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import piHAUPA.*;
import vn.edu.dlu.pihaupa.support.Json;

/** Báo cáo lớn: số đếm đầy đủ, preview có nhãn, mẫu phân trang từ CSV trên đĩa. */
public final class BulkReports {

  private static final Set<String> PREVIEWS = ConcurrentHashMap.newKeySet();

  private BulkReports() {}

  public static Path directory(String id) {
    if (!id.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Invalid id");
    return Path.of(System.getenv().getOrDefault("PIHAUPA_RUN_ROOT", "results/runs"))
      .toAbsolutePath()
      .normalize()
      .resolve("recovered")
      .resolve(id);
  }

  public static boolean needed(AnalysisReport report) {
    long count = report.managedPatterns().size();
    for (var batch : report.batches())
      count += (long) batch.largePatterns().size() + batch.preLargePatterns().size();
    return count > Integer.getInteger("pihaupa.bulkThreshold", 50000);
  }

  public static String payload(
    AnalysisService.Job job,
    String comparison,
    List<Map<String, Object>> preview
  ) {
    var report = job.report;
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("upperThreshold", report.upperThreshold());
    r.put("lowerThreshold", report.lowerThreshold());
    r.put("rescanPolicy", report.rescanPolicy().name());
    r.put("batchCount", report.batchCount());
    r.put("transactionCount", report.transactionCount());
    r.put("elapsedMs", report.elapsedNanos() / 1e6);
    r.put("memoryDeltaMb", report.memoryDeltaBytes() / 1048576.);
    r.put("peakMemoryMb", report.peakMemoryBytes() / 1048576.);
    r.put("stats", record(report.stats()));
    List<Map<String, Object>> batches = new ArrayList<>();
    for (var batch : report.batches()) {
      Map<String, Object> b = record(batch);
      b.put("largePatterns", sample(batch.largePatterns()));
      b.put("preLargePatterns", sample(batch.preLargePatterns()));
      b.put("largePatternCount", batch.largePatterns().size());
      b.put("preLargePatternCount", batch.preLargePatterns().size());
      b.put("patternsLimited", true);
      batches.add(b);
    }
    r.put("batches", batches);
    r.put(
      "upperBounds",
      report
        .upperBounds()
        .entrySet()
        .stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .map(e -> Map.of("item", e.getKey(), "auub", e.getValue()))
        .toList()
    );
    r.put("managedPatterns", sample(report.managedPatterns()));
    r.put("managedPatternCount", report.managedPatterns().size());
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("id", job.id);
    envelope.put("source", job.source);
    envelope.put("createdAt", job.createdAt);
    envelope.put("bulk", true);
    envelope.put("recovered", "FAILED".equals(job.state));
    envelope.put("report", r);
    envelope.put("processLog", job.logLines());
    envelope.put("manifest", job.manifest);
    envelope.put("oracle", job.oracle);
    Map<String, Object> measurement = new LinkedHashMap<>(
      job.measurements == null ? Map.of() : job.measurements
    );
    if (measurement.get("resources") instanceof Map<?, ?> resources) {
      Map<String, Object> limited = new LinkedHashMap<>();
      resources.forEach((k, v) -> limited.put(k.toString(), v));
      if (limited.get("samples") instanceof List<?> samples) {
        limited.put("sampleCount", samples.size());
        limited.put("samples", samples.subList(Math.max(0, samples.size() - 600), samples.size()));
      }
      measurement.put("resources", limited);
    }
    envelope.put("measurement", measurement);
    envelope.put("preview", preview);
    envelope.put("externalUtilities", Map.of());
    String json = Json.encode(envelope);
    return json.substring(0, json.length() - 1) + ",\"comparison\":" + comparison + "}";
  }

  private static Map<String, Object> record(Object value) {
    Map<String, Object> map = new LinkedHashMap<>();
    try {
      for (var component : value.getClass().getRecordComponents()) {
        if (component.getName().endsWith("Patterns")) continue;
        Object item = component.getAccessor().invoke(value);
        map.put(component.getName(), item);
      }
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    return map;
  }

  private static List<Map<String, Object>> sample(List<PatternResult> patterns) {
    List<Map<String, Object>> sample = new ArrayList<>();
    for (int i = 0; i < Math.min(100, patterns.size()); i++) {
      PatternResult p = patterns.get(i);
      sample.add(
        pattern(p.displayName(), p.type().name(), p.length(), p.sumUtility(), p.averageUtility())
      );
    }
    return sample;
  }

  private static Map<String, Object> pattern(
    String name,
    String type,
    int length,
    double sum,
    double average
  ) {
    return Map.of(
      "name",
      name,
      "items",
      name,
      "type",
      type,
      "length",
      length,
      "sumUtility",
      sum,
      "averageUtility",
      average
    );
  }

  public static void counts(Path directory, AnalysisReport report) throws IOException {
    Properties properties = new Properties();
    for (var batch : report.batches()) {
      properties.setProperty(
        batch.index() + ".LARGE",
        Integer.toString(batch.largePatterns().size())
      );
      properties.setProperty(
        batch.index() + ".PRE_LARGE",
        Integer.toString(batch.preLargePatterns().size())
      );
    }
    try (
      var out = Files.newBufferedWriter(
        directory.resolve("counts.properties"),
        StandardCharsets.UTF_8
      )
    ) {
      properties.store(out, "Exact pattern counts");
    }
  }

  /** Chỉ tạo preview sau khi gói cứu hộ hoàn tất; không sửa trạng thái FAILED gốc. */
  public static void recoverPreview(AnalysisService.Job job) {
    Path dir = directory(job.id);
    if (
      job.report == null ||
      !Files.exists(dir.resolve("recovered-report.zip")) ||
      !PREVIEWS.add(job.id)
    ) return;
    Thread thread = new Thread(() -> {
      try {
        counts(dir, job.report);
        Files.writeString(dir.resolve("BAO_CAO.html"), html(job), StandardCharsets.UTF_8);
        Files.writeString(
          dir.resolve("web-result.json"),
          payload(job, "null", List.of()),
          StandardCharsets.UTF_8
        );
        Path published = dir.getParent().getParent().resolve(job.id);
        if (!Files.exists(published)) {
          Path temp = Files.createTempDirectory(published.getParent(), ".recovered-");
          Files.copy(dir.resolve("web-result.json"), temp.resolve("result.json"));
          Files.copy(dir.resolve("manifest.json"), temp.resolve("manifest.json"));
          Files.copy(dir.resolve("recovered-report.zip"), temp.resolve("analysis.zip"));
          Files.move(temp, published);
        }
      } catch (IOException error) {
        PREVIEWS.remove(job.id);
      }
    }, "pihaupa-recovered-preview");
    thread.setDaemon(true);
    thread.start();
  }

  public static String html(AnalysisService.Job job) {
    Set<String> names = new LinkedHashSet<>(
      List.of(
        "BAO_CAO.html",
        "managed-patterns.csv",
        "manifest.json",
        "measurements.json",
        "oracle.json",
        "process.log",
        "summary.json",
        "counts.properties"
      )
    );
    for (var batch : job.report.batches()) names.add("batch-" + batch.index() + "-patterns.csv");
    return ReportDocument.html(job, names, true);
  }

  public static String page(String id, int batch, String type, String search, int page)
    throws IOException {
    if (
      batch < 0 ||
      page < 0 ||
      page > 100000000 ||
      !Set.of("ALL", "LARGE", "PRE_LARGE").contains(type)
    ) throw new IllegalArgumentException("Invalid page/filter");
    Path dir = directory(id);
    Properties counts = new Properties();
    try (
      var in = Files.newBufferedReader(dir.resolve("counts.properties"), StandardCharsets.UTF_8)
    ) {
      counts.load(in);
    }
    long total = type.equals("ALL")
      ? Long.parseLong(counts.getProperty(batch + ".LARGE", "0")) +
        Long.parseLong(counts.getProperty(batch + ".PRE_LARGE", "0"))
      : Long.parseLong(counts.getProperty(batch + "." + type, "0"));
    long offset = 25L * page,
      matched = 0;
    List<Map<String, Object>> rows = new ArrayList<>(),
      top = new ArrayList<>();
    String needle = search.toLowerCase(Locale.ROOT);
    try (
      var in = Files.newBufferedReader(
        dir.resolve("batch-" + batch + "-patterns.csv"),
        StandardCharsets.UTF_8
      )
    ) {
      in.readLine();
      String line;
      while ((line = in.readLine()) != null) {
        int quote = line.lastIndexOf("\",");
        String name = line.substring(1, quote).replace("\"\"", "\"");
        String[] fields = line.substring(quote + 2).split(",");
        if (!type.equals("ALL") && !fields[0].equals(type)) continue;
        if (!needle.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(needle)) continue;
        if (top.size() < 12 || (matched >= offset && rows.size() < 25)) {
          var p = pattern(
            name,
            fields[0],
            Integer.parseInt(fields[1]),
            Double.parseDouble(fields[2]),
            Double.parseDouble(fields[3])
          );
          if (top.size() < 12) top.add(p);
          if (matched >= offset && rows.size() < 25) rows.add(p);
        }
        matched++;
        if (needle.isEmpty() && rows.size() == 25 && top.size() == 12) break;
      }
    }
    return Json.encode(
      Map.of(
        "patterns",
        rows,
        "top",
        top,
        "total",
        needle.isEmpty() ? total : matched,
        "page",
        page,
        "pageSize",
        25
      )
    );
  }
}
