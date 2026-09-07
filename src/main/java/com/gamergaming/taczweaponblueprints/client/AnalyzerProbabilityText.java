package com.gamergaming.taczweaponblueprints.client;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Exact weights stay on the wire; presentation never turns a possible outcome into 0% or 100%. */
public final class AnalyzerProbabilityText {
    private AnalyzerProbabilityText() { }
    public static String format(int weight, long total) {
        if (total <= 0 || weight < 0 || weight > total) throw new IllegalArgumentException("invalid probability");
        if (weight == 0) return "0%";
        if (weight == total) return "100%";
        var numerator = BigDecimal.valueOf(weight).multiply(BigDecimal.valueOf(100));
        var denominator = BigDecimal.valueOf(total);
        if (numerator.compareTo(denominator.movePointLeft(2)) < 0) return "<0.01%";
        if (numerator.compareTo(denominator.multiply(new BigDecimal("99.99"))) > 0) return ">99.99%";
        var rounded = numerator.divide(denominator, 2, RoundingMode.HALF_UP);
        boolean exact = rounded.multiply(denominator).compareTo(numerator) == 0;
        return (exact ? "" : "≈") + rounded.stripTrailingZeros().toPlainString() + "%";
    }
}
