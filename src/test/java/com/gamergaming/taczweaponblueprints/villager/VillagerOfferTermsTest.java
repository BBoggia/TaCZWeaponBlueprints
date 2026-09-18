package com.gamergaming.taczweaponblueprints.villager;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VillagerOfferTermsTest {
    private static VillagerOfferTerms terms() {
        return new VillagerOfferTerms(new ResourceLocation("taczweaponblueprints", "test/slot"),22,false,8);
    }
    @Test void savesIndependentOfferIdentityFloorAvailabilityAndRevision() {
        var value=terms();assertEquals(value,VillagerOfferTerms.read(value.save(),64));
    }
    @Test void rejectsUnknownVersionAndMissingAvailability() {
        var tag=terms().save();tag.putInt("version",2);assertThrows(IllegalArgumentException.class,()->VillagerOfferTerms.read(tag,64));
        tag.putInt("version",1);tag.remove("available");assertThrows(IllegalArgumentException.class,()->VillagerOfferTerms.read(tag,64));
    }
    @Test void packNamespaceSlotsRoundTripAndNoncanonicalSavedIdsReject() {
        var terms = new VillagerOfferTerms(new ResourceLocation("example", "gunsmith/apprentice"), 18, true, 1);
        assertEquals(terms, VillagerOfferTerms.read(terms.save(), 64));
        var tag = terms.save(); tag.putString("slot", "unnamespaced");
        assertThrows(IllegalArgumentException.class, () -> VillagerOfferTerms.read(tag, 64));
    }
    @Test void validatesActualCurrencyStackLimit() {
        assertThrows(IllegalArgumentException.class,()->VillagerOfferTerms.read(terms().save(),16));
    }
    @Test void invalidIdentityOrRevisionDoesNotBecomeAnUnownedOffer() {
        var tag=terms().save();tag.putString("slot","invalid uppercase ID");assertThrows(IllegalArgumentException.class,()->VillagerOfferTerms.read(tag,64));
        var valid=terms().save();valid.putLong("revision",-1);assertThrows(IllegalArgumentException.class,()->VillagerOfferTerms.read(valid,64));
    }
}
