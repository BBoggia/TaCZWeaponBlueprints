package com.gamergaming.taczweaponblueprints.resource.award;

import java.util.Locale;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ResearchPointAwardPresentation(Visibility visibility, Optional<String> name) {
    private static final Codec<ResearchPointAwardPresentation> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Visibility.CODEC.fieldOf("visibility")
                            .forGetter(ResearchPointAwardPresentation::visibility),
                    new StrictOptionalFieldCodec<>("name", ResearchPointAwardCodecs.BOUNDED_STRING)
                            .forGetter(ResearchPointAwardPresentation::name))
                    .apply(instance, ResearchPointAwardPresentation::new));
    public static final Codec<ResearchPointAwardPresentation> CODEC = StrictRecordCodec.wrap(
            "Research Point award presentation",
            RAW_CODEC.flatXmap(
                    ResearchPointAwardPresentation::validate,
                    ResearchPointAwardPresentation::validate),
            "visibility",
            "name");

    public ResearchPointAwardPresentation {
        if (visibility == null) {
            throw new IllegalArgumentException("award presentation visibility cannot be null");
        }
        name = name == null ? Optional.empty() : name;
    }

    private static DataResult<ResearchPointAwardPresentation> validate(
            ResearchPointAwardPresentation presentation) {
        return presentation.visibility() == Visibility.HIDDEN || presentation.name().isPresent()
                ? DataResult.success(presentation)
                : DataResult.error(() -> "public and conditional awards require a presentation name");
    }

    public enum Visibility {
        PUBLIC,
        CONDITIONAL,
        HIDDEN;

        public static final Codec<Visibility> CODEC = Codec.STRING.flatXmap(
                Visibility::parse,
                value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));

        private static DataResult<Visibility> parse(String value) {
            if (value != null) {
                try {
                    return DataResult.success(valueOf(value.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return DataResult.error(() -> "unknown Research Point presentation visibility " + value);
        }
    }
}
