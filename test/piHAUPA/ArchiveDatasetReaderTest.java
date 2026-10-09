package piHAUPA;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ArchiveDatasetReaderTest {
    private ArchiveDatasetReaderTest() { }

    public static void main(String[] args) throws Exception {
        byte[] archive = archive(false);
        Path temporaryArchive = Files.createTempFile("pihaupa-archive-test-", ".zip");
        try {
            Files.write(temporaryArchive, archive);
            assert ArchiveDatasetReader.datasetNames(temporaryArchive).equals(List.of("First", "Second"));
        } finally {
            Files.deleteIfExists(temporaryArchive);
        }
        List<ArchiveDatasetReader.DatasetAudit> audits = ArchiveDatasetReader.inspect(
                new ByteArrayInputStream(archive), ignored -> { });
        assert audits.size() == 2;
        for (ArchiveDatasetReader.DatasetAudit audit : audits) {
            assert audit.rows() == 2;
            assert audit.items() == 2;
            assert audit.investmentRows() == 2;
            assert audit.missingInvestmentItems() == 0;
            assert audit.mismatchedTransactionUtilities() == 0;
            assert audit.totalTransactionUtility() == 16;
            assert audit.totalMaximumUtility() == 12;
            assert audit.strongestUpperBound() == 12;
        }
        InputData input = ArchiveDatasetReader.read(new ByteArrayInputStream(archive), "Second",
                0.23, 0.10, ignored -> { });
        assert input.batches().stream().mapToInt(batch -> batch.transactions().size()).sum() == 2;
        assert input.externalUtilities().get("1") == 1.0;
        assert input.batches().get(0).transactions().get(0).utilityOf("1", input.externalUtilities()) == 4;
        assert new PatternResult(List.of("1", "23"), 10, 2, PatternType.LARGE)
                .displayName().equals("{1, 23}");
        assert ComparisonReport.run(input).sameHaups();

        byte[] wrong = archive(true);
        assert ArchiveDatasetReader.inspect(new ByteArrayInputStream(wrong), ignored -> { })
                .get(0).mismatchedTransactionUtilities() == 1;
        boolean rejected = false;
        try {
            ArchiveDatasetReader.read(new ByteArrayInputStream(wrong), "First", 0.23, 0.10,
                    ignored -> { });
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage().contains("sai TU");
        }
        assert rejected;
        System.out.println("Archive import and TU validation tests passed.");
    }

    private static byte[] archive(boolean wrong) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (String name : List.of("First", "Second")) {
                write(zip, "Data/" + name + "-Metadata/transactions.txt",
                        "1 2:" + (wrong && name.equals("First") ? "9" : "10")
                                + ":4 6\n1:6:6\n");
                write(zip, "Data/" + name + "-Metadata/investment_table.txt",
                        "ItemID\tTotal Investment\n==============================\n1\t99.00\n2\t21.00\n");
            }
        }
        return bytes.toByteArray();
    }

    private static void write(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
