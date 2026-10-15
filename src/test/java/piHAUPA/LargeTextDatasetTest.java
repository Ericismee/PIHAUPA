package piHAUPA;

import java.io.StringReader;

/** Direct extracted transactions.txt with user-selected thresholds. */
public final class LargeTextDatasetTest {
    private LargeTextDatasetTest() { }

    public static void main(String[] args) throws Exception {
        String transactions = "1 2:9:4 5\n1:2:2\n2:3:3\n";
        InputData input = ArchiveDatasetReader.readTransactionsText(
                new StringReader(transactions), "test", 0.50, 0.25, ignored -> { });
        check(input.upperThreshold() == 0.50 && input.lowerThreshold() == 0.25, "custom thresholds");
        check(input.externalUtilities().get("1") == 1.0, "precomputed utility mapping");
        check(input.batches().stream().mapToInt(b -> b.transactions().size()).sum() == 3, "all rows");
        AnalysisReport report = AnalysisReport.run(input);
        check(report.rescanPolicy() == RescanPolicy.TIGHT, "only Tight policy");
        check(Math.abs(report.batches().get(2).totalTransactionUtility() - 14.0) < 1e-9, "TU sum");
        check(Math.abs(report.batches().get(2).minUtilUpper() - 7.0) < 1e-9, "custom Upper");
        check(Math.abs(report.batches().get(2).minUtilLower() - 3.5) < 1e-9, "custom Lower");
        check(Math.abs(report.upperBounds().get("2") - 8.0) < 1e-9, "AUUB from MU(T)");
        String exported = ExportBundle.reportText(input, report, "test/transactions.txt");
        check(exported.contains("Su = 0.500000") && !exported.contains("Original"), "single-policy export");
        check(ExportBundle.reportCsv(input, report, "test").contains("\"AUUB\",\"2\""), "AUUB CSV");
        verifiesInsertionPartition();
        verifiesCompactStorageAgainstMapAndOracle();
        verifiesNumericParsingAndSinglePassHash();
        System.out.println("Large TXT test passed.");
    }

    private static void verifiesInsertionPartition() throws Exception {
        var source=new vn.edu.dlu.pihaupa.application.InputSource(){
            public String name(){return "partition-test";}
            public InputData read(double su,double sl,java.util.function.Consumer<String> log)throws java.io.IOException{
                StringBuilder text=new StringBuilder();for(int i=0;i<100;i++)text.append("1:1:1\n");
                return ArchiveDatasetReader.readTransactionsText(new StringReader(text.toString()),name(),su,sl,log);
            }
        };
        var input=vn.edu.dlu.pihaupa.application.InputSource.insertion(source,9,.82).read(.3,.1,x->{});
        check(input.batches().size()==10,"initial plus nine insertions");
        check(input.batches().get(0).transactions().size()==82,"OR 82 percent");
        var rows=input.batches().stream().flatMap(b->b.transactions().stream()).toList();
        check(rows.size()==100 && rows.stream().map(Transaction::tid).distinct().count()==100,"all rows once");
        check(vn.edu.dlu.pihaupa.application.InputSource.insertion(source,0,.82)==source,"default partition unchanged");
        try{vn.edu.dlu.pihaupa.application.InputSource.insertion(source,9,.998).read(.3,.1,x->{});throw new AssertionError("small input must fail");}
        catch(IllegalArgumentException expected){check(expected.getMessage().contains("Không đủ"),"clear partition error");}
    }

    private static void verifiesCompactStorageAgainstMapAndOracle() throws Exception {
        java.util.Random random=new java.util.Random(20261015);
        for(int sample=0;sample<30;sample++){
            StringBuilder text=new StringBuilder();
            for(int row=0;row<12;row++){
                java.util.List<Integer> ids=new java.util.ArrayList<>(java.util.List.of(1,2,3,4,5,6));
                java.util.Collections.shuffle(ids,random);int count=1+random.nextInt(6),total=0;
                StringBuilder items=new StringBuilder(),utilities=new StringBuilder();
                for(int i=0;i<count;i++){int utility=1+random.nextInt(20);total+=utility;items.append(ids.get(i)).append(' ');utilities.append(utility).append(' ');}
                text.append(items).append(':').append(total).append(':').append(utilities).append('\n');
            }
            InputData compact=ArchiveDatasetReader.readTransactionsText(new StringReader(text.toString()),"random",.3,.1,line->{});
            var batches=compact.batches().stream().map(batch->new Batch(batch.name(),batch.transactions().stream().map(row->new Transaction(row.tid(),row.internalUtilities())).toList())).toList();
            InputData maps=new InputData(.3,.1,compact.externalUtilities(),batches);
            for(RescanPolicy policy:RescanPolicy.values()){
                AnalysisReport actual=AnalysisReport.run(compact,policy),expected=AnalysisReport.run(maps,policy);
                for(int i=0;i<actual.batchCount();i++){
                    check(actual.batches().get(i).largePatterns().equals(expected.batches().get(i).largePatterns()),"compact HAUP mismatch");
                    check(actual.batches().get(i).preLargePatterns().equals(expected.batches().get(i).preLargePatterns()),"compact PRE-LARGE mismatch");
                }
                check(actual.stats().rescanCount()==expected.stats().rescanCount(),"compact re-scan mismatch");
                check(vn.edu.dlu.pihaupa.application.SmallDataOracle.verify(compact,actual).get("state").equals("PASSED"),"compact oracle mismatch");
            }
            var row=compact.batches().get(0).transactions().get(0);
            try{row.internalUtilities().clear();throw new AssertionError("mutable compact map");}catch(UnsupportedOperationException expected){}
            try{row.internalUtilities().entrySet().iterator().next().setValue(0.);throw new AssertionError("mutable compact entry");}catch(UnsupportedOperationException expected){}
        }
    }

    private static void verifiesNumericParsingAndSinglePassHash() throws Exception {
        for(String token:java.util.List.of("1","0001","+1","1.25","1e3","0x1.0p2","9007199254740993","999999999999999","1.0D","0.0000001")){
            InputData data=ArchiveDatasetReader.readTransactionsText(new StringReader("01:"+token+":"+token+"\n"),"numeric",.3,.1,line->{});
            double actual=data.batches().get(0).transactions().get(0).internalUtilities().get("01");
            check(Double.doubleToLongBits(actual)==Double.doubleToLongBits(Double.parseDouble(token)),"Numeric parser rounding: "+token);
        }
        var whitespace=ArchiveDatasetReader.readTransactionsText(new StringReader("\t2\f1\t:3:1\u000b2\n"),"space",.3,.1,line->{});
        check(whitespace.batches().get(0).transactions().get(0).internalUtilities().get("1")==2.,"Whitespace parser");
        java.nio.file.Path file=java.nio.file.Files.createTempFile("single-pass-test-",".txt");
        try{
            for(boolean quantitative:java.util.List.of(false,true)){
                if(quantitative)InputWriter.writeCombined(InputParser.parseBuiltInSample(),file);
                else java.nio.file.Files.writeString(file,"1 2:9:4 5\r\n1:2:2\r\n2:3:3\r\n");
                var source=vn.edu.dlu.pihaupa.application.InputSource.upload(file,"hash-test",!quantitative);
                InputData input=source.read(.3,.1,line->{});
                String expected=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(file)));
                var manifest=vn.edu.dlu.pihaupa.application.ExperimentProfile.capture(source,input,1);
                check(expected.equals(manifest.get("sha256")),"Single-pass raw SHA mismatch");
                check("single-pass-read-and-hash".equals(manifest.get("hashReadMode")),"Second file pass was used");
            }
            java.nio.file.Files.writeString(file,"1 2:9:4 5\n1:2:2\n2:3:3\n");
            boolean rejected=false;
            try{
                var source=vn.edu.dlu.pihaupa.application.InputSource.upload(file,"changed",true);
                source.read(.3,.1,line->{if(line.startsWith("Đã đọc"))try{java.nio.file.Files.writeString(file,"1:2:2\n");}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}});
            }catch(java.io.IOException expected){rejected=expected.getMessage().contains("thay đổi");}
            check(rejected,"Changed file accepted");
            java.nio.file.Files.write(file,new byte[]{(byte)0xff});
            try{vn.edu.dlu.pihaupa.application.InputSource.upload(file,"invalid-utf8",true).read(.3,.1,line->{});throw new AssertionError("Invalid UTF-8 accepted");}
            catch(java.nio.charset.MalformedInputException expected){}
        }finally{java.nio.file.Files.deleteIfExists(file);}
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
