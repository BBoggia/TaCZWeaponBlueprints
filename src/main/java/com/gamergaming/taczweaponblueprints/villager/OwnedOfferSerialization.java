package com.gamergaming.taczweaponblueprints.villager;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.function.BiConsumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/** Adds ownership fields to the vanilla codec without replacing its item/component serialization. */
public final class OwnedOfferSerialization {
    private OwnedOfferSerialization() {}

    public static <V> Codec<V> withMetadata(Codec<V> delegate, BiConsumer<V, CompoundTag> read,
            BiConsumer<V, CompoundTag> write) {
        return withMetadata(delegate, read, write, java.util.function.UnaryOperator.identity());
    }

    public static <V> Codec<V> withMetadata(Codec<V> delegate, BiConsumer<V, CompoundTag> read,
            BiConsumer<V, CompoundTag> write, java.util.function.UnaryOperator<CompoundTag> decodeInput) {
        return new Codec<>() {
            @Override public <T> DataResult<Pair<V, T>> decode(DynamicOps<T> ops, T input) {
                var tag = ops.convertTo(NbtOps.INSTANCE, input);
                T decodable = tag instanceof CompoundTag compound
                        ? NbtOps.INSTANCE.convertTo(ops, decodeInput.apply(compound.copy())) : input;
                return delegate.decode(ops, decodable).map(pair -> {
                    if (tag instanceof CompoundTag compound) read.accept(pair.getFirst(), compound);
                    return pair;
                });
            }
            @Override public <T> DataResult<T> encode(V value, DynamicOps<T> ops, T prefix) {
                return delegate.encode(value, ops, prefix).flatMap(encoded -> {
                    var tag = new CompoundTag();
                    write.accept(value, tag);
                    var builder = ops.mapBuilder();
                    for (String key : tag.getAllKeys()) builder.add(key, NbtOps.INSTANCE.convertTo(ops, tag.get(key)));
                    return builder.build(encoded);
                });
            }
        };
    }

    /** Missing optional-mod items remain suspended stock and retain their original serialized identity. */
    public static CompoundTag preserveMissingItems(CompoundTag tag) {
        if (!tag.contains(VillagerOfferTerms.TAG)
                && !tag.contains(com.gamergaming.taczweaponblueprints.villager.stock.StockOfferBinding.TAG)) return tag;
        for (String key : java.util.List.of("buy", "buyB", "sell")) {
            var raw = tag.getCompound(key);
            if (!isMissingItem(raw)) continue;
            var placeholder = new CompoundTag();
            placeholder.putString("id", "minecraft:barrier");
            placeholder.putInt("count", savedCount(raw));
            tag.put(key, placeholder);
        }
        return tag;
    }

    /** ItemStack and ItemCost both default an absent count to one, but not an invalid count. */
    private static int savedCount(CompoundTag stack) {
        if (!stack.contains("count")) return 1;
        return stack.contains("count", Tag.TAG_ANY_NUMERIC) ? stack.getInt("count") : 0;
    }

    /** Shared by placeholder decoding and the mixin that retains the original suspended stack. */
    public static boolean isMissingItem(CompoundTag stack) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(stack.getString("id"));
        int count = savedCount(stack);
        return id != null && !id.getPath().isEmpty() && !id.equals(net.minecraft.resources.ResourceLocation.withDefaultNamespace("air"))
                && count >= 1 && count <= 64
                && !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id);
    }

    public static ItemCost cost(ItemStack stack) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Primary trade cost cannot be empty");
        return new ItemCost(stack.getItemHolder(), stack.getCount(), DataComponentPredicate.allOf(stack.getComponents()));
    }

    public static CompoundTag save(MerchantOffer offer, HolderLookup.Provider registries) {
        return (CompoundTag) MerchantOffer.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), offer).getOrThrow();
    }

    public static MerchantOffer read(CompoundTag tag, HolderLookup.Provider registries) {
        return MerchantOffer.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
    }
}
