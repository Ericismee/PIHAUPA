package piHAUPA;

import java.util.*;
import vn.edu.dlu.pihaupa.support.Json;

/** JSON preview with exact counts for the web disk-report mode; full patterns stay in CSV. */
public final class ReportJsonView {

  private ReportJsonView() {}

  public static String encode(AnalysisReport report) {
    return Json.encode(map(report));
  }

  public static Map<String, Object> map(AnalysisReport report) {
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
    r.put("patternsLimited", true);
    return r;
  }

  private static Map<String, Object> record(Object value) {
    Map<String, Object> map = new LinkedHashMap<>();
    try {
      for (var component : value.getClass().getRecordComponents()) {
        if (component.getName().endsWith("Patterns")) continue;
        map.put(component.getName(), component.getAccessor().invoke(value));
      }
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException(error);
    }
    return map;
  }

  private static List<Map<String, Object>> sample(List<PatternResult> patterns) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (int i = 0; i < Math.min(100, patterns.size()); i++) {
      var p = patterns.get(i);
      result.add(
        Map.of(
          "name",
          p.displayName(),
          "items",
          String.join(",", p.items()),
          "type",
          p.type().name(),
          "length",
          p.length(),
          "sumUtility",
          p.sumUtility(),
          "averageUtility",
          p.averageUtility()
        )
      );
    }
    return result;
  }
}
