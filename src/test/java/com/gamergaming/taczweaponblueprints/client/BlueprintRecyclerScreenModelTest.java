package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerPreview;
import com.gamergaming.taczweaponblueprints.progression.BlueprintRecyclingService;
import com.gamergaming.taczweaponblueprints.progression.BlueprintReverseEngineeringService;
import com.gamergaming.taczweaponblueprints.progression.FoundWeaponRecoveryService;
import com.gamergaming.taczweaponblueprints.progression.ResearchDataRedemptionService;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentAnalysisService;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;

class BlueprintRecyclerScreenModelTest {
    private static final ResourceLocation BLUEPRINT = new ResourceLocation("tacz:test_weapon");
    private static final ResourceLocation DATA = new ResourceLocation("minecraft:paper");

    @Test
    void emptyAndInvalidInputsNeverOfferActions() {
        BlueprintRecyclerScreenModel empty = BlueprintRecyclerScreenModel.from(
                BlueprintRecyclerPreview.empty(4, 100), false);
        BlueprintRecyclerScreenModel invalid = BlueprintRecyclerScreenModel.from(
                BlueprintRecyclerPreview.invalid(Optional.of(DATA), 2, 4, 100), false);

        assertTrue(empty.primaryAction().isEmpty());
        assertTrue(invalid.primaryAction().isEmpty());
        assertFalse(empty.controlsEnabled());
        assertFalse(invalid.controlsEnabled());
    }

    @Test
    void everyBlueprintStatusHasAStablePresentationAndOnlySuccessRecycles() {
        for (BlueprintRecyclingService.Status status : BlueprintRecyclingService.Status.values()) {
            BlueprintRecyclerScreenModel model = BlueprintRecyclerScreenModel.from(
                    blueprint(status), false);

            assertTrue(model.statusKey().endsWith(status.name().toLowerCase(Locale.ROOT)));
            assertEquals(
                    status == BlueprintRecyclingService.Status.SUCCESS,
                    model.primaryAction().filter(action ->
                            action == BlueprintRecyclerActionContract.Action.RECYCLE).isPresent());
            assertEquals(status == BlueprintRecyclingService.Status.SUCCESS,
                    model.controlsEnabled());
            assertTrue(model.secondaryAction().isEmpty());
        }
    }

    @Test
    void researchDataOffersOneAndStackActionsOnlyWhenAuthoritativelyReady() {
        for (ResearchDataRedemptionService.Status status
                : ResearchDataRedemptionService.Status.values()) {
            BlueprintRecyclerScreenModel model = BlueprintRecyclerScreenModel.from(
                    researchData(status, 4), false);
            boolean ready = status == ResearchDataRedemptionService.Status.SUCCESS;

            assertTrue(model.statusKey().endsWith(status.name().toLowerCase(Locale.ROOT)));
            assertEquals(ready, model.primaryAction().filter(action ->
                    action == BlueprintRecyclerActionContract.Action.REDEEM).isPresent());
            assertEquals(ready, model.secondaryAction().filter(action ->
                    action == BlueprintRecyclerActionContract.Action.REDEEM_STACK).isPresent());
            assertEquals(ready, model.controlsEnabled());
        }
    }

    @Test
    void oneResearchDataItemDoesNotOfferAFalseBulkChoice() {
        BlueprintRecyclerScreenModel model = BlueprintRecyclerScreenModel.from(
                researchData(ResearchDataRedemptionService.Status.SUCCESS, 1), false);

        assertEquals(Optional.of(BlueprintRecyclerActionContract.Action.REDEEM),
                model.primaryAction());
        assertTrue(model.secondaryAction().isEmpty());
    }

    @Test
    void pendingRequestPreservesMeaningButDisablesBothControls() {
        BlueprintRecyclerScreenModel model = BlueprintRecyclerScreenModel.from(
                researchData(ResearchDataRedemptionService.Status.SUCCESS, 4), true);

        assertTrue(model.primaryAction().isPresent());
        assertTrue(model.secondaryAction().isPresent());
        assertFalse(model.controlsEnabled());
    }

    @Test
    void physicalItemsOfferOnlyTheAuthoritativeReverseEngineeringAction() {
        for (BlueprintReverseEngineeringService.Status status
                : BlueprintReverseEngineeringService.Status.values()) {
            BlueprintRecyclerScreenModel model = BlueprintRecyclerScreenModel.from(
                    physical(status), false);
            boolean ready = status == BlueprintReverseEngineeringService.Status.READY;

            assertTrue(model.statusKey().endsWith(status.name().toLowerCase(Locale.ROOT)));
            assertEquals(ready, model.primaryAction().filter(action ->
                    action == BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER).isPresent());
            assertTrue(model.secondaryAction().isEmpty());
            assertEquals(ready, model.controlsEnabled());
        }
    }

    @Test
    void fragmentArchiveIsTheOnlyActionForAnAuthoritativelyReadyDeposit() {
        for (BlueprintFragmentAnalysisService.Status status
                : BlueprintFragmentAnalysisService.Status.values()) {
            BlueprintRecyclerScreenModel model = BlueprintRecyclerScreenModel.from(
                    fragment(status),
                    false);
            boolean ready = status == BlueprintFragmentAnalysisService.Status.READY;

            assertTrue(model.statusKey().endsWith(status.name().toLowerCase(Locale.ROOT)));
            assertEquals(ready, model.primaryAction().filter(action ->
                    action == BlueprintRecyclerActionContract.Action.ARCHIVE_FRAGMENTS)
                    .isPresent());
            assertTrue(model.secondaryAction().isEmpty());
            assertEquals(ready, model.controlsEnabled());
        }
    }

    @Test
    void learnedEquipmentHasAnExplicitProminentCopyState() {
        BlueprintRecyclerScreenModel blocked = BlueprintRecyclerScreenModel.from(
                physical(BlueprintReverseEngineeringService.Status.ALREADY_KNOWN, true),
                false);
        BlueprintRecyclerScreenModel copy = BlueprintRecyclerScreenModel.from(
                physical(BlueprintReverseEngineeringService.Status.READY, true),
                false);

        assertEquals(BlueprintRecyclerScreenModel.StatusEmphasis.NOTICE,
                blocked.statusEmphasis());
        assertFalse(blocked.summaryVisible());
        assertTrue(blocked.primaryAction().isEmpty());

        assertEquals(BlueprintRecyclerScreenModel.StatusEmphasis.NOTICE,
                copy.statusEmphasis());
        assertTrue(copy.statusKey().endsWith("known_copy"));
        assertTrue(copy.summaryVisible());
        assertEquals(Optional.of(BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER),
                copy.primaryAction());
    }

    @Test
    void verifiedFoundWeaponsExposeChoiceDirectAndFailureStates() {
        BlueprintRecyclerScreenModel choice = BlueprintRecyclerScreenModel.from(
                physicalRecovery(
                        BlueprintReverseEngineeringService.Status.READY,
                        FoundWeaponRecoveryService.Status.READY),
                false);
        assertEquals(Optional.of(BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER),
                choice.primaryAction());
        assertEquals(Optional.of(BlueprintRecyclerActionContract.Action.RECOVER_POINTS),
                choice.secondaryAction());
        assertTrue(choice.statusKey().endsWith("choice_ready"));

        BlueprintRecyclerScreenModel direct = BlueprintRecyclerScreenModel.from(
                physicalRecovery(
                        BlueprintReverseEngineeringService.Status.RECOVERY_MODE_DISABLED,
                        FoundWeaponRecoveryService.Status.READY),
                false);
        assertEquals(Optional.of(BlueprintRecyclerActionContract.Action.RECOVER_POINTS),
                direct.primaryAction());
        assertTrue(direct.secondaryAction().isEmpty());
        assertTrue(direct.statusKey().endsWith("direct_ready"));

        BlueprintRecyclerScreenModel capped = BlueprintRecyclerScreenModel.from(
                physicalRecovery(
                        BlueprintReverseEngineeringService.Status.RECOVERY_MODE_DISABLED,
                        FoundWeaponRecoveryService.Status.POINT_CAP_REACHED),
                false);
        assertTrue(capped.primaryAction().isEmpty());
        assertTrue(capped.statusKey().endsWith("recovery.status.point_cap_reached"));
    }

    @Test
    void directRecoveryExplainsUnmetRequirementsBeforeItsAwardCanBeComputed() {
        for (var status : List.of(BlueprintReverseEngineeringService.Status.INGREDIENTS_REQUIRED,
                BlueprintReverseEngineeringService.Status.POINTS_REQUIRED,
                BlueprintReverseEngineeringService.Status.ALREADY_KNOWN)) {
            var preview = physicalRecovery(BlueprintReverseEngineeringService.Status.RECOVERY_MODE_DISABLED,
                    FoundWeaponRecoveryService.Status.REVERSE_ENGINEERING_INELIGIBLE, 0)
                    .withPresentation(new com.gamergaming.taczweaponblueprints.menu.AnalyzerPresentation(1,
                            Optional.empty(), Optional.empty(), Optional.of(
                            new com.gamergaming.taczweaponblueprints.menu.AnalyzerPresentation.RecoveryCost(
                                    1, 7, List.of(), status))));
            var model = BlueprintRecyclerScreenModel.from(preview, false);
            assertTrue(model.statusKey().endsWith("reverse.status." + status.name().toLowerCase(java.util.Locale.ROOT)));
            assertTrue(model.primaryAction().isEmpty());
            assertTrue(model.secondaryAction().isEmpty());
        }
    }

    @Test
    void readyRecoveryOwnsTheDecisionEvenWhenTieredAnalysisPolicyIsBlocked() {
        for (var status : List.of(
                com.gamergaming.taczweaponblueprints.resource.research.AnalyzerPolicyQuote.Status.STATION_TIER_REQUIRED,
                com.gamergaming.taczweaponblueprints.resource.research.AnalyzerPolicyQuote.Status.DISABLED,
                com.gamergaming.taczweaponblueprints.resource.research.AnalyzerPolicyQuote.Status.ORIGIN_DISABLED)) {
            var presentation = new com.gamergaming.taczweaponblueprints.menu.AnalyzerPresentation(1,
                    Optional.of(new com.gamergaming.taczweaponblueprints.menu.AnalyzerPresentation.Weapon(3, status,
                            Optional.empty(), Optional.empty(), 0, 0, 0, 0)), Optional.empty(), Optional.empty());
            var ready = physicalRecovery(BlueprintReverseEngineeringService.Status.POLICY_INELIGIBLE,
                    FoundWeaponRecoveryService.Status.READY).withPresentation(presentation);
            var model = BlueprintRecyclerScreenModel.from(ready, false);
            assertTrue(model.statusKey().endsWith("reverse.status.direct_ready"));
            assertEquals(Optional.of(BlueprintRecyclerActionContract.Action.RECOVER_POINTS), model.primaryAction());
            assertTrue(BlueprintRecyclerScreenModel.focusesRecovery(ready));
            assertTrue(BlueprintRecyclerScreenModel.analysisStatusKey(ready).endsWith("analyzer.status." + status.name().toLowerCase(Locale.ROOT)));
            var blocked = physicalRecovery(BlueprintReverseEngineeringService.Status.POLICY_INELIGIBLE,
                    FoundWeaponRecoveryService.Status.RECOVERY_DISABLED).withPresentation(presentation);
            assertFalse(BlueprintRecyclerScreenModel.focusesRecovery(blocked));
            assertEquals(BlueprintRecyclerScreenModel.analysisStatusKey(blocked), BlueprintRecyclerScreenModel.from(blocked, false).statusKey());
        }
    }

    @Test
    void recoverySummaryFollowsTheOnlyAvailableActionWithoutHidingAValidChoice() {
        for (var status : List.of(BlueprintReverseEngineeringService.Status.ALREADY_KNOWN,
                BlueprintReverseEngineeringService.Status.INGREDIENTS_REQUIRED, BlueprintReverseEngineeringService.Status.OUTPUT_OCCUPIED)) {
            assertTrue(BlueprintRecyclerScreenModel.focusesRecovery(physicalRecovery(status, FoundWeaponRecoveryService.Status.READY)));
        }
        assertFalse(BlueprintRecyclerScreenModel.focusesRecovery(physicalRecovery(
                BlueprintReverseEngineeringService.Status.READY, FoundWeaponRecoveryService.Status.READY)));
        assertTrue(BlueprintRecyclerScreenModel.focusesRecovery(physicalRecovery(
                BlueprintReverseEngineeringService.Status.RECOVERY_MODE_DISABLED, FoundWeaponRecoveryService.Status.RECOVERY_DISABLED)));
    }

    @Test
    void everyGeneratedStatusActionAndResultKeyIsLocalized() throws IOException {
        JsonObject language;
        try (InputStreamReader reader = new InputStreamReader(
                getClass().getResourceAsStream(
                        "/assets/taczweaponblueprints/lang/en_us.json"),
                StandardCharsets.UTF_8)) {
            language = JsonParser.parseReader(reader).getAsJsonObject();
        }
        for (BlueprintRecyclingService.Status status : BlueprintRecyclingService.Status.values()) {
            assertTrue(language.has(blueprint(status).recyclingStatus()
                    .map(value -> BlueprintRecyclerScreenModel.from(
                            blueprint(value), false).statusKey()).orElseThrow()));
        }
        for (ResearchDataRedemptionService.Status status
                : ResearchDataRedemptionService.Status.values()) {
            assertTrue(language.has(BlueprintRecyclerScreenModel.from(
                    researchData(status, 2), false).statusKey()));
        }
        for (BlueprintReverseEngineeringService.Status status
                : BlueprintReverseEngineeringService.Status.values()) {
            assertTrue(language.has(BlueprintRecyclerScreenModel.from(
                    physical(status), false).statusKey()));
        }
        for (FoundWeaponRecoveryService.Status status
                : FoundWeaponRecoveryService.Status.values()) {
            assertTrue(language.has(BlueprintRecyclerScreenModel.from(
                    physicalRecovery(
                            BlueprintReverseEngineeringService.Status.RECOVERY_MODE_DISABLED,
                            status),
                    false).statusKey()));
        }
        for (BlueprintFragmentAnalysisService.Status status
                : BlueprintFragmentAnalysisService.Status.values()) {
            assertTrue(language.has(BlueprintRecyclerScreenModel.from(
                    fragment(status), false).statusKey()));
        }
        for (BlueprintRecyclerActionContract.Action action
                : BlueprintRecyclerActionContract.Action.values()) {
            String key = BlueprintRecyclerScreenModel.actionKey(action);
            assertTrue(language.has(key));
            assertTrue(language.has(key + ".tooltip"));
        }
        for (BlueprintRecyclerActionContract.ResultCode result
                : BlueprintRecyclerActionContract.ResultCode.values()) {
            assertTrue(language.has(BlueprintRecyclerScreenModel.resultKey(result)));
        }
        assertTrue(language.has(
                "gui.taczweaponblueprints.blueprint_recycler.summary_next"));
        assertTrue(language.has(
                "gui.taczweaponblueprints.blueprint_recycler.narration"));
        assertTrue(language.has(
                "gui.taczweaponblueprints.blueprint_recycler.narration.compact"));
        assertTrue(language.has(
                "gui.taczweaponblueprints.blueprint_recycler.narration.actions"));
        assertTrue(language.has(
                "gui.taczweaponblueprints.blueprint_recycler.narration.no_action"));
        assertTrue(language.has(
                "gui.taczweaponblueprints.blueprint_recycler.narration.waiting"));
        assertTrue(language.has(
                "gui.taczweaponblueprints.blueprint_recycler.reverse.material"));
        assertTrue(language.has(
                "gui.taczweaponblueprints.blueprint_recycler.reverse.alternatives_more"));
    }

    private static BlueprintRecyclerPreview blueprint(BlueprintRecyclingService.Status status) {
        return new BlueprintRecyclerPreview(
                BlueprintRecyclerPreview.InputKind.BLUEPRINT,
                Optional.of(BLUEPRINT),
                1,
                5,
                4,
                100,
                Optional.of(status),
                Optional.empty());
    }

    private static BlueprintRecyclerPreview researchData(
            ResearchDataRedemptionService.Status status,
            int count) {
        return new BlueprintRecyclerPreview(
                BlueprintRecyclerPreview.InputKind.RESEARCH_DATA,
                Optional.of(DATA),
                count,
                5,
                4,
                100,
                Optional.empty(),
                Optional.of(status));
    }

    private static BlueprintRecyclerPreview physical(
            BlueprintReverseEngineeringService.Status status) {
        return physical(
                status,
                status == BlueprintReverseEngineeringService.Status.ALREADY_KNOWN);
    }

    private static BlueprintRecyclerPreview physical(
            BlueprintReverseEngineeringService.Status status,
            boolean alreadyKnown) {
        return new BlueprintRecyclerPreview(
                BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM,
                Optional.of(BLUEPRINT),
                1,
                0,
                4,
                100,
                Optional.empty(),
                Optional.empty(),
                5L,
                Optional.of(BLUEPRINT),
                1,
                2,
                true,
                true,
                false,
                alreadyKnown,
                Optional.of(status),
                List.of());
    }

    private static BlueprintRecyclerPreview physicalRecovery(
            BlueprintReverseEngineeringService.Status reverseStatus,
            FoundWeaponRecoveryService.Status recoveryStatus) {
        return physicalRecovery(reverseStatus, recoveryStatus, 3);
    }

    private static BlueprintRecyclerPreview physicalRecovery(
            BlueprintReverseEngineeringService.Status reverseStatus,
            FoundWeaponRecoveryService.Status recoveryStatus, int recoveryPoints) {
        return new BlueprintRecyclerPreview(
                BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM,
                Optional.of(BLUEPRINT),
                1,
                0,
                4,
                100,
                Optional.empty(),
                Optional.empty(),
                5L,
                Optional.of(BLUEPRINT),
                1,
                0,
                true,
                true,
                false,
                reverseStatus == BlueprintReverseEngineeringService.Status.ALREADY_KNOWN,
                Optional.of(reverseStatus),
                List.of(),
                BlueprintRecyclerPreview.WeaponOrigin.LOOT_GENERATED,
                recoveryPoints,
                Optional.of(recoveryStatus));
    }

    private static BlueprintRecyclerPreview fragment(
            BlueprintFragmentAnalysisService.Status status) {
        boolean ready = status == BlueprintFragmentAnalysisService.Status.READY;
        return BlueprintRecyclerPreview.fragment(
                new BlueprintFragmentAnalysisService.Evaluation(
                        status,
                        Optional.of(BLUEPRINT),
                        BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST,
                        1,
                        ready ? 1 : 0,
                        ready ? 0 : 1,
                        2,
                        ready ? 3 : 2,
                        ready ? 3 : 2,
                        5,
                        0,
                        0,
                        false,
                        false,
                        0,
                        4,
                        100,
                        false,
                        ready ? 7L : 0L));
    }
}
