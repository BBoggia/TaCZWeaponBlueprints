package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Optional;
import java.util.stream.Stream;
import com.mojang.serialization.*;

/** Analyzer schema distinguishes an omitted field from an explicitly null field. */
final class AnalyzerOptionalFieldCodec<A> extends MapCodec<Optional<A>> {
    static final Codec<Boolean> BOOLEAN = Codec.of(Codec.BOOL, new Decoder<>() {
        @Override public <T> DataResult<com.mojang.datafixers.util.Pair<Boolean, T>> decode(DynamicOps<T> ops, T input) {
            if (input instanceof com.google.gson.JsonElement json
                    && (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isBoolean())) {
                return DataResult.error(() -> "expected an Analyzer boolean");
            }
            return Codec.BOOL.decode(ops, input);
        }
    });
    private final String name;
    private final Codec<A> codec;
    AnalyzerOptionalFieldCodec(String name, Codec<A> codec) { this.name = name; this.codec = codec; }
    @Override public <T> DataResult<Optional<A>> decode(DynamicOps<T> ops, MapLike<T> input) {
        T value = input.get(name);
        if (value == null) {
            boolean present = input.entries().anyMatch(entry -> ops.getStringValue(entry.getFirst()).result().filter(name::equals).isPresent());
            return present ? DataResult.error(() -> "Analyzer field " + name + " cannot be null") : DataResult.success(Optional.empty());
        }
        return codec.parse(ops, value).map(Optional::of);
    }
    @Override public <T> RecordBuilder<T> encode(Optional<A> value, DynamicOps<T> ops, RecordBuilder<T> builder) {
        return value.isEmpty() ? builder : builder.add(name, codec.encodeStart(ops, value.orElseThrow()));
    }
    @Override public <T> Stream<T> keys(DynamicOps<T> ops) { return Stream.of(ops.createString(name)); }
}
