package piHAUPA;

import java.util.List;

public record BatchResult(
        String action,
        boolean rescanTriggered,
        Double tightRescanLimit,
        Double tightRescanDenominator,
        Double originalRescanLimit,
        double accumulatedTransactionUtility,
        double accumulatedMaximumUtility,
        double batchTransactionUtility,
        double batchMaximumUtility,
        double totalTransactionUtility,
        double minUtilUpper,
        double minUtilLower,
        List<PatternResult> largePatterns,
        List<PatternResult> preLargePatterns) {
}
