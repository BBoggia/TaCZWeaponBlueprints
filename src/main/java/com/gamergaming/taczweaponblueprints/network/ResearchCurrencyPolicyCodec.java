package com.gamergaming.taczweaponblueprints.network;

import java.util.Optional;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Versioned policy payload for consumer integration; existing protocol-59 packets are unchanged. */
public final class ResearchCurrencyPolicyCodec {
    private record Fields(int format, String kind, Optional<String> item, int pointCap, long revision) { }
    private static final Codec<Fields> FIELDS = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("format").forGetter(Fields::format), Codec.STRING.fieldOf("kind").forGetter(Fields::kind),
            new StrictOptionalFieldCodec<>("item", Codec.STRING).forGetter(Fields::item),
            Codec.INT.fieldOf("point_cap").forGetter(Fields::pointCap), Codec.LONG.fieldOf("revision").forGetter(Fields::revision)
    ).apply(i, Fields::new));
    public static final Codec<ResearchCurrencyPolicy> CODEC = StrictRecordCodec.wrap("research currency policy", FIELDS,
            "format", "kind", "item", "point_cap", "revision").flatXmap(f -> {
                try {
                    if (f.format() != 1) throw new IllegalArgumentException("unknown currency policy format");
                    var kind = switch (f.kind()) {
                        case "banked_rp" -> ResearchCurrencyPolicy.Kind.BANKED_RP;
                        case "physical_item" -> ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM;
                        default -> throw new IllegalArgumentException("unknown currency kind");
                    };
                    return DataResult.success(new ResearchCurrencyPolicy(kind, f.item().map(ResearchCurrencyPolicyCodec::id), f.pointCap(), f.revision()));
                } catch (IllegalArgumentException error) { return DataResult.error(error::getMessage); }
            }, p -> DataResult.success(new Fields(1, p.kind() == ResearchCurrencyPolicy.Kind.BANKED_RP ? "banked_rp" : "physical_item",
                    p.item().map(ResourceLocation::toString), p.pointCap(), p.revision())));

    private ResearchCurrencyPolicyCodec() { }
    public static void write(FriendlyByteBuf buffer, ResearchCurrencyPolicy policy) {
        buffer.writeVarInt(1);
        buffer.writeVarInt(policy.kind() == ResearchCurrencyPolicy.Kind.BANKED_RP ? 0 : 1);
        if (policy.kind() == ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM)
            buffer.writeUtf(policy.item().orElseThrow().toString(), PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
        buffer.writeVarInt(policy.pointCap()); buffer.writeVarLong(policy.revision());
    }
    public static ResearchCurrencyPolicy read(FriendlyByteBuf buffer) {
        if (buffer.readVarInt() != 1) throw new IllegalArgumentException("unknown currency policy format");
        int kind = buffer.readVarInt();
        if (kind != 0 && kind != 1) throw new IllegalArgumentException("unknown currency kind");
        var item = kind == 1 ? Optional.of(id(buffer.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH))) : Optional.<ResourceLocation>empty();
        return new ResearchCurrencyPolicy(kind == 0 ? ResearchCurrencyPolicy.Kind.BANKED_RP : ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM,
                item, buffer.readVarInt(), buffer.readVarLong());
    }
    private static ResourceLocation id(String raw) {
        var id = ResourceLocation.tryParse(raw);
        if (id == null || !raw.equals(id.toString()) || raw.length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH)
            throw new IllegalArgumentException("invalid canonical currency item ID");
        return id;
    }
}
