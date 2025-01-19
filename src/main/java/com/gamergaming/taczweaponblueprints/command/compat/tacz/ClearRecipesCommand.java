package com.gamergaming.taczweaponblueprints.command.compat.tacz;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tacz.guns.resource.CommonAssetManager;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ClearRecipesCommand {
    
    private static final String CLEAR_RECIPES_NAME = "clearRecipes";

    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        LiteralArgumentBuilder<CommandSourceStack> clearRecipes = Commands.literal(CLEAR_RECIPES_NAME);
        clearRecipes.executes(ClearRecipesCommand::clearAllRecipes);
        return clearRecipes;
    }

    private static int clearAllRecipes(CommandContext<CommandSourceStack> context) {
        CommonAssetManager.INSTANCE.clearRecipes();
        if (context.getSource().getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.translatable("commands.taczweaponblueprints.clear_tacz_recipes.success"));
        }
        return Command.SINGLE_SUCCESS;
    }
    
}
