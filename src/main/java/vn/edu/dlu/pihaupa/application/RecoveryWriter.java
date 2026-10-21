package vn.edu.dlu.pihaupa.application;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.*;
import piHAUPA.*;
import vn.edu.dlu.pihaupa.support.Json;

/** Ghi cứu hộ tuần tự từ report đã tính; không mining lại, không gom CSV/ZIP vào RAM. */
public final class RecoveryWriter {

  private static final Map<String, String> STATES = new ConcurrentHashMap<>();

  private RecoveryWriter() {}

  public static String state(AnalysisService.Job job) {
    if (job.report == null || !"FAILED".equals(job.state)) return "";
    if (STATES.putIfAbsent(job.id, "WRITING") == null) {
      Thread writer = new Thread(() -> {
        try {
          STATES.put(job.id, "SAVED: " + write(job));
        } catch (Throwable error) {
          STATES.put(job.id, "FAILED: " + error);
        }
      }, "pihaupa-recovery-" + job.id);
      writer.setDaemon(true);
      writer.start();
    }
    return STATES.get(job.id);
  }

  public static Path write(AnalysisService.Job job) throws IOException {
    AnalysisReport report = Objects.requireNonNull(job.report);
    Path root = Path.of(System.getenv().getOrDefault("PIHAUPA_RUN_ROOT", "results/runs"))
      .toAbsolutePath()
      .normalize()
      .resolve("recovered");
    Files.createDirectories(root);
    if (!job.id.matches("[a-f0-9-]{36}")) throw new IOException("Invalid job id");
    Path destination = root.resolve(job.id);
    Path temp = Files.createTempDirectory(root, ".writing-");
    try {
      List<Map<String, Object>> batches = new ArrayList<>();
      for (var batch : report.batches()) {
        String file = "batch-" + batch.index() + "-patterns.csv";
        long count;
        try (
          BufferedWriter out = Files.newBufferedWriter(temp.resolve(file), StandardCharsets.UTF_8)
        ) {
          out.write("pattern,type,length,sumUtility,averageUtility\n");
          count = patterns(out, batch.largePatterns()) + patterns(out, batch.preLargePatterns());
        }
        batches.add(
          Map.of(
            "batch",
            batch.name(),
            "file",
            file,
            "rows",
            count,
            "haups",
            batch.largePatterns().size(),
            "preLarge",
            batch.preLargePatterns().size(),
            "TU",
            batch.totalTransactionUtility(),
            "upper",
            batch.minUtilUpper(),
            "lower",
            batch.minUtilLower()
          )
        );
      }
      long managed;
      try (
        BufferedWriter out = Files.newBufferedWriter(
          temp.resolve("managed-patterns.csv"),
          StandardCharsets.UTF_8
        )
      ) {
        out.write("pattern,type,length,sumUtility,averageUtility\n");
        managed = patterns(out, report.managedPatterns());
      }
      Map<String, Object> summary = new LinkedHashMap<>();
      summary.put("id", job.id);
      summary.put("source", job.source);
      summary.put("createdAt", job.createdAt);
      summary.put("recovered", true);
      summary.put("originalJobState", job.state);
      summary.put("transactionCount", report.transactionCount());
      summary.put("Su", report.upperThreshold());
      summary.put("Sl", report.lowerThreshold());
      summary.put("algorithmMs", report.elapsedNanos() / 1e6);
      summary.put("rescanCount", report.stats().rescanCount());
      summary.put("patternsVisited", report.stats().patternsVisited());
      summary.put("combinedNodes", report.stats().combinedNodes());
      summary.put("managedRows", managed);
      summary.put("batches", batches);
      Files.writeString(temp.resolve("summary.json"), Json.encode(summary), StandardCharsets.UTF_8);
      Files.writeString(
        temp.resolve("manifest.json"),
        Json.encode(job.manifest),
        StandardCharsets.UTF_8
      );
      Files.writeString(
        temp.resolve("measurements.json"),
        Json.encode(job.measurements),
        StandardCharsets.UTF_8
      );
      Files.writeString(
        temp.resolve("oracle.json"),
        Json.encode(job.oracle),
        StandardCharsets.UTF_8
      );
      Files.write(temp.resolve("process.log"), job.logLines(), StandardCharsets.UTF_8);
      Set<String> names = new LinkedHashSet<>();
      try (var entries = Files.list(temp)) {
        entries.forEach(file -> names.add(file.getFileName().toString()));
      }
      names.add("counts.properties");
      names.add("BAO_CAO.html");
      Files.writeString(
        temp.resolve("README.txt"),
        ExportGuide.readme(names),
        StandardCharsets.UTF_8
      );
      Files.writeString(
        temp.resolve("BAO_CAO.html"),
        BulkReports.html(job),
        StandardCharsets.UTF_8
      );
      BulkReports.counts(temp, report);
      try (
        ZipOutputStream zip = new ZipOutputStream(
          new BufferedOutputStream(Files.newOutputStream(temp.resolve("recovered-report.zip")))
        )
      ) {
        try (var files = Files.list(temp)) {
          for (Path file : files
            .filter(f -> !f.getFileName().toString().endsWith(".zip"))
            .sorted()
            .toList()) {
            zip.putNextEntry(new ZipEntry(ExportGuide.path(file.getFileName().toString())));
            Files.copy(file, zip);
            zip.closeEntry();
          }
        }
      }
      Files.move(temp, destination);
      return destination;
    } finally {
      if (Files.exists(temp)) try (var files = Files.walk(temp)) {
        for (Path file : files.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(file);
      }
    }
  }

  private static long patterns(Writer out, List<PatternResult> patterns) throws IOException {
    long count = 0;
    for (PatternResult pattern : patterns) {
      if (
        (count & 4095) == 0 && Thread.currentThread().isInterrupted()
      ) throw new InterruptedIOException("Report export interrupted");
      out.write('"');
      out.write(pattern.displayName().replace("\"", "\"\""));
      out.write("\",");
      out.write(pattern.type().name());
      out.write(',');
      out.write(Integer.toString(pattern.length()));
      out.write(',');
      out.write(Double.toString(pattern.sumUtility()));
      out.write(',');
      out.write(Double.toString(pattern.averageUtility()));
      out.write('\n');
      count++;
    }
    return count;
  }
}
