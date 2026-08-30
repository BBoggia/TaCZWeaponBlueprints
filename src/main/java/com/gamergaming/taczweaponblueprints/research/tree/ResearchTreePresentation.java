package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.network.BlueprintSyncLimits;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTreeGroupDefinition;

import net.minecraft.resources.ResourceLocation;

/** Immutable, disclosure-safe group metadata for one published research graph. */
public final class ResearchTreePresentation {
    public static final int MAX_GROUPS = ResearchTreeGraph.MAX_NODES;
    public static final String UNDISCLOSED_TITLE = "Undisclosed";
    public static final String UNDISCLOSED_TRANSLATION_KEY =
            "gui.taczweaponblueprints.research_group.undisclosed";
    public static final ResourceLocation PREFERRED_UNDISCLOSED_GROUP_ID =
            new ResourceLocation("taczweaponblueprints", "published/undisclosed");
    public static final ResearchTreePresentation EMPTY = new ResearchTreePresentation(List.of());

    private static final Comparator<Member> MEMBER_ORDER = Comparator
            .comparingInt(Member::rank)
            .thenComparingInt(Member::orderInRank)
            .thenComparing(member -> member.nodeId().toString());

    private final List<Group> groups;
    private final Map<ResourceLocation, Group> groupsById;
    private final Map<ResourceLocation, Membership> membershipsByNodeId;

    public ResearchTreePresentation(List<Group> groups) {
        if (groups != null && groups.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("research presentation cannot contain null groups");
        }
        this.groups = groups == null ? List.of() : List.copyOf(groups);
        if (this.groups.size() > MAX_GROUPS) {
            throw new IllegalArgumentException("research presentation exceeds its group limit");
        }

        Map<ResourceLocation, Group> groupIndex = new LinkedHashMap<>();
        Map<ResourceLocation, Membership> membershipIndex = new LinkedHashMap<>();
        for (int groupOrder = 0; groupOrder < this.groups.size(); groupOrder++) {
            Group group = this.groups.get(groupOrder);
            if (group.order() != groupOrder) {
                throw new IllegalArgumentException("published research group orders must be contiguous");
            }
            if (groupIndex.put(group.id(), group) != null) {
                throw new IllegalArgumentException("research presentation contains a duplicate group ID");
            }
            for (Member member : group.members()) {
                if (membershipIndex.put(
                        member.nodeId(),
                        new Membership(group.id(), member.rank(), member.orderInRank())) != null) {
                    throw new IllegalArgumentException(
                            "research presentation assigns one node to multiple groups");
                }
            }
        }
        if (membershipIndex.size() > ResearchTreeGraph.MAX_NODES) {
            throw new IllegalArgumentException("research presentation exceeds its member limit");
        }
        groupsById = Map.copyOf(groupIndex);
        membershipsByNodeId = Map.copyOf(membershipIndex);
    }

    public List<Group> groups() {
        return groups;
    }

    public Optional<Group> group(ResourceLocation groupId) {
        return groupId == null ? Optional.empty() : Optional.ofNullable(groupsById.get(groupId));
    }

    public Optional<Membership> membership(ResourceLocation nodeId) {
        return nodeId == null ? Optional.empty() : Optional.ofNullable(membershipsByNodeId.get(nodeId));
    }

    public boolean hasSameTopology(ResearchTreePresentation other) {
        return other != null && groups.equals(other.groups);
    }

    @Override
    public boolean equals(Object value) {
        return this == value || value instanceof ResearchTreePresentation other
                && groups.equals(other.groups);
    }

    @Override
    public int hashCode() {
        return groups.hashCode();
    }

    @Override
    public String toString() {
        return "ResearchTreePresentation[groups=" + groups + "]";
    }

    public record Group(
            ResourceLocation id,
            String title,
            Optional<String> translationKey,
            Optional<ResourceLocation> iconNodeId,
            int order,
            Kind kind,
            boolean includedInOverview,
            List<Member> members) {
        /** Uses the stable default for callers that do not author an override. */
        public Group(
                ResourceLocation id,
                String title,
                Optional<String> translationKey,
                Optional<ResourceLocation> iconNodeId,
                int order,
                Kind kind,
                List<Member> members) {
            this(
                    id,
                    title,
                    translationKey,
                    iconNodeId,
                    order,
                    kind,
                    kind != null && kind.includedInOverviewByDefault(),
                    members);
        }

        public Group {
            if (id == null || title == null || kind == null || members == null
                    || members.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("published research group fields cannot be null");
            }
            translationKey = translationKey == null ? Optional.empty() : translationKey;
            iconNodeId = iconNodeId == null ? Optional.empty() : iconNodeId;
            members = List.copyOf(members);
            validateId(id, "group");
            validateText(title, ResearchTreeGroupDefinition.MAX_TITLE_LENGTH, "group title");
            translationKey.ifPresent(ResearchTreePresentation::validateTranslationKey);
            iconNodeId.ifPresent(value -> validateId(value, "group icon node"));
            if (order < 0 || order >= MAX_GROUPS || members.isEmpty()
                    || members.size() > ResearchTreeGraph.MAX_NODES) {
                throw new IllegalArgumentException("invalid published research group order or member count");
            }
            if (!members.equals(members.stream().sorted(MEMBER_ORDER).toList())) {
                throw new IllegalArgumentException("published research group members are not deterministically ordered");
            }
            Set<ResourceLocation> memberIds = new LinkedHashSet<>();
            Map<Integer, Integer> nextOrderByRank = new LinkedHashMap<>();
            for (Member member : members) {
                if (!memberIds.add(member.nodeId())) {
                    throw new IllegalArgumentException("published research group contains a duplicate member");
                }
                int expectedOrder = nextOrderByRank.getOrDefault(member.rank(), 0);
                if (member.orderInRank() != expectedOrder) {
                    throw new IllegalArgumentException(
                            "published sibling orders must be contiguous within each rank");
                }
                nextOrderByRank.put(member.rank(), expectedOrder + 1);
            }
            if (iconNodeId.isPresent() && !memberIds.contains(iconNodeId.orElseThrow())) {
                throw new IllegalArgumentException("published research group icon must be one of its members");
            }
            if (kind == Kind.UNDISCLOSED
                    && (iconNodeId.isPresent()
                    || !title.equals(UNDISCLOSED_TITLE)
                    || !translationKey.equals(Optional.of(UNDISCLOSED_TRANSLATION_KEY)))) {
                throw new IllegalArgumentException("undisclosed research group contains identifying metadata");
            }
        }
    }

    public record Member(ResourceLocation nodeId, int rank, int orderInRank) {
        public Member {
            if (nodeId == null || rank < 0 || rank >= ResearchTreeGraph.MAX_NODES
                    || orderInRank < 0 || orderInRank >= ResearchTreeGraph.MAX_NODES) {
                throw new IllegalArgumentException("invalid published research group member");
            }
            validateId(nodeId, "group member");
        }
    }

    public record Membership(ResourceLocation groupId, int rank, int orderInRank) {
        public Membership {
            if (groupId == null || rank < 0 || rank >= ResearchTreeGraph.MAX_NODES
                    || orderInRank < 0 || orderInRank >= ResearchTreeGraph.MAX_NODES) {
                throw new IllegalArgumentException("invalid published research membership");
            }
            validateId(groupId, "membership group");
        }
    }

    public enum Kind {
        AUTHORED(true),
        ITEM_TYPE_FALLBACK(true),
        UNDISCLOSED(false);

        private final boolean includedInOverviewByDefault;

        Kind(boolean includedInOverviewByDefault) {
            this.includedInOverviewByDefault = includedInOverviewByDefault;
        }

        public boolean includedInOverviewByDefault() {
            return includedInOverviewByDefault;
        }
    }

    private static void validateId(ResourceLocation id, String field) {
        if (id.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("research presentation " + field + " ID is oversized");
        }
    }

    private static void validateText(String value, int maximumLength, String field) {
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > maximumLength
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("research presentation " + field + " is invalid");
        }
    }

    private static void validateTranslationKey(String value) {
        validateText(value, BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH, "group translation key");
        if (value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("research presentation group translation key is invalid");
        }
    }
}
