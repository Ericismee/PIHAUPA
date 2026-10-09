package piHAUPA;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Transaction {
    private final String tid;
    private final Map<String, Double> internalUtilities;

    public Transaction(String tid, Map<String, Double> internalUtilities) {
        this.tid = tid;
        this.internalUtilities = Collections.unmodifiableMap(new LinkedHashMap<>(internalUtilities));
    }

    public String tid() {
        return tid;
    }

    public Map<String, Double> internalUtilities() {
        return internalUtilities;
    }

    public double utilityOf(String item, Map<String, Double> externalUtilities) {
        Double internal = internalUtilities.get(item);
        if (internal == null) {
            return 0.0;
        }
        Double external = externalUtilities.get(item);
        if (external == null) {
            throw new IllegalArgumentException("Missing external utility for item " + item + " in " + tid);
        }
        return internal * external;
    }

    public double transactionUtility(Map<String, Double> externalUtilities) {
        double total = 0.0;
        for (Map.Entry<String, Double> item : internalUtilities.entrySet()) {
            Double external = externalUtilities.get(item.getKey());
            if (external == null) throw new IllegalArgumentException("Missing external utility for item " + item.getKey());
            total += item.getValue() * external;
        }
        return total;
    }

    public double maximumUtility(Map<String, Double> externalUtilities) {
        double maximum = 0.0;
        for (Map.Entry<String, Double> item : internalUtilities.entrySet()) {
            Double external = externalUtilities.get(item.getKey());
            if (external == null) throw new IllegalArgumentException("Missing external utility for item " + item.getKey());
            maximum = Math.max(maximum, item.getValue() * external);
        }
        return maximum;
    }
}

