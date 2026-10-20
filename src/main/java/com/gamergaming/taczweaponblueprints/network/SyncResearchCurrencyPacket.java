package com.gamergaming.taczweaponblueprints.network;

import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Authoritative active denomination and main-inventory count, separate from the saved RP packet. */
public record SyncResearchCurrencyPacket(ResearchCurrencyPolicy policy, int available, com.gamergaming.taczweaponblueprints.progression.ResearchDataSettings dataSettings) implements CustomPacketPayload {
    public static final Type<SyncResearchCurrencyPacket> TYPE = new Type<>(
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.loc("sync_research_currency"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncResearchCurrencyPacket> STREAM_CODEC =
            StreamCodec.ofMember(SyncResearchCurrencyPacket::toBytes, SyncResearchCurrencyPacket::new);

    @Override
    public Type<SyncResearchCurrencyPacket> type() { return TYPE; }

    public SyncResearchCurrencyPacket(ResearchCurrencyPolicy policy, int available) {
        this(policy, available, new com.gamergaming.taczweaponblueprints.progression.ResearchDataSettings(
                com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPolicy.Behavior.LEGACY_CURRENCY,
                com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPoolResolver.Defaults.STANDARD));
    }
    public SyncResearchCurrencyPacket {
        if (dataSettings == null || policy == null || available < 0 || available > 1_000_000_000
                || policy.kind() == ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM && available > 36 * 64)
            throw new IllegalArgumentException("invalid research currency publication");
    }
    public SyncResearchCurrencyPacket(FriendlyByteBuf buffer) {
        this(ResearchCurrencyPolicyCodec.read(buffer), buffer.readVarInt(), readData(buffer));
    }
    public void toBytes(FriendlyByteBuf buffer) {
        ResearchCurrencyPolicyCodec.write(buffer, policy); buffer.writeVarInt(available);
        buffer.writeVarInt(dataSettings.behavior().ordinal()); buffer.writeBoolean(dataSettings.defaults().allowLearned()); buffer.writeBoolean(dataSettings.defaults().allowUndiscovered());
    }
    private static com.gamergaming.taczweaponblueprints.progression.ResearchDataSettings readData(FriendlyByteBuf b) {
        int mode=b.readVarInt(); var modes=com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPolicy.Behavior.values();
        if(mode<0||mode>=modes.length) throw new IllegalArgumentException("invalid Data mode");
        return new com.gamergaming.taczweaponblueprints.progression.ResearchDataSettings(modes[mode],
                new com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPoolResolver.Defaults(b.readBoolean(),b.readBoolean()));
    }
    public void handle(IPayloadContext context) {
        new ClientHandler().handle(context);
    }

    /** Loaded only when this clientbound payload is handled, never during server registration. */
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private final class ClientHandler {
        void handle(IPayloadContext supplier) {
            var context = supplier;
            context.enqueueWork(() -> com.gamergaming.taczweaponblueprints.client.ClientResearchCurrency.accept(SyncResearchCurrencyPacket.this));
        }
    }
}
