package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionCoordinate;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.WeaponRating;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponBranchAnalyzer;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeEntryBundle;

import net.minecraft.resources.ResourceLocation;

/** Disclosure-safe, client-ready presentation of one authored Research Tech Tree. */
public record ResearchTechTreePresentation(
        Optional<ResourceLocation> treeId,
        String title,
        Optional<String> translationKey,
        Optional<ResourceLocation> iconNodeId,
        List<TierLabel> tiers,
        List<BandLabel> bands,
        int maxNodesPerLayer,
        List<DomainView> domains) {
    public static final ResearchTechTreePresentation EMPTY = new ResearchTechTreePresentation(
            Optional.empty(), "", Optional.empty(), Optional.empty(),
            List.of(), List.of(),
            ResearchTechTreeDefinition.LayoutDefinition.DEFAULT_NODES_PER_LAYER,
            List.of());

    /** Compatibility constructor for presentations predating tree-owned width. */
    public ResearchTechTreePresentation(
            Optional<ResourceLocation> treeId,
            String title,
            Optional<String> translationKey,
            Optional<ResourceLocation> iconNodeId,
            List<TierLabel> tiers,
            List<BandLabel> bands,
            List<DomainView> domains) {
        this(
                treeId,
                title,
                translationKey,
                iconNodeId,
                tiers,
                bands,
                ResearchTechTreeDefinition.LayoutDefinition.DEFAULT_NODES_PER_LAYER,
                domains);
    }

    /** Compatibility constructor that publishes the six legacy labels as bands. */
    public ResearchTechTreePresentation(
            Optional<ResourceLocation> treeId,
            String title,
            Optional<String> translationKey,
            Optional<ResourceLocation> iconNodeId,
            List<TierLabel> tiers,
            List<DomainView> domains) {
        this(
                treeId,
                title,
                translationKey,
                iconNodeId,
                tiers,
                tiers == null
                        ? List.of()
                        : tiers.stream().map(tier -> new BandLabel(
                                ResearchTechTreeContract.legacyBandId(tier.tier()),
                                tier.title(),
                                tier.translationKey())).toList(),
                ResearchTechTreeDefinition.LayoutDefinition.DEFAULT_NODES_PER_LAYER,
                domains);
    }

    public ResearchTechTreePresentation {
        treeId = treeId == null ? Optional.empty() : treeId;
        translationKey = translationKey == null ? Optional.empty() : translationKey;
        iconNodeId = iconNodeId == null ? Optional.empty() : iconNodeId;
        tiers = tiers == null ? List.of() : List.copyOf(tiers);
        bands = bands == null ? List.of() : List.copyOf(bands);
        domains = domains == null ? List.of() : List.copyOf(domains);
        validate(
                treeId,
                title,
                translationKey,
                iconNodeId,
                tiers,
                bands,
                maxNodesPerLayer,
                domains);
    }

    public boolean available() {
        return treeId.isPresent();
    }

    public int memberCount() {
        return domains.stream()
                .flatMap(domain -> domain.lanes().stream())
                .mapToInt(lane -> lane.members().size())
                .sum();
    }

    public Optional<DomainView> domain(Domain domain) {
        return domains.stream().filter(value -> value.domain() == domain).findFirst();
    }

    public void validateAgainst(ResearchTreeGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Research Tech Tree graph cannot be null");
        }
        if (!available()) {
            return;
        }
        java.util.Map<ResourceLocation, Member> membersById = new java.util.LinkedHashMap<>();
        for (Member member : members()) {
            ResearchTreeGraph.Node node = graph.node(member.nodeId()).orElseThrow(() ->
                    new IllegalArgumentException(
                            "Research Tech Tree member references an unknown public node"));
            if (!node.visibility().revealsIdentity()) {
                throw new IllegalArgumentException(
                        "Research Tech Tree cannot identify an anonymous public node");
            }
            membersById.put(member.nodeId(), member);
        }
        iconNodeId.ifPresent(icon -> validateIcon(graph, icon));
        bands.forEach(band -> band.icon().ifPresent(icon -> validateIcon(graph, icon)));
        for (DomainView domain : domains) {
            domain.iconNodeId().ifPresent(icon -> validateIcon(graph, icon));
            domain.lanes().forEach(lane ->
                    lane.iconNodeId().ifPresent(icon -> validateIcon(graph, icon)));
        }
        for (ResearchTreeGraph.Edge edge : graph.edges()) {
            Member prerequisite = membersById.get(edge.prerequisiteId());
            Member dependent = membersById.get(edge.dependentId());
            if (prerequisite != null && dependent != null
                    && !ResearchTechTreeContract.progressionTransitionAllowed(
                            prerequisite.position(), dependent.position())) {
                throw new IllegalArgumentException(
                        "Research Tech Tree ranks contradict a prerequisite edge");
            }
        }
    }

    private List<Member> members() {
        return domains.stream()
                .flatMap(domain -> domain.lanes().stream())
                .flatMap(lane -> lane.members().stream())
                .toList();
    }

    private static void validateIcon(ResearchTreeGraph graph, ResourceLocation icon) {
        ResearchTreeGraph.Node node = graph.node(icon).orElseThrow(() ->
                new IllegalArgumentException(
                        "Research Tech Tree icon references an unknown public node"));
        if (!node.visibility().revealsIcon()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree icon identifies a node whose icon is private");
        }
    }

    public record TierLabel(
            Tier tier,
            String title,
            Optional<String> translationKey) {
        public TierLabel {
            translationKey = translationKey == null ? Optional.empty() : translationKey;
            if (tier == null || !validTitle(title)
                    || translationKey.filter(value -> !validTranslationKey(value)).isPresent()) {
                throw new IllegalArgumentException("Invalid Research Tech Tree tier label");
            }
        }
    }

    /** Ordered bottom-to-top presentation label for optional progression bands. */
    public record BandLabel(
            ResourceLocation id,
            String title,
            Optional<String> translationKey,
            Optional<Integer> color,
            Optional<ResourceLocation> icon) {
        /** Compatibility constructor for labels predating optional style metadata. */
        public BandLabel(
                ResourceLocation id,
                String title,
                Optional<String> translationKey) {
            this(id, title, translationKey, Optional.empty(), Optional.empty());
        }

        public BandLabel {
            translationKey = translationKey == null ? Optional.empty() : translationKey;
            color = color == null ? Optional.empty() : color;
            icon = icon == null ? Optional.empty() : icon;
            if (!validId(id) || !validTitle(title)
                    || translationKey.filter(value -> !validTranslationKey(value)).isPresent()
                    || color.filter(value -> value < 0 || value > 0xFFFFFF).isPresent()
                    || icon.filter(value -> !validId(value)).isPresent()) {
                throw new IllegalArgumentException(
                        "Invalid Research Tech Tree progression-band label");
            }
        }
    }

    public record DomainView(
            Domain domain,
            String title,
            Optional<String> translationKey,
            Optional<ResourceLocation> iconNodeId,
            List<LaneView> lanes) {
        public DomainView {
            translationKey = translationKey == null ? Optional.empty() : translationKey;
            iconNodeId = iconNodeId == null ? Optional.empty() : iconNodeId;
            lanes = lanes == null ? List.of() : List.copyOf(lanes);
            if (domain == null || !validTitle(title)
                    || translationKey.filter(value -> !validTranslationKey(value)).isPresent()
                    || lanes.isEmpty()
                    || lanes.size() > ResearchTechTreeDefinition.MAX_LANES_PER_DOMAIN
                    || lanes.stream().anyMatch(java.util.Objects::isNull)
                    || !lanes.equals(lanes.stream().sorted(Comparator
                            .comparingInt(LaneView::order)
                            .thenComparing(value -> value.id().toString())).toList())) {
                throw new IllegalArgumentException("Invalid Research Tech Tree domain presentation");
            }
            Set<ResourceLocation> laneIds = new LinkedHashSet<>();
            Set<ResourceLocation> memberIds = new LinkedHashSet<>();
            for (LaneView lane : lanes) {
                if (!laneIds.add(lane.id())) {
                    throw new IllegalArgumentException("Duplicate Research Tech Tree lane ID");
                }
                for (Member member : lane.members()) {
                    if (!memberIds.add(member.nodeId())) {
                        throw new IllegalArgumentException(
                                "Research Tech Tree domain assigns one node more than once");
                    }
                    if (domain != Domain.WEAPONS
                            && (member.rating().isPresent()
                                    || member.origin() == PlacementOrigin.AUTOMATIC)) {
                        throw new IllegalArgumentException(
                                "Research Tech Tree automatic placements and ratings are valid only in Weapons");
                    }
                }
            }
            iconNodeId.ifPresent(icon -> {
                if (!memberIds.contains(icon)) {
                    throw new IllegalArgumentException(
                            "Research Tech Tree domain icon is not a domain member");
                }
            });
        }
    }

    public record LaneView(
            ResourceLocation id,
            String title,
            Optional<String> translationKey,
            Optional<ResourceLocation> iconNodeId,
            int order,
            List<Member> members) {
        private static final Comparator<Member> MEMBER_ORDER = Comparator
                .comparingInt(Member::rank)
                .thenComparingLong(Member::siblingOrder)
                .thenComparing(value -> value.nodeId().toString());

        public LaneView {
            translationKey = translationKey == null ? Optional.empty() : translationKey;
            iconNodeId = iconNodeId == null ? Optional.empty() : iconNodeId;
            members = members == null ? List.of() : List.copyOf(members);
            if (!validId(id) || !validTitle(title)
                    || translationKey.filter(value -> !validTranslationKey(value)).isPresent()
                    || order < 0 || order > ResearchTechTreeDefinition.MAX_ORDER
                    || members.isEmpty() || members.size() > ResearchTreeGraph.MAX_NODES
                    || members.stream().anyMatch(java.util.Objects::isNull)
                    || !members.equals(members.stream().sorted(MEMBER_ORDER).toList())) {
                throw new IllegalArgumentException("Invalid Research Tech Tree lane presentation");
            }
            Set<ResourceLocation> memberIds = new LinkedHashSet<>();
            for (Member member : members) {
                if (!memberIds.add(member.nodeId())) {
                    throw new IllegalArgumentException(
                            "Research Tech Tree lane contains a duplicate member");
                }
            }
            iconNodeId.ifPresent(icon -> {
                if (!memberIds.contains(icon)) {
                    throw new IllegalArgumentException(
                            "Research Tech Tree lane icon is not a lane member");
                }
            });
        }
    }

    public record Member(
            ResourceLocation nodeId,
            int rank,
            long siblingOrder,
            Optional<ResourceLocation> bandId,
            PlacementOrigin origin,
            Optional<WeaponRating> rating,
            Optional<AutomaticBranchPlacement> automaticBranch) {
        /** Compatibility constructor for members without canonical branch metadata. */
        public Member(
                ResourceLocation nodeId,
                int rank,
                long siblingOrder,
                Optional<ResourceLocation> bandId,
                PlacementOrigin origin,
                Optional<WeaponRating> rating) {
            this(
                    nodeId,
                    rank,
                    siblingOrder,
                    bandId,
                    origin,
                    rating,
                    Optional.empty());
        }

        /** Compatibility constructor for legacy tier/level fixtures and callers. */
        public Member(
                ResourceLocation nodeId,
                Tier tier,
                int level,
                long siblingOrder,
                PlacementOrigin origin,
                Optional<WeaponRating> rating) {
            this(
                    nodeId,
                    ResearchTechTreeContract.legacyProgressionCoordinate(
                            new ProgressionPosition(tier, level, siblingOrder)).rank(),
                    siblingOrder,
                    Optional.of(ResearchTechTreeContract.legacyBandId(tier)),
                    origin,
                    rating,
                    Optional.empty());
        }

        /** Compatibility constructor for legacy tier/order fixtures and callers. */
        public Member(
                ResourceLocation nodeId,
                Tier tier,
                int order,
                Optional<WeaponRating> rating) {
            this(nodeId, tier, 0, order, PlacementOrigin.EXACT, rating);
        }

        public Member {
            bandId = bandId == null ? Optional.empty() : bandId;
            rating = rating == null ? Optional.empty() : rating;
            automaticBranch = automaticBranch == null
                    ? Optional.empty() : automaticBranch;
            if (!validId(nodeId) || origin == null
                    || rank < 0 || rank > ResearchTechTreeContract.MAX_PROGRESSION_RANK
                    || siblingOrder < 0
                    || bandId.filter(value -> !validId(value)).isPresent()
                    || origin != PlacementOrigin.AUTOMATIC && automaticBranch.isPresent()
                    || (origin != PlacementOrigin.AUTOMATIC
                            && siblingOrder > ResearchTechTreeEntryBundle.MAX_ORDER)) {
                throw new IllegalArgumentException("Invalid Research Tech Tree member presentation");
            }
        }

        public ProgressionCoordinate position() {
            return new ProgressionCoordinate(rank, siblingOrder, bandId);
        }

        /** Legacy display metadata, when this member references one of the six old tiers. */
        public Optional<Tier> legacyTier() {
            return java.util.Arrays.stream(Tier.values())
                    .filter(value -> bandId.filter(
                            ResearchTechTreeContract.legacyBandId(value)::equals).isPresent())
                    .findFirst();
        }

        /** @deprecated Rank is progression authority; use {@link #legacyTier()} for labels. */
        @Deprecated(forRemoval = false)
        public Tier tier() {
            return legacyTier().orElseGet(() -> Tier.values()[Math.min(
                    Tier.values().length - 1,
                    rank / ResearchTechTreeContract.LEGACY_RANK_STRIDE)]);
        }

        /** @deprecated Rank is progression authority. */
        @Deprecated(forRemoval = false)
        public int level() {
            return rank % ResearchTechTreeContract.LEGACY_RANK_STRIDE;
        }
    }

    /** Canonical server-owned branch coordinates for automatic presentation. */
    public record AutomaticBranchPlacement(
            int branchIndex,
            int rankIndex,
            int familyStartIndex,
            int transitionEndIndex) {
        public AutomaticBranchPlacement {
            if (branchIndex < 0
                    || branchIndex >= AutomaticWeaponBranchAnalyzer.MAX_BRANCHES
                    || rankIndex < 0
                    || rankIndex > ResearchTechTreeContract.MAX_PROGRESSION_RANK
                    || familyStartIndex < 0
                    || familyStartIndex > transitionEndIndex
                    || transitionEndIndex
                            > ResearchTechTreeContract.MAX_PROGRESSION_RANK) {
                throw new IllegalArgumentException(
                        "Invalid automatic Research Tech Tree branch placement");
            }
        }
    }

    private static void validate(
            Optional<ResourceLocation> treeId,
            String title,
            Optional<String> translationKey,
            Optional<ResourceLocation> iconNodeId,
            List<TierLabel> tiers,
            List<BandLabel> bands,
            int maxNodesPerLayer,
            List<DomainView> domains) {
        if (maxNodesPerLayer
                        < ResearchTechTreeDefinition.LayoutDefinition.MIN_NODES_PER_LAYER
                || maxNodesPerLayer
                        > ResearchTechTreeDefinition.LayoutDefinition.MAX_NODES_PER_LAYER) {
            throw new IllegalArgumentException(
                    "Invalid Research Tech Tree maximum nodes per layer");
        }
        if (treeId.isEmpty()) {
            if (!"".equals(title) || translationKey.isPresent() || iconNodeId.isPresent()
                    || !tiers.isEmpty() || !bands.isEmpty() || !domains.isEmpty()) {
                throw new IllegalArgumentException("Empty Research Tech Tree presentation is inconsistent");
            }
            return;
        }
        if (!validId(treeId.orElseThrow()) || !validTitle(title)
                || translationKey.filter(value -> !validTranslationKey(value)).isPresent()
                || domains.isEmpty() || domains.size() > Domain.values().length
                || domains.stream().anyMatch(java.util.Objects::isNull)
                || !domains.equals(domains.stream()
                        .sorted(Comparator.comparingInt(value -> value.domain().ordinal()))
                        .toList())) {
            throw new IllegalArgumentException("Invalid Research Tech Tree presentation");
        }
        List<Tier> tierOrder = tiers.stream().map(TierLabel::tier).toList();
        if (!tierOrder.isEmpty() && !tierOrder.equals(List.of(Tier.values()))) {
            throw new IllegalArgumentException(
                    "Research Tech Tree presentation must publish either no legacy tiers "
                            + "or all six in order");
        }
        if (bands.size() > ResearchTechTreeDefinition.MAX_PRESENTATION_BANDS
                || bands.stream().anyMatch(java.util.Objects::isNull)
                || bands.stream().map(BandLabel::id).distinct().count() != bands.size()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree progression-band labels are invalid");
        }
        Set<Domain> domainIds = new LinkedHashSet<>();
        Set<ResourceLocation> memberIds = new LinkedHashSet<>();
        int automaticMemberCount = 0;
        int branchMetadataCount = 0;
        Integer automaticFamilyStart = null;
        Integer automaticTransitionEnd = null;
        Set<ResourceLocation> bandIds = bands.stream()
                .map(BandLabel::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (DomainView domain : domains) {
            if (!domainIds.add(domain.domain())) {
                throw new IllegalArgumentException("Duplicate Research Tech Tree domain");
            }
            for (LaneView lane : domain.lanes()) {
                for (Member member : lane.members()) {
                    if (!memberIds.add(member.nodeId())) {
                        throw new IllegalArgumentException(
                                "Research Tech Tree assigns one public node more than once");
                    }
                    if (member.bandId().filter(value -> !bandIds.contains(value)).isPresent()) {
                        throw new IllegalArgumentException(
                                "Research Tech Tree member references an unpublished band");
                    }
                    if (member.origin() == PlacementOrigin.AUTOMATIC) {
                        automaticMemberCount++;
                    }
                    if (member.automaticBranch().isPresent()) {
                        AutomaticBranchPlacement branch = member.automaticBranch().orElseThrow();
                        branchMetadataCount++;
                        if (automaticFamilyStart != null
                                && (automaticFamilyStart != branch.familyStartIndex()
                                        || automaticTransitionEnd
                                                != branch.transitionEndIndex())) {
                            throw new IllegalArgumentException(
                                    "Research Tech Tree automatic branch boundaries disagree");
                        }
                        automaticFamilyStart = branch.familyStartIndex();
                        automaticTransitionEnd = branch.transitionEndIndex();
                    }
                }
            }
        }
        if (branchMetadataCount > 0 && branchMetadataCount != automaticMemberCount) {
            throw new IllegalArgumentException(
                    "Research Tech Tree automatic branch metadata is incomplete");
        }
        if (memberIds.size() > ResearchTreeGraph.MAX_NODES) {
            throw new IllegalArgumentException("Research Tech Tree has too many public members");
        }
        iconNodeId.ifPresent(icon -> {
            if (!memberIds.contains(icon)) {
                throw new IllegalArgumentException(
                        "Research Tech Tree icon is not a published member");
            }
        });
    }

    private static boolean validId(ResourceLocation value) {
        return value != null
                && value.toString().length() <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH;
    }

    private static boolean validTitle(String value) {
        return value != null
                && !value.isBlank()
                && value.equals(value.trim())
                && value.length() <= ResearchTechTreeDefinition.MAX_TITLE_LENGTH
                && value.chars().noneMatch(Character::isISOControl);
    }

    private static boolean validTranslationKey(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= ResearchTechTreeDefinition.MAX_TRANSLATION_KEY_LENGTH
                && value.chars().noneMatch(character -> Character.isWhitespace(character)
                        || Character.isISOControl(character));
    }
}
