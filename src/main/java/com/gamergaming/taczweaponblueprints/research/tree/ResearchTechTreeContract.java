package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.item.BlueprintKind;

import net.minecraft.resources.ResourceLocation;

/**
 * Pure semantic contract for the authored Tech Tree browse view.
 *
 * <p>The contract is shared by the live browse view and the staged unified-tree
 * work. It keeps product intent executable without allowing presentation
 * metadata to become research authority.
 */
public final class ResearchTechTreeContract {
    /** Legacy reviewed ratings remain readable, but automatic placement never uses appeal. */
    public static final int COMBAT_WEIGHT = 55;
    public static final int UTILITY_WEIGHT = 20;
    public static final int APPEAL_WEIGHT = 25;
    public static final int AUTOMATIC_COMBAT_WEIGHT = 75;
    public static final int AUTOMATIC_UTILITY_WEIGHT = 25;
    public static final int SCORE_MAX = 100;
    public static final int MAX_APPEAL_TIER_SHIFT = 1;
    public static final int MIN_LEVELS_PER_TIER = 1;
    public static final int DEFAULT_LEVELS_PER_TIER = 3;
    public static final int MAX_LEVELS_PER_TIER = 5;
    /**
     * Leaves enough room to lift every supported legacy prerequisite chain without
     * allowing one legacy tier to collide with the next tier's initial ranks.
     */
    public static final int LEGACY_RANK_STRIDE = 4097;
    public static final int MAX_PROGRESSION_RANK = 1_000_000;
    public static final String AUTOMATIC_FORMULA_VERSION = "tacz-gun-mechanical-v2";
    public static final String AUTOMATIC_REFERENCE_VERSION = "tacz-1.1.8-mechanical-v2";
    /**
     * Bump this whenever automatic foundation, parent selection, merge, layering,
     * or rank-finalization semantics deliberately change.
     */
    public static final String AUTOMATIC_PLACEMENT_VERSION = "tacz-gun-placement-v12";
    /**
     * The lower two fifths of a sufficiently deep generated tree remain a shared
     * mesh. Shallow trees retain at least two shared transitions so small catalogs
     * do not turn into isolated columns immediately after their foundation.
     */
    public static final int SHARED_MESH_TRANSITION_NUMERATOR = 2;
    public static final int SHARED_MESH_TRANSITION_DENOMINATOR = 5;
    private static final int TAPERED_BRANCH_SHARED_RANK_NUMERATOR = 1;
    private static final int TAPERED_BRANCH_SHARED_RANK_DENOMINATOR = 3;
    /**
     * SHA-256 of the sorted built-in weapon id/rank/prerequisite manifest. Update
     * this only alongside an intentional built-in topology migration.
     */
    public static final String DEFAULT_WEAPON_TOPOLOGY_FINGERPRINT =
            "d6e3e62e77370a2a76cb486489f1cdb25b0d7f7767d9523874fc476485b85e08";

    public static final List<BrowseIntent> BROWSE_ORDER = List.of(
            BrowseIntent.BRANCHES,
            BrowseIntent.ALL_WEAPONS,
            BrowseIntent.TECH_TREE);
    public static final List<Domain> DOMAIN_ORDER = List.of(
            Domain.WEAPONS,
            Domain.ATTACHMENTS,
            Domain.AMMO);
    public static final List<Tier> TIER_ORDER = List.of(Tier.values());
    public static final Domain DEFAULT_DOMAIN = Domain.WEAPONS;
    public static final int DEFAULT_WEAPON_COUNT = 53;
    public static final int DEFAULT_ATTACHMENT_COUNT = 95;
    public static final int DEFAULT_AMMO_COUNT = 24;
    public static final int DEFAULT_CONTENT_TOTAL = DEFAULT_WEAPON_COUNT
            + DEFAULT_ATTACHMENT_COUNT
            + DEFAULT_AMMO_COUNT;
    public static final Map<Domain, Integer> DEFAULT_CONTENT_TARGETS = defaultContentTargets();
    public static final UnifiedDomainPolicy UNIFIED_DOMAIN_POLICY = new UnifiedDomainPolicy(
            DomainCanvasStructure.SINGLE_UNIFIED_GRAPH,
            ClassificationRole.AUTHORING_HINT_ONLY,
            true,
            true,
            true,
            true);
    public static final FallbackPolicy UNRATED_FALLBACK = new FallbackPolicy(
            false,
            false,
            true);

    static {
        if (COMBAT_WEIGHT + UTILITY_WEIGHT + APPEAL_WEIGHT != SCORE_MAX) {
            throw new IllegalStateException("Research Tech Tree score weights must total 100");
        }
        if (AUTOMATIC_COMBAT_WEIGHT + AUTOMATIC_UTILITY_WEIGHT != SCORE_MAX) {
            throw new IllegalStateException(
                    "Research Tech Tree automatic score weights must total 100");
        }
    }

    private ResearchTechTreeContract() {
    }

    /** Number of lower rank transitions that should retain cross-path interconnection. */
    public static int sharedMeshTransitionCount(int occupiedRankCount) {
        if (occupiedRankCount < 1 || occupiedRankCount > MAX_PROGRESSION_RANK) {
            throw new IllegalArgumentException(
                    "Research Tech Tree occupied rank count is out of bounds");
        }
        int transitions = occupiedRankCount - 1;
        if (transitions <= 2) {
            return transitions;
        }
        return Math.max(2, Math.floorDiv(
                Math.multiplyExact(transitions, SHARED_MESH_TRANSITION_NUMERATOR),
                SHARED_MESH_TRANSITION_DENOMINATOR));
    }

    /** First rank index assigned to tapered families after the dense shared trunk. */
    public static int taperedBranchFamilyStartIndex(int plannedRankCount) {
        if (plannedRankCount < 1 || plannedRankCount > MAX_PROGRESSION_RANK + 1) {
            throw new IllegalArgumentException(
                    "Research Tech Tree tapered rank count is out of bounds");
        }
        int transitions = plannedRankCount - 1;
        if (transitions < 2) {
            return 1;
        }
        return 1 + Math.max(1, Math.floorDiv(
                Math.multiplyExact(transitions, TAPERED_BRANCH_SHARED_RANK_NUMERATOR),
                TAPERED_BRANCH_SHARED_RANK_DENOMINATOR));
    }

    /** Legacy browse intents remain weapon-only while Tech Tree accepts every blueprint kind. */
    public static boolean includesKind(BrowseIntent intent, BlueprintKind kind) {
        if (intent == null || kind == null) {
            throw new IllegalArgumentException(
                    "Research Tech Tree browse scope fields cannot be null");
        }
        return switch (intent) {
            case BRANCHES, ALL_WEAPONS -> kind == BlueprintKind.GUN;
            case TECH_TREE -> true;
        };
    }

    /** Prerequisites may remain in one tier or lead upward, but never back down. */
    public static boolean tierTransitionAllowed(Tier prerequisite, Tier dependent) {
        if (prerequisite == null || dependent == null) {
            throw new IllegalArgumentException(
                    "Research Tech Tree prerequisite tiers cannot be null");
        }
        return prerequisite.ordinal() <= dependent.ordinal();
    }

    /**
     * Legacy format-1 ordering retained for datapack conversion and automatic
     * tier/level proposals. Published and new prerequisite authority must use
     * {@link ProgressionCoordinate}; sibling order is not progression authority.
     */
    public static boolean progressionTransitionAllowed(
            ProgressionPosition prerequisite,
            ProgressionPosition dependent) {
        if (prerequisite == null || dependent == null) {
            throw new IllegalArgumentException(
                    "Research Tech Tree progression positions cannot be null");
        }
        int tierComparison = Integer.compare(
                prerequisite.tier().ordinal(), dependent.tier().ordinal());
        if (tierComparison != 0) {
            return tierComparison < 0;
        }
        int levelComparison = Integer.compare(prerequisite.level(), dependent.level());
        if (levelComparison != 0) {
            return levelComparison < 0;
        }
        return prerequisite.siblingOrder() < dependent.siblingOrder();
    }

    /** A prerequisite is legal only when it occupies a strictly lower rank. */
    public static boolean progressionTransitionAllowed(
            ProgressionCoordinate prerequisite,
            ProgressionCoordinate dependent) {
        if (prerequisite == null || dependent == null) {
            throw new IllegalArgumentException(
                    "Research Tech Tree progression coordinates cannot be null");
        }
        return prerequisite.rank() < dependent.rank();
    }

    /** Converts a format-1 tier/level position into its initial rank. */
    public static ProgressionCoordinate legacyProgressionCoordinate(
            ProgressionPosition position) {
        if (position == null) {
            throw new IllegalArgumentException(
                    "Research Tech Tree legacy progression position cannot be null");
        }
        int rank = Math.addExact(
                Math.multiplyExact(position.tier().ordinal(), LEGACY_RANK_STRIDE),
                position.level());
        return new ProgressionCoordinate(
                rank,
                position.siblingOrder(),
                Optional.of(legacyBandId(position.tier())));
    }

    /** Stable presentation-only band identifier for converted format-1 tiers. */
    public static ResourceLocation legacyBandId(Tier tier) {
        if (tier == null) {
            throw new IllegalArgumentException("Research Tech Tree legacy tier cannot be null");
        }
        return new ResourceLocation(
                "taczweaponblueprints",
                "legacy_tier/" + tier.name().toLowerCase(Locale.ROOT));
    }

    /** Stable score-to-level mapping inside one tier; levels are zero based from bottom to top. */
    public static int levelForScore(int score, int levelsPerTier) {
        validateScore(score, "automatic weapon score");
        validateLevelsPerTier(levelsPerTier);
        Tier tier = Tier.forScore(score);
        int minimum = tier.ordinal() * 17;
        int maximum = tier == Tier.APEX ? SCORE_MAX : minimum + 16;
        int bandSize = maximum - minimum + 1;
        return Math.min(
                levelsPerTier - 1,
                (score - minimum) * levelsPerTier / bandSize);
    }

    /** Measures publication progress against the pinned TaCZ 1.1.8 default catalog. */
    public static DefaultContentCoverage defaultContentCoverage(
            Map<Domain, Integer> publishedCounts) {
        if (publishedCounts == null
                || publishedCounts.entrySet().stream().anyMatch(entry ->
                        entry.getKey() == null
                                || entry.getValue() == null
                                || entry.getValue() < 0)) {
            throw new IllegalArgumentException(
                    "Research Tech Tree published content counts are invalid");
        }
        EnumMap<Domain, Integer> published = new EnumMap<>(Domain.class);
        EnumMap<Domain, Integer> missing = new EnumMap<>(Domain.class);
        for (Domain domain : DOMAIN_ORDER) {
            int count = publishedCounts.getOrDefault(domain, 0);
            published.put(domain, count);
            missing.put(domain, Math.max(0, DEFAULT_CONTENT_TARGETS.get(domain) - count));
        }
        return new DefaultContentCoverage(published, missing);
    }

    /** Returns a disclosure-safe public domain only when identity is public. */
    public static Optional<Domain> publicDomain(
            BlueprintKind kind,
            boolean identityDisclosed) {
        if (kind == null) {
            throw new IllegalArgumentException("Research Tech Tree blueprint kind cannot be null");
        }
        return identityDisclosed ? Optional.of(Domain.forKind(kind)) : Optional.empty();
    }

    /** Stable fallback used when a remembered domain disappears after reload. */
    public static Optional<Domain> fallbackDomain(
            Set<Domain> publishedDomains,
            Domain preferred) {
        if (publishedDomains == null || publishedDomains.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Research Tech Tree domains cannot be null");
        }
        if (preferred != null && publishedDomains.contains(preferred)) {
            return Optional.of(preferred);
        }
        if (publishedDomains.contains(DEFAULT_DOMAIN)) {
            return Optional.of(DEFAULT_DOMAIN);
        }
        return DOMAIN_ORDER.stream().filter(publishedDomains::contains).findFirst();
    }

    /** Cross-domain prerequisites stay truthful but render as boundary portals. */
    public static RelationshipSurface relationshipSurface(
            Domain localDomain,
            Domain remoteDomain) {
        if (localDomain == null || remoteDomain == null) {
            throw new IllegalArgumentException("Research Tech Tree relationship domains cannot be null");
        }
        return localDomain == remoteDomain
                ? RelationshipSurface.INTERNAL_EDGE
                : RelationshipSurface.BOUNDARY_PORTAL;
    }

    /** The one allowed owner for each semantic concern. */
    public static AuthoritySource authorityFor(Concern concern) {
        if (concern == null) {
            throw new IllegalArgumentException("Research Tech Tree concern cannot be null");
        }
        return switch (concern) {
            case PREREQUISITES, POINT_COST, MATERIAL_COST,
                    RESEARCH_ELIGIBILITY, VISIBILITY -> AuthoritySource.RESEARCH_RULES;
            case BRANCH_MEMBERSHIP, BRANCH_RANK -> AuthoritySource.RESEARCH_TREE_GROUPS;
            case TECH_DOMAIN, TECH_LANE, TECH_RANK, TECH_TIER, TECH_LEVEL, TECH_SIBLING_ORDER,
                    RATING_EVIDENCE -> AuthoritySource.TECH_TREE_DATA;
            case SCORE_RECOMMENDATION -> AuthoritySource.AUTHORING_TOOL;
            case CAMERA, FILTER, FOCUS -> AuthoritySource.CLIENT_STATE;
        };
    }

    public enum BrowseIntent {
        BRANCHES,
        ALL_WEAPONS,
        TECH_TREE
    }

    public enum Domain {
        WEAPONS,
        ATTACHMENTS,
        AMMO;

        public static Domain forKind(BlueprintKind kind) {
            if (kind == null) {
                throw new IllegalArgumentException("Research Tech Tree blueprint kind cannot be null");
            }
            return switch (kind) {
                case GUN -> WEAPONS;
                case ATTACHMENT -> ATTACHMENTS;
                case AMMO -> AMMO;
            };
        }
    }

    /** Six equal score bands ordered from the bottom to the top of the tree. */
    public enum Tier {
        STARTER,
        BASIC,
        ESTABLISHED,
        ADVANCED,
        ELITE,
        APEX;

        public static Tier forScore(int score) {
            validateScore(score, "weapon score");
            return values()[Math.min(values().length - 1, score / 17)];
        }

        public boolean appearsAbove(Tier other) {
            if (other == null) {
                throw new IllegalArgumentException("Research Tech Tree tier cannot be null");
            }
            return ordinal() > other.ordinal();
        }
    }

    /**
     * Authoring evidence for a weapon placement. Scores are intentionally not
     * player-state or unlock authority and need not be synchronized to clients.
     */
    public record WeaponRating(int combat, int utility, int appeal) {
        public WeaponRating {
            validateScore(combat, "combat rating");
            validateScore(utility, "utility rating");
            validateScore(appeal, "appeal rating");
        }

        /** Mechanical baseline before subjective appeal is considered. */
        public int mechanicalScore() {
            return roundedDivide(
                    COMBAT_WEIGHT * combat + UTILITY_WEIGHT * utility,
                    COMBAT_WEIGHT + UTILITY_WEIGHT);
        }

        /** Documented 55/20/25 weighted score used by the authoring report. */
        public int weightedScore() {
            return roundedDivide(
                    COMBAT_WEIGHT * combat
                            + UTILITY_WEIGHT * utility
                            + APPEAL_WEIGHT * appeal,
                    SCORE_MAX);
        }

        /** Appeal may move the mechanical placement by at most one tier. */
        public Tier suggestedTier() {
            int mechanicalTier = Tier.forScore(mechanicalScore()).ordinal();
            int weightedTier = Tier.forScore(weightedScore()).ordinal();
            int minimum = Math.max(0, mechanicalTier - MAX_APPEAL_TIER_SHIFT);
            int maximum = Math.min(Tier.values().length - 1,
                    mechanicalTier + MAX_APPEAL_TIER_SHIFT);
            return Tier.values()[Math.max(minimum, Math.min(maximum, weightedTier))];
        }
    }

    /**
     * Runtime-safe rating contract for future automatic placement. It contains no subjective
     * appeal value and is not research authority until a server placement snapshot consumes it.
     */
    public record MechanicalRating(
            int combat,
            int utility,
            int confidence,
            String formulaVersion,
            String referenceVersion) {
        public MechanicalRating {
            validateScore(combat, "automatic combat rating");
            validateScore(utility, "automatic utility rating");
            validateScore(confidence, "automatic rating confidence");
            if (!validVersion(formulaVersion) || !validVersion(referenceVersion)) {
                throw new IllegalArgumentException(
                        "Research Tech Tree automatic rating versions are invalid");
            }
        }

        public static MechanicalRating current(int combat, int utility, int confidence) {
            return new MechanicalRating(
                    combat,
                    utility,
                    confidence,
                    AUTOMATIC_FORMULA_VERSION,
                    AUTOMATIC_REFERENCE_VERSION);
        }

        /** Automatic placement uses only the documented 75/25 mechanical blend. */
        public int score() {
            return roundedDivide(
                    AUTOMATIC_COMBAT_WEIGHT * combat
                            + AUTOMATIC_UTILITY_WEIGHT * utility,
                    SCORE_MAX);
        }

        public Tier suggestedTier() {
            return Tier.forScore(score());
        }

        public int suggestedLevel(int levelsPerTier) {
            return levelForScore(score(), levelsPerTier);
        }
    }

    /** Legacy tier/level placement retained for format-1 presentation compatibility. */
    public record ProgressionPosition(Tier tier, int level, long siblingOrder) {
        public ProgressionPosition {
            if (tier == null
                    || level < 0
                    || level >= MAX_LEVELS_PER_TIER
                    || siblingOrder < 0) {
                throw new IllegalArgumentException(
                        "Research Tech Tree progression position is invalid");
            }
        }
    }

    /**
     * Internal progression authority. Rank controls vertical dependency order,
     * sibling order only stabilizes peers horizontally, and the optional band is
     * presentation metadata.
     */
    public record ProgressionCoordinate(
            int rank,
            long siblingOrder,
            Optional<ResourceLocation> bandId) {
        public ProgressionCoordinate {
            bandId = bandId == null ? Optional.empty() : bandId;
            if (rank < 0
                    || rank > MAX_PROGRESSION_RANK
                    || siblingOrder < 0
                    || bandId.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException(
                        "Research Tech Tree progression coordinate is invalid");
            }
        }

        public ProgressionCoordinate withRank(int resolvedRank) {
            return new ProgressionCoordinate(resolvedRank, siblingOrder, bandId);
        }
    }

    /** Resolution provenance, ordered from strongest authored override to safest fallback. */
    public enum PlacementOrigin {
        EXACT(5, true),
        TAG(4, true),
        SELECTOR(3, true),
        AUTOMATIC(2, false),
        LEGACY_FALLBACK(1, false);

        private final int precedence;
        private final boolean authored;

        PlacementOrigin(int precedence, boolean authored) {
            this.precedence = precedence;
            this.authored = authored;
        }

        public int precedence() {
            return precedence;
        }

        public boolean authored() {
            return authored;
        }

        public boolean outranks(PlacementOrigin other) {
            if (other == null) {
                throw new IllegalArgumentException(
                        "Research Tech Tree placement origin cannot be null");
            }
            return precedence > other.precedence;
        }
    }

    /** Backward-compatible modes reserved for the future automatic placement profile. */
    public enum AutomaticPlacementMode {
        INDEPENDENT(false, false),
        DISTRIBUTED(true, false),
        CONNECTED(true, true);

        private final boolean assignsPlacement;
        private final boolean createsPrerequisite;

        AutomaticPlacementMode(boolean assignsPlacement, boolean createsPrerequisite) {
            this.assignsPlacement = assignsPlacement;
            this.createsPrerequisite = createsPrerequisite;
        }

        public boolean assignsPlacement() {
            return assignsPlacement;
        }

        public boolean createsPrerequisite() {
            return createsPrerequisite;
        }
    }

    public enum Concern {
        PREREQUISITES,
        POINT_COST,
        MATERIAL_COST,
        RESEARCH_ELIGIBILITY,
        VISIBILITY,
        BRANCH_MEMBERSHIP,
        BRANCH_RANK,
        TECH_DOMAIN,
        TECH_LANE,
        TECH_RANK,
        TECH_TIER,
        TECH_LEVEL,
        TECH_SIBLING_ORDER,
        RATING_EVIDENCE,
        SCORE_RECOMMENDATION,
        CAMERA,
        FILTER,
        FOCUS
    }

    public enum AuthoritySource {
        RESEARCH_RULES,
        RESEARCH_TREE_GROUPS,
        TECH_TREE_DATA,
        AUTHORING_TOOL,
        CLIENT_STATE
    }

    public enum RelationshipSurface {
        INTERNAL_EDGE,
        BOUNDARY_PORTAL
    }

    /** Tech Tree domains are canvases, not visible collections of type lanes. */
    public enum DomainCanvasStructure {
        SINGLE_UNIFIED_GRAPH
    }

    /** Kind and lane metadata may guide authoring/layout without partitioning the canvas. */
    public enum ClassificationRole {
        AUTHORING_HINT_ONLY
    }

    public record UnifiedDomainPolicy(
            DomainCanvasStructure canvasStructure,
            ClassificationRole classificationRole,
            boolean oneDomainVisibleAtATime,
            boolean requiresAcyclicGraph,
            boolean requiresSingleWeakComponent,
            boolean requiresReachabilityFromEntryPoints) {
        public UnifiedDomainPolicy {
            if (canvasStructure == null || classificationRole == null) {
                throw new IllegalArgumentException(
                        "Research Tech Tree unified-domain policy fields cannot be null");
            }
        }
    }

    public record DefaultContentCoverage(
            Map<Domain, Integer> publishedCounts,
            Map<Domain, Integer> missingCounts) {
        public DefaultContentCoverage {
            if (publishedCounts == null || missingCounts == null) {
                throw new IllegalArgumentException(
                        "Research Tech Tree default coverage maps cannot be null");
            }
            EnumMap<Domain, Integer> publishedCopy = new EnumMap<>(Domain.class);
            publishedCopy.putAll(publishedCounts);
            EnumMap<Domain, Integer> missingCopy = new EnumMap<>(Domain.class);
            missingCopy.putAll(missingCounts);
            if (!publishedCopy.keySet().equals(Set.copyOf(DOMAIN_ORDER))
                    || !missingCopy.keySet().equals(Set.copyOf(DOMAIN_ORDER))
                    || publishedCopy.values().stream().anyMatch(value -> value == null || value < 0)
                    || missingCopy.values().stream().anyMatch(value -> value == null || value < 0)) {
                throw new IllegalArgumentException(
                        "Research Tech Tree default coverage is invalid");
            }
            for (Domain domain : DOMAIN_ORDER) {
                int expectedMissing = Math.max(
                        0, DEFAULT_CONTENT_TARGETS.get(domain) - publishedCopy.get(domain));
                if (missingCopy.get(domain) != expectedMissing) {
                    throw new IllegalArgumentException(
                            "Research Tech Tree default coverage counts are inconsistent");
                }
            }
            publishedCounts = Collections.unmodifiableMap(publishedCopy);
            missingCounts = Collections.unmodifiableMap(missingCopy);
        }

        public int publishedTotal() {
            return publishedCounts.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int missingTotal() {
            return missingCounts.values().stream().mapToInt(Integer::intValue).sum();
        }

        public boolean complete() {
            return missingTotal() == 0;
        }
    }

    /** Safe behavior for content that has no authored Tech Tree placement. */
    public record FallbackPolicy(
            boolean createsPrerequisite,
            boolean changesResearchEligibility,
            boolean authoredPlacementOverrides) {
    }

    private static int roundedDivide(int numerator, int denominator) {
        return (numerator + denominator / 2) / denominator;
    }

    private static Map<Domain, Integer> defaultContentTargets() {
        EnumMap<Domain, Integer> targets = new EnumMap<>(Domain.class);
        targets.put(Domain.WEAPONS, DEFAULT_WEAPON_COUNT);
        targets.put(Domain.ATTACHMENTS, DEFAULT_ATTACHMENT_COUNT);
        targets.put(Domain.AMMO, DEFAULT_AMMO_COUNT);
        return Collections.unmodifiableMap(targets);
    }

    private static void validateScore(int value, String field) {
        if (value < 0 || value > SCORE_MAX) {
            throw new IllegalArgumentException(
                    "Research Tech Tree " + field + " must be between 0 and 100");
        }
    }

    private static void validateLevelsPerTier(int value) {
        if (value < MIN_LEVELS_PER_TIER || value > MAX_LEVELS_PER_TIER) {
            throw new IllegalArgumentException(
                    "Research Tech Tree levels per tier must be between "
                            + MIN_LEVELS_PER_TIER + " and " + MAX_LEVELS_PER_TIER);
        }
    }

    private static boolean validVersion(String value) {
        return value != null
                && !value.isBlank()
                && value.equals(value.trim())
                && value.length() <= 96
                && value.chars().noneMatch(character ->
                        Character.isWhitespace(character) || Character.isISOControl(character));
    }
}
