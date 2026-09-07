package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract.Action;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerPreview;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import net.minecraft.network.chat.Component;

/** Structured overview facts. Details and screen chrome do not repeat these facts. */
final class AnalyzerWorkbenchContent {
    enum Icon { BLUEPRINT, FRAGMENT, CURRENCY, NONE }
    record Row(Icon icon, Component label, Component value) { }
    record Progress(int saved, int incoming, int threshold) { }
    record Cost(int input, int currency, List<BlueprintRecyclerPreview.IngredientPreview> materials) { }
    record Overview(Component heading, Component subtitle, List<Row> rows, Optional<Progress> progress) {
        Overview { rows = List.copyOf(rows); }
    }
    private AnalyzerWorkbenchContent() { }
    static Component text(String key, Object... args) {
        return ClientResearchCurrency.text("gui.taczweaponblueprints.analyzer.workbench." + key, args);
    }
    static Optional<Action> selected(BlueprintRecyclerScreenModel model, Optional<Action> preferred) {
        return preferred.filter(a -> model.primaryAction().filter(a::equals).isPresent()
                || model.secondaryAction().filter(a::equals).isPresent()).or(model::primaryAction);
    }
    static boolean recovery(BlueprintRecyclerPreview p, Optional<Action> action) {
        return action.filter(a -> a == Action.RECOVER_POINTS).isPresent()
                || action.isEmpty() && BlueprintRecyclerScreenModel.focusesRecovery(p);
    }
    static Cost cost(BlueprintRecyclerPreview p, Optional<Action> action) {
        if (p.inputKind() == BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM && recovery(p, action)) {
            return p.presentation().recoveryCost().map(c -> new Cost(c.inputCount(), c.points(), c.ingredients()))
                    .orElse(new Cost(p.requiredInputCount(), p.pointCost(), p.ingredients()));
        }
        if (p.inputKind() == BlueprintRecyclerPreview.InputKind.BLUEPRINT_FRAGMENT)
            return new Cost(p.fragmentEvaluation().orElseThrow().accepted(), 0, List.of());
        return new Cost(switch (p.inputKind()) {
            case DATA_ANALYSIS, BLUEPRINT -> 1;
            case RESEARCH_DATA -> action.filter(a -> a == Action.REDEEM_STACK).isPresent() ? p.inputCount() : 1;
            default -> p.requiredInputCount();
        }, p.pointCost(), p.ingredients());
    }
    private static Component fragmentQuantity(int minimum,int maximum) {
        return minimum==1&&maximum==1?text("fragment"):text("fragments",minimum==maximum?Integer.toString(minimum):minimum+"–"+maximum);
    }
    static Overview overview(BlueprintRecyclerPreview p, Optional<Action> action) {
        var rows = new ArrayList<Row>();
        Component heading = text("possible_results"), subtitle = Component.empty();
        Optional<Progress> progress = Optional.empty();
        switch (p.inputKind()) {
            case DATA_ANALYSIS -> {
                var data = p.presentation().data();
                if (data.isPresent()) {
                    var d = data.orElseThrow(); var station = d.at(Math.max(1, p.presentation().stationTier()));
                    subtitle = text("random_tiers", ResearchDataPresentationText.tiers(
                            station.available() ? station.eligibleTiers() : d.configuredTiers()));
                    if (station.available()) {
                        if (station.blueprintsPossible()) rows.add(new Row(Icon.BLUEPRINT, text("blueprint"), Component.literal(ResearchDataPresentationText.full(station))));
                        if (station.fragmentsPossible()) rows.add(new Row(Icon.FRAGMENT, fragmentQuantity(station.minimumFragments(),station.maximumFragments()), Component.literal(ResearchDataPresentationText.fragments(station))));
                    } else rows.add(new Row(Icon.NONE, ResearchDataPresentationText.tr("odds_unavailable"), Component.empty()));
                }
            }
            case PHYSICAL_ITEM -> {
                if (recovery(p, action)) {
                    heading = text("you_receive");
                    if (p.recoveryPointValue() > 0) rows.add(new Row(Icon.CURRENCY, text("currency_amount", p.recoveryPointValue()), Component.empty()));
                    else rows.add(new Row(Icon.NONE, text("reward_pending"), Component.empty()));
                } else if (p.presentation().weapon().isPresent()) {
                    var weapon = p.presentation().weapon().orElseThrow();
                    subtitle = text("weapon_tier", weapon.tier() == 0 ? "?" : weapon.tier());
                    weapon.outcomes().ifPresent(o -> {
                        if (o.blueprintWeight() > 0) rows.add(new Row(Icon.BLUEPRINT, text("blueprint"), Component.literal(AnalyzerProbabilityText.format(o.blueprintWeight(), o.totalWeight()))));
                        if (o.fragmentWeight() > 0) rows.add(new Row(Icon.FRAGMENT, fragmentQuantity(o.minimumFragments(),o.maximumFragments()), Component.literal(AnalyzerProbabilityText.format(o.fragmentWeight(), o.totalWeight()))));
                        if (o.noResultWeight() > 0) rows.add(new Row(Icon.NONE, text("no_result"), Component.literal(AnalyzerProbabilityText.format(o.noResultWeight(), o.totalWeight()))));
                    });
                } else {
                    heading = text("you_receive");
                    rows.add(new Row(Icon.BLUEPRINT, text("blueprint"), Component.empty()));
                }
            }
            case BLUEPRINT_FRAGMENT -> {
                var f = p.fragmentEvaluation().orElseThrow();
                heading = text("fragment_progress");
                if (f.threshold() > 0) {
                    progress = Optional.of(new Progress(f.archivedBefore(), f.accepted(), f.threshold()));
                    subtitle = text("saved", f.archivedBefore(), f.threshold());
                }
                if(f.completionMode()==BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST)
                    heading=text("research_discount");
                if (f.ready()) {
                    if (f.createsBlueprint()) rows.add(new Row(Icon.BLUEPRINT, text("blueprint"), Component.empty()));
                    else if (f.awardedPoints() > 0) rows.add(new Row(Icon.CURRENCY, text("currency_amount", f.awardedPoints()), Component.empty()));
                    rows.add(new Row(Icon.NONE, text("saved_after", f.archivedAfterAction()), Component.empty()));
                    if (f.rejected() > 0) rows.add(new Row(Icon.NONE, text("left_in_input", f.rejected()), Component.empty()));
                }
            }
            case RESEARCH_DATA, BLUEPRINT -> {
                heading = text("you_receive");
                boolean stack = action.filter(a -> a == Action.REDEEM_STACK).isPresent();
                long amount = (long)p.pointValue() * (stack ? p.inputCount() : 1);
                rows.add(new Row(Icon.CURRENCY, text(stack ? "currency_up_to" : "currency_amount", amount), Component.empty()));
                if (stack) subtitle = text("stack_stops");
            }
            case EMPTY -> { heading = text("insert_item"); subtitle = text("supported_inputs"); }
            case INVALID -> { heading = text("unsupported"); subtitle = text("supported_inputs"); }
        }
        return new Overview(heading, subtitle, rows, progress);
    }
}
