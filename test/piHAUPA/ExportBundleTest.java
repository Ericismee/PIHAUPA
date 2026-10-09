package piHAUPA;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;

public final class ExportBundleTest {
    private ExportBundleTest() { }

    public static void main(String[] args) throws Exception {
        InputData input = InputParser.parseBuiltInSample();
        ComparisonReport comparison = ComparisonReport.run(input);
        Path output = Files.createTempDirectory("pihaupa-test-export-");
        try {
            AnalysisReport tight = AnalysisReport.run(input);
            String stepTxt = ExportBundle.reportText(input, tight, "paper-example.txt");
            require(stepTxt.equals(ExportBundle.reportText(input.externalUtilities(), tight, "paper-example.txt")),
                    "TXT xuất từ cache phải trùng TXT xuất từ dữ liệu gốc.");
            for (int step = 1; step <= 5; step++)
                require(stepTxt.contains("Bước " + step + " -"), "TXT phải có Bước " + step);
            require(stepTxt.contains("Vế trái = MU(ID) − Su×TU(ID)")
                    && stepTxt.contains("Vế phải = (Su−Sl)×TU(OD)")
                    && stepTxt.contains("KHÔNG re-scan dữ liệu cũ")
                    && stepTxt.contains("CÓ re-scan toàn bộ dữ liệu"),
                    "TXT phải ghi rõ hai vế Eq.(12) và lý do quyết định từng batch.");
            String stepCsv = ExportBundle.reportCsv(input, tight, "paper-example.txt");
            require(stepCsv.equals(ExportBundle.reportCsv(input.externalUtilities(), tight, "paper-example.txt")),
                    "CSV xuất từ cache phải trùng CSV xuất từ dữ liệu gốc.");
            for (int step = 1; step <= 5; step++)
                require(stepCsv.contains("\"Bước " + step + "\","), "CSV phải dùng nhãn Bước " + step);
            require(stepCsv.contains("Bước 1 - Đọc dữ liệu")
                    && stepCsv.contains("Bước 3 - Quyết định re-scan")
                    && stepCsv.contains("Bước 4 - Khai phá/cập nhật")
                    && stepCsv.contains("Bước 5 - Phân loại mẫu")
                    && stepCsv.contains("finalLargeCount")
                    && stepCsv.contains("Eq12Left")
                    && stepCsv.contains("Eq12Right")
                    && stepCsv.contains("rescanDecision"),
                    "CSV phải có các bước, số liệu đối chiếu và kết luận re-scan.");
            Path tightOutput = output.resolve("tight");
            ExportBundle.writeDirectory(input, tight, tightOutput, "paper-example.txt");
            require(Files.readString(tightOutput.resolve("BAO_CAO.txt"), StandardCharsets.UTF_8)
                    .equals(stepTxt), "TXT ghi ra tệp phải trùng nội dung báo cáo từng bước.");
            require(Files.readString(tightOutput.resolve("bao-cao.csv"), StandardCharsets.UTF_8)
                    .equals(stepCsv), "CSV ghi ra tệp phải trùng nội dung báo cáo từng bước.");
            ExportBundle.writeDirectory(input, comparison, output, "paper-example.txt");
            for (String name : Set.of("BAO_CAO.txt", "bao-cao.csv", "pihaupa-input.txt",
                    "patterns.csv", "performance-by-rows.csv", "comparison.csv")) {
                require(Files.isRegularFile(output.resolve(name)), "Missing export file: " + name);
            }
            String txt = Files.readString(output.resolve("BAO_CAO.txt"), StandardCharsets.UTF_8);
            require(txt.contains("84.000000") && txt.contains("75.000000") && txt.contains("63.500000"),
                    "Paper final AU values must appear in TXT.");
            require(txt.contains("Re-scan Eq.(12)") && txt.contains("TU(ID)"),
                    "TXT must explain re-scan decision.");
            require(txt.contains("Original=") && txt.contains("Tight="),
                    "TXT must compare both policies.");
            String csv = Files.readString(output.resolve("bao-cao.csv"), StandardCharsets.UTF_8);
            require(csv.contains("tightEq12Left") && csv.contains("DB2")
                    && csv.contains("84.000000"), "CSV must include formulas and final AU.");
            require(!Files.exists(output.resolve("BAO_CAO.html")), "Export must not create HTML.");
            require(!Files.exists(output.resolve("report.json")), "Export must not create JSON.");
            try {
                InputParser.parseText("72157607317745768 1808093328 277 159 218 294 1 1\n");
                throw new AssertionError("PIPA metadata must be rejected.");
            } catch (IllegalArgumentException expected) {
                require(expected.getMessage().contains("PIPA all_data.txt"),
                        "PIPA error must explain the incompatible dataset.");
            }
            System.out.println("Export and input-format tests passed.");
        } finally {
            try (var paths = Files.walk(output)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
