package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.DuplicateBlueprintPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeBuilder;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisitePlan;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisitePlanner;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementProposal;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponCandidatePositioner;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateClassifier;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateSnapshot;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootTag;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

/** Characterizes the complete format-1 seam that the rank-based redesign must migrate. */
public class ResearchTreeV1RedesignBaselineTest {
    private static final String FIXTURE = "fixtures/research-tree-v1-redesign-baseline.json";
    private static final ResourceLocation AUTOMATIC_PROFILE_ID = id("baseline:automatic");

    @AfterEach
    void clearResolverCache() {
        BlueprintResearchPolicyResolver.clearCache();
    }

    @Test
    void fixtureDecodesTheStrictLegacyShellAndCustomEntryPoints() throws Exception {
        Baseline baseline = loadBaseline();
        BlueprintResearchProfile profile = baseline.snapshot().profiles().get(baseline.profileId());
        ResearchTechTreeDefinition tree = baseline.snapshot().techTrees().get(baseline.treeId());

        assertEquals(1, baseline.source().get("format").getAsInt());
        assertEquals(1, profile.format());
        assertEquals(baseline.treeId(), profile.techTree().orElseThrow());
        assertEquals(List.of(id("baseline:root")), profile.entryPointCandidates());
        assertEquals(List.of(id("baseline:attachment")),
                profile.techEntryPointCandidates().get(Domain.ATTACHMENTS));
        assertEquals(List.of(id("baseline:ammo")),
                profile.techEntryPointCandidates().get(Domain.AMMO));
        assertEquals(List.of(Tier.values()), tree.tiers().stream()
                .map(ResearchTechTreeDefinition.TierDefinition::tier)
                .toList());
        assertEquals(List.of(Domain.WEAPONS, Domain.ATTACHMENTS, Domain.AMMO),
                tree.domains().stream()
                        .map(ResearchTechTreeDefinition.DomainDefinition::domain)
                        .toList());
        assertEquals(AutomaticPlacementMode.CONNECTED, baseline.automaticProfile().mode());
        assertEquals(3, baseline.automaticProfile().levelsPerTier());
        assertEquals(2, baseline.automaticProfile().maxGeneratedPrerequisites());
        assertEquals(4, baseline.automaticProfile().mergeInterval());
        assertEquals(7, baseline.snapshot().rules().size());
        assertEquals(4, baseline.snapshot().techTreeEntryBundles().size());
    }

    @Test
    void legacyPlacementAndPolicyAuthorityMatchesTheGoldenFixture() throws Exception {
        Baseline baseline = loadBaseline();
        JsonObject expected = baseline.source().getAsJsonObject("expected");

        for (Map.Entry<String, JsonElement> entry
                : expected.getAsJsonObject("placement_origins").entrySet()) {
            ResourceLocation blueprintId = id(entry.getKey());
            String expectedOrigin = entry.getValue().getAsString();
            Optional<ResearchTechTreePlacementResolver.Placement> placement =
                    ResearchTechTreePlacementResolver.resolve(
                            baseline.snapshot(),
                            baseline.treeId(),
                            blueprintId,
                            baseline.catalog().get(blueprintId))
                            .placement();
            String actualOrigin = placement
                    .map(value -> value.origin().name().toLowerCase(Locale.ROOT))
                    .orElse("unplaced");
            assertEquals(expectedOrigin, actualOrigin, blueprintId.toString());
        }

        JsonObject expectedRules = expected.getAsJsonObject("policy_rules");
        JsonObject expectedPoints = expected.getAsJsonObject("policy_points");
        for (ResourceLocation blueprintId : baseline.catalog().keySet()) {
            BlueprintResearchPolicyDefinition policy =
                    BlueprintResearchPolicyResolver.definitionFor(
                            baseline.snapshot(),
                            baseline.catalog(),
                            baseline.profileId(),
                            blueprintId);
            assertEquals(id(expectedRules.get(blueprintId.toString()).getAsString()),
                    policy.ruleId().orElseThrow(), blueprintId.toString());
            assertEquals(expectedPoints.get(blueprintId.toString()).getAsInt(),
                    policy.researchCost().points(), blueprintId.toString());
            assertTrue(policy.treeEnabled(), blueprintId.toString());
            assertTrue(policy.researchEnabled(), blueprintId.toString());
        }

        assertEquals(BlueprintResearchTarget.MatchSpecificity.EXACT,
                policy(baseline, "baseline:root").specificity());
        assertEquals(BlueprintResearchTarget.MatchSpecificity.EXACT,
                policy(baseline, "baseline:upgrade").specificity());
        assertEquals(BlueprintResearchTarget.MatchSpecificity.TAG,
                policy(baseline, "baseline:tagged").specificity());
        assertEquals(BlueprintResearchTarget.MatchSpecificity.SELECTOR,
                policy(baseline, "baseline:selector").specificity());
        assertEquals(BlueprintResearchTarget.MatchSpecificity.EXACT,
                policy(baseline, "baseline:attachment").specificity());
        assertEquals(BlueprintResearchTarget.MatchSpecificity.SELECTOR,
                policy(baseline, "baseline:ammo").specificity());
        assertEquals(List.of(id("baseline:root")),
                policy(baseline, "baseline:upgrade").prerequisites());

        ResearchTechTreePlacementResolver.Placement root = placement(baseline, "baseline:root");
        ResearchTechTreePlacementResolver.Placement upgrade =
                placement(baseline, "baseline:upgrade");
        assertEquals(Tier.STARTER, root.tier());
        assertEquals(Tier.STARTER, upgrade.tier());
        assertEquals(0, root.level());
        assertEquals(0, upgrade.level());
        assertTrue(root.order() < upgrade.order(),
                "the migration fixture must retain a same-tier/same-level prerequisite");
        assertEquals(root.progressionCoordinate().rank(), upgrade.progressionCoordinate().rank(),
                "format-1 decoding must retain the shared initial rank");
        assertEquals(
                baseline.snapshot().techTreeProgressionFor(
                        baseline.profileId(), id("baseline:root")).orElseThrow().rank() + 1,
                baseline.snapshot().techTreeProgressionFor(
                        baseline.profileId(), id("baseline:upgrade")).orElseThrow().rank(),
                "the dependent must be topologically lifted without rewriting the fixture");
        assertEquals(
                List.of(id("baseline:root")),
                policy(baseline, "baseline:upgrade").prerequisites());
    }

    @Test
    void automaticAuthorityMakesEveryGunEligible()
            throws Exception {
        Baseline baseline = loadBaseline();
        AutomaticBaseline automatic = automaticBaseline(baseline);
        JsonObject expected = baseline.source().getAsJsonObject("expected")
                .getAsJsonObject("automatic");

        assertEquals(6, automatic.candidates().catalogWeaponCount());
        assertEquals(strings(expected.getAsJsonArray("authored")),
                automatic.candidates().authoredBlueprintIds());
        assertEquals(strings(expected.getAsJsonArray("eligible")),
                automatic.candidates().eligibleProposals().keySet());
        assertEquals(strings(expected.getAsJsonArray("unplaced")),
                automatic.candidates().unplacedBlueprintIds());
        assertTrue(automatic.candidates().excludedAutomaticCandidates().isEmpty());
        assertEquals(6, automatic.candidates().automaticCandidateCount());

        AutomaticWeaponPlacementProposal fallback = automatic.candidates()
                .eligibleProposals().get("addon:fallback_gun");
        assertTrue(fallback.reviewRequired());
        assertTrue(fallback.reviewReasons().contains("unscored_fallback"));
        AutomaticWeaponPlacementProposal formerlyUnplaced = automatic.candidates()
                .eligibleProposals().get("orphan:unplaced_gun");
        assertTrue(formerlyUnplaced.reviewRequired());
        assertTrue(formerlyUnplaced.reviewReasons().contains("unscored_fallback"));
        assertFalse(automatic.prerequisites().prerequisitesFor(
                id("addon:fallback_gun")).isEmpty());
        assertTrue(automatic.prerequisites().omittedCandidates()
                .containsKey(id("orphan:unplaced_gun")));
        assertEquals(
                automatic.candidates().eligibleProposals().size(),
                automatic.prerequisites().prerequisites().size()
                        + automatic.prerequisites().omittedCandidates().size());

        var effective = ResearchTechTreePlacementResolver.resolveWithAutomatic(
                baseline.snapshot(),
                baseline.treeId(),
                id("orphan:unplaced_gun"),
                baseline.catalog().get(id("orphan:unplaced_gun")),
                automatic.candidates());
        assertTrue(effective.base().placement().isEmpty());
        assertTrue(effective.automaticProposal().isPresent());
        assertEquals(PlacementOrigin.AUTOMATIC, effective.effectiveOrigin().orElseThrow());
        assertEquals(expected.get("fingerprint").getAsString(),
                automaticFingerprint(automatic));
    }

    @Test
    void legacyPublicationHasAStableSemanticFingerprint() throws Exception {
        Baseline baseline = loadBaseline();
        AutomaticBaseline automatic = automaticBaseline(baseline);
        ResearchTreePublication publication = publicationFixture(baseline);

        assertEquals(8, publication.graph().nodes().size());
        assertEquals(6, publication.legacyGraph().nodes().size());
        assertTrue(publication.techTree().available());
        assertEquals(3, publication.techTree().domains().size());
        assertEquals(7, publication.techTree().memberCount());
        assertTrue(techMemberIds(publication).contains(id("orphan:unplaced_gun")));
        assertEquals(PlacementOrigin.AUTOMATIC,
                publication.techTree().domain(Domain.WEAPONS).orElseThrow().lanes().stream()
                        .flatMap(lane -> lane.members().stream())
                        .filter(member -> member.nodeId().equals(id("addon:fallback_gun")))
                        .findFirst().orElseThrow().origin());
        ResearchTechTreePresentation.LaneView weaponsFallback = publication.techTree()
                .domain(Domain.WEAPONS).orElseThrow().lanes().stream()
                .filter(lane -> lane.id().equals(id("baseline:weapons/general")))
                .findFirst().orElseThrow();
        assertEquals(PlacementOrigin.AUTOMATIC,
                weaponsFallback.members().stream()
                        .filter(member -> member.nodeId().equals(id("orphan:unplaced_gun")))
                        .findFirst().orElseThrow().origin());
        assertFalse(automatic.candidates().eligibleProposals()
                .containsKey("baseline:attachment"));
        assertFalse(automatic.candidates().eligibleProposals()
                .containsKey("baseline:ammo"));

        assertEquals(
                baseline.source().getAsJsonObject("expected")
                        .get("publication_fingerprint").getAsString(),
                publicationFingerprint(publication));
    }

    /** Shared with the network-package baseline without widening production packet APIs. */
    public static ResearchTreePublication publicationFixture() throws Exception {
        return publicationFixture(loadBaseline());
    }

    private static ResearchTreePublication publicationFixture(Baseline baseline) {
        AutomaticBaseline automatic = automaticBaseline(baseline);
        return ResearchTreeBuilder.buildPublication(
                baseline.catalog(),
                baseline.snapshot(),
                config(baseline.profileId()),
                new PlayerRecipeData(),
                ignored -> false,
                automatic.candidates(),
                automatic.prerequisites());
    }

    private static BlueprintResearchPolicyDefinition policy(Baseline baseline, String value) {
        return BlueprintResearchPolicyResolver.definitionFor(
                baseline.snapshot(), baseline.catalog(), baseline.profileId(), id(value));
    }

    private static ResearchTechTreePlacementResolver.Placement placement(
            Baseline baseline,
            String value) {
        ResourceLocation blueprintId = id(value);
        return ResearchTechTreePlacementResolver.resolve(
                        baseline.snapshot(),
                        baseline.treeId(),
                        blueprintId,
                        baseline.catalog().get(blueprintId))
                .placement().orElseThrow();
    }

    private static AutomaticBaseline automaticBaseline(Baseline baseline) {
        long catalogRevision = 29L;
        long researchRevision = 17L;
        AutomaticWeaponPlacementCandidateSnapshot candidates =
                AutomaticWeaponCandidatePositioner.position(
                        AutomaticWeaponPlacementCandidateClassifier.classify(
                        baseline.snapshot(),
                        researchRevision,
                        baseline.catalog(),
                        catalogRevision,
                        AutomaticWeaponEvidenceSnapshot.emptyForCatalog(catalogRevision),
                        baseline.automaticProfile()),
                        baseline.snapshot().techTrees().get(baseline.treeId()));
        AutomaticWeaponPrerequisitePlan prerequisites =
                new AutomaticWeaponPrerequisitePlanner().plan(
                        baseline.snapshot(),
                        baseline.catalog(),
                        baseline.profileId(),
                        candidates);
        return new AutomaticBaseline(candidates, prerequisites);
    }

    private static String automaticFingerprint(AutomaticBaseline automatic) throws Exception {
        StringBuilder manifest = new StringBuilder();
        manifest.append("authored|")
                .append(String.join(",", automatic.candidates().authoredBlueprintIds()))
                .append('\n');
        manifest.append("unplaced|")
                .append(String.join(",", automatic.candidates().unplacedBlueprintIds()))
                .append('\n');
        automatic.candidates().eligibleProposals().forEach((blueprintId, proposal) -> manifest
                .append("proposal|").append(blueprintId)
                .append('|').append(proposal.mechanicalScore())
                .append('|').append(proposal.confidence())
                .append('|').append(proposal.position().tier())
                .append('|').append(proposal.position().level())
                .append('|').append(proposal.position().siblingOrder())
                .append('|').append(String.join(",", proposal.reviewReasons()))
                .append('\n'));
        automatic.prerequisites().prerequisites().forEach((target, prerequisites) -> manifest
                .append("parents|").append(target)
                .append('|').append(prerequisites.stream()
                        .map(ResourceLocation::toString)
                        .collect(java.util.stream.Collectors.joining(",")))
                .append('\n'));
        return sha256(manifest.toString());
    }

    private static String publicationFingerprint(ResearchTreePublication publication)
            throws Exception {
        StringBuilder manifest = new StringBuilder();
        for (ResearchTreeGraph.Node node : publication.graph().nodes()) {
            manifest.append("node|").append(node.ordinal())
                    .append('|').append(node.blueprintId())
                    .append('|').append(node.visibility())
                    .append('|').append(node.pointCost())
                    .append('|').append(node.prerequisiteCount())
                    .append('|').append(node.availability())
                    .append('\n');
        }
        publication.graph().edges().stream()
                .sorted(java.util.Comparator
                        .comparing((ResearchTreeGraph.Edge edge) ->
                                edge.prerequisiteId().toString())
                        .thenComparing(edge -> edge.dependentId().toString()))
                .forEach(edge -> manifest.append("edge|")
                        .append(edge.prerequisiteId()).append('|')
                        .append(edge.dependentId()).append('\n'));
        publication.techTree().tiers().forEach(tier -> manifest
                .append("tier|").append(tier.tier()).append('|').append(tier.title())
                .append('\n'));
        publication.techTree().domains().forEach(domain -> {
            manifest.append("domain|").append(domain.domain()).append('|')
                    .append(domain.title()).append('\n');
            domain.lanes().forEach(lane -> {
                manifest.append("lane|").append(lane.id()).append('|')
                        .append(lane.order()).append('\n');
                lane.members().forEach(member -> manifest
                        .append("member|").append(member.nodeId())
                        .append('|').append(member.rank())
                        .append('|').append(member.siblingOrder())
                        .append('|').append(member.bandId().map(Object::toString).orElse("-"))
                        .append('|').append(member.origin())
                        .append('\n'));
            });
        });
        return sha256(manifest.toString());
    }

    private static Set<ResourceLocation> techMemberIds(ResearchTreePublication publication) {
        return publication.techTree().domains().stream()
                .flatMap(domain -> domain.lanes().stream())
                .flatMap(lane -> lane.members().stream())
                .map(ResearchTechTreePresentation.Member::nodeId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static BlueprintProgressionConfigSnapshot config(ResourceLocation profileId) {
        return new BlueprintProgressionConfigSnapshot(
                true,
                true,
                true,
                JournalVisibility.FULL,
                true,
                DuplicateBlueprintPolicy.MANUAL_RECYCLING,
                false,
                1_000_000,
                false,
                profileId);
    }

    private static Baseline loadBaseline() throws Exception {
        JsonObject source;
        try (var stream = ResearchTreeV1RedesignBaselineTest.class.getClassLoader()
                .getResourceAsStream(FIXTURE)) {
            assertTrue(stream != null, "missing " + FIXTURE);
            source = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }

        ResourceLocation profileId = id(source.get("profile_id").getAsString());
        ResourceLocation treeId = id(source.get("tree_id").getAsString());
        BlueprintResearchProfile profile = decode(
                BlueprintResearchProfile.CODEC, source.get("profile"));
        ResearchTechTreeDefinition tree = decode(
                ResearchTechTreeDefinition.CODEC, source.get("tree"));
        ResearchAutomaticPlacementProfile automaticProfile = decode(
                ResearchAutomaticPlacementProfile.CODEC, source.get("automatic_profile"));
        Map<ResourceLocation, BlueprintLootTag> tags = definitions(
                source.getAsJsonArray("tags"), BlueprintLootTag.CODEC);
        Map<ResourceLocation, BlueprintResearchRule> rules = definitions(
                source.getAsJsonArray("rules"), BlueprintResearchRule.CODEC);
        Map<ResourceLocation, ResearchTechTreeEntryBundle> bundles = definitions(
                source.getAsJsonArray("entry_bundles"), ResearchTechTreeEntryBundle.CODEC);
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                tags,
                Map.of(profileId, profile),
                rules,
                Map.of(),
                Map.of(treeId, tree),
                bundles,
                Map.of(AUTOMATIC_PROFILE_ID, automaticProfile));
        return new Baseline(
                source,
                profileId,
                treeId,
                snapshot,
                catalog(source.getAsJsonArray("catalog")),
                automaticProfile);
    }

    private static Map<ResourceLocation, BlueprintData> catalog(JsonArray definitions) {
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        for (JsonElement element : definitions) {
            JsonObject definition = element.getAsJsonObject();
            ResourceLocation blueprintId = id(definition.get("id").getAsString());
            BlueprintKind kind = BlueprintKind.valueOf(
                    definition.get("kind").getAsString().toUpperCase(Locale.ROOT));
            String itemType = definition.get("item_type").getAsString();
            BlueprintData previous = catalog.put(blueprintId, new BlueprintData(
                    blueprintId.toString(),
                    "name." + blueprintId.getNamespace() + "." + blueprintId.getPath(),
                    "tooltip." + blueprintId.getNamespace() + "." + blueprintId.getPath(),
                    new ResourceLocation(
                            blueprintId.getNamespace(), "recipe/" + blueprintId.getPath()),
                    null,
                    itemType,
                    new ResourceLocation(
                            blueprintId.getNamespace(), "slot/" + blueprintId.getPath()),
                    kind));
            assertTrue(previous == null, "duplicate fixture catalog ID " + blueprintId);
        }
        return Map.copyOf(catalog);
    }

    private static <T> Map<ResourceLocation, T> definitions(
            JsonArray definitions,
            Codec<T> codec) {
        Map<ResourceLocation, T> result = new LinkedHashMap<>();
        for (JsonElement element : definitions) {
            JsonObject entry = element.getAsJsonObject();
            ResourceLocation definitionId = id(entry.get("id").getAsString());
            T previous = result.put(definitionId, decode(codec, entry.get("definition")));
            assertTrue(previous == null, "duplicate fixture definition ID " + definitionId);
        }
        return Map.copyOf(result);
    }

    private static <T> T decode(Codec<T> codec, JsonElement value) {
        return codec.parse(JsonOps.INSTANCE, value).result().orElseThrow();
    }

    private static Set<String> strings(JsonArray values) {
        Set<String> result = new LinkedHashSet<>();
        values.forEach(value -> result.add(value.getAsString()));
        return Set.copyOf(result);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }

    private record Baseline(
            JsonObject source,
            ResourceLocation profileId,
            ResourceLocation treeId,
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResearchAutomaticPlacementProfile automaticProfile) {
    }

    private record AutomaticBaseline(
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            AutomaticWeaponPrerequisitePlan prerequisites) {
    }
}
