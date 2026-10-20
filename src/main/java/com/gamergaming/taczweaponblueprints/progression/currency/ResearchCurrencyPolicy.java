package com.gamergaming.taczweaponblueprints.progression.currency;

import java.util.Optional;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

/** Server-issued currency identity. A physical policy never exposes a saved RP balance as money. */
public record ResearchCurrencyPolicy(Kind kind, Optional<ResourceLocation> item, int pointCap, long revision) {
    public enum Kind implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
        BANKED_RP, PHYSICAL_ITEM;
        @Override public String prefix() { return "config.taczweaponblueprints.blueprint.researchCurrencyKind"; }
    }
    public static final ResourceLocation SCRAP = ResourceLocation.parse("taczweaponblueprints:scrap");

    public ResearchCurrencyPolicy {
        if (kind == null || item == null || revision < 1 || pointCap < 0
                || pointCap > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || (kind == Kind.PHYSICAL_ITEM) != item.isPresent()
                || kind == Kind.PHYSICAL_ITEM && pointCap != 0
                || item.filter(id -> id.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH
                        || id.equals(ResourceLocation.parse("minecraft:air"))).isPresent()) {
            throw new IllegalArgumentException("invalid research currency policy");
        }
    }

    public static ResearchCurrencyPolicy banked(int cap, long revision) {
        return new ResearchCurrencyPolicy(Kind.BANKED_RP, Optional.empty(), cap, revision);
    }

    public static ResearchCurrencyPolicy physical(ResourceLocation item, long revision) {
        return new ResearchCurrencyPolicy(Kind.PHYSICAL_ITEM, Optional.of(item), 0, revision);
    }

    /** Registry resolution is delayed until registries exist; unknown IDs cannot resolve to air. */
    public Optional<ItemStack> physicalUnit() {
        if (kind != Kind.PHYSICAL_ITEM || !BuiltInRegistries.ITEM.containsKey(item.orElseThrow())) return Optional.empty();
        var value = BuiltInRegistries.ITEM.get(item.orElseThrow());
        if (value == null || value == Items.AIR) return Optional.empty();
        var stack = new ItemStack(value);
        return stack.getMaxStackSize() > 0 && stack.getMaxStackSize() <= 64 ? Optional.of(stack) : Optional.empty();
    }
}
