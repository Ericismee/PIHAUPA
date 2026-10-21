package vn.edu.dlu.pihaupa.application;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import piHAUPA.*;

/** Dựng lại bố cục từ ZIP đã hoàn tất; không đọc input hoặc gọi miner. */
public final class RebuildReport {

  private RebuildReport() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 2) throw new IllegalArgumentException(
      "Cách dùng: RebuildReport <ZIP gốc> <thư mục mới>"
    );
    Path input = Path.of(args[0]).toAbsolutePath().normalize();
    Path output = Path.of(args[1]).toAbsolutePath().normalize();
    if (Files.exists(output)) throw new IOException(
      "Thư mục đích đã tồn tại; chọn thư mục mới để giữ bản gốc."
    );
    Map<String, String> files = new LinkedHashMap<>();
    long remaining = 128L * 1024 * 1024;
    try (ZipFile zip = new ZipFile(input.toFile(), StandardCharsets.UTF_8)) {
      var entries = zip.entries();
      while (entries.hasMoreElements()) {
        var entry = entries.nextElement();
        if (entry.isDirectory()) continue;
        if (
          files.size() >= 1000 || entry.getSize() < 0 || entry.getSize() > remaining
        ) throw new IOException("Gói quá lớn để dựng lại trong RAM; cần đường xuất CSV tuần tự.");
        String name = entry.getName().replace('\\', '/');
        Path relative = Path.of(name).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) throw new IOException(
          "Đường dẫn ZIP không hợp lệ."
        );
        String base = relative.getFileName().toString();
        if (files.containsKey(base)) throw new IOException("Trùng tên tệp trong ZIP: " + base);
        try (var in = zip.getInputStream(entry)) {
          byte[] bytes = in.readNBytes((int) remaining + 1);
          if (bytes.length > remaining) throw new IOException("ZIP vượt giới hạn giải nén.");
          remaining -= bytes.length;
          files.put(
            base,
            StandardCharsets.UTF_8.newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes))
              .toString()
          );
        }
      }
    }
    if (!files.containsKey("report.json")) throw new IOException(
      "Cần report.json đầy đủ; không dùng preview của gói CSV lớn để suy ra kết quả."
    );
    Map<String, Object> root = map(new JsonInput(files.get("report.json")).read());
    var job = job(root);
    files.put("BAO_CAO.html", ReportDocument.html(job, files.keySet(), false));
    files.put("README.txt", ExportGuide.readme(files.keySet()));
    Map<String, String> organized = ExportGuide.organize(files);
    Files.createDirectories(output);
    for (var entry : organized.entrySet()) {
      Path destination = output.resolve(entry.getKey()).normalize();
      if (!destination.startsWith(output)) throw new IOException(
        "Đích xuất nằm ngoài thư mục báo cáo."
      );
      Files.createDirectories(destination.getParent());
      Files.writeString(destination, entry.getValue(), StandardCharsets.UTF_8);
    }
    Files.write(output.resolve("bao-cao-pihaupa.zip"), ReportArchive.zip(organized));
    System.out.println(
      "Đã dựng lại từ số liệu gốc, không khai phá lại: " + output.resolve("BAO_CAO.html")
    );
  }

  private static AnalysisService.Job job(Map<String, Object> root) throws Exception {
    Map<String, Object> r = map(root.get("report"));
    if (Boolean.TRUE.equals(r.get("patternsLimited"))) throw new IOException(
      "report.json chỉ là preview, không dựng thành báo cáo đầy đủ."
    );
    var job = new AnalysisService.Job(
      String.valueOf(root.get("id")),
      String.valueOf(root.get("source"))
    );
    job.state = "COMPLETED";
    job.manifest = map(root.get("manifest"));
    job.measurements = map(root.get("measurement"));
    job.oracle = map(root.get("oracle"));
    job.result = "";
    List<AnalysisReport.BatchView> batches = new ArrayList<>();
    var components = AnalysisReport.BatchView.class.getRecordComponents();
    Class<?>[] types = Arrays.stream(components)
      .map(java.lang.reflect.RecordComponent::getType)
      .toArray(Class<?>[]::new);
    for (Object value : list(r.get("batches"))) {
      var b = map(value);
      Object[] arguments = new Object[components.length];
      for (int i = 0; i < components.length; i++) {
        Object field = b.get(components[i].getName());
        Class<?> type = types[i];
        arguments[i] =
          type == List.class
            ? patterns(field)
            : type == int.class
              ? ((Number) field).intValue()
              : type == double.class
                ? ((Number) field).doubleValue()
                : type == Double.class
                  ? field == null
                    ? null
                    : ((Number) field).doubleValue()
                  : field;
      }
      batches.add(
        AnalysisReport.BatchView.class.getDeclaredConstructor(types).newInstance(arguments)
      );
    }
    var s = map(r.get("stats"));
    Map<String, Double> bounds = new LinkedHashMap<>();
    for (Object value : list(r.get("upperBounds"))) {
      var b = map(value);
      bounds.put(String.valueOf(b.get("item")), number(b, "auub"));
    }
    job.report = new AnalysisReport(
      number(r, "upperThreshold"),
      number(r, "lowerThreshold"),
      RescanPolicy.valueOf(String.valueOf(r.get("rescanPolicy"))),
      (int) number(r, "batchCount"),
      (int) number(r, "transactionCount"),
      Math.round(number(r, "elapsedMs") * 1e6),
      Math.round(number(r, "memoryDeltaMb") * 1048576),
      Math.round(number(r, "peakMemoryMb") * 1048576),
      new MiningStats(
        (long) number(s, "rescanCount"),
        (long) number(s, "patternsVisited"),
        (long) number(s, "combinedNodes"),
        number(s, "totalTransactionUtilityProcessed"),
        number(s, "totalMaximumUtilityProcessed")
      ),
      batches,
      bounds,
      patterns(r.get("managedPatterns"))
    );
    List<Double> samples = new ArrayList<>();
    for (Object value : list(job.measurements.get("samplesMs")))
      samples.add(((Number) value).doubleValue());
    job.timingSamples = samples;
    return job;
  }

  private static List<PatternResult> patterns(Object value) {
    List<PatternResult> result = new ArrayList<>();
    for (Object element : list(value)) {
      var p = map(element);
      result.add(
        new PatternResult(
          List.of(String.valueOf(p.get("items")).split(",")),
          number(p, "sumUtility"),
          (int) number(p, "length"),
          PatternType.valueOf(String.valueOf(p.get("type")))
        )
      );
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
  }

  private static List<?> list(Object value) {
    return value instanceof List<?> values ? values : List.of();
  }

  private static double number(Map<String, Object> map, String key) {
    return ((Number) map.get(key)).doubleValue();
  }

  /** Bộ đọc JSON giới hạn cho CLI nhập lại báo cáo; không thực thi bất kỳ nội dung tệp nào. */
  private static final class JsonInput {

    private final String text;
    private int position;

    JsonInput(String text) {
      this.text = text.startsWith("\ufeff") ? text.substring(1) : text;
    }

    Object read() {
      Object value = value(0);
      space();
      if (position != text.length()) throw error();
      return value;
    }

    private Object value(int depth) {
      if (depth > 128) throw error();
      space();
      char ch = peek();
      if (ch == '"') return string();
      if (ch == '{') {
        position++;
        Map<String, Object> result = new LinkedHashMap<>();
        space();
        if (peek() == '}') {
          position++;
          return result;
        }
        do {
          space();
          String key = string();
          space();
          expect(':');
          if (result.containsKey(key)) throw error();
          result.put(key, value(depth + 1));
          space();
          if (peek() == '}') {
            position++;
            return result;
          }
          expect(',');
        } while (true);
      }
      if (ch == '[') {
        position++;
        List<Object> result = new ArrayList<>();
        space();
        if (peek() == ']') {
          position++;
          return result;
        }
        do {
          result.add(value(depth + 1));
          space();
          if (peek() == ']') {
            position++;
            return result;
          }
          expect(',');
        } while (true);
      }
      for (String literal : List.of("true", "false", "null"))
        if (text.startsWith(literal, position)) {
          position += literal.length();
          return literal.equals("null") ? null : Boolean.valueOf(literal);
        }
      int first = position;
      while (position < text.length() && "-+0123456789.eE".indexOf(text.charAt(position)) >= 0)
        position++;
      String token = text.substring(first, position);
      if (!token.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw error();
      double number = Double.parseDouble(token);
      if (!Double.isFinite(number)) throw error();
      return number;
    }

    private String string() {
      expect('"');
      StringBuilder out = new StringBuilder();
      while (position < text.length()) {
        char ch = text.charAt(position++);
        if (ch == '"') return out.toString();
        if (ch < 32) throw error();
        if (ch != '\\') {
          out.append(ch);
          continue;
        }
        char escaped = text.charAt(position++);
        switch (escaped) {
          case '"', '\\', '/' -> out.append(escaped);
          case 'n' -> out.append('\n');
          case 'r' -> out.append('\r');
          case 't' -> out.append('\t');
          case 'b' -> out.append('\b');
          case 'f' -> out.append('\f');
          case 'u' -> {
            out.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
            position += 4;
          }
          default -> throw error();
        }
      }
      throw error();
    }

    private void space() {
      while (position < text.length() && " \r\n\t".indexOf(text.charAt(position)) >= 0) position++;
    }

    private char peek() {
      if (position >= text.length()) throw error();
      return text.charAt(position);
    }

    private void expect(char value) {
      if (peek() != value) throw error();
      position++;
    }

    private IllegalArgumentException error() {
      return new IllegalArgumentException("JSON báo cáo không hợp lệ tại vị trí " + position);
    }
  }
}
