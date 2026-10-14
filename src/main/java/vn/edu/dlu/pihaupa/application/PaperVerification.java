package vn.edu.dlu.pihaupa.application;

import piHAUPA.*;
import java.util.*;

/** Fixed paper reference values, independent of the displayed report. */
public final class PaperVerification {
    private PaperVerification() {}
    public static Map<String,Object> run() {
        InputData sample = InputParser.parseBuiltInSample();
        AnalysisReport tight = AnalysisReport.run(sample, RescanPolicy.TIGHT);
        AnalysisReport original = AnalysisReport.run(sample, RescanPolicy.ORIGINAL);
        List<Map<String,Object>> checks = new ArrayList<>();
        double[] tu = {141,173,240}, mu = {73,24,40};
        // Pattern display is normalized from item IDs, not formatting conventions.
        List<Map<String,Double>> canonical = List.of(Map.of("E",60.,"B",35.,"B,E",45.,"A,E",34.),
                Map.of("E",72.,"B,E",45.), Map.of("E",84.,"B",75.,"B,E",63.5));
        for (int i=0;i<3;i++) {
            var batch = tight.batches().get(i);
            checks.add(check(i==0?"TU(DB0)":"TU(DB0…DB" + i + ")", tu[i], batch.totalTransactionUtility()));
            checks.add(check("MU(DB" + i + ")", mu[i], batch.batchMaximumUtility()));
            Map<String,Double> actual = new TreeMap<>();
            batch.largePatterns().forEach(p -> actual.put(String.join(",", new TreeSet<>(p.items())), p.averageUtility()));
            checks.add(Map.of("label", "HAUP DB"+i, "expected", new TreeMap<>(canonical.get(i)).toString(),
                    "actual", actual.toString(), "passed", canonical.get(i).equals(actual)));
        }
        checks.add(check("Tight · số re-scan (không gồm initial scan)",1,tight.stats().rescanCount()));
        checks.add(check("Original · số re-scan",2,original.stats().rescanCount()));
        checks.add(check("DB1 · cận Eq. (6)",26.4375,tight.batches().get(1).tightRescanLimit()));
        boolean passed = checks.stream().allMatch(c -> Boolean.TRUE.equals(c.get("passed")));
        return Map.of("passed",passed,"checks",checks);
    }
    private static Map<String,Object> check(String label,double expected,double actual) {
        return Map.of("label",label,"expected",expected,"actual",actual,"passed",Math.abs(expected-actual)<1e-8);
    }
}

