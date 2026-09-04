package com.gamergaming.taczweaponblueprints.command.sub;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.loot.BlueprintFragmentLootResolver;
import com.gamergaming.taczweaponblueprints.loot.BlueprintLootPolicyResolver;
import com.gamergaming.taczweaponblueprints.loot.BlueprintLootRuntimeConfig;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootDataManager;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootDiagnostics;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootSnapshot;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class BlueprintLootCommand {
    private static final int MAX_RULE_DETAILS = 20;
    private static final int MAX_PREVIEW_RULES = 5;
    private static final int MAX_PREVIEW_CANDIDATES = 3;
    private static final String COMMAND_NAME = "loot";
    private static final String LOOT_TABLE_ARGUMENT = "loot_table";
    private static final String POOL_ARGUMENT = "pool";

    private BlueprintLootCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        return Commands.literal(COMMAND_NAME)
                .then(Commands.literal("status").executes(BlueprintLootCommand::status))
                .then(Commands.literal("inspect")
                        .then(Commands.argument(LOOT_TABLE_ARGUMENT, ResourceLocationArgument.id())
                                .executes(BlueprintLootCommand::inspect)))
                .then(Commands.literal("preview")
                        .then(Commands.argument(LOOT_TABLE_ARGUMENT, ResourceLocationArgument.id())
                                .executes(BlueprintLootCommand::preview)))
                .then(Commands.literal("pool")
                        .then(Commands.argument(POOL_ARGUMENT, ResourceLocationArgument.id())
                                .executes(BlueprintLootCommand::inspectPool)));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        BlueprintLootDataManager.Publication publication = BlueprintLootDataManager.INSTANCE.publication();
        BlueprintLootSnapshot snapshot = publication.snapshot();
        BlueprintLootDiagnostics.Summary summary = BlueprintLootDiagnostics.summarize(
                snapshot,
                BlueprintDataManager.SERVER.getBlueprintDataMap().size());
        var balance = ModConfigs.BLUEPRINT.balanceSettings();

        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.loot.status",
                summary.tagCount(),
                summary.poolCount(),
                summary.ruleCount(),
                summary.enabledRuleCount(),
                summary.exactBindingCount(),
                summary.selectorRuleCount(),
                summary.catalogSize(),
                publication.revision()), false);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.loot.config",
                ModConfigs.BLUEPRINT.enableBlueprints.get(),
                balance.preset().serializedName(),
                balance.lootChance(),
                balance.minimumLootRolls(),
                balance.maximumLootRolls(),
                blacklistSize(),
                ModConfigs.BLUEPRINT.researchFeatureSnapshot()
                        .fragmentLootReplacementBasisPoints() / 100.0), false);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.loot.mode",
                mode(summary)), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int inspect(CommandContext<CommandSourceStack> context) {
        ResourceLocation lootTableId = ResourceLocationArgument.getId(context, LOOT_TABLE_ARGUMENT);
        ResourceLocation dimension = context.getSource().getLevel().dimension().location();
        float luck = context.getSource().getEntity() instanceof ServerPlayer player ? player.getLuck() : 0.0f;
        BlueprintLootDiagnostics.TableReport report = BlueprintLootDiagnostics.inspect(
                BlueprintLootDataManager.INSTANCE.snapshot(),
                lootTableId,
                dimension,
                luck,
                BlueprintDataManager.SERVER.getBlueprintDataMap());

        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.loot.inspect.header",
                lootTableId,
                report.dynamicallyOwned(),
                report.rules().size(),
                report.contextEligibleRuleCount(),
                dimension,
                luck), false);
        if (report.rules().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.taczweaponblueprints.loot.inspect.none"), false);
        } else {
            report.rules().stream().limit(MAX_RULE_DETAILS).forEach(rule ->
                    context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.taczweaponblueprints.loot.inspect.rule",
                    rule.ruleId(),
                    rule.enabled(),
                    rule.poolId(),
                    rule.targetMatch().description(),
                    rule.predicateMatches(),
                    rule.catalogCandidates(),
                    rule.contextEligible()), false));
            if (report.rules().size() > MAX_RULE_DETAILS) {
                context.getSource().sendSuccess(() -> Component.translatable(
                        "commands.taczweaponblueprints.loot.inspect.truncated",
                        report.rules().size() - MAX_RULE_DETAILS), false);
            }
        }
        return Math.max(Command.SINGLE_SUCCESS, report.rules().size());
    }

    private static int preview(CommandContext<CommandSourceStack> context) {
        ResourceLocation lootTableId = ResourceLocationArgument.getId(context, LOOT_TABLE_ARGUMENT);
        ResourceLocation dimension = context.getSource().getLevel().dimension().location();
        float luck = context.getSource().getEntity() instanceof ServerPlayer player ? player.getLuck() : 0.0f;
        BlueprintLootDataManager.Publication publication = BlueprintLootDataManager.INSTANCE.publication();
        BlueprintLootSnapshot snapshot = publication.snapshot();
        var catalog = BlueprintDataManager.SERVER.getBlueprintDataMap();
        BlueprintLootPolicyResolver.RuntimeDefaults defaults = BlueprintLootRuntimeConfig.capture();
        List<BlueprintLootPolicyResolver.EffectiveRule> policies = snapshot.rulesFor(lootTableId).stream()
                .map(binding -> BlueprintLootPolicyResolver.resolve(
                        snapshot, binding, catalog, dimension, luck, defaults))
                .toList();
        long activeRules = policies.stream().filter(BlueprintLootPolicyResolver.EffectiveRule::active).count();
        double expectedAdditions = policies.stream()
                .mapToDouble(BlueprintLootPolicyResolver.EffectiveRule::expectedAdditions)
                .sum();

        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.loot.preview.header",
                lootTableId,
                policies.size(),
                activeRules,
                decimal(expectedAdditions),
                defaults.blueprintsEnabled(),
                dimension,
                decimal(luck)), false);
        if (policies.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.taczweaponblueprints.loot.preview.none"), false);
            return Command.SINGLE_SUCCESS;
        }

        policies.stream().limit(MAX_PREVIEW_RULES).forEach(policy -> {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.taczweaponblueprints.loot.preview.rule",
                    policy.ruleId(),
                    policy.active(),
                    percent(policy.chance()),
                    policy.rolls().min(),
                    policy.rolls().max(),
                    policy.candidates().size(),
                    policy.catalogCandidateCount(),
                    decimal(policy.totalWeight()),
                    decimal(policy.expectedAdditions())), false);
            policy.candidates().stream()
                    .sorted(Comparator
                            .comparingDouble(BlueprintLootPolicyResolver.Candidate::probability)
                            .reversed()
                            .thenComparing(candidate -> candidate.blueprintId().toString()))
                    .limit(MAX_PREVIEW_CANDIDATES)
                    .forEach(candidate -> context.getSource().sendSuccess(() -> Component.translatable(
                            "commands.taczweaponblueprints.loot.preview.candidate",
                            candidate.blueprintId(),
                            decimal(candidate.weight()),
                            percent(candidate.probability())), false));
            BlueprintFragmentLootResolver.Plan fragments = BlueprintFragmentLootResolver.resolveRuntime(
                    policy.candidates().stream()
                            .map(candidate -> new BlueprintFragmentLootResolver.WeightedTarget(
                                    candidate.blueprintId(), candidate.weight()))
                            .toList(),
                    context.getSource().getEntity() instanceof ServerPlayer player
                            ? player
                            : null);
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.taczweaponblueprints.loot.preview.fragments",
                    fragments.policyAvailable(),
                    percent(fragments.replacementBasisPoints()
                            / (double) BlueprintFragmentLootResolver.BASIS_POINTS),
                    fragments.candidates().size(),
                    fragments.playerAware(),
                    decimal(fragments.expectedFragments(policy.expectedAdditions())),
                    fragments.thresholdCounts(),
                    fragments.candidates().stream()
                            .filter(BlueprintFragmentLootResolver.Candidate::exactThreshold)
                            .count()), false);
        });
        if (policies.size() > MAX_PREVIEW_RULES) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.taczweaponblueprints.loot.preview.truncated",
                    policies.size() - MAX_PREVIEW_RULES), false);
        }
        return Math.max(Command.SINGLE_SUCCESS, policies.size());
    }

    private static int inspectPool(CommandContext<CommandSourceStack> context) {
        ResourceLocation poolId = ResourceLocationArgument.getId(context, POOL_ARGUMENT);
        BlueprintLootDiagnostics.PoolReport report = BlueprintLootDiagnostics.inspectPool(
                BlueprintLootDataManager.INSTANCE.snapshot(),
                poolId,
                BlueprintDataManager.SERVER.getBlueprintDataMap());
        if (!report.exists()) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.taczweaponblueprints.loot.pool.missing", poolId));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.taczweaponblueprints.loot.pool",
                poolId,
                report.composedEntryCount(),
                report.selectorCount(),
                report.catalogCandidateCount()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int blacklistSize() {
        BlueprintConfig config = ModConfigs.BLUEPRINT;
        return config.gunBlacklist.size() + config.ammoBlacklist.size() + config.attachmentBlacklist.size();
    }

    private static String mode(BlueprintLootDiagnostics.Summary summary) {
        if (summary.active()) {
            return "dynamic";
        }
        if (summary.globallyDisabled()) {
            return "datapack-disabled";
        }
        if (summary.ownsDistribution()) {
            return "targeted-disabled";
        }
        return "legacy-fallback";
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100.0);
    }
}
