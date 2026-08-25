package com.gamergaming.taczweaponblueprints.command.sub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCatalogExporter;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDiagnostics;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicyResolver;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTreeGroupPlacement;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;

/** Operator diagnostics and authoring support for the effective research graph. */
public final class BlueprintResearchCommand {
    private static final String BLUEPRINT_ARGUMENT = "blueprint";
    private static final String EXPORT_FILE = "research-catalog.json";

    private BlueprintResearchCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        return Commands.literal("research")
                .then(Commands.literal("status").executes(BlueprintResearchCommand::status))
                .then(Commands.literal("inspect")
                        .then(Commands.argument(BLUEPRINT_ARGUMENT, ResourceLocationArgument.id())
                                .executes(BlueprintResearchCommand::inspect)))
                .then(Commands.literal("export").executes(BlueprintResearchCommand::exportCatalog));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        BlueprintResearchDataManager manager = BlueprintResearchDataManager.INSTANCE;
        ResourceLocation profileId = manager.progressionConfig().activeProfileId();
        BlueprintResearchDiagnostics.Summary summary = BlueprintResearchDiagnostics.summarize(manager.snapshot());
        BlueprintResearchDiagnostics.Audit audit = BlueprintResearchDiagnostics.audit(
                manager.snapshot(),
                BlueprintDataManager.SERVER.getBlueprintDataMap(),
                profileId);
        BlueprintResearchDiagnostics.GroupAudit groupAudit = BlueprintResearchDiagnostics.auditGroups(
                manager.snapshot(),
                BlueprintDataManager.SERVER.getBlueprintDataMap(),
                profileId);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.status",
                profileId,
                audit.assignedBlueprintCount(),
                audit.catalogSize(),
                audit.treeVisibleBlueprintCount(),
                audit.rootCount(),
                audit.componentCount(),
                audit.independentBlueprintIds().size(),
                manager.revision()), false);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.definitions",
                summary.profileCount(),
                summary.ruleCount(),
                summary.exactTargetCount(),
                summary.tagTargetCount(),
                summary.selectorTargetCount(),
                audit.missingPrerequisiteIds().size(),
                audit.hiddenPrerequisiteTargetIds().size(),
                audit.competitions().size()), false);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.groups",
                groupAudit.authoredGroupCount(),
                groupAudit.groupedCatalogCount(),
                groupAudit.catalogSize(),
                groupAudit.fallbackBlueprintIds().size(),
                groupAudit.missingMemberIds().size()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int inspect(CommandContext<CommandSourceStack> context) {
        ResourceLocation blueprintId = ResourceLocationArgument.getId(context, BLUEPRINT_ARGUMENT);
        BlueprintResearchDataManager manager = BlueprintResearchDataManager.INSTANCE;
        var catalog = BlueprintDataManager.SERVER.getBlueprintDataMap();
        if (!catalog.containsKey(blueprintId)) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.taczweaponblueprints.research.inspect.missing", blueprintId));
            return 0;
        }
        ResourceLocation profileId = manager.progressionConfig().activeProfileId();
        BlueprintResearchPolicy policy = BlueprintResearchDiagnostics.inspect(
                manager.snapshot(), catalog, profileId, blueprintId, null);
        BlueprintResearchPolicyResolver.RuleSelection selection = BlueprintResearchDiagnostics.inspectSelection(
                manager.snapshot(), catalog, profileId, blueprintId);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.inspect",
                blueprintId,
                policy.ruleId().map(ResourceLocation::toString).orElse("inherited profile"),
                selection.specificity().name().toLowerCase(java.util.Locale.ROOT),
                policy.visibility().serializedName(),
                policy.researchEnabled(),
                policy.researchCost().points(),
                policy.researchCost().ingredients().size(),
                policy.prerequisites().size(),
                selection.tiedRuleIds().size()), false);
        java.util.Optional<ResearchTreeGroupPlacement> placement = manager.snapshot()
                .placementFor(profileId, blueprintId);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.inspect.presentation",
                placement.map(value -> value.groupId().toString()).orElse("automatic fallback"),
                placement.map(value -> Integer.toString(value.rank())).orElse("-"),
                placement.map(value -> Integer.toString(value.orderInRank())).orElse("-")), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int exportCatalog(CommandContext<CommandSourceStack> context) {
        BlueprintResearchDataManager manager = BlueprintResearchDataManager.INSTANCE;
        Path directory = context.getSource().getServer().getWorldPath(LevelResource.ROOT)
                .resolve("taczweaponblueprints");
        Path target = directory.resolve(EXPORT_FILE);
        Path temporary = directory.resolve(EXPORT_FILE + ".tmp");
        String json = BlueprintResearchCatalogExporter.export(
                manager.snapshot(),
                BlueprintDataManager.SERVER.getBlueprintDataMap(),
                manager.progressionConfig().activeProfileId());
        try {
            Files.createDirectories(directory);
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.taczweaponblueprints.research.export.failed", exception.getMessage()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.export.success", target.toAbsolutePath()), false);
        return Command.SINGLE_SUCCESS;
    }
}
