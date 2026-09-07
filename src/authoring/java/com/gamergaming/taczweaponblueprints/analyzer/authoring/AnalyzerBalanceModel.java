package com.gamergaming.taczweaponblueprints.analyzer.authoring;

import java.util.OptionalLong;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable;

/** First-blueprint acquisition from zero matching progress, including partial reconstruction. */
public final class AnalyzerBalanceModel {
    public static final int MAX_THRESHOLD = 1_000_000;
    private AnalyzerBalanceModel() { }

    public record Estimate(boolean reachable, double expectedAttempts,
            OptionalLong maximumAttempts) { }

    public static Estimate estimate(AnalyzerOutcomeTable table, int threshold) {
        if (threshold < 0 || threshold > MAX_THRESHOLD || table.fragmentWeight() > 0 && threshold == 0)
            throw new IllegalArgumentException("positive fragments require a threshold from 1 to 1000000");
        long useful = (long) table.blueprintWeight() + table.fragmentWeight();
        if (useful == 0) return new Estimate(false, 0, OptionalLong.empty());
        if (table.fragmentWeight() == 0)
            return new Estimate(true, (double) table.totalWeight() / useful,
                    table.noResultWeight() == 0 ? OptionalLong.of(1) : OptionalLong.empty());

        // E(r) = [total + fragmentWeight * mean(E(max(0,r-k)))] / usefulWeight.
        // Removing the no-result self-loop analytically avoids cancellation for tiny chances.
        // Uniform inclusive ranges permit a sliding window and at most 65 stored states.
        int min = table.minimumFragments(), max = table.maximumFragments(), size = max + 1;
        double[] attempts = new double[size];
        double sumAttempts = 0, expected = 0;
        double fragmentPerQuantity = (double) table.fragmentWeight() / (max - min + 1);
        for (int remaining = 1; remaining <= threshold; remaining++) {
            if (remaining >= min) {
                sumAttempts += attempts[(remaining - min) % size];
            }
            if (remaining > max) {
                sumAttempts -= attempts[(remaining - max - 1) % size];
            }
            expected = (table.totalWeight() + fragmentPerQuantity * sumAttempts) / useful;
            attempts[remaining % size] = expected;
        }
        var maximum = table.noResultWeight() == 0
                ? OptionalLong.of(((long) threshold + min - 1) / min) : OptionalLong.empty();
        return new Estimate(true, expected, maximum);
    }
}
