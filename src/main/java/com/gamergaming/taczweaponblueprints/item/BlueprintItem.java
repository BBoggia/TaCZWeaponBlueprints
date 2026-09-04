package com.gamergaming.taczweaponblueprints.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.gamergaming.taczweaponblueprints.client.ClientRendererRegistry;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModItems;
import com.gamergaming.taczweaponblueprints.progression.BlueprintDiscoveryService;
import com.gamergaming.taczweaponblueprints.progression.BlueprintLearningService;
import com.gamergaming.taczweaponblueprints.progression.BlueprintLearningService.Status;
import com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.util.ItemNameFilterHelper;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class BlueprintItem extends Item {
    public BlueprintItem(Properties properties) {
        super(properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return ClientRendererRegistry.getBlueprintItemRenderer();
            }
        });
    }

    public static String getBpId(ItemStack stack) {
        return getBlueprintId(stack).map(ResourceLocation::toString).orElse("NULL");
    }

    /** Returns a bounded canonical ID only for a valid physical blueprint stack. */
    public static Optional<ResourceLocation> getBlueprintId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlueprintItem)) {
            return Optional.empty();
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("bpId", Tag.TAG_STRING)) {
            return Optional.empty();
        }
        return parseBlueprintId(tag.getString("bpId"));
    }

    public static Optional<ResourceLocation> parseBlueprintId(String value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value.isBlank() || value.length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            return Optional.empty();
        }
        return Optional.ofNullable(ResourceLocation.tryParse(value));
    }

    public static ItemStack createBlueprint(String bpId) {
        ItemStack blueprint = new ItemStack(ModItems.BLUEPRINT_ITEM.get());
        CompoundTag tag = blueprint.getOrCreateTag();
        tag.putString("bpId", bpId);
        return blueprint;
    }

    public static ItemStack createBlueprint(String bpId, BlueprintProvenance provenance) {
        if (provenance == null) {
            throw new IllegalArgumentException("blueprint provenance cannot be null");
        }
        ItemStack blueprint = createBlueprint(bpId);
        blueprint.getOrCreateTag().put(BlueprintProvenance.TAG_KEY, provenance.toTag());
        return blueprint;
    }

    public static Optional<BlueprintProvenance> getProvenance(ItemStack stack) {
        if (getBlueprintId(stack).isEmpty()) {
            return Optional.empty();
        }
        return BlueprintProvenance.fromTag(stack.getTag());
    }

    /**
     * Legacy stacks remain recyclable. A present but malformed provenance tag
     * fails closed so NBT damage cannot turn protected output into RP.
     */
    public static boolean provenanceAllowsRecycling(ItemStack stack) {
        if (getBlueprintId(stack).isEmpty()) {
            return false;
        }
        return BlueprintProvenance.allowsRecycling(stack.getTag());
    }

    public static PhysicalBlueprintLearningMode physicalLearningMode(
            ItemStack stack,
            PhysicalBlueprintLearningMode legacyDefault) {
        if (getBlueprintId(stack).isEmpty()) {
            return PhysicalBlueprintLearningMode.DISABLED;
        }
        return BlueprintProvenance.learningMode(stack.getTag(), legacyDefault);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean selected) {
        super.inventoryTick(stack, level, entity, slotId, selected);
        if (!level.isClientSide && entity instanceof ServerPlayer serverPlayer) {
            // Post-pickup discovery handles the common path immediately. This
            // fallback catches commands, menus, and third-party inserts. Known
            // canonical IDs use an allocation-free membership fast path.
            BlueprintDiscoveryService.discoverInventoryBlueprint(serverPlayer, stack);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        String bpId = getBpId(stack);
        if (bpId == null || bpId.equals("NULL")) {
            return super.getName(stack);
        }
        BlueprintData data = BlueprintDataManager.presentationCatalog().getBlueprintData(bpId);
        if (data != null) {
            Component firstHalfName = Component.translatable("item.taczweaponblueprints.blueprint");
            String nameKey = data.getNameKey();

            Component secondHalfName = Component.translatable(nameKey);
            if (secondHalfName.getString().strip().equals(nameKey.strip())) {
                secondHalfName = Component.translatable(nameKey.replace(".name", ""));
            }

            String itemName;
            switch (data.getItemType()) {
                case "rifle", "shotgun", "pistol", "sniper", "smg", "mg", "rpg":
                    itemName = firstHalfName.getString() + ItemNameFilterHelper.filterGunName(secondHalfName.getString());
                    break;

                case "ammo":
                    itemName = firstHalfName.getString() + ItemNameFilterHelper.filterAmmoName(secondHalfName.getString());
                    break;

                default:
                    itemName = firstHalfName.getString() + secondHalfName.getString();
                    break;
            }

            return Component.literal(itemName);
        } else {

            return Component.translatable("item.taczweaponblueprints.blueprint.invalid_name");
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag flag) {
        String bpId = getBpId(stack);
        BlueprintData data = BlueprintDataManager.presentationCatalog().getBlueprintData(bpId);
        if (data != null) {
            String itemName = Component.translatable(data.getNameKey()).getString();

            switch (data.getItemType()) {
                case "rifle", "shotgun", "pistol", "sniper", "smg", "mg", "rpg":
                    itemName = ItemNameFilterHelper.filterGunName(itemName);
                    break;

                case "ammo":
                    itemName = ItemNameFilterHelper.filterAmmoName(itemName);
                    break;

                default:
                    break;
            }

            tooltip.add(Component.translatable(data.getTooltipKey(), Component.literal(itemName)));
        } else {
            tooltip.add(Component.translatable("item.taczweaponblueprints.blueprint.tooltip.invalid"));
        }
        super.appendHoverText(stack, world, tooltip, flag);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!world.isClientSide) {
            handleBlueprintUse(player, stack);
        }

        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide);
    }

    private void handleBlueprintUse(Player player, ItemStack stack) {
        Optional<ResourceLocation> blueprintId = getBlueprintId(stack);
        if (blueprintId.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable(
                            "message.taczweaponblueprints.blueprint.invalid_blueprint"),
                    true);
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            player.displayClientMessage(
                    Component.translatable(
                            "message.taczweaponblueprints.blueprint.data_unavailable"),
                    true);
            return;
        }

        BlueprintLearningService.Result result =
                BlueprintLearningService.learnPhysicalBlueprint(
                        serverPlayer,
                        blueprintId.orElseThrow(),
                        stack);
        if (!result.successful()) {
            player.displayClientMessage(feedback(result.status()), true);
            return;
        }

        BlueprintData data = BlueprintDataManager.SERVER.getBlueprintData(
                blueprintId.orElseThrow().toString());
        player.displayClientMessage(
                data == null
                        ? Component.translatable(
                                "message.taczweaponblueprints.blueprint.unlocked_generic")
                        : Component.translatable(
                                "message.taczweaponblueprints.blueprint.unlocked",
                                Component.translatable(data.getNameKey())),
                true);
    }

    private static Component feedback(Status status) {
        String key = switch (status) {
            case INVALID_INPUT, CONTENT_UNAVAILABLE, INVALID_IDENTITY ->
                    "message.taczweaponblueprints.blueprint.invalid_blueprint";
            case PLAYER_DATA_UNAVAILABLE, POLICY_UNAVAILABLE ->
                    "message.taczweaponblueprints.blueprint.data_unavailable";
            case POLICY_MISMATCH, STALE_POLICY, TRANSACTION_FAILED ->
                    "message.taczweaponblueprints.blueprint.transaction_failed";
            case BLUEPRINTS_DISABLED ->
                    "message.taczweaponblueprints.blueprint.system_disabled";
            case BLOCKED -> "message.taczweaponblueprints.blueprint.blocked";
            case PROGRESSION_EXEMPT ->
                    "message.taczweaponblueprints.blueprint.progression_exempt";
            case ALREADY_LEARNED ->
                    "message.taczweaponblueprints.blueprint.already_known";
            case PHYSICAL_BLUEPRINT_LEARNING_DISABLED ->
                    "message.taczweaponblueprints.blueprint.learning_disabled";
            case PREREQUISITES_REQUIRED ->
                    "message.taczweaponblueprints.blueprint.prerequisites_required";
            case PROGRESSION_CAPACITY_EXHAUSTED ->
                    "message.taczweaponblueprints.blueprint.progression_full";
            case SUCCESS -> throw new IllegalArgumentException(
                    "successful learning does not have failure feedback");
        };
        return Component.translatable(key);
    }
}
