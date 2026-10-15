package vn.edu.dlu.pihaupa.application;

import piHAUPA.*;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Oracle độc lập: liệt kê mọi tập con của giao dịch, không dùng PI-list/pruning. */
public final class SmallDataOracle {
    private SmallDataOracle() {}
    public static Map<String,Object> verify(InputData input,AnalysisReport report){
        int rows=input.batches().stream().mapToInt(b->b.transactions().size()).sum();
        if(input.externalUtilities().size()>12||rows>1000)return Map.of("state","NOT_CHECKED","method","exhaustive-subsets","reason","Oracle giới hạn ≤12 item và ≤1.000 giao dịch.");
        Map<List<String>,Double> sums=new HashMap<>();double total=0;
        for(Batch batch:input.batches())for(Transaction t:batch.transactions()){
            if(Thread.currentThread().isInterrupted())throw new CancellationException();
            var items=t.internalUtilities().keySet().stream().sorted().toList();total+=t.transactionUtility(input.externalUtilities());
            for(int mask=1;mask<(1<<items.size());mask++){
                List<String> key=new ArrayList<>();double utility=0;
                for(int i=0;i<items.size();i++)if((mask&(1<<i))!=0){key.add(items.get(i));utility+=t.utilityOf(items.get(i),input.externalUtilities());}
                sums.merge(List.copyOf(key),utility,Double::sum);
            }
        }
        Map<List<String>,Double> expected=new HashMap<>(),actual=new HashMap<>();
        double threshold=input.upperThreshold()*total;
        sums.forEach((key,u)->{if(u/key.size()>=threshold)expected.put(key,u/key.size());});
        report.batches().get(report.batches().size()-1).largePatterns().forEach(p->actual.put(p.items().stream().sorted().toList(),p.averageUtility()));
        boolean equal=actual.keySet().equals(expected.keySet())&&actual.entrySet().stream().allMatch(e->Math.abs(e.getValue()-expected.get(e.getKey()))<=Math.max(1e-8,Math.abs(e.getValue())*1e-10));
        return Map.of("state",equal?"PASSED":"FAILED","method","exhaustive-subsets","expectedHaups",expected.size(),"actualHaups",actual.size(),"scope","Tập HAUP cuối và AU; không kiểm chứng toàn bộ PRE-LARGE.");
    }
}
