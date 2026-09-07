package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.network.SyncResearchCurrencyPacket;

/** Connection-scoped authoritative currency publication. Saved RP retains its separate meaning. */
public final class ClientResearchCurrency {
    private static SyncResearchCurrencyPacket current;
    private ClientResearchCurrency() { }
    public static void accept(SyncResearchCurrencyPacket snapshot) { if (snapshot != null && (current == null || snapshot.policy().revision() >= current.policy().revision())) current = snapshot; }
    public static java.util.Optional<SyncResearchCurrencyPacket> current() { return java.util.Optional.ofNullable(current); }
    public static com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy policy() {
        return current().map(SyncResearchCurrencyPacket::policy).orElseGet(() -> com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy.banked(0,1));
    }
    public static net.minecraft.network.chat.MutableComponent text(String key, Object... values) {
        return com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyText.translatable(policy(), key, values);
    }
    public static net.minecraft.world.item.ItemStack icon() {
        return policy().physicalUnit().orElse(net.minecraft.world.item.ItemStack.EMPTY);
    }
    public static String dataMode() { return current().map(p -> p.dataSettings().behavior().name().toLowerCase(java.util.Locale.ROOT)).orElse("unknown"); }
    public static void clear() { current = null; }
}
