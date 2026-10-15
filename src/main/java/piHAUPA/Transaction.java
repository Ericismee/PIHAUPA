package piHAUPA;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Transaction {
    private final String tid;
    private final Map<String, Double> internalUtilities;
    private final CompactUtilities compact;

    public Transaction(String tid, Map<String, Double> internalUtilities) {
        this.tid = tid;
        this.internalUtilities = Collections.unmodifiableMap(new LinkedHashMap<>(internalUtilities));
        this.compact = null;
    }

    /** Từ điển dùng chung cho một tệp; chỉ parser thêm mã trước khi công bố InputData. */
    static final class ItemDictionary {
        private final Map<String, Integer> codes = new java.util.HashMap<>();
        private final java.util.List<String> names = new java.util.ArrayList<>();

        int code(String item) {
            Integer known = codes.get(item);
            if (known != null) return known;
            int code = names.size();
            names.add(item);
            codes.put(item, code);
            return code;
        }
    }

    // Nhận quyền sở hữu mảng mới từ parser; không lộ mảng qua API Map bất biến.
    static Transaction compact(String tid, ItemDictionary dictionary, int[] codes, double[] values) {
        return new Transaction(tid, new CompactUtilities(dictionary, codes, values));
    }

    private Transaction(String tid, CompactUtilities utilities) {
        this.tid = tid;
        this.internalUtilities = Collections.unmodifiableMap(utilities);
        this.compact = utilities;
    }

    @FunctionalInterface
    public interface ItemUtilityConsumer {
        void accept(String item, double utility);
    }

    public void forEachUtility(ItemUtilityConsumer consumer) {
        if (compact != null) {
            for (int i = 0; i < compact.codes.length; i++) {
                consumer.accept(compact.dictionary.names.get(compact.codes[i]), compact.values[i]);
            }
        } else {
            internalUtilities.forEach(consumer::accept);
        }
    }

    public double unitTotalUtility() {
        if (compact != null) return compact.total;
        double total = 0;
        for (double value : internalUtilities.values()) total += value;
        return total;
    }

    public double unitMaximumUtility() {
        if (compact != null) return compact.maximum;
        double maximum = 0;
        for (double value : internalUtilities.values()) maximum = Math.max(maximum, value);
        return maximum;
    }

    private static final class CompactUtilities extends java.util.AbstractMap<String, Double> {
        private final ItemDictionary dictionary;
        private final int[] codes;
        private final double[] values;
        private final long[] lookup;
        private final double total;
        private final double maximum;

        CompactUtilities(ItemDictionary dictionary, int[] codes, double[] values) {
            this.dictionary = dictionary;
            this.codes = codes;
            this.values = values;
            this.lookup = new long[codes.length];
            double sum = 0;
            double max = 0;
            for (int i = 0; i < codes.length; i++) {
                lookup[i] = ((long) codes[i] << 32) | (i & 0xffffffffL);
                sum += values[i];
                max = Math.max(max, values[i]);
            }
            java.util.Arrays.sort(lookup);
            total = sum;
            maximum = max;
        }

        public int size() {
            return codes.length;
        }

        public Double get(Object key) {
            Integer code = dictionary.codes.get(key);
            if (code == null) return null;
            int low = 0;
            int high = lookup.length - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                int found = (int) (lookup[mid] >>> 32);
                if (found < code) low = mid + 1;
                else if (found > code) high = mid - 1;
                else return values[(int) lookup[mid]];
            }
            return null;
        }

        public boolean containsKey(Object key) {
            return get(key) != null;
        }

        public java.util.Set<Map.Entry<String, Double>> entrySet() {
            return new java.util.AbstractSet<>() {
                public int size() {
                    return codes.length;
                }

                public java.util.Iterator<Map.Entry<String, Double>> iterator() {
                    return new java.util.Iterator<>() {
                        private int index;

                        public boolean hasNext() {
                            return index < codes.length;
                        }

                        public Map.Entry<String, Double> next() {
                            if (!hasNext()) throw new java.util.NoSuchElementException();
                            int current = index++;
                            return new java.util.AbstractMap.SimpleImmutableEntry<>(
                                    dictionary.names.get(codes[current]), values[current]);
                        }
                    };
                }
            };
        }
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

