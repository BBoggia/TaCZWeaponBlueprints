package com.gamergaming.taczweaponblueprints.villager;

/** Arithmetic shared by native offers and validation. Secondary material is never discounted. */
public final class VillagerTradePricing {
    public static final int DEFAULT_MINIMUM_BASIS_POINTS = 7500;
    private VillagerTradePricing() {}
    public record Counts(int primary, int secondary, int minimumPrimary) {}
    /** Authored denominations survive normalization so later repricing never discounts material B. */
    public record NormalizedPrice(int originalPrimary, int protectedSecondary, Counts charged) {}
    public static NormalizedPrice price(int primary, int secondary, boolean identical,
            int primaryLimit, int secondaryLimit, int multiplierBasisPoints, int minimumBasisPoints) {
        int scaled = scale(primary, multiplierBasisPoints, primaryLimit);
        if (secondary < 0 || secondary > secondaryLimit) throw new IllegalArgumentException("Secondary input exceeds its stack limit");
        return new NormalizedPrice(scaled, secondary, normalize(scaled, secondary, identical, primaryLimit, minimumBasisPoints));
    }
    /** Round upward; reject unrepresentable prices/stock instead of clamping or giving free output. */
    public static int scale(int count, int basisPoints, int maximum) {
        if (count < 1 || basisPoints < 1 || maximum < 1) throw new IllegalArgumentException("Invalid trade multiplier");
        long result = ((long) count * basisPoints + 9999) / 10000;
        if (result < 1 || result > maximum) throw new IllegalArgumentException("Scaled trade amount exceeds its limit");
        return (int) result;
    }
    public static Counts normalize(int primary, int secondary, boolean identical, int stackLimit, int basisPoints) {
        if (stackLimit < 1 || primary < 1 || primary > stackLimit || secondary < 0
                || basisPoints < 0 || basisPoints > 10000) throw new IllegalArgumentException("Invalid trade price");
        long minimum = Math.max(1, ((long) primary * basisPoints + 9999) / 10000);
        long combined = identical ? (long) primary + secondary : primary;
        if (combined > stackLimit) throw new IllegalArgumentException("Identical inputs exceed one stack; supply an alternate price");
        return new Counts((int) combined, identical ? 0 : secondary, (int) (minimum + (identical ? secondary : 0)));
    }
    public static int cost(int base, int demand, float multiplier, int adjustment, int minimum, int stackLimit) {
        if (base < 1 || minimum < 1 || minimum > stackLimit || !Float.isFinite(multiplier) || multiplier < 0)
            throw new IllegalArgumentException("Invalid live trade terms");
        // Widen before multiplication; accumulated demand must never wrap into a discount.
        double bonus = Math.max(0, Math.floor((float) ((long) base * demand) * multiplier));
        return (int) Math.max(minimum, Math.min(stackLimit, base + bonus + adjustment));
    }
}
