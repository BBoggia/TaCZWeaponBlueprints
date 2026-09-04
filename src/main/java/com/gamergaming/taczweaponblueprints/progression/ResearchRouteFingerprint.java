package com.gamergaming.taczweaponblueprints.progression;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchAccessFingerprint;

import net.minecraft.resources.ResourceLocation;

/** Opaque server-authoritative identity for one selected route and its quote. */
public record ResearchRouteFingerprint(long high, long low) {
    public static final ResearchRouteFingerprint NONE = new ResearchRouteFingerprint(0L, 0L);

    public static ResearchRouteFingerprint create(
            ResourceLocation target,
            ResearchPathUnlockPlanner.Plan plan,
            IPlayerRecipeData playerData,
            boolean creativePlayer,
            Context context) {
        return create(
                target,
                plan,
                playerData,
                creativePlayer,
                context,
                ResearchAccessFingerprint.EMPTY);
    }

    public static ResearchRouteFingerprint create(
            ResourceLocation target,
            ResearchPathUnlockPlanner.Plan plan,
            IPlayerRecipeData playerData,
            boolean creativePlayer,
            Context context,
            ResearchAccessFingerprint accessFingerprint) {
        if (target == null || plan == null || playerData == null || context == null) {
            throw new IllegalArgumentException("research route fingerprint input is invalid");
        }
        if (accessFingerprint == null) {
            throw new IllegalArgumentException("research access fingerprint cannot be null");
        }
        MessageDigest digest = sha256();
        update(digest, "taczweaponblueprints:research_route_fingerprint:v1");
        update(digest, "revisions");
        update(digest, context.catalogRevision());
        update(digest, context.researchRevision());
        update(digest, context.automaticPublicationRevision());
        update(digest, "research_access");
        update(digest, accessFingerprint.high());
        update(digest, accessFingerprint.low());
        updateProgressionConfig(digest, context.progressionConfig());
        updateCanonical(
                digest,
                "progression_exempt_blueprints",
                context.accessConfig().progressionExemptBlueprints());
        updateCanonicalStrings(
                digest,
                "progression_exempt_kinds",
                context.accessConfig().progressionExemptKinds().stream()
                        .map(Enum::name)
                        .collect(java.util.stream.Collectors.toSet()));
        updateCanonicalStrings(
                digest,
                "progression_exempt_item_types",
                context.accessConfig().progressionExemptItemTypes());
        updateCanonical(
                digest,
                "starting_blueprints",
                context.accessConfig().startingBlueprints());
        updateCanonicalStrings(
                digest, "learned_blueprints", playerData.getLearnedBlueprints());
        updateCanonicalStrings(
                digest, "discovered_blueprints", playerData.getDiscoveredBlueprints());
        update(digest, "request");
        update(digest, creativePlayer ? 1L : 0L);
        update(digest, target.toString());
        update(digest, ResearchPathUnlockPlanner.routeSelectionPolicy().name());
        update(digest, "support_ids");
        update(digest, plan.solution().supportIds().size());
        plan.solution().supportIds().forEach(id -> update(digest, id.toString()));
        update(digest, "purchased_nodes");
        update(digest, plan.solution().nodes().size());
        for (ResearchPathUnlockPlanner.PlannedNode node : plan.solution().nodes()) {
            update(digest, node.blueprintId().toString());
            update(digest, node.costBypassed() ? 1L : 0L);
            update(digest, node.policy().researchCost().points());
        }
        update(digest, "fragment_set_uses");
        update(digest, plan.fragmentSetUses().size());
        for (ResearchPathUnlockPlanner.FragmentSetUse setUse : plan.fragmentSetUses()) {
            update(digest, setUse.blueprintId().toString());
            update(digest, setUse.archivedBefore());
            update(digest, setUse.threshold());
            update(digest, setUse.pointDiscount());
        }
        update(digest, "route_quote");
        update(digest, plan.quote().pointCost());
        update(digest, plan.quote().costBypassed() ? 1L : 0L);
        update(digest, plan.quote().ingredients().size());
        for (ResearchIngredientPlanner.Requirement requirement : plan.quote().ingredients()) {
            update(digest, "requirement");
            update(digest, requirement.items().size());
            requirement.items().stream()
                    .sorted(Comparator.comparing(ResourceLocation::toString))
                    .forEach(id -> update(digest, id.toString()));
            update(digest, requirement.tag().map(ResourceLocation::toString).orElse(""));
            update(digest, requirement.count());
        }
        byte[] hash = digest.digest();
        long high = readLong(hash, 0);
        long low = readLong(hash, Long.BYTES);
        return high == 0L && low == 0L
                ? new ResearchRouteFingerprint(0L, 1L)
                : new ResearchRouteFingerprint(high, low);
    }

    public boolean present() {
        return high != 0L || low != 0L;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void updateProgressionConfig(
            MessageDigest digest,
            BlueprintProgressionConfigSnapshot config) {
        update(digest, "progression_config");
        update(digest, config.blueprintsEnabled() ? 1L : 0L);
        update(digest, config.discoveryTrackingEnabled() ? 1L : 0L);
        update(digest, config.journalEnabled() ? 1L : 0L);
        update(digest, config.maximumUndiscoveredVisibility().name());
        update(digest, config.researchEnabled() ? 1L : 0L);
        update(digest, config.duplicatePolicy().name());
        update(digest, config.allowUnlearnedRecycling() ? 1L : 0L);
        update(digest, config.pointCap());
        update(digest, config.creativeBypassesResearchCost() ? 1L : 0L);
        update(digest, config.activeProfileId().toString());
        update(digest, config.treeResearchResultMode().name());
        update(digest, config.researchCostMode().name());
        update(digest, config.foundWeaponRecoveryMode().name());
    }

    private static void updateCanonical(
            MessageDigest digest,
            String section,
            Set<ResourceLocation> ids) {
        update(digest, section);
        update(digest, ids.size());
        ids.stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .forEach(id -> update(digest, id.toString()));
    }

    private static void updateCanonicalStrings(
            MessageDigest digest,
            String section,
            Set<String> values) {
        update(digest, section);
        update(digest, values.size());
        values.stream().sorted().forEach(value -> update(digest, value));
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

    public record Context(
            long catalogRevision,
            long researchRevision,
            long automaticPublicationRevision,
            BlueprintProgressionConfigSnapshot progressionConfig,
            BlueprintAccessConfigSnapshot accessConfig) {
        public static final Context EMPTY = new Context(
                0L,
                0L,
                0L,
                new BlueprintProgressionConfigSnapshot(
                        false,
                        false,
                        false,
                        com.gamergaming.taczweaponblueprints.resource.research
                                .JournalVisibility.HIDDEN,
                        false,
                        DuplicateBlueprintPolicy.KEEP,
                        false,
                        0,
                        false,
                        new ResourceLocation("taczweaponblueprints:empty")),
                BlueprintAccessConfigSnapshot.EMPTY);

        public Context {
            if (catalogRevision < 0L
                    || researchRevision < 0L
                    || automaticPublicationRevision < 0L
                    || progressionConfig == null
                    || accessConfig == null) {
                throw new IllegalArgumentException("research fingerprint context is invalid");
            }
        }
    }
}
