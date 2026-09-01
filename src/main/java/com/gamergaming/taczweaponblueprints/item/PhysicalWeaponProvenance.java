package com.gamergaming.taczweaponblueprints.item;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.tacz.guns.api.item.IGun;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Versioned, positive acquisition evidence attached to physical TaCZ guns. */
public record PhysicalWeaponProvenance(
        int format,
        Origin origin,
        ResourceLocation sourceId) {
    public static final String TAG_KEY = "taczweaponblueprints:weapon_provenance";
    public static final int CURRENT_FORMAT = 1;

    public PhysicalWeaponProvenance {
        if (format != CURRENT_FORMAT || origin == null || sourceId == null
                || sourceId.toString().length()
                        > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("invalid physical weapon provenance");
        }
    }

    public static boolean stampCrafted(
            ItemStack stack,
            ResourceLocation recipeId) {
        return stamp(stack, new PhysicalWeaponProvenance(
                CURRENT_FORMAT, Origin.CRAFTED_SURVIVAL, recipeId), true);
    }

    public static boolean stampLootGenerated(
            ItemStack stack,
            ResourceLocation lootTableId) {
        // Never launder a positively identified crafted weapon if another
        // system later places that same stack in a generated loot collection.
        return stamp(stack, new PhysicalWeaponProvenance(
                CURRENT_FORMAT, Origin.LOOT_GENERATED, lootTableId), false);
    }

    private static boolean stamp(
            ItemStack stack,
            PhysicalWeaponProvenance provenance,
            boolean replaceExisting) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IGun)
                || provenance == null) {
            return false;
        }
        CompoundTag root = stack.getOrCreateTag();
        if (!replaceExisting && root.contains(TAG_KEY)) {
            return false;
        }
        root.put(TAG_KEY, provenance.toTag());
        return true;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("format", format);
        tag.putString("origin", origin.serializedName());
        tag.putString("source_id", sourceId.toString());
        return tag;
    }

    public static Optional<PhysicalWeaponProvenance> from(ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? Optional.empty()
                : fromTag(stack.getTag());
    }

    public static Optional<PhysicalWeaponProvenance> fromTag(CompoundTag root) {
        if (root == null || !root.contains(TAG_KEY, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag tag = root.getCompound(TAG_KEY);
        if (!tag.contains("format", Tag.TAG_INT)
                || !tag.contains("origin", Tag.TAG_STRING)
                || !tag.contains("source_id", Tag.TAG_STRING)) {
            return Optional.empty();
        }
        Origin origin = Origin.parse(tag.getString("origin")).orElse(null);
        ResourceLocation sourceId = ResourceLocation.tryParse(tag.getString("source_id"));
        if (tag.getInt("format") != CURRENT_FORMAT || origin == null || sourceId == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PhysicalWeaponProvenance(
                    tag.getInt("format"), origin, sourceId));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public boolean verifiedLoot() {
        return origin == Origin.LOOT_GENERATED;
    }

    public enum Origin {
        CRAFTED_SURVIVAL("crafted_survival"),
        LOOT_GENERATED("loot_generated");

        private final String serializedName;

        Origin(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

        public static Optional<Origin> parse(String value) {
            if (value == null) {
                return Optional.empty();
            }
            for (Origin origin : values()) {
                if (origin.serializedName.equals(value)) {
                    return Optional.of(origin);
                }
            }
            return Optional.empty();
        }
    }
}
