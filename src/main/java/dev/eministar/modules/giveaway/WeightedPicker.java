package dev.eministar.modules.giveaway;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class WeightedPicker {

    public static <T> List<T> pickWeighted(Map<T, Integer> weights, int count) {
        if (weights.isEmpty() || count <= 0) {
            return new ArrayList<>();
        }

        List<T> result = new ArrayList<>();
        Map<T, Integer> remainingWeights = new HashMap<>(weights);

        for (int i = 0; i < count && !remainingWeights.isEmpty(); i++) {
            T picked = pickOne(remainingWeights);
            if (picked != null) {
                result.add(picked);
                remainingWeights.remove(picked); // No duplicates
            }
        }

        return result;
    }

    private static <T> T pickOne(Map<T, Integer> weights) {
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
        if (totalWeight <= 0) {
            return null;
        }

        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;

        for (Map.Entry<T, Integer> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (random < cumulative) {
                return entry.getKey();
            }
        }

        return null;
    }

    public static <T> List<T> pickRandom(List<T> items, int count) {
        if (items.isEmpty() || count <= 0) {
            return new ArrayList<>();
        }

        List<T> shuffled = new ArrayList<>(items);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }
}
