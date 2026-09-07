package com.gamergaming.taczweaponblueprints.progression.currency;

import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Localized denomination with an explicit variant for existing amount-bearing text. */
public final class ResearchCurrencyText {
    private ResearchCurrencyText() { }
    public static Component name(ResearchCurrencyPolicy policy) {
        return ResearchCurrencyPayments.physical(policy) ? policy.physicalUnit().map(s -> s.getHoverName())
                .orElseGet(() -> Component.literal(policy.item().orElseThrow().toString()))
                : Component.translatable("currency.taczweaponblueprints.banked_rp");
    }
    public static MutableComponent translatable(String key, Object... arguments) {
        return translatable(ModConfigs.BLUEPRINT.currencyPolicy(), key, arguments);
    }
    public static MutableComponent translatable(ResearchCurrencyPolicy policy, String key, Object... arguments) {
        if (!ResearchCurrencyPayments.physical(policy) || !Language.getInstance().has(key + ".currency"))
            return Component.translatable(key, arguments);
        var expanded = java.util.Arrays.copyOf(arguments, arguments.length + 1);
        expanded[arguments.length] = name(policy);
        return Component.translatable(key + ".currency", expanded);
    }
}
