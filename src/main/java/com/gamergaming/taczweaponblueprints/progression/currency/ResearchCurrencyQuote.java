package com.gamergaming.taczweaponblueprints.progression.currency;

/** Counts are currency units, never item-stack counts or a mixture of Scrap and RP. */
public record ResearchCurrencyQuote(Status status, ResearchCurrencyPolicy policy,
        long requestedDebit, long requestedReward, long availableCurrency, long deliveredReward,
        int outputUnits, int inventoryUnits, int droppedUnits) {
    public enum Status {
        READY, INVALID_CURRENCY, INSUFFICIENT_CURRENCY, INSUFFICIENT_MATERIALS,
        OUTPUT_BLOCKED, DELIVERY_FULL, DELIVERY_LIMIT, POINT_CAP_REACHED
    }
    public ResearchCurrencyQuote {
        if (status == null || policy == null || requestedDebit < 0 || requestedReward < 0
                || availableCurrency < 0 || deliveredReward < 0 || deliveredReward > requestedReward
                || outputUnits < 0 || inventoryUnits < 0 || droppedUnits < 0
                || status != Status.READY && (deliveredReward != 0 || outputUnits != 0 || inventoryUnits != 0 || droppedUnits != 0)
                || policy.kind() == ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM
                    && (long) outputUnits + inventoryUnits + droppedUnits != deliveredReward
                || policy.kind() == ResearchCurrencyPolicy.Kind.BANKED_RP
                    && (outputUnits != 0 || inventoryUnits != 0 || droppedUnits != 0)) {
            throw new IllegalArgumentException("invalid currency quote");
        }
    }
    public boolean ready() { return status == Status.READY; }
}
