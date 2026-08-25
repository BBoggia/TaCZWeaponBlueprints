package com.gamergaming.taczweaponblueprints.command;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.mojang.brigadier.CommandDispatcher;
import org.junit.jupiter.api.Test;

import net.minecraft.commands.CommandSourceStack;

class RootCommandTest {

    @Test
    void registersAllPhaseSixLootDiagnostics() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        RootCommand.register(dispatcher);

        var root = dispatcher.getRoot().getChild("gg");
        assertNotNull(root);
        assertNotNull(root.getChild("clearRecipes"));
        assertNotNull(root.getChild("reloadRecipes"));

        var loot = root.getChild("loot");
        assertNotNull(loot);
        assertNotNull(loot.getChild("status"));
        assertNotNull(loot.getChild("inspect"));
        assertNotNull(loot.getChild("preview"));
        assertNotNull(loot.getChild("pool"));

        var progression = root.getChild("progression");
        assertNotNull(progression);
        assertNotNull(progression.getChild("inspect"));
        assertNotNull(progression.getChild("reset"));
    }
}
