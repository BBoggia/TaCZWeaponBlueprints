package com.gamergaming.taczweaponblueprints.villager;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OwnedOfferSerializationTest {
    private static CompoundTag offer() {
        var offer = new MerchantOffer(new ItemCost(Items.EMERALD), Optional.of(new ItemCost(Items.IRON_INGOT)),
                new ItemStack(Items.DIAMOND), 8, 1, 0.05f);
        var tag = (CompoundTag) MerchantOffer.CODEC.encodeStart(NbtOps.INSTANCE, offer).getOrThrow();
        tag.put(VillagerOfferTerms.TAG, new VillagerOfferTerms(ResourceLocation.parse("example:slot"), 1, true, 0).save());
        return tag;
    }

    @ParameterizedTest @ValueSource(strings = {"buy", "buyB", "sell"})
    void omittedCountsMatchNativeDefaultsAndPreserveOriginalMissingStack(String slot) {
        var tag = offer();
        tag.getCompound(slot).remove("count");
        assertTrue(MerchantOffer.CODEC.parse(NbtOps.INSTANCE, tag).isSuccess(), "Native codec defaults to one");
        tag.getCompound(slot).putString("id", "removed:item");
        var original = tag.copy();
        var captured = new AtomicReference<CompoundTag>();
        var codec = OwnedOfferSerialization.withMetadata(MerchantOffer.CODEC,
                (value, raw) -> captured.set(raw.copy()),
                (value, raw) -> {
                    raw.put(slot, captured.get().getCompound(slot).copy());
                    raw.put(VillagerOfferTerms.TAG, captured.get().getCompound(VillagerOfferTerms.TAG).copy());
                },
                OwnedOfferSerialization::preserveMissingItems);
        var restored = codec.parse(NbtOps.INSTANCE, tag).getOrThrow();
        ItemStack stack = switch (slot) {
            case "buy" -> restored.getBaseCostA();
            case "buyB" -> restored.getCostB();
            default -> restored.getResult();
        };
        assertTrue(stack.is(Items.BARRIER));
        assertEquals(1, stack.getCount());
        assertTrue(OwnedOfferSerialization.isMissingItem(captured.get().getCompound(slot)),
                "The metadata reader must retain the same missing stack the decoder substituted");
        assertEquals(original, tag, "Decoding must not overwrite the caller's serialized identity");
        var saved = (CompoundTag) codec.encodeStart(NbtOps.INSTANCE, restored).getOrThrow();
        assertEquals(original.getCompound(slot), saved.getCompound(slot));
        assertTrue(codec.parse(NbtOps.INSTANCE, saved).isSuccess(), "A suspended save must remain readable");
    }

    @ParameterizedTest @ValueSource(ints = {1, 64})
    void explicitCountsRemainUnchanged(int count) {
        var tag = offer();
        var raw = tag.getCompound("sell"); raw.putString("id", "removed:item"); raw.putInt("count", count);
        assertTrue(OwnedOfferSerialization.isMissingItem(raw));
        assertEquals(count, OwnedOfferSerialization.preserveMissingItems(tag).getCompound("sell").getInt("count"));
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 65})
    void invalidCountsAreNotTreatedAsImplicitOne(int count) {
        var tag = offer();
        var raw = tag.getCompound("sell"); raw.putString("id", "removed:item"); raw.putInt("count", count);
        assertNotSubstituted(tag);
    }

    @Test void malformedCountAndInvalidOrPresentItemsAreNotSubstituted() {
        var malformed = offer();
        malformed.getCompound("sell").putString("id", "removed:item");
        malformed.getCompound("sell").putString("count", "one");
        assertNotSubstituted(malformed);
        for (String id : new String[]{"", "minecraft:", "bad ID", "minecraft:air", "minecraft:diamond"}) {
            var tag = offer(); tag.getCompound("sell").putString("id", id); tag.getCompound("sell").remove("count");
            assertNotSubstituted(tag);
        }
    }

    @Test void absentSecondaryCostDoesNotBecomeAPlaceholder() {
        var tag = offer(); tag.remove("buyB");
        var original = tag.copy();
        assertEquals(original, OwnedOfferSerialization.preserveMissingItems(tag));
        assertTrue(MerchantOffer.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().getCostB().isEmpty());
    }

    @Test void unownedOffersStayUnderVanillaRules() {
        var tag = offer(); tag.remove(VillagerOfferTerms.TAG);
        tag.getCompound("sell").putString("id", "removed:item"); tag.getCompound("sell").remove("count");
        var original = tag.copy();
        assertEquals(original, OwnedOfferSerialization.preserveMissingItems(tag));
        assertFalse(MerchantOffer.CODEC.parse(NbtOps.INSTANCE, tag).isSuccess());
    }

    private static void assertNotSubstituted(CompoundTag tag) {
        assertFalse(OwnedOfferSerialization.isMissingItem(tag.getCompound("sell")));
        var original = tag.copy();
        assertEquals(original, OwnedOfferSerialization.preserveMissingItems(tag));
    }
}
