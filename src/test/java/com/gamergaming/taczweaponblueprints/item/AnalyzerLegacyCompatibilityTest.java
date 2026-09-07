package com.gamergaming.taczweaponblueprints.item;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Frozen pre-tiered inputs exercise readers independently of current writers. */
class AnalyzerLegacyCompatibilityTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void versionZeroKnowledgeStillLoadsWithoutInventingPointsOrFragments() throws Exception {
        var data = new PlayerRecipeData();
        data.deserializeNBT(nbt("player-v0.snbt"));
        assertEquals(Set.of("tacz:ak47"), data.getLearnedBlueprints());
        assertEquals(Set.of("tacz:ak47"), data.getDiscoveredBlueprints());
        assertEquals(Set.of("tacz:gun/ak47"), data.getLearnedRecipes());
        assertEquals(0, data.getResearchPoints());
        assertTrue(data.getArchivedBlueprintFragments().isEmpty());
    }

    @Test
    void versionFourArchivePreservesKnownUnknownAndRemovedTargetsAcrossResave() throws Exception {
        var data = new PlayerRecipeData();
        data.deserializeNBT(nbt("player-v4.snbt"));
        var restored = new PlayerRecipeData();
        restored.deserializeNBT(data.serializeNBT());
        for (var candidate : new PlayerRecipeData[]{data, restored}) {
            assertEquals(125, candidate.getResearchPoints());
            assertEquals(Set.of("tacz:ak47"), candidate.getLearnedBlueprints());
            assertEquals(Set.of("tacz:ak47", "tacz:m4a1"), candidate.getDiscoveredBlueprints());
            assertEquals(Map.of("tacz:ak47", 5, "tacz:m4a1", 17, "removed_pack:rifle", 11),
                    candidate.getArchivedBlueprintFragments());
            assertEquals(Map.of("test:trial_wins", 3), candidate.getProgressionCriteria());
            assertEquals(1, candidate.getRecentUnlockBatches().size());
            assertEquals("tacz:ak47", candidate.getRecentUnlockBatches().get(0).targetBlueprintId());
        }
    }

    @Test
    void targetOnlyFragmentKeepsItsLegacyIdentity() throws Exception {
        var tag = nbt("item-tags.snbt").getCompound("fragment");
        assertEquals(new ResourceLocation("tacz:ak47"), BlueprintFragmentItem.getTarget(tag).orElseThrow());
        assertEquals(1, tag.size());
    }

    @Test
    void oldBlueprintsRetainTheirDifferentLearningAndRecyclingContracts() throws Exception {
        var tags = nbt("item-tags.snbt");
        var legacy = tags.getCompound("legacy_blueprint");
        assertTrue(BlueprintProvenance.fromTag(legacy).isEmpty());
        assertTrue(BlueprintProvenance.allowsRecycling(legacy));
        assertEquals(PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES,
                BlueprintProvenance.learningMode(legacy, PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES));
        assertEquals(PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                BlueprintProvenance.learningMode(legacy, PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES));
        for (String name : new String[]{"reverse_blueprint", "reconstructed_blueprint", "found_blueprint"}) {
            var tag = tags.getCompound(name);
            assertEquals(name.equals("found_blueprint"), BlueprintProvenance.allowsRecycling(tag));
            assertEquals(name.equals("reconstructed_blueprint")
                            ? PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES
                            : PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                    BlueprintProvenance.learningMode(tag, PhysicalBlueprintLearningMode.DISABLED));
        }
    }

    @Test
    void acquisitionEvidenceCannotBeInferredForOldUnmarkedWeapons() throws Exception {
        var tags = nbt("item-tags.snbt");
        assertTrue(PhysicalWeaponProvenance.fromTag(tags.getCompound("unknown_weapon")).isEmpty());
        assertFalse(PhysicalWeaponProvenance.fromTag(tags.getCompound("crafted_weapon")).orElseThrow().verifiedLoot());
        assertTrue(PhysicalWeaponProvenance.fromTag(tags.getCompound("found_weapon")).orElseThrow().verifiedLoot());
    }

    @Test
    void shippedVersionFourProfileKeepsKnownCopiesAndDiscountFragments() throws Exception {
        var profile = BlueprintResearchProfile.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString(read("research-profile-v4.json"))).getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        assertTrue(profile.reverseEngineering().enabled());
        assertTrue(profile.reverseEngineering().allowKnown());
        assertTrue(profile.reverseEngineering().allowModified());
        assertEquals(0, profile.reverseEngineering().cost().points());
        assertFalse(profile.reverseEngineering().outputRecyclable());
        assertEquals(PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                profile.reverseEngineering().physicalBlueprintLearningMode());
        assertEquals(com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy
                .CompletionMode.TARGETED_RESEARCH_BOOST, profile.progression().fragments().mode());
        assertEquals(1, profile.progression().fragments().learnedTargetResearchPoints());
    }

    private static CompoundTag nbt(String name) throws Exception {
        return TagParser.parseTag(read(name));
    }

    private static String read(String name) throws IOException {
        try (var input = AnalyzerLegacyCompatibilityTest.class.getResourceAsStream("/analyzer-compatibility/" + name)) {
            if (input == null) throw new IOException("Missing compatibility fixture " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
