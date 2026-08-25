package com.gamergaming.taczweaponblueprints.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.gamergaming.taczweaponblueprints.client.ClientRendererRegistry;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModItems;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.BlueprintDiscoveryService;
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
            String bpId = getBpId(stack);
            handleBlueprintUse(player, stack, bpId);
        }

        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide);
    }

    private void handleBlueprintUse(Player player, ItemStack stack, String bpId) {
        BlueprintData data = BlueprintDataManager.SERVER.getBlueprintData(bpId);
        if (data == null) {
            player.displayClientMessage(Component.translatable("message.taczweaponblueprints.blueprint.invalid_blueprint"), true);
            return;
        }

        Optional<IPlayerRecipeData> recipeData = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve();
        if (recipeData.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.taczweaponblueprints.blueprint.data_unavailable"),
                    true);
            return;
        }

        BlueprintDataManager.SERVER.migrateLegacyUnlocks(recipeData.get());
        if (recipeData.get().hasBlueprint(bpId)) {
            player.displayClientMessage(Component.translatable("message.taczweaponblueprints.blueprint.already_known"), true);
            return;
        }
        if (!recipeData.get().addBlueprint(bpId)) {
            player.displayClientMessage(Component.translatable("message.taczweaponblueprints.blueprint.progression_full"), true);
            return;
        }
        // Retain the canonical recipe list for downgrade compatibility. New code
        // uses the blueprint output ID as the durable progression identity.
        recipeData.get().addRecipe(data.getRecipeId().toString());

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.displayClientMessage(
                Component.translatable(
                        "message.taczweaponblueprints.blueprint.unlocked",
                        Component.translatable(data.getNameKey())),
                true);

        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.syncPlayerRecipeData(serverPlayer);
        }
    }
}
