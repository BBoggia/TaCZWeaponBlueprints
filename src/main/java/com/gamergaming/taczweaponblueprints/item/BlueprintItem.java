package com.gamergaming.taczweaponblueprints.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.client.ClientRendererRegistry;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModItems;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.network.SyncPlayerRecipeDataPacket;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.util.ItemNameFilterHelper;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.resource.CommonAssetsManager;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
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
        CompoundTag tag = stack.getTag();
        return (tag != null && tag.contains("bpId")) ? tag.getString("bpId") : "NULL";
    }

    public static ItemStack createBlueprint(String bpId) {
        ItemStack blueprint = new ItemStack(ModItems.BLUEPRINT_ITEM.get());
        CompoundTag tag = blueprint.getOrCreateTag();
        tag.putString("bpId", bpId);
        return blueprint;
    }

    @Override
    public Component getName(ItemStack stack) {
        String bpId = getBpId(stack);
        BlueprintData data = BlueprintDataManager.INSTANCE.getBlueprintData(bpId);
        if (data != null) {
            Component firstHalfName = Component.translatable("item.taczweaponblueprints.blueprint");
            String nameKey = data.getNameKey();
            if (nameKey.contains("attachments")) {
                nameKey = nameKey.replace(".attachments.", ".attachment.");
            }
            Component secondHalfName = Component.translatable(nameKey);
            if (secondHalfName.getString().strip() == nameKey.strip()) {
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
            return Component.translatable("item.taczweaponblueprints.blueprint.invalid");
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag flag) {
        String bpId = getBpId(stack);
        BlueprintData data = BlueprintDataManager.INSTANCE.getBlueprintData(bpId);
        if (data != null) {
            String tooltipTemplate = Component.translatable(data.getTooltipKey()).getString();
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

            tooltip.add(Component.literal(String.format(tooltipTemplate, itemName)));
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
            handleBlueprintUse(world, player, stack, bpId);
        }

        return InteractionResultHolder.success(stack);
    }

    // private void handleBlueprintUse(Level world, Player player, ItemStack stack, String bpId) {
    //     BlueprintData data = BlueprintDataManager.INSTANCE.getBlueprintData(bpId);
    //     if (data != null) {
    //         GunSmithTableRecipe recipe = data.getRecipe();
    //         TaCZWeaponBlueprints.LOGGER.info("BlueprintItem handleBlueprintUse: " + data.getRecipeId() + " - " + recipe.getResult().getResult().getDisplayName().getString());
            
    //         if (recipe != null && !CommonAssetManager.INSTANCE.getRecipe(data.getRecipeId()).isPresent()) {
    //             CommonAssetManager.INSTANCE.putRecipe(data.getRecipeId(), recipe);
    //             player.displayClientMessage(Component.translatable("message.taczweaponblueprints.blueprint.unlocked", Component.translatable(data.getNameKey())), true);
    //             stack.shrink(1); // Consume one blueprint item
    //         } else {
    //             player.displayClientMessage(Component.translatable("message.taczweaponblueprints.blueprint.already_known"), true);
    //         }
    //     } else {
    //         player.displayClientMessage(Component.translatable("message.taczweaponblueprints.blueprint.invalid_blueprint"), true);
    //     }
    // }

    private void handleBlueprintUse(Level world, Player player, ItemStack stack, String bpId) {
        BlueprintData data = BlueprintDataManager.INSTANCE.getBlueprintData(bpId);
        if (data != null) {
            player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(recipeData -> {
                if (!recipeData.hasRecipe(data.getRecipeId().toString())) {
                    recipeData.addRecipe(data.getRecipeId().toString());
                    player.displayClientMessage(Component.translatable("message.taczweaponblueprints.blueprint.unlocked", Component.translatable(data.getNameKey())), true);
                    stack.shrink(1); // Consume one blueprint item

                    // Sync to client
                    if (player instanceof ServerPlayer serverPlayer) {
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new SyncPlayerRecipeDataPacket(recipeData.getLearnedRecipes()));
                    }
                } else {
                    player.displayClientMessage(Component.translatable("message.taczweaponblueprints.blueprint.already_known"), true);
                }
            });
        } else {
            player.displayClientMessage(Component.translatable("message.taczweaponblueprints.blueprint.invalid_blueprint"), true);
        }
    }
}