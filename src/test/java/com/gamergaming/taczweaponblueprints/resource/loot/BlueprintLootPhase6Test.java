package com.gamergaming.taczweaponblueprints.resource.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class BlueprintLootPhase6Test {

    @Test
    void summarizesActiveAndFallbackSnapshots() {
        BlueprintLootDiagnostics.Summary empty = BlueprintLootDiagnostics.summarize(null, -4);
        assertEquals(0, empty.catalogSize());
        assertFalse(empty.active());
        assertFalse(empty.ownsDistribution());
        assertFalse(empty.globallyDisabled());

        BlueprintLootSnapshot snapshot = BlueprintLootSnapshot.create(
                Map.of(id("test:tag"), new BlueprintLootTag(1, List.of(id("test:item")))),
                Map.of(id("test:pool"), pool()),
                Map.of(
                        id("test:enabled"), rule(true, List.of(id("test:loot")), Optional.empty(), Optional.empty()),
                        id("test:disabled"), rule(false, List.of(id("test:disabled_loot")), Optional.empty(), Optional.empty())));
        BlueprintLootDiagnostics.Summary summary = BlueprintLootDiagnostics.summarize(snapshot, 17);

        assertEquals(1, summary.tagCount());
        assertEquals(1, summary.poolCount());
        assertEquals(2, summary.ruleCount());
        assertEquals(1, summary.enabledRuleCount());
        assertEquals(1, summary.exactBindingCount());
        assertEquals(17, summary.catalogSize());
        assertTrue(summary.active());
        assertTrue(summary.ownsDistribution());
        assertFalse(summary.globallyDisabled());
    }

    @Test
    void inspectionExplainsTargetsPredicatesCandidatesAndOrdering() {
        ResourceLocation table = id("minecraft:chests/simple_dungeon");
        BlueprintLootTableSelector tableSelector = new BlueprintLootTableSelector(
                List.of("minecraft"), List.of("chests/"));
        BlueprintLootRulePredicate netherLuck = new BlueprintLootRulePredicate(
                List.of(id("minecraft:the_nether")), Optional.of(1.0f), Optional.of(2.0f));
        BlueprintLootSnapshot snapshot = BlueprintLootSnapshot.create(
                Map.of(id("test:pool"), pool()),
                Map.of(
                        id("test:z_global"), rule(false, List.of(), Optional.empty(), Optional.empty()),
                        id("test:m_active"), rule(
                                true,
                                List.of(table),
                                Optional.of(tableSelector),
                                Optional.of(netherLuck)),
                        id("test:a_disabled"), rule(
                                false,
                                List.of(table),
                                Optional.empty(),
                                Optional.empty())));
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                id("tacz:ak47"), blueprint(id("tacz:ak47"), "rifle"),
                id("tacz:ammo/9mm"), blueprint(id("tacz:ammo/9mm"), "ammo"));

        BlueprintLootDiagnostics.TableReport report = BlueprintLootDiagnostics.inspect(
                snapshot,
                table,
                id("minecraft:overworld"),
                1.5f,
                catalog);

        assertTrue(report.dynamicallyOwned());
        assertEquals(
                List.of(id("test:a_disabled"), id("test:m_active"), id("test:z_global")),
                report.rules().stream().map(BlueprintLootDiagnostics.RuleReport::ruleId).toList());
        assertEquals(BlueprintLootDiagnostics.TargetMatch.EXACT, report.rules().get(0).targetMatch());
        assertEquals(BlueprintLootDiagnostics.TargetMatch.EXACT_AND_SELECTOR, report.rules().get(1).targetMatch());
        assertFalse(report.rules().get(1).predicateMatches());
        assertEquals(1, report.rules().get(1).catalogCandidates());
        assertEquals(BlueprintLootDiagnostics.TargetMatch.GLOBAL_DISABLE, report.rules().get(2).targetMatch());
        assertEquals(0, report.contextEligibleRuleCount());
    }

    @Test
    void inspectionReportsRulesRunnableInTheCurrentContext() {
        ResourceLocation table = id("minecraft:chests/bastion_treasure");
        BlueprintLootRulePredicate predicate = new BlueprintLootRulePredicate(
                List.of(id("minecraft:the_nether")), Optional.of(1.0f), Optional.of(2.0f));
        BlueprintLootSnapshot snapshot = BlueprintLootSnapshot.create(
                Map.of(id("test:pool"), pool()),
                Map.of(id("test:rule"), rule(
                        true,
                        List.of(table),
                        Optional.empty(),
                        Optional.of(predicate))));

        BlueprintLootDiagnostics.TableReport report = BlueprintLootDiagnostics.inspect(
                snapshot,
                table,
                id("minecraft:the_nether"),
                2.0f,
                Map.of(id("tacz:ak47"), blueprint(id("tacz:ak47"), "rifle")));

        assertEquals(1, report.rules().size());
        assertTrue(report.rules().get(0).contextEligible());
        assertEquals(1, report.contextEligibleRuleCount());
    }

    @Test
    void inspectionIsSafeForMissingInputsAndUntargetedTables() {
        BlueprintLootDiagnostics.TableReport missing = BlueprintLootDiagnostics.inspect(
                null, null, null, 0.0f, null);
        assertFalse(missing.dynamicallyOwned());
        assertTrue(missing.rules().isEmpty());

        BlueprintLootSnapshot snapshot = BlueprintLootSnapshot.create(
                Map.of(id("test:pool"), pool()),
                Map.of(id("test:rule"), rule(
                        true,
                        List.of(id("test:owned")),
                        Optional.empty(),
                        Optional.empty())));
        BlueprintLootDiagnostics.TableReport untargeted = BlueprintLootDiagnostics.inspect(
                snapshot, id("test:legacy"), id("minecraft:overworld"), 0.0f, Map.of());
        assertFalse(untargeted.dynamicallyOwned());
        assertTrue(untargeted.rules().isEmpty());
    }

    @Test
    void poolInspectionReportsComposedAndCatalogCounts() {
        BlueprintLootSnapshot snapshot = BlueprintLootSnapshot.create(
                Map.of(id("test:pool"), pool()),
                Map.of());
        BlueprintLootDiagnostics.PoolReport report = BlueprintLootDiagnostics.inspectPool(
                snapshot,
                id("test:pool"),
                Map.of(
                        id("tacz:ak47"), blueprint(id("tacz:ak47"), "rifle"),
                        id("tacz:ammo/9mm"), blueprint(id("tacz:ammo/9mm"), "ammo")));

        assertTrue(report.exists());
        assertEquals(0, report.composedEntryCount());
        assertEquals(1, report.selectorCount());
        assertEquals(1, report.catalogCandidateCount());
        assertFalse(BlueprintLootDiagnostics.inspectPool(snapshot, id("test:missing"), Map.of()).exists());
    }

    private static BlueprintLootPool pool() {
        return new BlueprintLootPool(
                2,
                List.of(),
                List.of(),
                List.of(),
                List.of(new BlueprintCatalogSelector(
                        List.of("tacz"), List.of("rifle"), List.of(), List.of(), 1.0f)));
    }

    private static BlueprintLootRule rule(
            boolean enabled,
            List<ResourceLocation> tables,
            Optional<BlueprintLootTableSelector> selector,
            Optional<BlueprintLootRulePredicate> predicate) {
        return new BlueprintLootRule(
                2,
                enabled,
                id("test:pool"),
                tables,
                Optional.empty(),
                Optional.empty(),
                selector,
                predicate);
    }

    private static BlueprintData blueprint(ResourceLocation id, String itemType) {
        return new BlueprintData(
                id.toString(),
                "item.test.name",
                "item.test.tooltip",
                id("test:recipe/" + id.getPath()),
                null,
                itemType,
                id("test:display/" + itemType));
    }

    private static ResourceLocation id(String value) {
        ResourceLocation result = ResourceLocation.tryParse(value);
        if (result == null) {
            throw new IllegalArgumentException(value);
        }
        return result;
    }
}
