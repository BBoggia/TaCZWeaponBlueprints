package com.gamergaming.taczweaponblueprints.command.compat.tacz;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
// import com.tacz.guns.resource.CommonAssetManager;
// import com.tacz.guns.resource.GunPackLoader;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ReloadRecipesCommand {

    private static final String RELOAD_RECIPES_NAME = "reloadRecipes";

    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        LiteralArgumentBuilder<CommandSourceStack> reloadRecipes = Commands.literal(RELOAD_RECIPES_NAME);
        reloadRecipes.executes(ReloadRecipesCommand::reloadAllRecipes);
        return reloadRecipes;
    }

    private static int reloadAllRecipes(CommandContext<CommandSourceStack> context) {
        // CommonAssetManager.INSTANCE.clearRecipes();
        // CommonGunPackLoader.reloadRecipes();
        if (context.getSource().getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.translatable("commands.taczweaponblueprints.reload_tacz_recipes.success"));
        }
        return Command.SINGLE_SUCCESS;
    }
    
}
