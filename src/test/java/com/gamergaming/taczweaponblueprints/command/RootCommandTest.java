package com.gamergaming.taczweaponblueprints.command;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mojang.brigadier.CommandDispatcher;
import org.junit.jupiter.api.Test;

import net.minecraft.commands.CommandSourceStack;

class RootCommandTest {

    @Test
    void registersAllOperatorCommandFamilies() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        RootCommand.register(dispatcher);

        var root = dispatcher.getRoot().getChild("gg");
        assertNotNull(root);
        assertNull(root.getChild("clearRecipes"));
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
        var points = progression.getChild("points");
        assertNotNull(points);
        var give = points.getChild("give");
        assertNotNull(give);
        var targets = give.getChild("targets");
        assertNotNull(targets);
        assertNotNull(targets.getChild("amount"));

        var research = root.getChild("research");
        assertNotNull(research);
        assertNotNull(research.getChild("status"));
        assertNotNull(research.getChild("inspect"));
        assertNotNull(research.getChild("export"));
        var setup = research.getChild("setup");
        assertNotNull(setup);
        assertNotNull(setup.getChild("assess"));
        assertNotNull(setup.getChild("preview"));
        assertNotNull(setup.getChild("apply"));
        assertNotNull(setup.getChild("export"));
        var preset = setup.getChild("apply").getChild("preset");
        assertNotNull(preset);
        assertNotNull(preset.getChild("confirm"));
        var awards = research.getChild("awards");
        assertNotNull(awards);
        assertNotNull(awards.getChild("status"));
        assertNotNull(awards.getChild("inspect"));
        assertNotNull(awards.getChild("sources"));
        var trigger = awards.getChild("trigger");
        assertNotNull(trigger);
        var awardTargets = trigger.getChild("targets");
        assertNotNull(awardTargets);
        assertNotNull(awardTargets.getChild("source"));
    }
}
