package piHAUPA;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;

public final class PiHaupaMiner {
    private final Map<String, Double> externalUtilities;
    private final boolean unitExternalUtilities;
    private final double upperThreshold;
    private final double lowerThreshold;
    private final RescanPolicy rescanPolicy;
    private final List<Transaction> allTransactions = new ArrayList<>();
    private final Map<PatternKey, PatternStat> patternTree = new LinkedHashMap<>();
    private final ManagedPatternTrie managedPatternTrie = new ManagedPatternTrie();
    private final Set<String> transactionIds = new HashSet<>();
    private final Map<String, Double> globalAuub = new HashMap<>();
    private final Map<String, Double> globalItemUtility = new HashMap<>();

    /** AUUB for each observed item across every processed transaction. */
    public Map<String, Double> upperBounds() { return Map.copyOf(globalAuub); }

    private double totalTransactionUtility;
    private double utilityAtLastRescan;
    private double insertedTransactionUtility;
    private double insertedMaximumUtility;
    private long rescanCount;
    private long patternsVisited;
    private long combinedNodes;
    private double totalTransactionUtilityProcessed;
    private double totalMaximumUtilityProcessed;
    private boolean initialized;

    public PiHaupaMiner(Map<String, Double> externalUtilities, double upperThreshold, double lowerThreshold) {
        this(externalUtilities, upperThreshold, lowerThreshold, RescanPolicy.TIGHT);
    }

    public PiHaupaMiner(Map<String, Double> externalUtilities, double upperThreshold, double lowerThreshold,
                        RescanPolicy rescanPolicy) {
        if (!(lowerThreshold > 0.0 && lowerThreshold < upperThreshold && upperThreshold <= 1.0)) {
            throw new IllegalArgumentException("Thresholds must satisfy 0 < Sl < Su <= 1.");
        }
        if (externalUtilities.isEmpty()) {
            throw new IllegalArgumentException("External utilities must not be empty.");
        }
        externalUtilities.forEach((item, utility) -> {
            if (item == null || item.isBlank() || utility == null || !Double.isFinite(utility) || utility <= 0.0) {
                throw new IllegalArgumentException("External utilities must be finite and positive.");
            }
        });
        this.externalUtilities = new HashMap<>(externalUtilities);
        this.unitExternalUtilities = externalUtilities.values().stream().allMatch(value -> value == 1.0);
        this.upperThreshold = upperThreshold;
        this.lowerThreshold = lowerThreshold;
        this.rescanPolicy = Objects.requireNonNull(rescanPolicy, "rescanPolicy");
    }

    public BatchResult processBatch(Batch batch) {
        checkCancelled();
        validateBatch(batch);
        double batchTU = 0.0;
        double batchMU = 0.0;
        for (Transaction transaction : batch.transactions()) {
            checkCancelled();
            double maximum = 0.0;
            if (unitExternalUtilities) {
                for (double utility : transaction.internalUtilities().values()) {
                    batchTU += utility;
                    maximum = Math.max(maximum, utility);
                }
            } else {
                batchTU += transaction.transactionUtility(externalUtilities);
                maximum = transaction.maximumUtility(externalUtilities);
            }
            batchMU += maximum;
            for (String item : transaction.internalUtilities().keySet()) {
                globalAuub.merge(item, maximum, Double::sum);
                globalItemUtility.merge(item, unitExternalUtilities ? transaction.internalUtilities().get(item)
                        : transaction.utilityOf(item,externalUtilities), Double::sum);
            }
        }
        totalTransactionUtilityProcessed += batchTU;
        totalMaximumUtilityProcessed += batchMU;

        allTransactions.addAll(batch.transactions());
        totalTransactionUtility += batchTU;

        String action;
        boolean triggered = false;
        Double limit = null;
        Double denominator = null;
        Double originalLimit = null;
        double reportedInsertedTU = insertedTransactionUtility;
        double reportedInsertedMU = insertedMaximumUtility;
        if (!initialized) {
            rebuildPatternTree();
            utilityAtLastRescan = totalTransactionUtility;
            initialized = true;
            action = "INITIAL SCAN + PATTERN EXPANSION";
        } else {
            insertedTransactionUtility += batchTU;
            insertedMaximumUtility += batchMU;
            reportedInsertedTU = insertedTransactionUtility;
            reportedInsertedMU = insertedMaximumUtility;
            denominator = tightRescanDenominator();
            limit = tightRescanLimit();
            originalLimit = originalRescanLimit();
            /* Chính sách quét lại: nhánh lựa chọn bằng enum, chưa phải GoF Strategy.
             * TIGHT kiểm tra dạng ổn định: MU(ID) - Su*TU(ID) >= (Su-Sl)*TU(OD).
             * Dạng này tránh chia cho mẫu số không dương của Eq.(6), tránh đổi chiều
             * bất đẳng thức sai khi chuyển sang phép chia; bằng ngưỡng vẫn quét lại.
             * ORIGINAL dùng lượng TU đã thêm và originalLimit theo Eq.(5).
             * Mục đích: chỉ xây lại cây khi điều kiện yêu cầu; nếu không thì cập nhật
             * các mẫu đang quản lý. Giảm quét lại là tối ưu thuật toán, không phải
             * lợi ích tốc độ tự động của một design pattern.
             * rescanCount/combinedNodes thể hiện công việc thuật toán; thời gian
             * và bộ nhớ phải đo riêng, không suy ra trực tiếp từ hai bộ đếm này.
             */
            triggered = rescanPolicy == RescanPolicy.TIGHT
                    ? tightRescanConditionMet()
                    : insertedTransactionUtility >= originalLimit;
            if (triggered) {
                rescanCount++;
                rebuildPatternTree();
                utilityAtLastRescan = totalTransactionUtility;
                insertedTransactionUtility = 0.0;
                insertedMaximumUtility = 0.0;
                action = (rescanPolicy == RescanPolicy.TIGHT ? "TIGHT" : "ORIGINAL")
                        + " RE-SCAN + FULL PATTERN EXPANSION";
            } else {
                updateManagedPatterns(batch.transactions());
                action = "NO RE-SCAN: UPDATE EXISTING PATTERN TREE";
            }
        }

        double upper = upperThreshold * totalTransactionUtility;
        double lower = lowerThreshold * totalTransactionUtility;
        List<PatternResult> large = collect(PatternType.LARGE, upper, lower);
        List<PatternResult> preLarge = collect(PatternType.PRE_LARGE, upper, lower);
        return new BatchResult(action, triggered, limit, denominator, originalLimit,
                reportedInsertedTU, reportedInsertedMU,
                batchTU, batchMU, totalTransactionUtility, upper, lower, large, preLarge);
    }

    public List<PatternResult> managedPatterns() {
        return collect(null, upperThreshold * totalTransactionUtility, lowerThreshold * totalTransactionUtility);
    }

    public MiningStats stats() {
        return new MiningStats(rescanCount, patternsVisited, combinedNodes,
                totalTransactionUtilityProcessed, totalMaximumUtilityProcessed);
    }

    private void rebuildPatternTree() {
        patternTree.clear();
        if (allCandidatesBelowLowerBound()) {
            managedPatternTrie.rebuild(patternTree);
            return;
        }
        ListNodeList globalList = constructGlobalList(allTransactions);
        restructure(globalList.nodes());
        patternExpansion(globalList.nodes(), new ArrayList<>());
        managedPatternTrie.rebuild(patternTree);
    }

    /** AU(P) cannot exceed AUUB of any item in P; this avoids materializing empty lists. */
    private boolean allCandidatesBelowLowerBound() {
        double lower = lowerThreshold * totalTransactionUtility;
        return globalAuub.values().stream().noneMatch(bound -> bound >= lower);
    }

    private ListNodeList constructGlobalList(List<Transaction> transactions) {
        double lower = lowerThreshold * totalTransactionUtility;
        Map<String, PiNode> byItem = new LinkedHashMap<>();
        Map<String, Double> auub = new HashMap<>();
        for (Transaction transaction : transactions) {
            checkCancelled();
            double mu = unitExternalUtilities
                    ? transaction.internalUtilities().values().stream().mapToDouble(Double::doubleValue)
                            .max().orElse(0.0)
                    : transaction.maximumUtility(externalUtilities);
            for (String item : transaction.internalUtilities().keySet()) {
                // AU(P) <= AUUB(i) for every i in P. An item below Sl*TU(DB)
                // cannot belong to any managed pattern. Keep MU(T) from the full
                // transaction above; pruning must not change transaction utility.
                if (globalAuub.getOrDefault(item,0.0) < lower) continue;
                double utility = unitExternalUtilities ? transaction.internalUtilities().get(item)
                        : transaction.utilityOf(item, externalUtilities);
                PiNode node = byItem.computeIfAbsent(item, PiNode::new);
                node.sumUtility += utility;
                node.entries.add(new PiEntry(transaction.tid(), utility, 0.0));
                auub.merge(item, mu, Double::sum);
            }
        }
        List<PiNode> nodes = new ArrayList<>(byItem.values());
        for (PiNode node : nodes) {
            node.auub = auub.getOrDefault(node.item, 0.0);
        }
        nodes.sort(Comparator.comparingDouble((PiNode n) -> n.auub).thenComparing(n -> n.item));
        return new ListNodeList(nodes);
    }

    private void restructure(List<PiNode> nodes) {
        Map<String, TempRemaining> temp = new HashMap<>();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            checkCancelled();
            PiNode node = nodes.get(i);
            for (PiEntry entry : node.entries) {
                TempRemaining remaining = temp.get(entry.tid);
                if (remaining == null) {
                    temp.put(entry.tid, new TempRemaining(entry.utility, 1));
                } else {
                    entry.maximumRemainingUtility = remaining.maximumRemainingUtility;
                    node.maximumRemainingNumber = Math.max(node.maximumRemainingNumber, remaining.remainingNumber);
                    remaining.maximumRemainingUtility = Math.max(remaining.maximumRemainingUtility, entry.utility);
                    remaining.remainingNumber++;
                }
            }
        }
    }

    private void patternExpansion(List<PiNode> currentList, List<String> prefix) {
        double lower = lowerThreshold * totalTransactionUtility;
        for (int i = 0; i < currentList.size(); i++) {
            checkCancelled();
            PiNode node = currentList.get(i);
            List<String> pattern = new ArrayList<>(prefix);
            pattern.add(node.item);
            patternsVisited++;

            double averageUtility = node.sumUtility / pattern.size();
            if (averageUtility >= lower) {
                addOrReplacePattern(pattern, node.sumUtility);
            }

            double mau = maximumAverageUpperBound(node, pattern.size());
            if (mau >= lower && extensionCanReachLower(node,pattern.size(),currentList,i+1,lower)) {
                List<PiNode> conditional = new ArrayList<>();
                for (int j = i + 1; j < currentList.size(); j++) {
                    PiNode combined = combine(node, currentList.get(j));
                    if (!combined.entries.isEmpty()) {
                        conditional.add(combined);
                    }
                }
                if (!conditional.isEmpty()) {
                    patternExpansion(conditional, pattern);
                }
            }
        }
    }

    /** Implementation-only safe bound, separate from the paper's MAU definition.
     * For any extension S of P, U(P union S) <= U(P) + sum U({i}), i in S.
     * For each possible extension length, taking the largest remaining singleton
     * utilities bounds every extension of that length. This avoids expanding
     * huge subtrees whose AU cannot reach Sl*TU(DB), without changing re-scan.
     */
    private boolean extensionCanReachLower(PiNode node,int length,List<PiNode> candidates,int first,double lower) {
        double[] utilities=new double[candidates.size()-first];
        for(int j=first;j<candidates.size();j++)utilities[j-first]=globalItemUtility.get(candidates.get(j).item);
        java.util.Arrays.sort(utilities);
        double upperUtility=node.sumUtility;
        int added=0;
        for(int j=utilities.length-1;j>=0;j--) {
            upperUtility+=utilities[j]; added++;
            if(upperUtility/(length+added)>=lower-Math.max(1,lower)*1e-12)return true;
        }
        return false;
    }

    private PiNode combine(PiNode left, PiNode right) {
        combinedNodes++;
        PiNode combined = new PiNode(right.item);
        combined.auub = Math.min(left.auub, right.auub);
        combined.maximumRemainingNumber = Math.min(left.maximumRemainingNumber, right.maximumRemainingNumber);
        Map<String, PiEntry> rightEntries = new HashMap<>();
        for (PiEntry entry : right.entries) {
            rightEntries.put(entry.tid, entry);
        }
        for (PiEntry leftEntry : left.entries) {
            PiEntry rightEntry = rightEntries.get(leftEntry.tid);
            if (rightEntry == null) {
                continue;
            }
            double utility = leftEntry.utility + rightEntry.utility - leftEntry.prefixUtility;
            double prefixUtility = leftEntry.utility;
            PiEntry entry = new PiEntry(leftEntry.tid, utility, prefixUtility);
            entry.maximumRemainingUtility = Math.max(leftEntry.maximumRemainingUtility, rightEntry.maximumRemainingUtility);
            combined.entries.add(entry);
            combined.sumUtility += utility;
        }
        return combined;
    }

    private double maximumAverageUpperBound(PiNode node, int patternLength) {
        double total = 0.0;
        for (PiEntry entry : node.entries) {
            double average = entry.utility / patternLength;
            if (entry.maximumRemainingUtility > average && node.maximumRemainingNumber > 0) {
                total += (entry.utility + node.maximumRemainingNumber * entry.maximumRemainingUtility)
                        / (patternLength + node.maximumRemainingNumber);
            } else if (entry.maximumRemainingUtility > 0.0) {
                total += (entry.utility + entry.maximumRemainingUtility) / (patternLength + 1.0);
            }
        }
        return total;
    }

    private void addOrReplacePattern(List<String> pattern, double sumUtility) {
        PatternKey key = PatternKey.of(pattern);
        PatternStat old = patternTree.get(key);
        if (old == null || old.sumUtility < sumUtility) {
            patternTree.put(key, new PatternStat(key.items, sumUtility));
        }
    }

    private void updateManagedPatterns(List<Transaction> transactions) {
        for (Transaction transaction : transactions) {
            checkCancelled();
            managedPatternTrie.update(transaction, externalUtilities);
        }
    }

    private List<PatternResult> collect(PatternType only, double upper, double lower) {
        List<PatternResult> results = new ArrayList<>();
        for (PatternStat stat : patternTree.values()) {
            double au = stat.sumUtility / stat.items.size();
            PatternType type = au >= upper ? PatternType.LARGE : au >= lower ? PatternType.PRE_LARGE : PatternType.SMALL;
            if (only == null || type == only) {
                results.add(new PatternResult(stat.items, stat.sumUtility, stat.items.size(), type));
            }
        }
        results.sort(Comparator.comparing(PatternResult::type)
                .thenComparing(Comparator.comparingDouble(PatternResult::averageUtility).reversed())
                .thenComparing(PatternResult::displayName));
        return results;
    }

    private double tightRescanLimit() {
        if (insertedMaximumUtility <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        double denominator = tightRescanDenominator();
        if (denominator <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return ((upperThreshold - lowerThreshold) / denominator) * utilityAtLastRescan;
    }

    private double tightRescanDenominator() {
        if (insertedMaximumUtility <= 0.0) {
            return 1.0;
        }
        return 1.0 - (insertedTransactionUtility / insertedMaximumUtility) * upperThreshold;
    }

    private double originalRescanLimit() {
        return ((upperThreshold - lowerThreshold) / (1.0 - upperThreshold)) * utilityAtLastRescan;
    }

    private boolean tightRescanConditionMet() {
        double left = insertedMaximumUtility - upperThreshold * insertedTransactionUtility;
        double right = (upperThreshold - lowerThreshold) * utilityAtLastRescan;
        return left >= right;
    }

    private double transactionUtility(List<Transaction> transactions) {
        return transactions.stream().mapToDouble(t -> t.transactionUtility(externalUtilities)).sum();
    }

    private double maximumUtilitySum(List<Transaction> transactions) {
        return transactions.stream().mapToDouble(t -> t.maximumUtility(externalUtilities)).sum();
    }

    private void validateBatch(Batch batch) {
        if (batch == null || batch.name() == null || batch.name().isBlank() || batch.transactions() == null) {
            throw new IllegalArgumentException("Batch name and transactions are required.");
        }
        for (Transaction transaction : batch.transactions()) {
            if (!transactionIds.add(transaction.tid())) {
                throw new IllegalArgumentException("Duplicate transaction id across batches: " + transaction.tid());
            }
            for (Map.Entry<String, Double> entry : transaction.internalUtilities().entrySet()) {
                if (!externalUtilities.containsKey(entry.getKey())) {
                    throw new IllegalArgumentException("Missing external utility for item " + entry.getKey());
                }
                double value = entry.getValue();
                if (!Double.isFinite(value) || value <= 0.0) {
                    throw new IllegalArgumentException("Internal utility must be finite and positive for item "
                            + entry.getKey() + " in " + transaction.tid());
                }
            }
        }
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("PIHAUPA run cancelled.");
        }
    }

    private record ListNodeList(List<PiNode> nodes) {
    }

    private static final class PiNode {
        private final String item;
        private final List<PiEntry> entries = new ArrayList<>();
        private double sumUtility;
        private double auub;
        private int maximumRemainingNumber;

        private PiNode(String item) {
            this.item = item;
        }
    }

    private static final class PiEntry {
        private final String tid;
        private final double utility;
        private final double prefixUtility;
        private double maximumRemainingUtility;

        private PiEntry(String tid, double utility, double prefixUtility) {
            this.tid = tid;
            this.utility = utility;
            this.prefixUtility = prefixUtility;
        }
    }

    private static final class TempRemaining {
        private double maximumRemainingUtility;
        private int remainingNumber;

        private TempRemaining(double maximumRemainingUtility, int remainingNumber) {
            this.maximumRemainingUtility = maximumRemainingUtility;
            this.remainingNumber = remainingNumber;
        }
    }

    private static final class PatternStat {
        private final List<String> items;
        private double sumUtility;

        private PatternStat(List<String> items, double sumUtility) {
            this.items = List.copyOf(items);
            this.sumUtility = sumUtility;
        }
    }

    /**
     * Prefix tree over canonical (lexicographically sorted) pattern items. During an
     * incremental update it follows only paths that already exist in the managed
     * pattern set. This avoids enumerating all 2^n subsets of a wide transaction.
     */
    private static final class ManagedPatternTrie {
        private final TrieNode root = new TrieNode(null);

        private void rebuild(Map<PatternKey, PatternStat> patterns) {
            root.children.clear();
            root.stat = null;
            for (Map.Entry<PatternKey, PatternStat> entry : patterns.entrySet()) {
                TrieNode current = root;
                for (String item : entry.getKey().items) {
                    current = current.children.computeIfAbsent(item, TrieNode::new);
                }
                current.stat = entry.getValue();
            }
        }

        private void update(Transaction transaction, Map<String, Double> externalUtilities) {
            updateChildren(root, transaction, externalUtilities, 0.0);
        }

        private void updateChildren(TrieNode parent, Transaction transaction,
                                    Map<String, Double> externalUtilities, double prefixUtility) {
            for (TrieNode child : parent.children.values()) {
                if (!transaction.internalUtilities().containsKey(child.item)) {
                    continue;
                }
                double utility = prefixUtility + transaction.utilityOf(child.item, externalUtilities);
                if (child.stat != null) {
                    child.stat.sumUtility += utility;
                }
                updateChildren(child, transaction, externalUtilities, utility);
            }
        }
    }

    private static final class TrieNode {
        private final String item;
        private final Map<String, TrieNode> children = new LinkedHashMap<>();
        private PatternStat stat;

        private TrieNode(String item) {
            this.item = item;
        }
    }

    private static final class PatternKey {
        private final List<String> items;

        private PatternKey(List<String> items) {
            this.items = List.copyOf(items);
        }

        private static PatternKey of(List<String> rawItems) {
            Set<String> sorted = new TreeSet<>(rawItems);
            return new PatternKey(new ArrayList<>(sorted));
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PatternKey that)) {
                return false;
            }
            return Objects.equals(items, that.items);
        }

        @Override
        public int hashCode() {
            return Objects.hash(items);
        }
    }
}
