package piHAUPA;

import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import vn.edu.dlu.pihaupa.application.*;

public final class RecoveryWriterTest {

  public static void main(String[] args) throws Exception {
    var constructor = AnalysisService.Job.class.getDeclaredConstructor(String.class, String.class);
    constructor.setAccessible(true);
    var job = constructor.newInstance(UUID.randomUUID().toString(), "recovery-test");
    job.state = "FAILED";
    job.report = AnalysisReport.run(InputParser.parseBuiltInSample());
    job.manifest = Map.of("test", true);
    job.measurements = Map.of("runtimeMs", job.report.elapsedNanos() / 1e6);
    job.oracle = Map.of("state", "PASSED");
    if (args.length > 0 && args[0].equals("stress")) {
      var original = job.report;
      var batch = original.batches().get(0);
      var components = batch.getClass().getRecordComponents();
      Object[] values = new Object[components.length];
      Class<?>[] types = new Class<?>[components.length];
      var rows = java.util.Collections.nCopies(2_000_000, batch.largePatterns().get(0));
      for (int i = 0; i < components.length; i++) {
        types[i] = components[i].getType();
        values[i] = components[i].getAccessor().invoke(batch);
        if (components[i].getName().equals("largePatterns")) values[i] = rows;
        if (components[i].getName().equals("preLargePatterns")) values[i] = java.util.List.of();
      }
      var largeBatch = (AnalysisReport.BatchView) batch
        .getClass()
        .getDeclaredConstructor(types)
        .newInstance(values);
      job.report = new AnalysisReport(
        original.upperThreshold(),
        original.lowerThreshold(),
        original.rescanPolicy(),
        1,
        original.transactionCount(),
        original.elapsedNanos(),
        original.memoryDeltaBytes(),
        original.peakMemoryBytes(),
        original.stats(),
        java.util.List.of(largeBatch),
        original.upperBounds(),
        rows
      );
      System.setProperty("pihaupa.diskReports", "true");
      if (job.report.toJson().length() > 100000) throw new AssertionError(
        "JSON transport must be bounded for in-flight legacy worker"
      );
      job.result = BulkReports.payload(job, "null", java.util.List.of());
      if (
        job.result.length() > 100000 || !job.result.contains("\"largePatternCount\":2000000")
      ) throw new AssertionError("large report must retain exact count with bounded preview");
    }
    Path directory = RecoveryWriter.write(job);
    try (ZipFile zip = new ZipFile(directory.resolve("recovered-report.zip").toFile())) {
      if (
        zip.getEntry("05-du-lieu-ky-thuat/summary.json") == null ||
        zip.getEntry("managed-patterns.csv") == null
      ) throw new AssertionError("missing recovery files");
      for (var batch : job.report.batches()) {
        var entry = zip.getEntry("02-tung-batch/batch-" + batch.index() + "-patterns.csv");
        try (
          var reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(
              zip.getInputStream(entry),
              java.nio.charset.StandardCharsets.UTF_8
            )
          )
        ) {
          if (
            reader.lines().count() !=
            1L + batch.largePatterns().size() + batch.preLargePatterns().size()
          ) throw new AssertionError("missing pattern rows");
        }
      }
      try (
        var in = new ZipInputStream(Files.newInputStream(directory.resolve("recovered-report.zip")))
      ) {
        while (in.getNextEntry() != null) in.transferTo(java.io.OutputStream.nullOutputStream());
      }
    }
    BulkReports.counts(directory, job.report);
    String page = BulkReports.page(job.id, 0, "ALL", "", 1);
    if (!page.contains("\"page\":1")) throw new AssertionError("disk pagination");
    System.out.println(
      "PASS streaming recovery counts, disk pagination and ZIP CRC" +
        (args.length > 0 ? " · 2 million rows under 128MiB" : "")
    );
  }
}
