package com.gamergaming.taczweaponblueprints.progression;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.ClaimKey;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDataManager;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDefinition;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardPresentation.Visibility;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardResolver;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardResolver.ResolvedAward;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTarget;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Produces bounded disclosure-filtered player presentation from award state. */
public final class ResearchPointPresentationService {
    private ResearchPointPresentationService() {
    }

    public static Feedback feedback(
            ResearchPointAwardResolver.Resolution resolution,
            ResearchPointAwardService.BatchResult result,
            Predicate<ResolvedAward> conditionalDisclosure) {
        if (resolution == null || result == null || !resolution.successful()) {
            return Feedback.EMPTY;
        }
        int count = Math.min(resolution.awards().size(), result.awards().size());
        int points = 0;
        int generic = 0;
        boolean claimedAtCap = false;
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) {
            ResearchPointAwardService.AwardResult awarded = result.awards().get(index);
            if (!awarded.committed()) {
                continue;
            }
            points = saturatedAdd(points, awarded.awardedPoints());
            claimedAtCap |= awarded.status()
                    == ResearchPointAwardService.Status.LEDGER_RECORDED_AT_CAP;
            ResolvedAward resolved = resolution.awards().get(index);
            ResearchPointAwardDefinition definition = resolved.binding().definition();
            boolean named = definition.presentation().visibility() == Visibility.PUBLIC
                    || definition.presentation().visibility() == Visibility.CONDITIONAL
                            && conditionalDisclosure != null
                            && conditionalDisclosure.test(resolved);
            Optional<String> name = named ? definition.presentation().name() : Optional.empty();
            if (name.isPresent()
                    && names.size() < PlayerProgressionLimits.MAX_RESEARCH_POINT_FEEDBACK_NAMES) {
                names.add(name.orElseThrow());
            } else {
                generic++;
            }
        }
        return new Feedback(points, generic, claimedAtCap, List.copyOf(names));
    }

    public static void sendFeedback(
            ServerPlayer player,
            ResearchPointAwardResolver.Resolution resolution,
            ResearchPointAwardService.BatchResult result,
            ResearchPointAwardContext context) {
        if (player == null) {
            return;
        }
        Feedback feedback = feedback(
                resolution,
                result,
                ignored -> conditionalFeedbackVisible(player, context));
        if (feedback.present()) {
            NetworkHandler.sendResearchPointFeedback(player, feedback);
        }
    }

    public static Feedback feedback(
            ResolvedAward resolved,
            ResearchPointAwardService.AwardResult result,
            boolean conditionalDisclosure) {
        if (resolved == null || result == null || !result.committed()) {
            return Feedback.EMPTY;
        }
        ResearchPointAwardDefinition definition = resolved.binding().definition();
        boolean named = definition.presentation().visibility() == Visibility.PUBLIC
                || definition.presentation().visibility() == Visibility.CONDITIONAL
                        && conditionalDisclosure;
        Optional<String> name = named ? definition.presentation().name() : Optional.empty();
        return new Feedback(
                result.awardedPoints(),
                name.isPresent() ? 0 : 1,
                result.status() == ResearchPointAwardService.Status.LEDGER_RECORDED_AT_CAP,
                name.map(List::of).orElseGet(List::of));
    }

    public static Feedback combine(List<Feedback> feedbacks) {
        if (feedbacks == null || feedbacks.isEmpty()) {
            return Feedback.EMPTY;
        }
        int points = 0;
        int generic = 0;
        boolean claimedAtCap = false;
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Feedback value : feedbacks) {
            if (value == null || !value.present()) {
                continue;
            }
            points = saturatedAdd(points, value.awardedPoints());
            generic = boundedAwardCount(generic, value.genericAwardCount());
            claimedAtCap |= value.claimedAtCap();
            for (String name : value.namedAwards()) {
                if (names.size() < PlayerProgressionLimits.MAX_RESEARCH_POINT_FEEDBACK_NAMES) {
                    names.add(name);
                } else {
                    generic = boundedAwardCount(generic, 1);
                }
            }
        }
        return new Feedback(points, generic, claimedAtCap, List.copyOf(names));
    }

    public static void sendFeedback(
            ServerPlayer player,
            ResolvedAward resolved,
            ResearchPointAwardService.AwardResult result,
            ResearchPointAwardContext context) {
        if (player == null) {
            return;
        }
        Feedback feedback = feedback(player, resolved, result, context);
        if (feedback.present()) {
            NetworkHandler.sendResearchPointFeedback(player, feedback);
        }
    }

    public static Feedback feedback(
            ServerPlayer player,
            ResolvedAward resolved,
            ResearchPointAwardService.AwardResult result,
            ResearchPointAwardContext context) {
        return player == null
                ? Feedback.EMPTY
                : feedback(resolved, result, conditionalFeedbackVisible(player, context));
    }

    public static HelpSnapshot help(ServerPlayer player) {
        if (player == null) {
            return HelpSnapshot.EMPTY;
        }
        IPlayerRecipeData data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        ResearchPointAwardConfigSnapshot config = ModConfigs.BLUEPRINT.awardSnapshot();
        ResearchPointAwardDataManager.Publication publication =
                ResearchPointAwardDataManager.INSTANCE.publication();
        if (data == null || config == null || !config.awardsEnabled()) {
            return new HelpSnapshot(publication.revision(), List.of());
        }
        List<HelpEntry> entries = new ArrayList<>();
        Set<HelpEntry> unique = new LinkedHashSet<>();
        for (var definitionEntry : publication.snapshot().definitions().entrySet()) {
            ResourceLocation definitionId = definitionEntry.getKey();
            ResearchPointAwardDefinition definition = definitionEntry.getValue();
            if (!definition.enabled()
                    || !definition.appliesToProfile(config.activeProfileId())
                    || definition.presentation().visibility() == Visibility.HIDDEN
                    || definition.presentation().name().isEmpty()
                    || definition.trigger().type() == ResearchPointAwardTrigger.Type.ENTITY_KILLED
                            && !config.combatAwardsEnabled()) {
                continue;
            }
            if (finiteAwardExhausted(data, definitionId, definition)) {
                continue;
            }
            if (definition.presentation().visibility() == Visibility.CONDITIONAL
                    && !conditionalHelpVisible(player, data, definition)) {
                continue;
            }
            HelpEntry entry = new HelpEntry(
                    definition.presentation().name().orElseThrow(),
                    definition.trigger().type(),
                    definition.reward().points());
            if (unique.add(entry)) {
                entries.add(entry);
                if (entries.size() >= PlayerProgressionLimits.MAX_RESEARCH_POINT_HELP_ENTRIES) {
                    break;
                }
            }
        }
        return new HelpSnapshot(publication.revision(), entries);
    }

    static boolean finiteAwardExhausted(
            IPlayerRecipeData data,
            ResourceLocation definitionId,
            ResearchPointAwardDefinition definition) {
        if (data == null || definitionId == null || definition == null
                || data.getResearchPointAwardLedger() == null) {
            return false;
        }
        ResourceLocation claimId = definition.effectiveClaimId(definitionId);
        return switch (definition.repeat().type()) {
            case ONCE -> data.getResearchPointAwardLedger().hasClaim(ClaimKey.once(claimId));
            case ONCE_PER_TARGET -> definition.trigger().target()
                    .filter(target -> !target.ids().isEmpty())
                    .map(target -> target.ids().stream().allMatch(id ->
                            data.getResearchPointAwardLedger().hasClaim(
                                    ClaimKey.targeted(claimId, id))))
                    .orElse(false);
            case COOLDOWN, WINDOWED, UNLIMITED -> false;
        };
    }

    public static void syncHelp(ServerPlayer player) {
        if (player != null) {
            NetworkHandler.sendResearchPointHelp(player, help(player));
        }
    }

    private static boolean conditionalFeedbackVisible(
            ServerPlayer player,
            ResearchPointAwardContext context) {
        if (context == null || context.targetId().isEmpty()) {
            return false;
        }
        return switch (context.triggerType()) {
            case BLUEPRINT_DISCOVERED, BLUEPRINT_LEARNED, BLUEPRINT_MILESTONE ->
                    blueprintVisible(player, context.targetId().orElseThrow());
            case INVENTORY_TURN_IN, ADVANCEMENT_COMPLETED, ENTITY_KILLED, INTEGRATION -> true;
        };
    }

    private static boolean conditionalHelpVisible(
            ServerPlayer player,
            IPlayerRecipeData data,
            ResearchPointAwardDefinition definition) {
        ResearchPointAwardTarget target = definition.trigger().target().orElse(null);
        if (target == null) {
            return false;
        }
        return switch (definition.trigger().type()) {
            case BLUEPRINT_DISCOVERED, BLUEPRINT_LEARNED, BLUEPRINT_MILESTONE ->
                    !target.ids().isEmpty()
                            && target.ids().stream().allMatch(id -> blueprintVisible(data, id));
            case INVENTORY_TURN_IN -> inventoryContains(player, target);
            case ADVANCEMENT_COMPLETED, ENTITY_KILLED, INTEGRATION -> false;
        };
    }

    private static boolean blueprintVisible(ServerPlayer player, ResourceLocation id) {
        IPlayerRecipeData data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        return blueprintVisible(data, id);
    }

    private static boolean blueprintVisible(IPlayerRecipeData data, ResourceLocation id) {
        if (data == null || id == null) {
            return false;
        }
        try {
            return BlueprintResearchDataManager.INSTANCE.policyFor(id, data)
                    .visibility().revealsIdentity();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean inventoryContains(ServerPlayer player, ResearchPointAwardTarget target) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (itemId != null && target.ids().contains(itemId)) {
                return true;
            }
            Set<ResourceLocation> tags = stack.getTags().map(TagKey<Item>::location)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (tags.stream().anyMatch(target.tags()::contains)) {
                return true;
            }
        }
        return false;
    }

    private static int saturatedAdd(int left, int right) {
        return (int) Math.min(
                PlayerProgressionLimits.MAX_RESEARCH_POINTS,
                Math.max(0L, (long) left + right));
    }

    private static int boundedAwardCount(int left, int right) {
        return Math.min(
                PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_GROUPS_PER_EVENT,
                Math.max(0, left) + Math.max(0, right));
    }

    public record Feedback(
            int awardedPoints,
            int genericAwardCount,
            boolean claimedAtCap,
            List<String> namedAwards) {
        public static final Feedback EMPTY = new Feedback(0, 0, false, List.of());

        public Feedback {
            namedAwards = namedAwards == null ? List.of() : List.copyOf(namedAwards);
            if (awardedPoints < 0
                    || awardedPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || genericAwardCount < 0
                    || genericAwardCount > PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_GROUPS_PER_EVENT
                    || namedAwards.size() > PlayerProgressionLimits.MAX_RESEARCH_POINT_FEEDBACK_NAMES
                    || namedAwards.stream().anyMatch(value -> value == null || value.isBlank()
                            || value.length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH)) {
                throw new IllegalArgumentException("invalid Research Point feedback");
            }
        }

        public boolean present() {
            return awardedPoints > 0 || claimedAtCap;
        }
    }

    public record HelpEntry(
            String nameKey,
            ResearchPointAwardTrigger.Type triggerType,
            int points) {
        public HelpEntry {
            if (nameKey == null || nameKey.isBlank()
                    || nameKey.length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH
                    || triggerType == null || points <= 0
                    || points > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
                throw new IllegalArgumentException("invalid Research Point help entry");
            }
        }
    }

    public record HelpSnapshot(long revision, List<HelpEntry> entries) {
        public static final HelpSnapshot EMPTY = new HelpSnapshot(0L, List.of());

        public HelpSnapshot {
            entries = entries == null ? List.of() : List.copyOf(entries);
            if (revision < 0L
                    || entries.size() > PlayerProgressionLimits.MAX_RESEARCH_POINT_HELP_ENTRIES) {
                throw new IllegalArgumentException("invalid Research Point help snapshot");
            }
        }
    }
}
