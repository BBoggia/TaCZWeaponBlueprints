package com.gamergaming.taczweaponblueprints.client;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.WeaponRating;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

final class ResearchTechTreeClientFixture {
    static final ResourceLocation OPAQUE = ResearchTreeGraph.redactedNodeId(0);
    static final ResourceLocation AMMO = id("test:ammo");
    static final ResourceLocation WEAPON_ROOT = id("test:weapon_root");
    static final ResourceLocation WEAPON_UPGRADE = id("test:weapon_upgrade");
    static final ResourceLocation SCOPE = id("test:scope");

    private ResearchTechTreeClientFixture() {
    }

    static ResearchTreePublication publication() {
        return publication(Set.of(Domain.values()),
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
    }

    static ResearchTreePublication publication(Set<Domain> domains) {
        return publication(domains,
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
    }

    static ResearchTreePublication publicationWithoutUpgradePlacement() {
        return publication(
                Set.of(Domain.values()),
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED,
                false,
                Tier.BASIC);
    }

    static ResearchTreePublication publicationWithSameTierWeaponChain() {
        return publication(
                Set.of(Domain.values()),
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED,
                true,
                Tier.STARTER);
    }

    static ResearchTreePublication publication(
            Set<Domain> domains,
            ResearchTreeGraph.Availability upgradeAvailability) {
        return publication(domains, upgradeAvailability, true);
    }

    private static ResearchTreePublication publication(
            Set<Domain> domains,
            ResearchTreeGraph.Availability upgradeAvailability,
            boolean includeUpgrade) {
        return publication(domains, upgradeAvailability, includeUpgrade, Tier.BASIC);
    }

    private static ResearchTreePublication publication(
            Set<Domain> domains,
            ResearchTreeGraph.Availability upgradeAvailability,
            boolean includeUpgrade,
            Tier upgradeTier) {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        opaqueNode(),
                        node(1, AMMO, "ammo", 0,
                                ResearchTreeGraph.Availability.AVAILABLE),
                        node(2, WEAPON_ROOT, "pistol", 1,
                                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED),
                        node(3, WEAPON_UPGRADE, "pistol", 2, upgradeAvailability),
                        node(4, SCOPE, "scope", 1,
                                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED)),
                List.of(
                        new ResearchTreeGraph.Edge(OPAQUE, WEAPON_ROOT),
                        new ResearchTreeGraph.Edge(AMMO, WEAPON_UPGRADE),
                        new ResearchTreeGraph.Edge(WEAPON_ROOT, WEAPON_UPGRADE),
                        new ResearchTreeGraph.Edge(WEAPON_UPGRADE, SCOPE)));
        ResearchTreePresentation presentation = new ResearchTreePresentation(List.of(
                new ResearchTreePresentation.Group(
                        id("test:undisclosed"),
                        ResearchTreePresentation.UNDISCLOSED_TITLE,
                        Optional.of(ResearchTreePresentation.UNDISCLOSED_TRANSLATION_KEY),
                        Optional.empty(),
                        0,
                        ResearchTreePresentation.Kind.UNDISCLOSED,
                        List.of(new ResearchTreePresentation.Member(OPAQUE, 0, 0))),
                new ResearchTreePresentation.Group(
                        id("test:published"),
                        "Published",
                        Optional.empty(),
                        Optional.of(WEAPON_ROOT),
                        1,
                        ResearchTreePresentation.Kind.AUTHORED,
                        List.of(
                                new ResearchTreePresentation.Member(WEAPON_ROOT, 1, 0),
                                new ResearchTreePresentation.Member(WEAPON_UPGRADE, 2, 0)))));
        return new ResearchTreePublication(
                graph, presentation, techTree(domains, includeUpgrade, upgradeTier));
    }

    private static ResearchTechTreePresentation techTree(
            Set<Domain> domains,
            boolean includeUpgrade,
            Tier upgradeTier) {
        List<ResearchTechTreePresentation.DomainView> views = Arrays.stream(Domain.values())
                .filter(domains::contains)
                .map(domain -> domain(domain, includeUpgrade, upgradeTier))
                .toList();
        if (views.isEmpty()) {
            return ResearchTechTreePresentation.EMPTY;
        }
        ResourceLocation icon = domains.contains(Domain.WEAPONS)
                ? WEAPON_ROOT
                : domains.contains(Domain.ATTACHMENTS) ? SCOPE : AMMO;
        return new ResearchTechTreePresentation(
                Optional.of(id("test:tech_tree")),
                "Tech Tree",
                Optional.empty(),
                Optional.of(icon),
                Arrays.stream(Tier.values())
                        .map(tier -> new ResearchTechTreePresentation.TierLabel(
                                tier, tier.name(), Optional.empty()))
                        .toList(),
                views);
    }

    private static ResearchTechTreePresentation.DomainView domain(
            Domain domain,
            boolean includeUpgrade,
            Tier upgradeTier) {
        ResourceLocation node = switch (domain) {
            case WEAPONS -> WEAPON_ROOT;
            case ATTACHMENTS -> SCOPE;
            case AMMO -> AMMO;
        };
        ResourceLocation lane = id("test:" + domain.name().toLowerCase());
        List<ResearchTechTreePresentation.Member> members;
        if (domain == Domain.WEAPONS) {
            ResearchTechTreePresentation.Member root =
                    new ResearchTechTreePresentation.Member(
                                WEAPON_ROOT,
                                Tier.STARTER,
                                0,
                                0,
                                PlacementOrigin.EXACT,
                                Optional.of(new WeaponRating(20, 30, 40)));
            ResearchTechTreePresentation.Member upgrade =
                    new ResearchTechTreePresentation.Member(
                                WEAPON_UPGRADE,
                                upgradeTier,
                                upgradeTier == Tier.STARTER ? 1 : 0,
                                0,
                                PlacementOrigin.EXACT,
                                Optional.of(new WeaponRating(50, 60, 70)));
            members = includeUpgrade ? List.of(root, upgrade) : List.of(root);
        } else {
            members = List.of(new ResearchTechTreePresentation.Member(
                    node,
                    domain == Domain.ATTACHMENTS ? Tier.ESTABLISHED : Tier.STARTER,
                    0,
                    Optional.empty()));
        }
        return new ResearchTechTreePresentation.DomainView(
                domain,
                title(domain),
                Optional.empty(),
                Optional.of(node),
                List.of(new ResearchTechTreePresentation.LaneView(
                        lane,
                        title(domain),
                        Optional.empty(),
                        Optional.of(node),
                        0,
                        members)));
    }

    private static ResearchTreeGraph.Node opaqueNode() {
        return new ResearchTreeGraph.Node(
                0,
                OPAQUE,
                ResearchTreeGraph.REDACTED_NAME_KEY,
                ResearchTreeGraph.REDACTED_ITEM_TYPE,
                ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                JournalVisibility.SILHOUETTE,
                false,
                false,
                false,
                0,
                0,
                0,
                0,
                ResearchTreeGraph.Availability.REDACTED);
    }

    private static ResearchTreeGraph.Node node(
            int ordinal,
            ResourceLocation nodeId,
            String itemType,
            int prerequisiteCount,
            ResearchTreeGraph.Availability availability) {
        return new ResearchTreeGraph.Node(
                ordinal,
                nodeId,
                "name." + nodeId.getPath(),
                itemType,
                id("test:slot/" + nodeId.getPath()),
                JournalVisibility.FULL,
                availability == ResearchTreeGraph.Availability.LEARNED,
                true,
                availability == ResearchTreeGraph.Availability.AVAILABLE,
                8,
                0,
                prerequisiteCount,
                0,
                availability);
    }

    private static String title(Domain domain) {
        String lower = domain.name().toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
