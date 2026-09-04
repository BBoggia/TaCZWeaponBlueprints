package com.gamergaming.taczweaponblueprints.progression.eligibility;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.progression.ResearchFeatureConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchPathUnlockPlanner;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchEligibilityBlocker.Gate;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchEligibilityBlocker.Policy;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchEligibilityBlocker.PolicyReason;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchEligibilityBlocker.WorkbenchTier;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateEvaluation;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateEvaluator;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchInteractionMode;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchAuthority;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchContext;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.ProgressionPolicyAccessService;
import com.gamergaming.taczweaponblueprints.resource.research.ResolvedBlueprintProgressionPolicy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** One server-authoritative tier and Progression Gate evaluation for a complete route. */
public final class ResearchRouteEligibilityService {
    public static final String ANY_GATE_ALTERNATIVE_MESSAGE_KEY =
            "gui.taczweaponblueprints.research_bench.tree.selection.progression_gate_any_of";

    private ResearchRouteEligibilityService() {
    }

    public static Evaluation evaluate(
            ServerPlayer player,
            ResearchPathUnlockPlanner.Plan path,
            ResearchWorkbenchContext workbench) {
        if (path == null) {
            return Evaluation.unavailable();
        }
        return evaluate(
                player,
                path.nodes().stream()
                        .map(ResearchPathUnlockPlanner.PlannedNode::blueprintId)
                        .toList(),
                workbench);
    }

    public static Evaluation evaluate(
            ServerPlayer player,
            List<ResourceLocation> pendingBlueprintIds,
            ResearchWorkbenchContext workbench) {
        if (player == null || pendingBlueprintIds == null || workbench == null
                || workbench.interactionMode() != ResearchInteractionMode.RESEARCH
                || pendingBlueprintIds.isEmpty()
                || pendingBlueprintIds.size()
                        > ResearchPathUnlockPlanner.MAX_UNLOCKS_PER_PURCHASE
                || pendingBlueprintIds.stream().anyMatch(java.util.Objects::isNull)
                || pendingBlueprintIds.stream().anyMatch(id -> id.toString().length()
                        > com.gamergaming.taczweaponblueprints.capabilities
                                .PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH)
                || pendingBlueprintIds.stream().distinct().count() != pendingBlueprintIds.size()
                || !ResearchWorkbenchAuthority.validForResearch(player, workbench)) {
            return Evaluation.unavailable();
        }
        IPlayerRecipeData playerData = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        if (playerData == null) {
            return Evaluation.unavailable();
        }
        var policyAccess = ProgressionPolicyAccessService.acquire(
                ProgressionPolicyAccessService.Mode.ENSURE_CURRENT).orElse(null);
        if (policyAccess == null) {
            return Evaluation.unavailable();
        }
        ResearchFeatureConfigSnapshot config = policyAccess.config();

        boolean bypassTier = player.isCreative() && config.creativeBypassesWorkbenchTiers();
        boolean bypassGates = player.isCreative() && config.creativeBypassesProgressionGates();
        List<ResearchEligibilityBlocker> blockers = new ArrayList<>();
        List<NodeEvidence> evidence = new ArrayList<>(pendingBlueprintIds.size());
        for (ResourceLocation nodeId : pendingBlueprintIds) {
            ResolvedBlueprintProgressionPolicy policy = policyAccess
                    .policyFor(nodeId).orElse(null);
            if (policy == null) {
                return Evaluation.unavailable();
            }

            ResearchWorkbenchTier requiredTier = policy.researchWorkbenchTier();
            boolean tierSatisfied = !config.enforceResearchTiers()
                    || bypassTier || workbench.tier().satisfies(requiredTier);
            if (!tierSatisfied && blockers.size() < ResearchEligibilityBlockers.MAX_BLOCKERS) {
                blockers.add(new WorkbenchTier(
                        nodeId,
                        ResearchInteractionMode.RESEARCH,
                        Optional.of(workbench.tier()),
                        requiredTier));
            }

            ProgressionGateEvaluation gate = null;
            String primaryGateMessageKey = null;
            if (!bypassGates) {
                gate = ProgressionGateEvaluator.evaluateRequirements(
                        player,
                        nodeId,
                        policy.gates(),
                        ResearchInteractionMode.RESEARCH,
                        Optional.of(workbench));
                if (gate.status() != ProgressionGateEvaluation.Status.EVALUATED) {
                    return Evaluation.unavailable();
                }
                for (ProgressionGateEvaluation.UnmetGroup unmet : gate.unmetGroups()) {
                    List<ProgressionGateCondition> applicable = policy.gates().allOf()
                            .get(unmet.groupOrdinal()).anyOf().stream()
                            .filter(candidate -> candidate.appliesTo(
                                    ResearchInteractionMode.RESEARCH))
                            .toList();
                    ProgressionGateCondition condition = applicable.stream()
                            .findFirst().orElseThrow();
                    if (primaryGateMessageKey == null) {
                        primaryGateMessageKey = applicable.size() > 1
                                ? ANY_GATE_ALTERNATIVE_MESSAGE_KEY
                                : condition.messageKey();
                    }
                    if (blockers.size() < ResearchEligibilityBlockers.MAX_BLOCKERS) {
                        blockers.add(new Gate(
                                nodeId,
                                ResearchInteractionMode.RESEARCH,
                                unmet.groupOrdinal(),
                                condition));
                    }
                }
            }
            List<Integer> unmetGateGroups = gate == null
                    ? List.of()
                    : gate.unmetGroups().stream()
                            .map(ProgressionGateEvaluation.UnmetGroup::groupOrdinal)
                            .toList();
            evidence.add(new NodeEvidence(
                    nodeId,
                    requiredTier,
                    tierSatisfied,
                    bypassGates || gate.satisfied(),
                    unmetGateGroups,
                    Optional.ofNullable(primaryGateMessageKey)));
        }
        return finish(
                workbench, policyAccess.policy().revision(), config,
                player.isCreative(), blockers, evidence);
    }

    private static Evaluation finish(
            ResearchWorkbenchContext workbench,
            long publicationRevision,
            ResearchFeatureConfigSnapshot config,
            boolean creativePlayer,
            List<ResearchEligibilityBlocker> blockers,
            List<NodeEvidence> evidence) {
        ResearchEligibilityBlockers result = new ResearchEligibilityBlockers(blockers);
        ResearchAccessSummary summary = primarySummary(
                result, evidence, workbench.tier());
        return new Evaluation(
                result,
                summary,
                fingerprint(
                        workbench,
                        publicationRevision,
                        config,
                        creativePlayer,
                        evidence));
    }

    private static ResearchAccessSummary primarySummary(
            ResearchEligibilityBlockers blockers,
            List<NodeEvidence> evidence,
            ResearchWorkbenchTier currentTier) {
        NodeEvidence highestTier = evidence.stream()
                .filter(node -> !node.tierSatisfied())
                .max(java.util.Comparator
                        .comparingInt((NodeEvidence node) -> node.requiredTier().level())
                        .thenComparing(node -> node.blueprintId().toString()))
                .orElse(null);
        if (highestTier != null) {
            return ResearchAccessSummary.workbench(
                    currentTier, highestTier.requiredTier());
        }
        ResearchAccessSummary gate = evidence.stream()
                .filter(node -> !node.gatesSatisfied())
                .filter(node -> node.primaryGateMessageKey().isPresent())
                .sorted(java.util.Comparator.comparing(
                        node -> node.blueprintId().toString()))
                .map(NodeEvidence::primaryGateMessageKey)
                .map(Optional::orElseThrow)
                .map(ResearchAccessSummary::gate)
                .findFirst()
                .orElse(null);
        if (gate != null) {
            return gate;
        }
        return blockers.primary()
                .map(ResearchRouteEligibilityService::summary)
                .orElse(ResearchAccessSummary.NONE);
    }

    private static ResearchAccessSummary summary(ResearchEligibilityBlocker blocker) {
        if (blocker instanceof WorkbenchTier tier) {
            return ResearchAccessSummary.workbench(
                    tier.currentTier().orElse(null), tier.requiredTier());
        }
        if (blocker instanceof Gate gate) {
            return ResearchAccessSummary.gate(gate.condition().messageKey());
        }
        return ResearchAccessSummary.POLICY_UNAVAILABLE;
    }

    private static ResearchAccessFingerprint fingerprint(
            ResearchWorkbenchContext workbench,
            long publicationRevision,
            ResearchFeatureConfigSnapshot config,
            boolean creativePlayer,
            List<NodeEvidence> evidence) {
        MessageDigest digest = sha256();
        update(digest, "taczweaponblueprints:research_access:v1");
        update(digest, publicationRevision);
        update(digest, workbench.dimensionId().toString());
        update(digest, workbench.workstationId().toString());
        update(digest, workbench.rootPosition().asLong());
        update(digest, workbench.tier().level());
        update(digest, workbench.interactionMode().name());
        update(digest, workbench.sessionId());
        update(digest, creativePlayer ? 1L : 0L);
        update(digest, config.enforceResearchTiers() ? 1L : 0L);
        update(digest, config.creativeBypassesWorkbenchTiers() ? 1L : 0L);
        update(digest, config.creativeBypassesProgressionGates() ? 1L : 0L);
        update(digest, evidence.size());
        for (NodeEvidence node : evidence) {
            update(digest, node.blueprintId().toString());
            update(digest, node.requiredTier() == null ? 0L : node.requiredTier().level());
            update(digest, node.tierSatisfied() ? 1L : 0L);
            update(digest, node.gatesSatisfied() ? 1L : 0L);
            update(digest, node.unmetGateGroups().size());
            node.unmetGateGroups().forEach(group -> update(digest, group.longValue()));
        }
        byte[] hash = digest.digest();
        long high = readLong(hash, 0);
        long low = readLong(hash, Long.BYTES);
        return high == 0L && low == 0L
                ? new ResearchAccessFingerprint(0L, 1L)
                : new ResearchAccessFingerprint(high, low);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        update(digest, bytes.length);
        digest.update(bytes);
    }

    private static void update(MessageDigest digest, long value) {
        for (int shift = Long.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
            digest.update((byte) (value >>> shift));
        }
    }

    private static long readLong(byte[] bytes, int offset) {
        long value = 0L;
        for (int index = 0; index < Long.BYTES; index++) {
            value = value << Byte.SIZE | bytes[offset + index] & 0xffL;
        }
        return value;
    }

    public record Evaluation(
            ResearchEligibilityBlockers blockers,
            ResearchAccessSummary summary,
            ResearchAccessFingerprint fingerprint) {
        public Evaluation {
            if (blockers == null || summary == null || fingerprint == null
                    || !fingerprint.present()
                    || blockers.eligible() != !summary.blocked()) {
                throw new IllegalArgumentException("research route eligibility is invalid");
            }
        }

        public static Evaluation unavailable() {
            ResourceLocation placeholder = new ResourceLocation(
                    "taczweaponblueprints:unavailable");
            return new Evaluation(
                    new ResearchEligibilityBlockers(List.of(
                            new Policy(placeholder, PolicyReason.STALE_POLICY))),
                    ResearchAccessSummary.POLICY_UNAVAILABLE,
                    new ResearchAccessFingerprint(0L, 1L));
        }

        public boolean eligible() {
            return blockers.eligible();
        }
    }

    private record NodeEvidence(
            ResourceLocation blueprintId,
            ResearchWorkbenchTier requiredTier,
            boolean tierSatisfied,
            boolean gatesSatisfied,
            List<Integer> unmetGateGroups,
            Optional<String> primaryGateMessageKey) {
        private NodeEvidence {
            unmetGateGroups = unmetGateGroups == null ? List.of() : List.copyOf(unmetGateGroups);
            primaryGateMessageKey = primaryGateMessageKey == null
                    ? Optional.empty()
                    : primaryGateMessageKey;
            if (gatesSatisfied == primaryGateMessageKey.isPresent()) {
                throw new IllegalArgumentException("Progression Gate evidence is inconsistent");
            }
        }

    }
}
