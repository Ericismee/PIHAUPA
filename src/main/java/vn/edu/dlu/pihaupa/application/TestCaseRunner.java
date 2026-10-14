package vn.edu.dlu.pihaupa.application;

import piHAUPA.*;
import vn.edu.dlu.pihaupa.support.AppError;
import java.io.StringReader;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Lightweight, real executable cases exposed in the UI. Full/large suites remain CLI tests. */
public final class TestCaseRunner {
    private TestCaseRunner() {}
    @FunctionalInterface private interface Action { String run() throws Exception; }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private record Case(String id,String title,String input,String expected,Action action) {}
    private static volatile Map<String,Object> last;
    public static Map<String,Object> snapshot(){
        if(last!=null)return last;
        return Map.of("state","NOT_RUN","cases",definitions().stream().map(c->Map.of(
                "id",c.id,"title",c.title,"input",c.input,"expected",c.expected,"state","NOT_RUN")).toList());
    }
    public static Map<String,Object> run(){
        List<Map<String,Object>> results=new ArrayList<>();
        long started=System.nanoTime();
        for(Case c:definitions()){
            long start=System.nanoTime();
            String actual;String detail="";
            try{actual=c.action.run();}catch(Throwable failure){actual="ERROR";detail=failure.getClass().getSimpleName()+": "+failure.getMessage();}
            boolean passed=actual.equals(c.expected);
            results.add(Map.of("id",c.id,"title",c.title,"input",c.input,"expected",c.expected,
                    "actual",actual,"state",passed?"PASSED":"FAILED","detail",detail,"elapsedMs",(System.nanoTime()-start)/1e6));
        }
        long passed=results.stream().filter(r->r.get("state").equals("PASSED")).count();
        last=Map.of("state",passed==results.size()?"PASSED":"FAILED","passed",passed,"total",results.size(),
                "runAt",Instant.now().toString(),"elapsedMs",(System.nanoTime()-started)/1e6,"cases",results);
        return last;
    }
    private static List<Case> definitions(){
        List<Case> cases=new ArrayList<>();
        AtomicReference<List<Map<String,Object>>> paper=new AtomicReference<>();
        String[] labels={"TU DB0","MU DB0","HAUP DB0","TU DB0+DB1","MU DB1","HAUP DB1",
                "TU DB0+DB1+DB2","MU DB2","HAUP DB2","Số re-scan Tight","Số re-scan Original","Cận Tight DB1"};
        String[] expected={"141.0","73.0","{A,E=34.0, B=35.0, B,E=45.0, E=60.0}","173.0","24.0",
                "{B,E=45.0, E=72.0}","240.0","40.0","{B=75.0, B,E=63.5, E=84.0}","1.0","2.0","26.4375"};
        for(int i=0;i<labels.length;i++){
            final int index=i;
            cases.add(new Case(String.format("TC-P%02d",i+1),labels[i],"Tables 2–3 · Su=0.23, Sl=0.10",expected[i],()->{
                if(paper.get()==null){
                    @SuppressWarnings("unchecked") List<Map<String,Object>> checks=(List<Map<String,Object>>)PaperVerification.run().get("checks");
                    paper.set(checks);
                }
                return String.valueOf(paper.get().get(index).get("actual"));
            }));
        }
        cases.add(new Case("TC-E01","Từ chối Sl ≥ Su","Su=0.10, Sl=0.10","REJECTED:INVALID_THRESHOLDS",
                ()->reject(()->new PiHaupaMiner(Map.of("A",1.),.1,.1))));
        cases.add(new Case("TC-E02","Từ chối TU không bằng tổng utility","1 2:99:4 5","REJECTED:TU_MISMATCH",
                ()->reject(()->text("1 2:99:4 5\n"))));
        cases.add(new Case("TC-E03","Từ chối số item/utility không khớp","1 2:9:4","REJECTED:INVALID_INPUT",
                ()->reject(()->text("1 2:9:4\n"))));
        cases.add(new Case("TC-E04","Từ chối item trùng trong giao dịch","1 1:9:4 5","REJECTED:DUPLICATE_ITEM",
                ()->reject(()->text("1 1:9:4 5\n"))));
        cases.add(new Case("TC-E05","Từ chối utility âm","1:-2:-2","REJECTED:INVALID_INPUT",
                ()->reject(()->text("1:-2:-2\n"))));
        cases.add(new Case("TC-A01","Re-scan khi bằng ranh giới Eq. (12)","OD: A=100; ID: A=50; Su=.5, Sl=.25","true",()->{
            PiHaupaMiner miner=new PiHaupaMiner(Map.of("A",1.),.5,.25);
            miner.processBatch(new Batch("DB0",List.of(new Transaction("T0",Map.of("A",100.)))));
            return String.valueOf(miner.processBatch(new Batch("DB1",List.of(new Transaction("T1",Map.of("A",50.))))).rescanTriggered());
        }));
        cases.add(new Case("TC-A02","JSON giữ đầy đủ ngưỡng cấu hình","Su=0.0358694084702077, Sl=0.03","EXACT",()->{
            InputData input=InputParser.parseBuiltInSample(); double su=.0358694084702077;
            String json=AnalysisReport.run(new InputData(su,.03,input.externalUtilities(),input.batches())).toJson();
            return json.contains("\"upperThreshold\":"+Double.toString(su))?"EXACT":"ROUNDED";
        }));
        cases.add(new Case("TC-D01","transactions.txt giữ utility gốc, EU=1","1 2:9:4 5 / 1:2:2 / 2:3:3","14.0",()->{
            var report=AnalysisReport.run(text("1 2:9:4 5\n1:2:2\n2:3:3\n"));
            return String.valueOf(report.batches().get(2).totalTransactionUtility());
        }));
        return cases;
    }
    private static InputData text(String input)throws Exception{return ArchiveDatasetReader.readTransactionsText(new StringReader(input),"testcase",.5,.25,s->{});}
    private static String reject(Checked run)throws Exception{
        try{run.run();return "ACCEPTED";}catch(IllegalArgumentException failure){return "REJECTED:"+AppError.from(failure).code();}
    }
}
