package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.EnumSet;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Profile-wide crafting defaults kept independent from research inclusion. */
public record BlueprintCraftingProfilePolicy(
        BlueprintAuthoredGunCraftingPolicy authoredGuns,
        BlueprintCraftingStrategy authoredOmittedGuns,
        BlueprintCraftingStrategy automaticGuns,
        BlueprintCraftingStrategy ammo,
        BlueprintAttachmentCraftingPolicy attachments,
        BlueprintCraftingAccessPolicy other) {
    private static final Set<BlueprintCraftingStrategy.Mode> OMITTED_MODES = EnumSet.of(
            BlueprintCraftingStrategy.Mode.FIXED,
            BlueprintCraftingStrategy.Mode.UNRESTRICTED,
            BlueprintCraftingStrategy.Mode.DISABLED,
            BlueprintCraftingStrategy.Mode.AUTOMATIC_TIER);
    private static final Set<BlueprintCraftingStrategy.Mode> AUTOMATIC_MODES = EnumSet.of(
            BlueprintCraftingStrategy.Mode.FIXED,
            BlueprintCraftingStrategy.Mode.UNRESTRICTED,
            BlueprintCraftingStrategy.Mode.DISABLED,
            BlueprintCraftingStrategy.Mode.AUTOMATIC_TIER);
    private static final Set<BlueprintCraftingStrategy.Mode> AMMO_MODES = EnumSet.of(
            BlueprintCraftingStrategy.Mode.FIXED,
            BlueprintCraftingStrategy.Mode.UNRESTRICTED,
            BlueprintCraftingStrategy.Mode.DISABLED,
            BlueprintCraftingStrategy.Mode.LINKED_WEAPON);

    public static final BlueprintCraftingProfilePolicy DEFAULT = new BlueprintCraftingProfilePolicy(
            BlueprintAuthoredGunCraftingPolicy.DEFAULT,
            BlueprintCraftingStrategy.OMITTED_DEFAULT,
            BlueprintCraftingStrategy.AUTOMATIC_DEFAULT,
            BlueprintCraftingStrategy.AMMO_DEFAULT,
            BlueprintAttachmentCraftingPolicy.DEFAULT,
            BlueprintCraftingAccessPolicy.TIER_1);
    public static final BlueprintCraftingProfilePolicy LEGACY = new BlueprintCraftingProfilePolicy(
            BlueprintAuthoredGunCraftingPolicy.LEGACY,
            BlueprintCraftingStrategy.LEGACY,
            BlueprintCraftingStrategy.LEGACY,
            BlueprintCraftingStrategy.LEGACY,
            BlueprintAttachmentCraftingPolicy.LEGACY,
            BlueprintCraftingAccessPolicy.UNRESTRICTED);

    private static final Codec<Fields> FIELDS_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    BlueprintAuthoredGunCraftingPolicy.CODEC.fieldOf("authored_guns")
                            .forGetter(Fields::authoredGuns),
                    BlueprintCraftingStrategy.CODEC.fieldOf("authored_omitted_guns")
                            .forGetter(Fields::authoredOmittedGuns),
                    BlueprintCraftingStrategy.CODEC.fieldOf("automatic_guns")
                            .forGetter(Fields::automaticGuns),
                    BlueprintCraftingStrategy.CODEC.fieldOf("ammo")
                            .forGetter(Fields::ammo),
                    BlueprintAttachmentCraftingPolicy.CODEC.fieldOf("attachments")
                            .forGetter(Fields::attachments),
                    BlueprintCraftingAccessPolicy.CODEC.fieldOf("other")
                            .forGetter(Fields::other))
                    .apply(instance, Fields::new));
    public static final Codec<BlueprintCraftingProfilePolicy> CODEC = StrictRecordCodec.wrap(
            "blueprint crafting profile policy",
            FIELDS_CODEC.flatXmap(
                    BlueprintCraftingProfilePolicy::fromFields,
                    value -> DataResult.success(new Fields(
                            value.authoredGuns(),
                            value.authoredOmittedGuns(),
                            value.automaticGuns(),
                            value.ammo(),
                            value.attachments(),
                            value.other()))),
            "authored_guns",
            "authored_omitted_guns",
            "automatic_guns",
            "ammo",
            "attachments",
            "other");

    public BlueprintCraftingProfilePolicy {
        if (authoredGuns == null || authoredOmittedGuns == null || automaticGuns == null
                || ammo == null || attachments == null || other == null) {
            throw new IllegalArgumentException("blueprint crafting profile policy fields cannot be null");
        }
        requireMode("authored_omitted_guns", authoredOmittedGuns, OMITTED_MODES);
        requireMode("automatic_guns", automaticGuns, AUTOMATIC_MODES);
        requireMode("ammo", ammo, AMMO_MODES);
    }

    private static void requireMode(
            String field,
            BlueprintCraftingStrategy strategy,
            Set<BlueprintCraftingStrategy.Mode> allowed) {
        if (!allowed.contains(strategy.mode())) {
            throw new IllegalArgumentException(
                    field + " does not support crafting strategy mode " + strategy.mode().serializedName());
        }
    }

    private static DataResult<BlueprintCraftingProfilePolicy> fromFields(Fields fields) {
        try {
            return DataResult.success(new BlueprintCraftingProfilePolicy(
                    fields.authoredGuns(),
                    fields.authoredOmittedGuns(),
                    fields.automaticGuns(),
                    fields.ammo(),
                    fields.attachments(),
                    fields.other()));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> exception.getMessage());
        }
    }

    private record Fields(
            BlueprintAuthoredGunCraftingPolicy authoredGuns,
            BlueprintCraftingStrategy authoredOmittedGuns,
            BlueprintCraftingStrategy automaticGuns,
            BlueprintCraftingStrategy ammo,
            BlueprintAttachmentCraftingPolicy attachments,
            BlueprintCraftingAccessPolicy other) {
    }
}
