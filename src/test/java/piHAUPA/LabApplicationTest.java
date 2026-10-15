package piHAUPA;

import vn.edu.dlu.pihaupa.application.*;
import vn.edu.dlu.pihaupa.support.Json;
import vn.edu.dlu.pihaupa.support.AppError;
import java.nio.file.*;

public final class LabApplicationTest {
    public static void main(String[] args) throws Exception {
        var verification = PaperVerification.run();
        if (!Boolean.TRUE.equals(verification.get("passed"))) throw new AssertionError(verification);
        if(!TestCaseRunner.snapshot().get("state").equals("NOT_RUN"))throw new AssertionError("Cases must not claim success before execution");
        var cases=TestCaseRunner.run();
        if(!cases.get("state").equals("PASSED") || ((Number)cases.get("passed")).intValue()!=20)
            throw new AssertionError(cases);
        if(!AppError.from(new OutOfMemoryError()).code().equals("OUT_OF_MEMORY")
                ||!AppError.from(new java.util.NoSuchElementException("expired")).code().equals("JOB_EXPIRED")
                ||!AppError.from(new IllegalStateException("busy")).code().equals("BUSY"))
            throw new AssertionError("Structured error classification");
        var oracleInput=InputParser.parseBuiltInSample();
        if(!SmallDataOracle.verify(oracleInput,AnalysisReport.run(oracleInput)).get("state").equals("PASSED"))throw new AssertionError("Independent oracle");
        var wrong=AnalysisReport.run(new InputData(.01,.005,oracleInput.externalUtilities(),oracleInput.batches()));
        if(!SmallDataOracle.verify(oracleInput,wrong).get("state").equals("FAILED"))throw new AssertionError("Oracle must detect a different HAUP set");
        var fp1=ExperimentProfile.capture(InputSource.paper(),oracleInput,1);
        var fp2=ExperimentProfile.capture(InputSource.paper(),new InputData(.2,.1,oracleInput.externalUtilities(),oracleInput.batches()),3);
        if(!fp1.get("sha256").equals(fp2.get("sha256")))throw new AssertionError("Dataset identity must not depend on thresholds");
        try(AnalysisService cancellation=new AnalysisService()){
            var started=new java.util.concurrent.CountDownLatch(1);var cleaned=new java.util.concurrent.atomic.AtomicBoolean();
            InputSource slow=new InputSource(){public String name(){return "cancellation-test";}public InputData read(double su,double sl,java.util.function.Consumer<String> log){started.countDown();try{Thread.sleep(30000);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.util.concurrent.CancellationException();}return oracleInput;}public void cleanup(){cleaned.set(true);}};
            var job=cancellation.submit(slow,.23,.1,false);if(!started.await(2,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Worker not started");
            if(!cancellation.activeStatus().contains(job.id)||!cancellation.activeStatus().contains("\"active\":true"))throw new AssertionError("Active job must be discoverable after reload");
            try{cancellation.submit(InputSource.paper(),.23,.1,false);throw new AssertionError("Concurrent job accepted");}catch(IllegalStateException expected){}
            cancellation.cancel(job.id);long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);while(job.state.equals("RUNNING")&&System.nanoTime()<deadline)Thread.sleep(10);
            if(!job.state.equals("CANCELLED")||job.result!=null||!cleaned.get())throw new AssertionError("Cancelled job must cleanup without publishing partial result: "+job.status());
            if(!cancellation.activeStatus().contains("\"active\":false"))throw new AssertionError("BUSY remained after cancellation cleanup");
            var next=cancellation.submit(InputSource.paper(),.23,.1,false,3);deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);while(next.state.equals("RUNNING")&&System.nanoTime()<deadline)Thread.sleep(10);
            if(!next.state.equals("COMPLETED")||next.timingSamples.size()!=3||!next.result.contains("\"medianMs\""))throw new AssertionError("Repeated measurement after cancel");
            if(!cancellation.store().result(next.id).equals(next.result)||cancellation.store().archive(next.id).length==0)throw new AssertionError("Persisted experiment");
        }
        var childStarted=new java.util.concurrent.CountDownLatch(1);var childStopped=new java.util.concurrent.atomic.AtomicBoolean();var childFailure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread childTest=new Thread(()->{try{IsolatedBenchmark.run(oracleInput,3,line->{if(line.startsWith("Đã khởi động JVM"))childStarted.countDown();});}catch(java.util.concurrent.CancellationException expected){childStopped.set(true);}catch(Throwable failure){childFailure.set(failure);}});
        childTest.start();if(!childStarted.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Child JVM did not start");childTest.interrupt();childTest.join(5000);
        if(childTest.isAlive()||!childStopped.get()||childFailure.get()!=null)throw new AssertionError("Isolated cancellation cleanup: "+childFailure.get());
        var profile=DatasetProfile.find("NewMushroom-Metadata",2597897);
        if(profile==null || !(0<profile.sl() && profile.sl()<profile.su() && profile.su()<profile.singletonAU()/profile.totalTU()))
            throw new AssertionError("Dataset threshold profile must retain the strongest singleton as HAUP");
        if(DatasetProfile.find("NewMushroom-Metadata",1)!=null)throw new AssertionError("Different file must not use reference profile");
        var sample=InputParser.parseBuiltInSample();
        var precise=AnalysisReport.run(new InputData(profile.su(),profile.sl(),sample.externalUtilities(),sample.batches()));
        if(!precise.toJson().contains("\"upperThreshold\":"+Double.toString(profile.su())))
            throw new AssertionError("JSON must not round the configured threshold");
        if (!Json.encode("a\n\"\t\\").equals("\"a\\n\\\"\\t\\\\\"")) throw new AssertionError("JSON escape");
        Path root=Files.createTempDirectory("pihaupa-catalog-test");
        try {
            Path dataset=Files.createDirectory(root.resolve("Example"));
            Files.writeString(dataset.resolve("transactions.txt"),"1 2:5:2 3\n1:4:4\n2:6:6\n");
            DatasetCatalog catalog=new DatasetCatalog(root);
            if (catalog.list().size()!=1) throw new AssertionError("catalog discovery");
            try { catalog.read("../outside",.23,.1,s -> {}); throw new AssertionError("path traversal accepted"); }
            catch (IllegalArgumentException expected) {}
            var data=catalog.read("dataset-0",.23,.1,s -> {});
            if (data.batches().stream().mapToInt(b -> b.transactions().size()).sum()!=3) throw new AssertionError("lost rows");
            try (AnalysisService service=new AnalysisService()) {
                var job=service.submit(InputSource.paper(),.23,.1,true);
                if(!job.status().contains("\"heapUsedMb\""))throw new AssertionError("Missing live heap metric");
                long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
                while (job.state.equals("RUNNING") && System.nanoTime()<deadline) Thread.sleep(20);
                if (!job.state.equals("COMPLETED") || job.result==null || !job.result.contains("\"sameHaups\":true"))
                    throw new AssertionError(job.status());
                if(!job.status().contains("\"progressPercent\":100") || !job.status().contains("\"completedSteps\":7") || !job.status().contains("\"totalSteps\":7"))throw new AssertionError("Incorrect completed progress: "+job.status());
                var archive=ReportArchive.analysis(job);
                if(!archive.keySet().containsAll(java.util.List.of("BAO_CAO.html","patterns.csv","performance-by-rows.csv","upper-bounds.csv","report.json","measurements.csv","comparison.csv","manifest.json","verification.json","timing-samples.csv")))throw new AssertionError("Missing archive files");
                if(!archive.get("BAO_CAO.html").contains("<svg") || !archive.get("measurements.csv").contains("threadCpuMs"))throw new AssertionError("Missing chart or CPU measurement");
                byte[] zipped=ReportArchive.zip(archive);
                try(var zin=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zipped))){int entries=0;while(zin.getNextEntry()!=null){zin.readAllBytes();entries++;}if(entries!=archive.size())throw new AssertionError("ZIP entries lost");}
                var testArchive=ReportArchive.tests(cases);
                if(!testArchive.get("testcases.csv").contains("TC-P12")||!testArchive.get("TESTCASES.html").contains("<svg"))throw new AssertionError("Test evidence export");
                if (job.report.transactionCount()!=9) throw new AssertionError("paper rows");
                if(!job.status().contains("\"phase\":\"COMPLETED\"") || !job.status().contains("Đọc hoàn tất"))
                    throw new AssertionError("Missing completed phase or read notification");
            }
        } finally {
            Files.deleteIfExists(root.resolve("Example/transactions.txt"));
            Files.deleteIfExists(root.resolve("Example")); Files.deleteIfExists(root);
        }
        System.out.println("Lab application tests passed (paper reference, catalog, async report, comparison, JSON).");
    }
}
