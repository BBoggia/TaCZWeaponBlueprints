package com.gamergaming.taczweaponblueprints.resource.loot;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

/** Unknown-field rejecting wrapper that preserves MapCodec identity for dispatch codecs. */
public final class StrictMapCodec {
    private StrictMapCodec() {
    }

    public static <A> MapCodec<A> wrap(
            String description,
            MapCodec<A> delegate,
            String... allowedFields) {
        Set<String> allowed = Set.copyOf(Arrays.asList(allowedFields));
        return new MapCodec<>() {
            @Override
            public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
                List<String> unknownFields = input.entries()
                        .map(entry -> ops.getStringValue(entry.getFirst())
                                .result().orElse("<non-string key>"))
                        .filter(field -> !allowed.contains(field))
                        .sorted()
                        .collect(Collectors.toList());
                if (!unknownFields.isEmpty()) {
                    return DataResult.error(() -> description + " contains unknown field(s): "
                            + String.join(", ", unknownFields));
                }
                try {
                    return delegate.decode(ops, input);
                } catch (RuntimeException exception) {
                    String detail = exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage();
                    return DataResult.error(() -> description + " is invalid: " + detail);
                }
            }

            @Override
            public <T> RecordBuilder<T> encode(
                    A input,
                    DynamicOps<T> ops,
                    RecordBuilder<T> prefix) {
                return delegate.encode(input, ops, prefix);
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return delegate.keys(ops);
            }

            @Override
            public String toString() {
                return "StrictMap[" + description + "]";
            }
        };
    }
}
