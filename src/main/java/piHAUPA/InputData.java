package piHAUPA;

import java.util.List;
import java.util.Map;

public record InputData(
        double upperThreshold,
        double lowerThreshold,
        Map<String, Double> externalUtilities,
        List<Batch> batches) {
}

