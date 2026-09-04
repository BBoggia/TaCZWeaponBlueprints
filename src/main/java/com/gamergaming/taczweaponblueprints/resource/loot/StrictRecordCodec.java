package com.gamergaming.taczweaponblueprints.resource.loot;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;

public final class StrictRecordCodec {
    private StrictRecordCodec() {
    }

    public static <A> Codec<A> wrap(String description, Codec<A> delegate, String... allowedFields) {
        Set<String> allowed = Set.copyOf(Arrays.asList(allowedFields));
        Decoder<A> strictDecoder = new Decoder<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                return ops.getMap(input).flatMap(map -> {
                    List<String> unknownFields = map.entries()
                            .map(entry -> ops.getStringValue(entry.getFirst()).result().orElse("<non-string key>"))
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
                });
            }
        };
        return Codec.of(delegate, strictDecoder, "Strict[" + description + "]");
    }
}
