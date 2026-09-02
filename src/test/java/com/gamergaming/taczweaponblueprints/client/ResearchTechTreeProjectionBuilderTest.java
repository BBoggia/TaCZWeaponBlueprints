package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;

class ResearchTechTreeProjectionBuilderTest {
    @Test
    void buildsDisclosureSafeDomainGraphsAndTruthfulReciprocalBoundaries() {
        ResearchTreePublication publication = ResearchTechTreeClientFixture.publication();

        ResearchTechTreeProjectionCatalog catalog =
                ResearchTechTreeProjectionBuilder.build(publication);

        assertTrue(catalog.available());
        assertEquals(List.of(Domain.WEAPONS, Domain.ATTACHMENTS, Domain.AMMO),
                catalog.domains());
        assertTrue(catalog.domainOf(ResearchTechTreeClientFixture.OPAQUE).isEmpty());

        ResearchTechTreeProjection weapons = catalog.projection(Domain.WEAPONS).orElseThrow();
        assertEquals(List.of(
                ResearchTechTreeClientFixture.WEAPON_ROOT,
                ResearchTechTreeClientFixture.WEAPON_UPGRADE),
                weapons.graph().nodes().stream()
                        .map(ResearchTreeGraph.Node::blueprintId)
                        .toList());
        assertEquals(List.of(new ResearchTreeGraph.Edge(
                        ResearchTechTreeClientFixture.WEAPON_ROOT,
                        ResearchTechTreeClientFixture.WEAPON_UPGRADE)),
                weapons.graph().edges());
        assertEquals(List.of(0, 1), weapons.graph().nodes().stream()
                .map(ResearchTreeGraph.Node::ordinal).toList());
        assertEquals(List.of(2, 3), weapons.graph().nodes().stream()
                .map(ResearchTreeGraph.Node::sourceOrdinal).toList());
        assertEquals(1, weapons.graph().node(
                ResearchTechTreeClientFixture.WEAPON_UPGRADE)
                .orElseThrow().prerequisiteCount());
        assertTrue(weapons.graph().nodes().stream()
                .allMatch(node -> node.visibility().revealsIdentity()));

        assertEquals(2, weapons.boundaryLinks().size());
        assertTrue(weapons.containsBoundaryLink(link(
                ResearchTechTreeClientFixture.WEAPON_UPGRADE,
                ResearchTechTreeClientFixture.AMMO,
                Domain.AMMO,
                ResearchTechTreeProjection.Direction.REQUIREMENT)));
        assertTrue(weapons.containsBoundaryLink(link(
                ResearchTechTreeClientFixture.WEAPON_UPGRADE,
                ResearchTechTreeClientFixture.SCOPE,
                Domain.ATTACHMENTS,
                ResearchTechTreeProjection.Direction.UNLOCK)));
        assertFalse(weapons.boundaryLinks().stream().anyMatch(link ->
                link.remoteNodeId().equals(ResearchTechTreeClientFixture.OPAQUE)));

        ResearchTechTreeProjection attachments = catalog.projection(
                Domain.ATTACHMENTS).orElseThrow();
        assertTrue(attachments.containsBoundaryLink(link(
                ResearchTechTreeClientFixture.SCOPE,
                ResearchTechTreeClientFixture.WEAPON_UPGRADE,
                Domain.WEAPONS,
                ResearchTechTreeProjection.Direction.REQUIREMENT)));
        ResearchTechTreeProjection ammo = catalog.projection(Domain.AMMO).orElseThrow();
        assertTrue(ammo.containsBoundaryLink(link(
                ResearchTechTreeClientFixture.AMMO,
                ResearchTechTreeClientFixture.WEAPON_UPGRADE,
                Domain.WEAPONS,
                ResearchTechTreeProjection.Direction.UNLOCK)));

        ResearchTechTreeRelationshipIndex relationships = catalog.relationships();
        assertEquals(List.of(
                new ResearchTechTreeRelationshipIndex.Relationship(
                        ResearchTechTreeClientFixture.WEAPON_UPGRADE,
                        Domain.WEAPONS,
                        ResearchTechTreeClientFixture.SCOPE,
                        Domain.ATTACHMENTS),
                new ResearchTechTreeRelationshipIndex.Relationship(
                        ResearchTechTreeClientFixture.AMMO,
                        Domain.AMMO,
                        ResearchTechTreeClientFixture.WEAPON_UPGRADE,
                        Domain.WEAPONS)),
                relationships.relationships());
        assertEquals(List.of(
                new ResearchTechTreeRelationshipIndex.NavigationTarget(
                        ResearchTechTreeClientFixture.AMMO,
                        Domain.AMMO,
                        ResearchTechTreeProjection.Direction.REQUIREMENT),
                new ResearchTechTreeRelationshipIndex.NavigationTarget(
                        ResearchTechTreeClientFixture.SCOPE,
                        Domain.ATTACHMENTS,
                        ResearchTechTreeProjection.Direction.UNLOCK)),
                relationships.navigationFrom(
                        Domain.WEAPONS,
                        ResearchTechTreeClientFixture.WEAPON_UPGRADE));
        assertEquals(1, relationships.requirementsOf(
                Domain.WEAPONS,
                ResearchTechTreeClientFixture.WEAPON_UPGRADE).size());
        assertEquals(1, relationships.unlocksFrom(
                Domain.WEAPONS,
                ResearchTechTreeClientFixture.WEAPON_UPGRADE).size());
        assertTrue(relationships.navigationTo(
                Domain.WEAPONS,
                ResearchTechTreeClientFixture.WEAPON_UPGRADE,
                Domain.ATTACHMENTS,
                ResearchTechTreeClientFixture.SCOPE).isPresent());
        assertThrows(UnsupportedOperationException.class, () ->
                relationships.relationships().clear());

        ResearchTechTreeProjection.Placement upgrade = weapons.placement(
                ResearchTechTreeClientFixture.WEAPON_UPGRADE).orElseThrow();
        assertEquals(Tier.BASIC, upgrade.tier());
        assertEquals(0, upgrade.laneOrder());
        assertEquals(0, upgrade.siblingOrder());
        assertTrue(upgrade.rating().isPresent());
    }

    @Test
    void omitsLinksToDisclosedButUnplacedNodesAndDetectsStateOnlyUpdates() {
        ResearchTechTreeProjectionCatalog withoutAmmo =
                ResearchTechTreeProjectionBuilder.build(
                        ResearchTechTreeClientFixture.publication(
                                java.util.Set.of(Domain.WEAPONS, Domain.ATTACHMENTS)));
        ResearchTechTreeProjection weapons = withoutAmmo.projection(
                Domain.WEAPONS).orElseThrow();

        assertTrue(withoutAmmo.domainOf(ResearchTechTreeClientFixture.AMMO).isEmpty());
        assertEquals(1, weapons.boundaryLinks().size());
        assertFalse(weapons.boundaryLinks().stream().anyMatch(link ->
                link.remoteNodeId().equals(ResearchTechTreeClientFixture.AMMO)));
        assertEquals(1, withoutAmmo.relationships().relationships().size());
        assertTrue(withoutAmmo.relationships().navigationFrom(
                Domain.WEAPONS,
                ResearchTechTreeClientFixture.WEAPON_UPGRADE).stream()
                .noneMatch(target -> target.remoteNodeId().equals(
                        ResearchTechTreeClientFixture.AMMO)));

        ResearchTechTreeProjectionCatalog initial =
                ResearchTechTreeProjectionBuilder.build(
                        ResearchTechTreeClientFixture.publication());
        ResearchTechTreeProjectionCatalog stateOnly =
                ResearchTechTreeProjectionBuilder.build(
                        ResearchTechTreeClientFixture.publication(
                                java.util.Set.of(Domain.values()),
                                ResearchTreeGraph.Availability.CONTENT_UNAVAILABLE));
        assertTrue(initial.hasSameTopology(stateOnly));
        assertEquals(ResearchTreeGraph.Availability.CONTENT_UNAVAILABLE,
                stateOnly.projection(Domain.WEAPONS).orElseThrow().graph()
                        .node(ResearchTechTreeClientFixture.WEAPON_UPGRADE)
                        .orElseThrow().availability());
    }

    @Test
    void emptyPublicationUsesTheCanonicalEmptyCatalog() {
        assertSame(ResearchTechTreeProjectionCatalog.EMPTY,
                ResearchTechTreeProjectionBuilder.build(ResearchTreePublication.EMPTY));
        assertFalse(ResearchTechTreeProjectionCatalog.EMPTY.available());
        assertSame(ResearchTechTreeRelationshipIndex.EMPTY,
                ResearchTechTreeProjectionCatalog.EMPTY.relationships());
        assertEquals(Optional.empty(),
                ResearchTechTreeProjectionCatalog.EMPTY.projection(Domain.WEAPONS));
    }

    @Test
    void rejectsRelationshipMetadataWhenOneReciprocalEndpointIsMissing() {
        ResearchTechTreeProjectionCatalog valid = ResearchTechTreeProjectionBuilder.build(
                ResearchTechTreeClientFixture.publication());
        ResearchTechTreeProjection attachments = valid.projection(
                Domain.ATTACHMENTS).orElseThrow();
        ResearchTechTreeProjection brokenAttachments = new ResearchTechTreeProjection(
                attachments.domain(),
                attachments.presentation(),
                attachments.graph(),
                attachments.placements(),
                List.of());

        assertThrows(IllegalArgumentException.class, () ->
                new ResearchTechTreeProjectionCatalog(
                        valid.presentation(),
                        List.of(
                                valid.projection(Domain.WEAPONS).orElseThrow(),
                                brokenAttachments,
                                valid.projection(Domain.AMMO).orElseThrow())));
    }

    private static ResearchTechTreeProjection.BoundaryLink link(
            net.minecraft.resources.ResourceLocation local,
            net.minecraft.resources.ResourceLocation remote,
            Domain remoteDomain,
            ResearchTechTreeProjection.Direction direction) {
        return new ResearchTechTreeProjection.BoundaryLink(
                local, remote, remoteDomain, direction);
    }
}
