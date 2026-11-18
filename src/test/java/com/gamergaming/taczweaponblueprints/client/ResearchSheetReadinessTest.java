package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.client.ui.UiSheet.TileState;
import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchAccessSummary;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import net.minecraft.resources.ResourceLocation;
import static com.gamergaming.taczweaponblueprints.client.ResearchTreeSelectedNodePresenter.Message.*;

class ResearchSheetReadinessTest {
    private static final ResourceLocation ID=ResourceLocation.parse("test:target");
    private static ResearchTreeGraph.Node node() {
        return new ResearchTreeGraph.Node(0,ID,"target","rifle",ID,JournalVisibility.FULL,false,true,true,10,0,0,0,
                ResearchTreeGraph.Availability.AVAILABLE).withBenchTier(1);
    }
    private ResearchSheetReadiness.Result evaluate(ResearchSelectionPreview p, boolean direct) {
        return ResearchSheetReadiness.evaluate(node(), Optional.of(p), Optional.empty(), false, direct);
    }
    @Test void publicationAloneNeverPromisesResearchIsReady() {
        var result=ResearchSheetReadiness.evaluate(node(),Optional.empty(),Optional.empty(),false,true);
        assertEquals(CHECKING_REQUIREMENTS,result.message()); assertNotEquals(TileState.READY,result.tile());
        var advice=TechTreeGuidance.advise(List.of(new TechTreeGuidance.Entry(ID,result.tile(),false,-1,1,result.message())));
        assertEquals(TechTreeGuidance.Kind.CHECKING,advice.kind());
    }
    @Test void discountedQuoteOverridesBasePrice() {
        var result=evaluate(ResearchPurchaseConfirmationTest.quote("target",0,0,1),true);
        assertEquals(READY,result.message()); assertEquals(TileState.READY,result.tile()); assertEquals(0,result.quotedCost());
    }
    @Test void exactMaterialGateAndCapacityRefusalsAreNeverReady() {
        var ingredients=List.of(new ResearchSelectionPreview.IngredientPreview(List.of(ID),Optional.empty(),1,0));
        var missing=new ResearchSelectionPreview(Optional.of(ID),0,0,true,false,true,false,false,ingredients,1,1);
        assertEquals(MATERIALS_REQUIRED,evaluate(missing,true).message());
        var gated=new ResearchSelectionPreview(Optional.of(ID),0,0,false,true,true,false,false,List.of(),1,0,
                ResearchSelectionPreview.PathPlanningState.NONE,ResearchCostMode.POINTS_ONLY,Optional.empty(),ResearchAccessSummary.gate("test.gate"));
        assertEquals(PROGRESSION_GATE_REQUIRED,evaluate(gated,true).message());
        var capacity=new ResearchSelectionPreview(Optional.of(ID),0,0,true,true,false,false,false,List.of(),1,0);
        assertEquals(PROGRESSION_CAPACITY_EXHAUSTED,evaluate(capacity,true).message());
        assertEquals(INVENTORY_SPACE_REQUIRED,evaluate(capacity,false).message());
        for(var quote:List.of(missing,gated,capacity))assertNotEquals(TileState.READY,evaluate(quote,true).tile());
    }
    @Test void batchCapacityAndPrintingModeAreRespectedAndUnknownPricesStayUnknown() {
        for(boolean capacity:new boolean[]{false,true}) {
            var entry=new ResearchAffordabilitySnapshot.Entry(ID,ResearchGuidanceSnapshot.State.AFFORDABLE,capacity);
            var result=ResearchSheetReadiness.evaluate(node(),Optional.empty(),Optional.of(entry),false,true);
            assertEquals(capacity?READY:PROGRESSION_CAPACITY_EXHAUSTED,result.message()); assertEquals(-1,result.quotedCost());
            assertEquals(CHECKING_REQUIREMENTS,ResearchSheetReadiness.evaluate(node(),Optional.empty(),Optional.of(entry),false,false).message());
        }
    }
    @Test void materialBlockersAreNotDescribedAsMissingCurrency() {
        var entry=new ResearchAffordabilitySnapshot.Entry(ID,ResearchGuidanceSnapshot.State.MISSING_MATERIALS,true);
        var result=ResearchSheetReadiness.evaluate(node(),Optional.empty(),Optional.of(entry),false,true);
        var advice=TechTreeGuidance.advise(List.of(new TechTreeGuidance.Entry(ID,result.tile(),false,-1,1,result.message())));
        assertEquals(TechTreeGuidance.Kind.BLOCKED,advice.kind());
    }
}
