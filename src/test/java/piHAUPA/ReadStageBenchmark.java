package piHAUPA;

import java.nio.file.Path;
import java.util.*;
import vn.edu.dlu.pihaupa.application.*;

/** Bước đọc thực của Web: parse + kiểm tra + SHA-256 + manifest, không mining hay sampler. */
public final class ReadStageBenchmark {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);String name=args[1];
        DatasetCatalog catalog=new DatasetCatalog(root);
        String id=catalog.list().stream().filter(d->d.get("name").equals(name)).map(d->d.get("id").toString()).findFirst().orElseThrow();
        List<Double> times=new ArrayList<>();String hash="";int rows=0;double tu=0;
        for(int i=0;i<4;i++){
            InputSource source=InputSource.catalog(catalog,id);
            long start=System.nanoTime();InputData input=source.read(.23,.10,line->{});
            var manifest=ExperimentProfile.capture(source,input,1);double ms=(System.nanoTime()-start)/1e6;
            if(i>0)times.add(ms);
            String current=manifest.get("sha256").toString();if(!hash.isEmpty()&&!hash.equals(current))throw new AssertionError("Hash changed");hash=current;
            rows=input.batches().stream().mapToInt(b->b.transactions().size()).sum();
            tu=input.batches().stream().flatMap(b->b.transactions().stream()).mapToDouble(t->t.transactionUtility(input.externalUtilities())).sum();
        }
        Collections.sort(times);
        System.out.printf(Locale.ROOT,"dataset=%s readAndHashMedianMs=%.3f rows=%d TU=%.0f sha256=%s%n",name,times.get(1),rows,tu,hash);
    }
}
