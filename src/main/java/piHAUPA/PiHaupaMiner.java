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
    private java.util.function.Consumer<String> progress = ignored -> {};
    private long lastProgressNanos;
    private double[] transactionMaximums;
    private double[][] pairUpperBounds;
    private final boolean earlyJoinBounds = Boolean.getBoolean("pihaupa.earlyJoin");
    private final boolean suffixRemainingBound = Boolean.getBoolean("pihaupa.suffixRemainingBound");
    private boolean allIntegerUtilities = true;
    public void setProgress(java.util.function.Consumer<String> progress) { this.progress = progress; }
    private void reportProgress(String message) { progress.accept(message); }

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
    private long earlyJoinsPruned;
    private long extensionBranchesPruned;
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
        reportProgress("01 · Kiểm tra giao dịch và tính TU/MU, AUUB.");
        validateBatch(batch);
        double batchTU = 0.0;
        double batchMU = 0.0;
        for (Transaction transaction : batch.transactions()) {
            checkCancelled();
            double maximum = 0.0;
            if (unitExternalUtilities) {
                batchTU = transaction.accumulateUnitUtility(batchTU);
                maximum = transaction.unitMaximumUtility();
            } else {
                batchTU += transaction.transactionUtility(externalUtilities);
                maximum = transaction.maximumUtility(externalUtilities);
            }
            batchMU += maximum;
            final double transactionMaximum = maximum;
            transaction.forEachUtility((item, utility) -> {
                if (utility != Math.rint(utility)) allIntegerUtilities = false;
                globalAuub.merge(item, transactionMaximum, Double::sum);
                globalItemUtility.merge(item, unitExternalUtilities ? utility
                        : utility*externalUtilities.get(item), Double::sum);
            });
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
            double left = rescanPolicy == RescanPolicy.TIGHT
                    ? insertedMaximumUtility-upperThreshold*insertedTransactionUtility
                    : (1-upperThreshold)*insertedTransactionUtility;
            double right = (upperThreshold-lowerThreshold)*utilityAtLastRescan;
            reportProgress("02 · Điều kiện "+rescanPolicy+": trái="+left+", phải="+right+" → "+(triggered?"RE-SCAN":"KHÔNG RE-SCAN"));
            if (triggered) {
                rescanCount++;
                rebuildPatternTree();
                utilityAtLastRescan = totalTransactionUtility;
                insertedTransactionUtility = 0.0;
                insertedMaximumUtility = 0.0;
                action = (rescanPolicy == RescanPolicy.TIGHT ? "TIGHT" : "ORIGINAL")
                        + " RE-SCAN + FULL PATTERN EXPANSION";
            } else {
                reportProgress("03 · Cập nhật tiện ích các mẫu đã lưu trong PT.");
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
            reportProgress("03 · Mọi AUUB đều dưới ngưỡng PRE-LARGE; không cần mở rộng, PT rỗng.");
            managedPatternTrie.rebuild(patternTree, globalAuub);
            return;
        }
        reportProgress("03 · Xây dựng PIHAUP-List trên "+allTransactions.size()+" giao dịch.");
        ListNodeList globalList = constructGlobalList(allTransactions);
        reportProgress("04 · Sắp xếp AUUB, tính MRU/MRN cho "+globalList.nodes().size()+" item.");
        restructure(globalList.nodes());
        reportProgress("05 · Mở rộng mẫu và cắt tỉa AUUB/MAU; đang tìm LARGE/PRE-LARGE.");
        if (suffixRemainingBound) reportProgress("05 · Thử nghiệm cận suffix bổ sung đang bật; không phải MAU gốc của bài báo.");
        lastProgressNanos=System.nanoTime();
        patternExpansion(globalList.nodes(), new ArrayList<>());
        reportProgress("06 · Hoàn tất mở rộng; xây PT từ "+patternTree.size()+" mẫu đang giữ.");
        managedPatternTrie.rebuild(patternTree, globalAuub);
    }

    /** AU(P) cannot exceed AUUB of any item in P; this avoids materializing empty lists. */
    private boolean allCandidatesBelowLowerBound() {
        double lower = lowerThreshold * totalTransactionUtility;
        return globalAuub.values().stream().noneMatch(bound -> bound >= lower);
    }

    private ListNodeList constructGlobalList(List<Transaction> transactions) {
        double lower = lowerThreshold * totalTransactionUtility;
        Map<String, PiNode> byItem = new LinkedHashMap<>();
        // Chỉ tạo node cho item vượt cận dưới; AUUB đã tích lũy trong processBatch.
        globalAuub.forEach((item, bound) -> {
            if (bound >= lower) byItem.put(item, new PiNode(item));
        });
        // Bộ lọc cặp thử nghiệm: chỉ dùng phép cộng chính xác và bảng kích thước nhỏ.
        // pairUpperBounds[a][b] = tổng MU(T) trên giao dịch chứa cả a,b.
        String pairMode = System.getProperty("pihaupa.pairFilter", "auto");
        boolean pairFilter = !pairMode.equalsIgnoreCase("false") && unitExternalUtilities
                && allIntegerUtilities && totalTransactionUtility < 0x1.0p52 && byItem.size() <= 256
                && (pairMode.equalsIgnoreCase("true") || byItem.size() >= 32);
        if (pairFilter) {
            long preparationWork = 0;
            for (Transaction transaction : transactions) {
                checkCancelled();
                long width = Math.min(byItem.size(), transaction.internalUtilities().size());
                preparationWork += width * (width - 1) / 2;
                if (preparationWork > 100_000_000L) { pairFilter = false; break; }
            }
        }
        final boolean usePairFilter = pairFilter;
        pairUpperBounds = pairFilter ? new double[byItem.size()][byItem.size()] : null;
        if (pairFilter) reportProgress("03 · Chuẩn bị cận cặp cho " + byItem.size()
                + " item; loại phép ghép có cận dưới ngưỡng PRE-LARGE.");
        int pairPosition = 0;
        for (PiNode node : byItem.values()) node.pairPosition = pairPosition++;
        int[] pairItems = pairFilter ? new int[byItem.size()] : null;
        int[] pairCount = new int[1];
        transactionMaximums = earlyJoinBounds ? new double[transactions.size()] : null;
        int transactionIndex = 0;
        for (Transaction transaction : transactions) {
            checkCancelled();
            final int position = transactionIndex;
            pairCount[0] = 0;
            if (earlyJoinBounds) transactionMaximums[position] = unitExternalUtilities ? transaction.unitMaximumUtility()
                    : transaction.maximumUtility(externalUtilities);
            transaction.forEachUtility((item,internalUtility) -> {
                PiNode node = byItem.get(item);
                if (node == null) return;
                if (usePairFilter) pairItems[pairCount[0]++] = node.pairPosition;
                double utility = unitExternalUtilities ? internalUtility
                        : internalUtility * externalUtilities.get(item);
                node.sumUtility += utility;
                node.entries.add(new PiEntry(position, utility, 0.0, utility));
            });
            if (pairFilter) {
                double maximum = transaction.unitMaximumUtility();
                for (int a = 0; a < pairCount[0]; a++) {
                    if ((a & 31) == 0) checkCancelled();
                    for (int b = a + 1; b < pairCount[0]; b++) {
                        int x = Math.min(pairItems[a], pairItems[b]);
                        int y = Math.max(pairItems[a], pairItems[b]);
                        pairUpperBounds[x][y] += maximum;
                    }
                }
            }
            transactionIndex++;
        }
        List<PiNode> nodes = new ArrayList<>(byItem.values());
        for (PiNode node : nodes) {
            node.auub = globalAuub.getOrDefault(node.item, 0.0);
            node.projectedUtility = node.sumUtility;
        }
        nodes.sort(Comparator.comparingDouble((PiNode n) -> n.auub).thenComparing(n -> n.item));
        return new ListNodeList(nodes);
    }

    private void restructure(List<PiNode> nodes) {
        // Bước trung gian: trạng thái remaining theo vị trí giao dịch, dùng mảng nguyên thủy.
        // Không tạo một TempRemaining object cho mỗi giao dịch.
        double[] maximum = new double[allTransactions.size()];
        int[] count = new int[allTransactions.size()];
        for (int i = nodes.size() - 1; i >= 0; i--) {
            checkCancelled();
            PiNode node = nodes.get(i);
            for (PiEntry entry : node.entries) {
                int position = entry.transactionIndex;
                if (count[position] != 0) {
                    entry.maximumRemainingUtility = maximum[position];
                    node.maximumRemainingNumber = Math.max(node.maximumRemainingNumber, count[position]);
                }
                maximum[position] = Math.max(maximum[position], entry.utility);
                count[position]++;
            }
        }
    }

    private void patternExpansion(List<PiNode> currentList, List<String> prefix) {
        double lower = lowerThreshold * totalTransactionUtility;
        // PRECOMPUTATION: chuẩn bị thứ tự utility một lần cho cả danh sách anh em.
        // Chỉ cấp phát khi thật sự cần kiểm tra nhánh mở rộng.
        ExtensionPlan extensionPlan = null;
        for (int i = 0; i < currentList.size(); i++) {
            checkCancelled();
            PiNode node = currentList.get(i);
            prefix.add(node.item);
            List<String> pattern = prefix;
            patternsVisited++;
            if ((patternsVisited & 1023) == 0 && System.nanoTime()-lastProgressNanos >= 2_000_000_000L) {
                reportProgress("05 · Đang mở rộng: đã duyệt "+patternsVisited+" mẫu, kết hợp "+combinedNodes+" nút; PT giữ "+patternTree.size()+" mẫu. Chặn mở rộng "+extensionBranchesPruned+" nhánh; cắt sớm "+earlyJoinsPruned+" phép ghép. Chưa hoàn tất.");
                lastProgressNanos=System.nanoTime();
            }

            double averageUtility = node.sumUtility / pattern.size();
            if (averageUtility >= lower) {
                addOrReplacePattern(pattern, node.sumUtility);
            }

            // Node cuối không còn item phía sau để mở rộng: tránh quét MAU vô ích.
            if (i + 1 < currentList.size()) {
                if (extensionPlan == null) extensionPlan = new ExtensionPlan(currentList);
                if (!extensionPlan.canReach(node, pattern.size(), i + 1, lower)) {
                    extensionBranchesPruned++;
                    node.entries = List.of();
                    prefix.remove(prefix.size() - 1);
                    continue;
                }
                // Cận chiếu chỉ duyệt item; kiểm tra trước MAU phải duyệt giao dịch.
                if (!canExpandByRemainingBound(node, pattern.size(), lower)) {
                    node.entries = List.of();
                    prefix.remove(prefix.size() - 1);
                    continue;
                }
                List<PiNode> conditional = new ArrayList<>();
                for (int j = i + 1; j < currentList.size(); j++) {
                    PiNode combined = combine(node, currentList.get(j), lower);
                    if (combined != null && !combined.entries.isEmpty()) {
                        conditional.add(combined);
                    }
                }
                // Node này đã ghép xong mọi nhánh; giải phóng cache trước khi DFS xuống sâu.
                node.suffixAuub = null;
                // Các list con đã sở hữu entry riêng. Nhánh cha đã ghép xong;
                // bỏ tham chiếu trước DFS để GC thu hồi entry, tránh giữ cả cây tìm kiếm.
                // Các vòng sau chỉ ghép với j > i, không dùng lại node này.
                node.entries = List.of();
                if (!conditional.isEmpty()) {
                    patternExpansion(conditional, pattern);
                }
            }
            node.entries = List.of();
            prefix.remove(prefix.size() - 1);
        }
    }

    /** Implementation-only safe bound, separate from the paper's MAU definition.
     * Với tiền tố chung Q, V_Q(j) = tổng u(j,T) trên các T chứa Q∪{j}.
     * Mọi mở rộng P∪S có U(P∪S) ≤ U(P) + tổng V_Q(j), j thuộc S.
     * Xét tổng k giá trị V lớn nhất, chia |P|+k, cho cận mọi mở rộng độ dài k.
     * Chỉ dùng cận chiếu khi utility nguyên dương, EU=1, TU<2^52: cộng chính xác.
     * Dữ liệu số thực/EU khác 1 giữ cận singleton toàn DB cũ.
     * Không đổi ngưỡng, công thức MAU của bài báo hoặc điều kiện re-scan.
     */
    private final class ExtensionPlan {
        private final int[] descendingPositions;
        private final double[] utilities;
        private ExtensionPlan(List<PiNode> candidates) {
            Integer[] order = new Integer[candidates.size()];
            utilities = new double[order.length];
            for (int i=0;i<order.length;i++) {
                order[i]=i;
                // Cận theo tiền tố chung Q: chỉ cộng utility item j trên các T chứa Q∪{j}.
                // Chặt hơn utility singleton toàn DB; không cộng lặp utility của Q.
                utilities[i] = unitExternalUtilities && allIntegerUtilities && totalTransactionUtility < 0x1.0p52
                        ? candidates.get(i).projectedUtility : globalItemUtility.get(candidates.get(i).item);
            }
            java.util.Arrays.sort(order, (a,b)->Double.compare(utilities[b],utilities[a]));
            descendingPositions = new int[order.length];
            for(int i=0;i<order.length;i++)descendingPositions[i]=order[i];
        }
        private boolean canReach(PiNode node,int length,int first,double lower) {
            double upperUtility=node.sumUtility;
            int added=0;
            for(int position:descendingPositions)if(position>=first){
                upperUtility+=utilities[position]; added++;
                if(upperUtility/(length+added)>=lower-Math.max(1,lower)*1e-12)return true;
            }
            return false;
        }
    }

    private PiNode combine(PiNode left, PiNode right, double lower) {
        combinedNodes++;
        // Mọi siêu mẫu chứa hai item cuối đều có AU ≤ cận cặp này.
        // Loại trước khi cấp phát entry; không dùng AU hiện tại để loại siêu mẫu.
        if (pairUpperBounds != null && pairUpperBounds[Math.min(left.pairPosition, right.pairPosition)]
                [Math.max(left.pairPosition, right.pairPosition)] < lower) {
            earlyJoinsPruned++;
            return null;
        }
        // AUUB chặn mọi siêu mẫu. Không cấp phát/ghép nếu cận đã dưới Sl×TU.
        // Dùng biên bảo thủ cho số thực; không loại mẫu chỉ vì AU hiện tại thấp.
        if (Math.min(left.auub, right.auub) < lower - Math.max(1.0, lower) * 1e-12) {
            earlyJoinsPruned++;
            return null;
        }
        PiNode combined = new PiNode(right.item);
        combined.pairPosition = right.pairPosition;
        combined.auub = Math.min(left.auub, right.auub);
        combined.maximumRemainingNumber = Math.min(left.maximumRemainingNumber, right.maximumRemainingNumber);
        // Cắt ghép rỗng bằng khoảng vị trí: không có giao dịch chung thì không có mẫu ghép.
        if (left.entries.isEmpty() || right.entries.isEmpty()
                || left.entries.get(left.entries.size()-1).transactionIndex < right.entries.get(0).transactionIndex
                || right.entries.get(right.entries.size()-1).transactionIndex < left.entries.get(0).transactionIndex) return combined;
        // Các list giữ cùng thứ tự giao dịch gốc. Merge hai con trỏ thay HashMap mới mỗi phép ghép.
        // Chỉ số là vị trí trong allTransactions, không giả định TID là số hoặc đã sắp xếp.
        // AUUB trên giao dịch chung chặn cả mẫu ghép và mọi siêu mẫu.
        // Suffix được dựng một lần khi node được dùng để ghép nhiều nhánh.
        boolean exactBound = unitExternalUtilities && allIntegerUtilities && totalTransactionUtility < 0x1.0p52;
        double[] suffix = earlyJoinBounds ? suffixAuub(left) : null;
        double matchedUpper = 0.0;
        double conservativeLower = lower - Math.max(1.0, lower) * 1e-12;
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.entries.size() && rightIndex < right.entries.size()) {
            if (((leftIndex + rightIndex) & 4095) == 0) checkCancelled();
            PiEntry leftEntry = left.entries.get(leftIndex);
            PiEntry rightEntry = right.entries.get(rightIndex);
            if (leftEntry.transactionIndex < rightEntry.transactionIndex) {
                leftIndex = left.entries.size() - leftIndex >= 8L * (right.entries.size() - rightIndex)
                        ? seekPosition(left.entries, leftIndex + 1, rightEntry.transactionIndex) : leftIndex + 1;
                if (earlyJoinBounds) {
                    double remainingUpper = matchedUpper + suffix[leftIndex];
                    if ((exactBound ? remainingUpper : Math.nextUp(remainingUpper)) < conservativeLower) { earlyJoinsPruned++; return null; }
                }
                continue;
            }
            if (leftEntry.transactionIndex > rightEntry.transactionIndex) {
                rightIndex = right.entries.size() - rightIndex >= 8L * (left.entries.size() - leftIndex)
                        ? seekPosition(right.entries, rightIndex + 1, leftEntry.transactionIndex) : rightIndex + 1;
                continue;
            }
            double utility = leftEntry.utility + rightEntry.utility - leftEntry.prefixUtility;
            PiEntry entry = new PiEntry(leftEntry.transactionIndex, utility, leftEntry.utility, rightEntry.lastItemUtility);
            // Mặc định: MRU ghép bằng max, đúng mô tả PIHAUPA trang 14.
            // Chế độ thử nghiệm: cận suffix riêng chỉ giữ maximum sau right,
            // vì DFS chỉ thêm item phía sau right. Không gán cận này cho tác giả.
            entry.maximumRemainingUtility = suffixRemainingBound ? rightEntry.maximumRemainingUtility
                    : Math.max(leftEntry.maximumRemainingUtility, rightEntry.maximumRemainingUtility);
            combined.entries.add(entry);
            combined.sumUtility += utility;
            if (exactBound) combined.projectedUtility += rightEntry.lastItemUtility;
            if (earlyJoinBounds) {
                matchedUpper += transactionMaximums[leftEntry.transactionIndex];
                if (!exactBound) matchedUpper = Math.nextUp(matchedUpper);
            }
            leftIndex++;
            rightIndex++;
        }
        if (earlyJoinBounds) {
            if (matchedUpper < conservativeLower) { earlyJoinsPruned++; return null; }
            combined.auub = matchedUpper;
        }
        return combined;
    }

    /** Cận suffix được cộng làm tròn lên: không làm giảm cận do sai số double.
     * Khi giao dịch không khớp bị bỏ qua, suffix tự loại MU(T) tương ứng.
     * Không dùng AU hoặc MAU theo thứ tự để loại node anh em.
     */
    private double[] suffixAuub(PiNode node) {
        if (node.suffixAuub == null) {
            double[] suffix = new double[node.entries.size() + 1];
            boolean exactBound = unitExternalUtilities && allIntegerUtilities && totalTransactionUtility < 0x1.0p52;
            for (int i = node.entries.size() - 1; i >= 0; i--) {
                if ((i & 4095) == 0) checkCancelled();
                double value = suffix[i + 1] + transactionMaximums[node.entries.get(i).transactionIndex];
                suffix[i] = exactBound ? value : Math.nextUp(value);
            }
            node.suffixAuub = suffix;
        }
        return node.suffixAuub;
    }

    /** Ghép thích nghi: list lệch kích thước lớn thì nhảy bằng binary search.
     * Chỉ bỏ các vị trí < target, không bỏ giao dịch khớp. Thứ tự kết quả không đổi.
     * List cân bằng vẫn dùng merge tuyến tính, tránh thêm chi phí tìm kiếm nhị phân.
     */
    private static int seekPosition(List<PiEntry> entries, int first, int target) {
        int low = first;
        int high = entries.size();
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (entries.get(middle).transactionIndex < target) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    /** Cận suffix bổ sung dùng cùng biểu thức với MAU, thay MRU bằng maximum
     * trên suffix sau item cuối. MRN vẫn là cận trên số item còn lại. Vì suffix
     * là tập con của remaining gốc, cận này không lớn hơn MAU của bài báo.
     * Mặc định vẫn tính MAU gốc; chỉ chế độ thử nghiệm dùng cận suffix riêng.
     * Mỗi số hạng không âm, nên khi tổng
     * đã đạt Sl×TU, các số hạng còn lại không thể đổi quyết định thành loại bỏ.
     * Dừng sớm phép kiểm tra, không sửa công thức MAU hay utility của mẫu.
     */
    private boolean canExpandByRemainingBound(PiNode node, int patternLength, double lower) {
        double total = 0.0;
        int processed = 0;
        for (PiEntry entry : node.entries) {
            double remaining = entry.maximumRemainingUtility;
            double average = entry.utility / patternLength;
            if (remaining > average && node.maximumRemainingNumber > 0) {
                total += (entry.utility + node.maximumRemainingNumber * remaining)
                        / (patternLength + node.maximumRemainingNumber);
            } else if (remaining > 0.0) {
                total += (entry.utility + remaining) / (patternLength + 1.0);
            }
            // Kiểm tra theo khối, tránh thêm một nhánh so sánh ở mỗi entry.
            if ((++processed & 63) == 0 && total >= lower) return true;
        }
        return total >= lower;
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
            managedPatternTrie.update(transaction, externalUtilities,
                    unitExternalUtilities && allIntegerUtilities && totalTransactionUtility < 0x1.0p52);
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
            if (transaction.hasCompactStorage()) {
                transaction.forEachUtility((item, value) -> {
                    if (!externalUtilities.containsKey(item))
                        throw new IllegalArgumentException("Missing external utility for item " + item);
                    if (!Double.isFinite(value) || value <= 0.0)
                        throw new IllegalArgumentException("Internal utility must be finite and positive for item " + item + " in " + transaction.tid());
                });
                continue;
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
        private int pairPosition;
        private List<PiEntry> entries = new ArrayList<>();
        private double sumUtility;
        private double auub;
        private double projectedUtility;
        private double[] suffixAuub;
        private int maximumRemainingNumber;

        private PiNode(String item) {
            this.item = item;
        }
    }

    private static final class PiEntry {
        private final int transactionIndex;
        private final double utility;
        private final double prefixUtility;
        private final double lastItemUtility;
        private double maximumRemainingUtility;

        private PiEntry(int transactionIndex, double utility, double prefixUtility, double lastItemUtility) {
            this.transactionIndex = transactionIndex;
            this.utility = utility;
            this.prefixUtility = prefixUtility;
            this.lastItemUtility = lastItemUtility;
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
     * Cây cập nhật PT theo AUUB giảm dần như bài báo; khóa mẫu vẫn chuẩn hóa.
     * Thứ tự được chốt lúc rebuild và không đổi giữa hai lần re-scan. During an
     * incremental update it follows only paths that already exist in the managed
     * pattern set. This avoids enumerating all 2^n subsets of a wide transaction.
     */
    private static final class ManagedPatternTrie {
        private final TrieNode root = new TrieNode(null);

        private void rebuild(Map<PatternKey, PatternStat> patterns, Map<String, Double> auub) {
            root.children.clear();
            root.stat = null;
            for (Map.Entry<PatternKey, PatternStat> entry : patterns.entrySet()) {
                TrieNode current = root;
                List<String> ordered = new ArrayList<>(entry.getKey().items);
                ordered.sort(Comparator.comparingDouble((String item) -> auub.getOrDefault(item, 0.0))
                        .reversed().thenComparing(Comparator.naturalOrder()));
                for (String item : ordered) {
                    current = current.children.computeIfAbsent(item, TrieNode::new);
                }
                current.stat = entry.getValue();
            }
        }

        private void update(Transaction transaction, Map<String, Double> externalUtilities, boolean exactIntegerSums) {
            updateChildren(root, transaction, externalUtilities, 0.0, exactIntegerSums);
        }

        private void updateChildren(TrieNode parent, Transaction transaction,
                                    Map<String, Double> externalUtilities, double prefixUtility, boolean exactIntegerSums) {
            for (TrieNode child : parent.children.values()) {
                if (!transaction.internalUtilities().containsKey(child.item)) {
                    continue;
                }
                double pathUtility = exactIntegerSums
                        ? prefixUtility + transaction.utilityOf(child.item, externalUtilities) : 0.0;
                if (child.stat != null) {
                    // Cây đổi thứ tự nhưng phép cộng utility giữ thứ tự chuẩn hóa cũ.
                    // Tránh sai khác làm tròn số thực tại biên ngưỡng phân loại.
                    double canonicalUtility = pathUtility;
                    if (!exactIntegerSums) {
                        canonicalUtility = 0.0;
                        for (String item : child.stat.items) {
                            canonicalUtility += transaction.utilityOf(item, externalUtilities);
                        }
                    }
                    child.stat.sumUtility += canonicalUtility;
                }
                updateChildren(child, transaction, externalUtilities, pathUtility, exactIntegerSums);
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
