package com.gamergaming.taczweaponblueprints.resource.loot;

import java.util.Optional;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

public final class StrictOptionalFieldCodec<A> extends MapCodec<Optional<A>> {
    private final String name;
    private final Codec<A> elementCodec;

    public StrictOptionalFieldCodec(String name, Codec<A> elementCodec) {
        this.name = name;
        this.elementCodec = elementCodec;
    }

    @Override
    public <T> DataResult<Optional<A>> decode(DynamicOps<T> ops, MapLike<T> input) {
        T value = input.get(name);
        if (value == null) {
            return DataResult.success(Optional.empty());
        }
        return elementCodec.parse(ops, value).map(Optional::of);
    }

    @Override
    public <T> RecordBuilder<T> encode(
            Optional<A> input,
            DynamicOps<T> ops,
            RecordBuilder<T> prefix) {
        if (input.isEmpty()) {
            return prefix;
        }
        return prefix.add(name, elementCodec.encodeStart(ops, input.get()));
    }

    @Override
    public <T> Stream<T> keys(DynamicOps<T> ops) {
        return Stream.of(ops.createString(name));
    }
}
