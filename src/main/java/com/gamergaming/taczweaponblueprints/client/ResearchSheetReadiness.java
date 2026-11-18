package com.gamergaming.taczweaponblueprints.client;

import java.util.Optional;
import com.gamergaming.taczweaponblueprints.client.ui.UiSheet.TileState;
import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
import com.gamergaming.taczweaponblueprints.progression.ResearchAffordabilitySnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import static com.gamergaming.taczweaponblueprints.client.ResearchTreeSelectedNodePresenter.Message.*;

/** One classification for tiles, hover, guidance, and the selected inspector. Unknown is never ready. */
final class ResearchSheetReadiness {
    record Result(TileState tile, ResearchTreeSelectedNodePresenter.Message message, int quotedCost) { }

    static Result evaluate(ResearchTreeGraph.Node node, Optional<ResearchSelectionPreview> preview,
            Optional<ResearchAffordabilitySnapshot.Entry> affordability, boolean benchTooLow, boolean directLearning) {
        if (node.learned()) return new Result(TileState.LEARNED, LEARNED, -1);
        if (!node.visibility().revealsName()) return new Result(TileState.HIDDEN, FOLLOW_PATH, -1);
        var matching = preview.filter(p -> p.blueprintId().filter(node.blueprintId()::equals).isPresent());
        if (matching.isPresent()) {
            var quote = matching.orElseThrow();
            var message = quote.researchable() ? READY : ResearchTreeSelectedNodePresenter.authoritativeMessage(node, quote);
            if (!directLearning && message == PROGRESSION_CAPACITY_EXHAUSTED) message = INVENTORY_SPACE_REQUIRED;
            return result(message, quote.pointCost());
        }
        var entry = affordability.filter(e -> e.targetId().equals(node.blueprintId()));
        if (directLearning && entry.isPresent()) {
            var value = entry.orElseThrow();
            var message = switch (value.state()) {
                case AFFORDABLE -> value.transactionCapacityAvailable() ? READY : PROGRESSION_CAPACITY_EXHAUSTED;
                case MISSING_POINTS -> POINTS_REQUIRED;
                case MISSING_MATERIALS -> MATERIALS_REQUIRED;
                case MISSING_POINTS_AND_MATERIALS -> POINTS_AND_MATERIALS_REQUIRED;
                case LEARNED -> LEARNED;
                case POLICY_BLOCKED -> benchTooLow ? WORKBENCH_TIER_REQUIRED : LOCKED;
                case ROUTE_UNAVAILABLE -> REQUIREMENTS_UNAVAILABLE;
                case CHECKING -> CHECKING_REQUIREMENTS;
            };
            return result(message, -1); // A batch reports affordability, not an exact purchase price.
        }
        var message = switch (node.availability()) {
            case RESEARCH_DISABLED -> RESEARCH_DISABLED;
            case CONTENT_UNAVAILABLE -> CONTENT_UNAVAILABLE;
            case COST_ABOVE_CAP -> COST_UNAVAILABLE;
            case DISCOVERY_REQUIRED -> DISCOVERY_REQUIRED;
            case REDACTED -> FOLLOW_PATH;
            default -> benchTooLow ? WORKBENCH_TIER_REQUIRED : CHECKING_REQUIREMENTS;
        };
        return result(message, -1);
    }

    private static Result result(ResearchTreeSelectedNodePresenter.Message message, int cost) {
        TileState tile = switch (message) {
            case READY -> TileState.READY;
            case LEARNED -> TileState.LEARNED;
            case FOLLOW_PATH -> TileState.HIDDEN;
            case CHECKING_REQUIREMENTS, POINTS_REQUIRED, POINTS_AND_MATERIALS_REQUIRED, MATERIALS_REQUIRED,
                    INVENTORY_SPACE_REQUIRED, PROGRESSION_CAPACITY_EXHAUSTED, WORKBENCH_TIER_REQUIRED -> TileState.OPEN;
            default -> TileState.LOCKED;
        };
        return new Result(tile, message, cost);
    }
    private ResearchSheetReadiness() { }
}
