package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
import com.gamergaming.taczweaponblueprints.progression.*;
import net.minecraft.resources.ResourceLocation;

class ResearchPurchaseConfirmationTest {
    static ResearchSelectionPreview quote(String target, int price, int balance, long identity) {
        return new ResearchSelectionPreview(Optional.of(ResourceLocation.parse("test:"+target)), price, balance,
                true, true, true, balance >= price, false, List.of(), 1, 0,
                ResearchSelectionPreview.PathPlanningState.NONE, ResearchCostMode.POINTS_ONLY,
                Optional.of(new ResearchRouteFingerprint(identity, 1)));
    }
    @Test void aMoreExpensiveAffordableQuoteCancelsAndKeyRepeatCannotRearmUntilRelease() {
        var hold = new ResearchPurchaseConfirmation();
        var old = quote("target", 10, 100, 1); var changed = quote("target", 30, 100, 2);
        hold.begin(old, true, 0, 700);
        hold.validate(changed);
        assertFalse(hold.holding(300));
        hold.begin(changed, true, 300, 700);
        assertTrue(hold.advance(changed, 1500).isEmpty());
        hold.release(); hold.begin(changed, true, 1500, 700);
        assertTrue(hold.advance(changed, 2199).isEmpty());
        assertEquals(changed.routeFingerprint().orElseThrow(), hold.advance(changed, 2200).orElseThrow().quote());
        assertTrue(hold.advance(changed, 3000).isEmpty());
    }
    @Test void harmlessRefreshesDoNotCancelOrConsumeCompletionOutsideInputHandling() {
        var hold = new ResearchPurchaseConfirmation();
        var old = quote("target", 10, 100, 1); var refreshed = quote("target", 10, 120, 1);
        hold.begin(old, false, 0, 700);
        hold.validate(refreshed); hold.validate(refreshed);
        assertEquals(.5F, hold.progress(350));
        assertTrue(hold.advance(refreshed, 700).isPresent());
        assertTrue(hold.advance(refreshed, 701).isEmpty());
    }
    @Test void targetModeAffordabilityAndLifecycleChangesInvalidateTheGesture() {
        var old = quote("target", 10, 100, 1);
        for (var changed : List.of(quote("other",10,100,1), quote("target",10,100,2), quote("target",10,0,1), ResearchSelectionPreview.EMPTY)) {
            var hold = new ResearchPurchaseConfirmation(); hold.begin(old, true, 0, 700);
            assertTrue(hold.advance(changed, 700).isEmpty());
            assertFalse(hold.holding(700));
        }
        var hold = new ResearchPurchaseConfirmation(); hold.begin(old, true, 0, 700); hold.cancel();
        hold.begin(old, true, 700, 700); assertTrue(hold.advance(old, 1500).isEmpty());
        hold.release(); hold.begin(old, true, 1600, 700); assertTrue(hold.advance(old, 2300).isPresent());
    }
    @Test void paginationDoesNotChangeTheConfirmedQuote() {
        var ingredient = new ResearchSelectionPreview.IngredientPreview(List.of(ResourceLocation.parse("minecraft:paper")),Optional.empty(),1,1);
        var hold = new ResearchPurchaseConfirmation();
        for(int offset : new int[]{0,6}) {
            var quote = new ResearchSelectionPreview(Optional.of(ResourceLocation.parse("test:target")), 0,0,true,true,true,true,false,
                    java.util.Collections.nCopies(offset==0?6:1,ingredient),2,7,ResearchSelectionPreview.PathPlanningState.NONE,
                    ResearchCostMode.POINTS_AND_ITEMS,Optional.of(new ResearchRouteFingerprint(1,1)),
                    com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchAccessSummary.NONE,
                    com.gamergaming.taczweaponblueprints.menu.ResearchSelectionProgressionPreview.EMPTY,offset);
            if(offset==0) hold.begin(quote,true,0,700);
            else { hold.validate(quote); assertTrue(hold.advance(quote,700).isPresent()); }
        }
    }
}
