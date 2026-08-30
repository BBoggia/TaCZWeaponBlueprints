package com.gamergaming.taczweaponblueprints.command.sub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.BlueprintBalancePreset;
import com.gamergaming.taczweaponblueprints.progression.BlueprintBalanceSettings;
import com.gamergaming.taczweaponblueprints.progression.BlueprintSetupAssistant;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootDataManager;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootDiagnostics;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDiagnostics;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;

/** Permission-gated, preview-first setup assistant for discovery pacing. */
public final class BlueprintSetupCommand {
    private static final String PRESET_ARGUMENT = "preset";
    private static final String EXPORT_FILE = "setup-assessment.json";

    private BlueprintSetupCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        return Commands.literal("setup")
                .then(Commands.literal("assess").executes(BlueprintSetupCommand::assess))
                .then(Commands.literal("preview")
                        .then(presetArgument().executes(BlueprintSetupCommand::preview)))
                .then(Commands.literal("apply")
                        .then(presetArgument()
                                .then(Commands.literal("confirm")
                                        .executes(BlueprintSetupCommand::apply))))
                .then(Commands.literal("export").executes(BlueprintSetupCommand::export));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
            presetArgument() {
        return Commands.argument(PRESET_ARGUMENT, StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        Arrays.stream(BlueprintBalancePreset.values())
                                .map(BlueprintBalancePreset::serializedName),
                        builder));
    }

    private static int assess(CommandContext<CommandSourceStack> context) {
        LiveAssessment live = liveAssessment();
        var assessment = live.assessment();
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.setup.assess.catalog",
                assessment.catalogSize(),
                assessment.kindCount(BlueprintKind.GUN),
                assessment.kindCount(BlueprintKind.ATTACHMENT),
                assessment.kindCount(BlueprintKind.AMMO),
                assessment.addOnBlueprintCount(),
                assessment.namespaces().size()), false);
        var audit = assessment.researchAudit();
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.setup.assess.research",
                audit.assignedBlueprintCount(),
                audit.treeVisibleBlueprintCount(),
                audit.rootCount(),
                audit.componentCount(),
                audit.independentBlueprintIds().size(),
                audit.missingPrerequisiteIds().size()
                        + audit.hiddenPrerequisiteTargetIds().size()
                        + audit.competitions().size()), false);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.setup.assess.access",
                assessment.effectiveExemptionCount(),
                assessment.unmatchedExemptionSelectorCount(),
                assessment.configuredStartingCount(),
                assessment.missingStartingCount()), false);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.setup.assess.readiness",
                assessment.effectiveDiscoveryCount(),
                assessment.effectiveAddOnDiscoveryCount(),
                assessment.runtimeReadiness().blueprintsEnabled(),
                assessment.runtimeReadiness().researchEnabled(),
                assessment.runtimeReadiness().lootDistributionAvailable()), false);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.setup.assess.recommendation",
                assessment.status().serializedName(),
                assessment.recommendedPreset().serializedName(),
                assessment.reasons().stream()
                        .map(reason -> reason.replace('_', ' '))
                        .collect(java.util.stream.Collectors.joining(", "))), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int preview(CommandContext<CommandSourceStack> context) {
        BlueprintBalancePreset preset = parsePreset(context);
        if (preset == null) {
            return 0;
        }
        BlueprintConfig config = ModConfigs.BLUEPRINT;
        BlueprintBalanceSettings settings = BlueprintBalanceSettings.resolve(
                preset,
                config.maximumUndiscoveredVisibility.get(),
                config.blueprintSpawnChance.get(),
                config.minBlueprints.get(),
                config.maxBlueprints.get());
        sendSettings(context, settings, false, config.balancePreset.get() != preset);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.setup.preview.preserved"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int apply(CommandContext<CommandSourceStack> context) {
        BlueprintBalancePreset preset = parsePreset(context);
        if (preset == null) {
            return 0;
        }
        BlueprintConfig config = ModConfigs.BLUEPRINT;
        BlueprintConfig.BalancePresetApplication result =
                config.applyBalancePreset(preset, context.getSource().getServer());
        sendSettings(context, config.balanceSettings(), true, result.changed());
        if (!result.persisted()) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.taczweaponblueprints.research.setup.apply.persistence_failed"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.setup.apply.synchronized",
                result.synchronizedPlayers()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int export(CommandContext<CommandSourceStack> context) {
        LiveAssessment live = liveAssessment();
        Path directory = context.getSource().getServer().getWorldPath(LevelResource.ROOT)
                .resolve("taczweaponblueprints");
        Path target = directory.resolve(EXPORT_FILE);
        Path temporary = directory.resolve(EXPORT_FILE + ".tmp");
        String json = BlueprintSetupAssistant.export(
                live.assessment(),
                live.catalogRevision(),
                live.researchRevision(),
                ModConfigs.BLUEPRINT.balanceSettings());
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
                    "commands.taczweaponblueprints.research.setup.export.failed",
                    exception.getMessage()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.research.setup.export.success",
                target.toAbsolutePath()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void sendSettings(
            CommandContext<CommandSourceStack> context,
            BlueprintBalanceSettings settings,
            boolean applied,
            boolean changed) {
        String key = applied
                ? "commands.taczweaponblueprints.research.setup.apply"
                : "commands.taczweaponblueprints.research.setup.preview";
        context.getSource().sendSuccess(() -> Component.translatable(
                key,
                settings.preset().serializedName(),
                settings.maximumUndiscoveredVisibility().serializedName(),
                Math.round(settings.lootChance() * 100.0),
                settings.minimumLootRolls(),
                settings.maximumLootRolls(),
                changed), false);
    }

    private static BlueprintBalancePreset parsePreset(CommandContext<CommandSourceStack> context) {
        String value = StringArgumentType.getString(context, PRESET_ARGUMENT);
        try {
            return BlueprintBalancePreset.parse(value);
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.taczweaponblueprints.research.setup.unknown_preset", value));
            return null;
        }
    }

    private static LiveAssessment liveAssessment() {
        BlueprintDataManager.CatalogPublication catalog =
                BlueprintDataManager.SERVER.catalogPublication();
        BlueprintResearchDataManager.Publication research =
                BlueprintResearchDataManager.INSTANCE.publication();
        ResourceLocation profileId = BlueprintResearchDataManager.INSTANCE
                .progressionConfig().activeProfileId();
        BlueprintResearchDiagnostics.Audit audit = BlueprintResearchDiagnostics.audit(
                research.snapshot(), catalog.blueprints(), profileId);
        var lootSnapshot = BlueprintLootDataManager.INSTANCE.snapshot();
        var lootSummary = BlueprintLootDiagnostics.summarize(
                lootSnapshot, catalog.blueprints().size());
        boolean enabledDynamicTarget = lootSnapshot.rules().values().stream().anyMatch(rule ->
                rule.enabled()
                        && (!rule.lootTables().isEmpty()
                                || rule.lootTableSelector().isPresent()));
        boolean lootDistributionAvailable = !lootSummary.globallyDisabled()
                && (enabledDynamicTarget || !lootSummary.ownsDistribution());
        BlueprintSetupAssistant.RuntimeReadiness readiness =
                new BlueprintSetupAssistant.RuntimeReadiness(
                        ModConfigs.BLUEPRINT.enableBlueprints.get(),
                        ModConfigs.BLUEPRINT.enableResearch.get(),
                        lootDistributionAvailable);
        return new LiveAssessment(
                BlueprintSetupAssistant.assess(
                        catalog.blueprints(),
                        audit,
                        ModConfigs.BLUEPRINT.accessSnapshot(),
                        readiness),
                catalog.revision(),
                research.revision());
    }

    private record LiveAssessment(
            BlueprintSetupAssistant.Assessment assessment,
            long catalogRevision,
            long researchRevision) {
    }
}
