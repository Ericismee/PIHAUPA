package piHAUPA;

public record MiningStats(
        long rescanCount,
        long patternsVisited,
        long combinedNodes,
        double totalTransactionUtilityProcessed,
        double totalMaximumUtilityProcessed) {
}

