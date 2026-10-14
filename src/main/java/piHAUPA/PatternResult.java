package piHAUPA;

import java.util.List;

public record PatternResult(List<String> items, double sumUtility, int length, PatternType type) {
    public double averageUtility() {
        return sumUtility / length;
    }

    public String displayName() {
        if (items.stream().anyMatch(item -> item.matches("[0-9]+"))) {
            return "{" + String.join(", ", items) + "}";
        }
        return String.join("", items);
    }
}

