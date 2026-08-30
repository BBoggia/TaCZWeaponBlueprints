package com.gamergaming.taczweaponblueprints.resource.award;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class DefaultResearchPointEconomyTest {
    private static final String ROOT =
            "/data/taczweaponblueprints/taczweaponblueprints/research_point_awards/";
    private static final int DEFAULT_CATALOG_SIZE = 172;
    private static final int DEFAULT_ENABLED_WEAPON_TREE_COST = 418;

    private static final List<String> DEFINITIONS = List.of(
            "advancements/acquire_hardware",
            "advancements/blaze_rod",
            "advancements/diamonds",
            "advancements/enter_end",
            "advancements/fortress",
            "advancements/kill_dragon",
            "blueprints/first_discovery",
            "milestones/discovered_10",
            "milestones/discovered_25",
            "milestones/learned_5",
            "milestones/learned_15",
            "milestones/learned_30",
            "research_data/note",
            "research_data/report",
            "research_data/dossier");

    @Test
    void packagedDefaultsDecodeAsOneConflictFreeSnapshot() {
        ResearchPointAwardSnapshot snapshot = packagedSnapshot();

        assertEquals(15, snapshot.definitions().size());
        assertEquals(15, snapshot.enabledDefinitionCount());
        assertEquals(6, snapshot.bindingsByTrigger().get(
                ResearchPointAwardTrigger.Type.ADVANCEMENT_COMPLETED).size());
        assertEquals(5, snapshot.bindingsByTrigger().get(
                ResearchPointAwardTrigger.Type.BLUEPRINT_MILESTONE).size());
        assertEquals(3, snapshot.bindingsByTrigger().get(
                ResearchPointAwardTrigger.Type.INVENTORY_TURN_IN).size());
        assertEquals(1, snapshot.bindingsByTrigger().get(
                ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED).size());
        assertFalse(snapshot.bindingsByTrigger().containsKey(
                ResearchPointAwardTrigger.Type.ENTITY_KILLED));
        assertFalse(snapshot.bindingsByTrigger().containsKey(
                ResearchPointAwardTrigger.Type.INTEGRATION));
        assertTrue(snapshot.budgets().isEmpty());
    }

    @Test
    void finiteIncomeSupplementsRatherThanPaysForTheDefaultTree() {
        ResearchPointAwardSnapshot snapshot = packagedSnapshot();
        int advancementAndMilestonePoints = snapshot.definitions().values().stream()
                .filter(value -> value.trigger().type()
                        == ResearchPointAwardTrigger.Type.ADVANCEMENT_COMPLETED
                        || value.trigger().type()
                        == ResearchPointAwardTrigger.Type.BLUEPRINT_MILESTONE)
                .mapToInt(value -> value.reward().points())
                .sum();
        ResearchPointAwardDefinition discovery = snapshot.definitions().get(
                id("taczweaponblueprints:blueprints/first_discovery"));

        assertEquals(46, advancementAndMilestonePoints);
        assertEquals(1, discovery.reward().points());
        assertEquals(ResearchPointAwardReward.Overflow.REQUIRE_FULL,
                discovery.reward().overflow());
        assertEquals(ResearchPointAwardRepeat.Type.ONCE_PER_TARGET, discovery.repeat().type());
        assertTrue(discovery.trigger().retroactive());

        snapshot.definitions().values().stream()
                .filter(value -> value.repeat().finite())
                .forEach(value -> {
                    assertEquals(ResearchPointAwardReward.Overflow.REQUIRE_FULL,
                            value.reward().overflow());
                    assertTrue(value.trigger().retroactive());
                });

        int maximumPinnedFiniteIncome = advancementAndMilestonePoints
                + discovery.reward().points() * DEFAULT_CATALOG_SIZE;
        assertEquals(218, maximumPinnedFiniteIncome);
        assertTrue(maximumPinnedFiniteIncome < DEFAULT_ENABLED_WEAPON_TREE_COST,
                "packaged finite awards should supplement rather than pay for the enabled tree");
        assertEquals(5_215L, Math.round(
                maximumPinnedFiniteIncome * 10_000.0
                        / DEFAULT_ENABLED_WEAPON_TREE_COST));

        Map<ResourceLocation, ResearchPointAwardBlueprintFacts> facts = new LinkedHashMap<>();
        for (int index = 0; index < DEFAULT_CATALOG_SIZE; index++) {
            ResourceLocation id = id("test:blueprint_" + index);
            facts.put(id, new ResearchPointAwardBlueprintFacts(
                    id, Set.of(), "pistol", BlueprintKind.GUN));
        }
        ResearchPointAwardEconomyProjection.Projection projection =
                ResearchPointAwardEconomyProjection.project(
                        snapshot, facts, id("taczweaponblueprints:default"));
        assertEquals(12, projection.finiteDefinitionCount());
        assertEquals(3, projection.renewableDefinitionCount());
        assertEquals(maximumPinnedFiniteIncome, projection.maximumFinitePoints());
        assertEquals(DEFAULT_CATALOG_SIZE, projection.finitePointsByTrigger().get(
                ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED));
    }

    @Test
    void researchDataUsesTheAuditedOneThreeSixExchangeWithoutPartialConsumption() {
        ResearchPointAwardSnapshot snapshot = packagedSnapshot();
        Map<ResourceLocation, Integer> expected = Map.of(
                id("taczweaponblueprints:research_note"), 1,
                id("taczweaponblueprints:research_report"), 3,
                id("taczweaponblueprints:research_dossier"), 6);
        Map<ResourceLocation, Integer> observed = new LinkedHashMap<>();

        snapshot.bindingsByTrigger().get(
                ResearchPointAwardTrigger.Type.INVENTORY_TURN_IN).forEach(binding -> {
                    ResearchPointAwardDefinition definition = binding.definition();
                    assertEquals(ResearchPointAwardReward.Overflow.REQUIRE_FULL,
                            definition.reward().overflow());
                    assertEquals(ResearchPointAwardRepeat.Type.UNLIMITED,
                            definition.repeat().type());
                    assertEquals(1, definition.trigger().target().orElseThrow().ids().size());
                    observed.put(
                            definition.trigger().target().orElseThrow().ids().get(0),
                            definition.reward().points());
                });

        assertEquals(expected, observed);
    }

    private static ResearchPointAwardSnapshot packagedSnapshot() {
        Map<ResourceLocation, ResearchPointAwardDefinition> definitions = new LinkedHashMap<>();
        for (String path : DEFINITIONS) {
            JsonElement json = readJson(ROOT + path + ".json");
            ResearchPointAwardDefinition definition = ResearchPointAwardDefinition.CODEC
                    .parse(JsonOps.INSTANCE, json)
                    .result()
                    .orElseThrow(() -> new AssertionError("Invalid packaged award " + path));
            definitions.put(id("taczweaponblueprints:" + path), definition);
        }
        return ResearchPointAwardSnapshot.create(definitions);
    }

    private static JsonElement readJson(String path) {
        try (InputStream stream = DefaultResearchPointEconomyTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new AssertionError("Missing packaged award " + path);
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to read packaged award " + path, exception);
        }
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
