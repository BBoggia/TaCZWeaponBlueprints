package com.gamergaming.taczweaponblueprints.item;

import java.util.List;
import java.util.Optional;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisRewardClass;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModItems;
import com.gamergaming.taczweaponblueprints.progression.BlueprintDiscoveryService;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.util.ItemNameFilterHelper;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Target-specific ordinary fragments or versioned, reconstruction-only analysis fragments. */
public final class BlueprintFragmentItem extends Item {
    public static final String TARGET_TAG = "BlueprintTarget";
    public static final String ANALYSIS_TAG = "taczweaponblueprints:analysis";
    public static final int ANALYSIS_FORMAT = 1;

    public BlueprintFragmentItem(Properties properties) {
        super(properties);
    }

    /** Returns a target only for an exact, bounded fragment stack. */
    public static Optional<ResourceLocation> getTarget(ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || !(stack.getItem() instanceof BlueprintFragmentItem)) {
            return Optional.empty();
        }
        return getTarget(stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag());
    }

    static Optional<ResourceLocation> getTarget(CompoundTag tag) {
        return identity(tag).map(Identity::target);
    }

    public record Identity(ResourceLocation target, Optional<AnalysisRewardClass> analysisClass) {
        public Identity {
            if (target == null || parseTarget(target.toString()).isEmpty() || analysisClass == null) {
                throw new IllegalArgumentException("invalid fragment identity");
            }
        }
    }

    public static Optional<Identity> identity(ItemStack stack) {
        return stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlueprintFragmentItem)
                ? Optional.empty() : identity(stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag());
    }

    static Optional<Identity> identity(CompoundTag tag) {
        if (tag == null || !tag.contains(TARGET_TAG, Tag.TAG_STRING)) return Optional.empty();
        var target = parseTarget(tag.getString(TARGET_TAG));
        if (target.isEmpty()) return Optional.empty();
        if (!tag.contains(ANALYSIS_TAG)) {
            return tag.size() == 1 ? Optional.of(new Identity(target.orElseThrow(), Optional.empty())) : Optional.empty();
        }
        if (tag.size() != 2 || !tag.contains(ANALYSIS_TAG, Tag.TAG_COMPOUND)) return Optional.empty();
        var marker = tag.getCompound(ANALYSIS_TAG);
        if (marker.size() != 2 || !marker.contains("format", Tag.TAG_INT)
                || marker.getInt("format") != ANALYSIS_FORMAT || !marker.contains("reward_class", Tag.TAG_STRING)) return Optional.empty();
        return AnalysisRewardClass.parse(marker.getString("reward_class"))
                .map(reward -> new Identity(target.orElseThrow(), Optional.of(reward)));
    }

    /** Ordinary discount/RP consumers must use this method, never the shared target accessor. */
    public static Optional<ResourceLocation> getOrdinaryTarget(ItemStack stack) {
        return identity(stack).filter(value -> value.analysisClass().isEmpty()).map(Identity::target);
    }

    public static ItemStack createAnalysis(ResourceLocation target, AnalysisRewardClass reward) {
        return createAnalysis(ModItems.BLUEPRINT_FRAGMENT.get(), target, reward);
    }

    public static ItemStack createAnalysis(BlueprintFragmentItem item, ResourceLocation target, AnalysisRewardClass reward) {
        if (reward == null) throw new IllegalArgumentException("analysis reward class cannot be null");
        var stack = create(item, target);
        var marker = new CompoundTag();
        marker.putInt("format", ANALYSIS_FORMAT);
        marker.putString("reward_class", reward.serializedName());
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, tag -> tag.put(ANALYSIS_TAG, marker));
        return stack;
    }

    public static Optional<ResourceLocation> parseTarget(String value) {
        if (value == null || value.isBlank()
                || value.length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            return Optional.empty();
        }
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        return parsed != null && parsed.toString().equals(value)
                ? Optional.of(parsed)
                : Optional.empty();
    }

    public static ItemStack create(ResourceLocation target) {
        return create(ModItems.BLUEPRINT_FRAGMENT.get(), target);
    }

    static ItemStack create(BlueprintFragmentItem item, ResourceLocation target) {
        if (item == null) {
            throw new IllegalArgumentException("Blueprint Fragment item cannot be null");
        }
        if (target == null || parseTarget(target.toString()).isEmpty()) {
            throw new IllegalArgumentException("Blueprint Fragment target is invalid or oversized");
        }
        ItemStack fragment = new ItemStack(item);
        fragment.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(createTargetTag(target)));
        return fragment;
    }

    static CompoundTag createTargetTag(ResourceLocation target) {
        if (target == null || parseTarget(target.toString()).isEmpty()) {
            throw new IllegalArgumentException("Blueprint Fragment target is invalid or oversized");
        }
        CompoundTag tag = new CompoundTag();
        tag.putString(TARGET_TAG, target.toString());
        return tag;
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int slotId,
            boolean selected) {
        super.inventoryTick(stack, level, entity, slotId, selected);
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            BlueprintDiscoveryService.discoverInventoryFragment(player, stack);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        Optional<ResourceLocation> target = getTarget(stack);
        if (target.isEmpty()) {
            return Component.translatable(
                    "item.taczweaponblueprints.blueprint_fragment.invalid_name");
        }
        BlueprintData data = BlueprintDataManager.presentationCatalog()
                .getBlueprintData(target.orElseThrow().toString());
        if (data == null) {
            return Component.translatable(
                    "item.taczweaponblueprints.blueprint_fragment.unknown_name");
        }
        return Component.translatable(
                "item.taczweaponblueprints.blueprint_fragment.named",
                displayName(data));
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            net.minecraft.world.item.Item.TooltipContext level,
            List<Component> tooltip,
            TooltipFlag flag) {
        Optional<ResourceLocation> target = getTarget(stack);
        BlueprintData data = target
                .map(ResourceLocation::toString)
                .map(BlueprintDataManager.presentationCatalog()::getBlueprintData)
                .orElse(null);
        if (target.isEmpty()) {
            tooltip.add(Component.translatable(
                    "item.taczweaponblueprints.blueprint_fragment.tooltip.invalid"));
        } else if (data == null) {
            tooltip.add(Component.translatable(
                    "item.taczweaponblueprints.blueprint_fragment.tooltip.unknown"));
        } else {
            tooltip.add(Component.translatable(
                    "item.taczweaponblueprints.blueprint_fragment.tooltip.target",
                    displayName(data)));
            var reward = identity(stack).flatMap(Identity::analysisClass);
            if (reward.isPresent()) {
                tooltip.add(Component.translatable("item.taczweaponblueprints.blueprint_fragment.tooltip.analysis"));
                tooltip.add(Component.translatable("item.taczweaponblueprints.blueprint_fragment.tooltip." + reward.orElseThrow().serializedName()));
            } else {
                tooltip.add(Component.translatable("item.taczweaponblueprints.blueprint_fragment.tooltip.archive"));
            }
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }

    private static Component displayName(BlueprintData data) {
        Component translated = Component.translatable(data.getNameKey());
        String name = translated.getString();
        if (name.strip().equals(data.getNameKey().strip())) {
            translated = Component.translatable(data.getNameKey().replace(".name", ""));
            name = translated.getString();
        }
        String filtered = switch (data.getKind()) {
            case GUN -> ItemNameFilterHelper.filterGunName(name);
            case AMMO -> ItemNameFilterHelper.filterAmmoName(name);
            case ATTACHMENT -> name;
        };
        return Component.literal(filtered);
    }
}
