package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;

import net.minecraft.resources.ResourceLocation;

class AutomaticWeaponLayerPlannerTest {
    @Test
    void dynamicLayersReserveFoundationAndRemainBoundedStatOrderedAndBandOptional() {
        AutomaticWeaponProgressionBand early = new AutomaticWeaponProgressionBand(
                id("test:early"), 49, "Early", Optional.empty());
        AutomaticWeaponProgressionBand late = new AutomaticWeaponProgressionBand(
                id("test:late"), 100, "Late", Optional.of("tree.band.late"));
        AutomaticWeaponPlacementPolicy policy = new AutomaticWeaponPlacementPolicy(
                3,
                60,
                AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                2,
                4,
                AutomaticWeaponPlacementPolicy.LayeringStrategy.DYNAMIC_STAT_LAYERS,
                9,
                List.of(early, late));
        Map<String, AutomaticWeaponPlacementProposal> forward = proposals(28);
        List<Map.Entry<String, AutomaticWeaponPlacementProposal>> entries =
                new ArrayList<>(forward.entrySet());
        java.util.Collections.reverse(entries);
        Map<String, AutomaticWeaponPlacementProposal> reverse = new LinkedHashMap<>();
        entries.forEach(entry -> reverse.put(entry.getKey(), entry.getValue()));

        AutomaticWeaponLayerPlanner planner = new AutomaticWeaponLayerPlanner();
        Map<String, AutomaticWeaponPlacementProposal> first = planner.assign(forward, policy);
        Map<String, AutomaticWeaponPlacementProposal> second = planner.assign(reverse, policy);

        assertEquals(first, second);
        Map<Integer, Long> widths = first.values().stream().collect(
                java.util.stream.Collectors.groupingBy(
                        value -> value.progressionCoordinate().rank(),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()));
        assertEquals(List.of(2L, 9L, 9L, 8L), widths.values().stream().toList());
        assertTrue(widths.values().stream().allMatch(width -> width <= 9));
        List<AutomaticWeaponPlacementProposal> ordered = first.values().stream()
                .sorted(java.util.Comparator
                        .comparingInt((AutomaticWeaponPlacementProposal value) ->
                                value.progressionCoordinate().rank())
                        .thenComparingInt(
                                AutomaticWeaponPlacementProposal::mechanicalScore))
                .toList();
        for (int index = 1; index < ordered.size(); index++) {
            assertTrue(ordered.get(index - 1).mechanicalScore()
                    <= ordered.get(index).mechanicalScore());
        }
        first.values().forEach(proposal -> assertEquals(
                proposal.mechanicalScore() <= 49 ? Optional.of(early.id()) : Optional.of(late.id()),
                proposal.progressionCoordinate().bandId()));

        AutomaticWeaponPlacementPolicy bandless = new AutomaticWeaponPlacementPolicy(
                3,
                60,
                AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                2,
                4,
                AutomaticWeaponPlacementPolicy.LayeringStrategy.DYNAMIC_STAT_LAYERS,
                9,
                List.of());
        assertTrue(planner.assign(forward, bandless).values().stream()
                .allMatch(value -> value.progressionCoordinate().bandId().isEmpty()));
        AutomaticWeaponPlacementPolicy oneNodeLegacyCompatibility =
                new AutomaticWeaponPlacementPolicy(
                        3,
                        60,
                        AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                        2,
                        4,
                        AutomaticWeaponPlacementPolicy.LayeringStrategy.DYNAMIC_STAT_LAYERS,
                        1,
                        List.of());
        assertTrue(planner.assign(proposals(3), oneNodeLegacyCompatibility).values().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        value -> value.progressionCoordinate().rank(),
                        java.util.stream.Collectors.counting()))
                .values().stream().allMatch(width -> width == 1));
        AutomaticWeaponPlacementPolicy threeFoundations =
                new AutomaticWeaponPlacementPolicy(
                        3,
                        60,
                        AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                        2,
                        4,
                        AutomaticWeaponPlacementPolicy.LayeringStrategy.DYNAMIC_STAT_LAYERS,
                        9,
                        List.of(),
                        3);
        assertEquals(List.of(3L, 9L), planner.assign(
                        proposals(12), threeFoundations).values().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        value -> value.progressionCoordinate().rank(),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()))
                .values().stream().toList());

        Map<String, AutomaticWeaponPlacementProposal> extended = new LinkedHashMap<>(forward);
        String addedId = "addon:weapon_added";
        extended.put(addedId, new AutomaticWeaponPlacementProposal(
                addedId,
                100,
                100,
                new ProgressionPosition(Tier.APEX, 2, Long.MAX_VALUE - 1),
                3,
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                List.of()));
        Map<String, AutomaticWeaponPlacementProposal> expanded =
                planner.assign(extended, policy);
        first.forEach((id, proposal) -> assertEquals(
                proposal.progressionCoordinate(),
                expanded.get(id).progressionCoordinate()));
    }

    private static Map<String, AutomaticWeaponPlacementProposal> proposals(int count) {
        Map<String, AutomaticWeaponPlacementProposal> result = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            int score = index * ResearchTechTreeContract.SCORE_MAX / (count - 1);
            String id = "addon:weapon_" + index;
            long order = Math.multiplyExact(score, 1L << 56) + index;
            ProgressionPosition position = new ProgressionPosition(
                    Tier.forScore(score),
                    ResearchTechTreeContract.levelForScore(score, 3),
                    order);
            result.put(id, new AutomaticWeaponPlacementProposal(
                    id,
                    score,
                    100,
                    position,
                    3,
                    ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                    ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                    ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                    List.of()));
        }
        return result;
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
