package piHAUPA;

import java.util.List;

public record Batch(String name, List<Transaction> transactions) {
}

