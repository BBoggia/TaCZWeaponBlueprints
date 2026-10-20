package com.gamergaming.taczweaponblueprints.network;

import java.util.ArrayList;
import java.util.List;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.client.ClientResearchPointPresentationState;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.Feedback;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Bounded server-filtered RP result; contains no definition or target IDs. */
public final class ResearchPointFeedbackPacket implements CustomPacketPayload {
    public static final Type<ResearchPointFeedbackPacket> TYPE = new Type<>(
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.loc("research_point_feedback"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchPointFeedbackPacket> STREAM_CODEC =
            StreamCodec.ofMember(ResearchPointFeedbackPacket::toBytes, ResearchPointFeedbackPacket::new);

    @Override
    public Type<ResearchPointFeedbackPacket> type() { return TYPE; }

    private final Feedback feedback;
    private final com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy currency;

    public ResearchPointFeedbackPacket(Feedback feedback) {
        this(feedback, com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy.banked(1_000_000_000, 1));
    }
    public ResearchPointFeedbackPacket(Feedback feedback, com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy currency) {
        this.currency = java.util.Objects.requireNonNull(currency);
        if (feedback == null || !feedback.present()) {
            throw new IllegalArgumentException("empty Research Point feedback packet");
        }
        this.feedback = feedback;
    }

    public ResearchPointFeedbackPacket(FriendlyByteBuf buffer) {
        currency = ResearchCurrencyPolicyCodec.read(buffer);
        int points = buffer.readVarInt();
        int generic = buffer.readVarInt();
        boolean claimedAtCap = buffer.readBoolean();
        int names = buffer.readVarInt();
        if (names < 0 || names > PlayerProgressionLimits.MAX_RESEARCH_POINT_FEEDBACK_NAMES) {
            throw new IllegalArgumentException("invalid Research Point feedback name count");
        }
        List<String> decoded = new ArrayList<>(names);
        for (int index = 0; index < names; index++) {
            decoded.add(buffer.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH));
        }
        feedback = new Feedback(points, generic, claimedAtCap, decoded);
        if (!feedback.present()) {
            throw new IllegalArgumentException("empty Research Point feedback packet");
        }
    }

    public void toBytes(FriendlyByteBuf buffer) {
        ResearchCurrencyPolicyCodec.write(buffer, currency);
        buffer.writeVarInt(feedback.awardedPoints());
        buffer.writeVarInt(feedback.genericAwardCount());
        buffer.writeBoolean(feedback.claimedAtCap());
        buffer.writeVarInt(feedback.namedAwards().size());
        feedback.namedAwards().forEach(name -> buffer.writeUtf(
                name, PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH));
    }

    public void handle(IPayloadContext context) {
        new ClientHandler().handle(context);
    }

    /** Loaded only when this clientbound payload is handled, never during server registration. */
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private final class ClientHandler {
        void handle(IPayloadContext contextSupplier) {
            IPayloadContext context = contextSupplier;
            context.enqueueWork(() -> {
                if (!ModConfigs.RESEARCH_TREE_CLIENT.showResearchPointNotifications.get()) {
                    return;
                }
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player == null) {
                    return;
                }
                if (currency.kind() == com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM) {
                    minecraft.player.displayClientMessage(Component.translatable("message.taczweaponblueprints.currency.awarded",
                            feedback.awardedPoints(), com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyText.name(currency)), true);
                    return;
                }
                var view = ClientResearchPointPresentationState.acceptFeedback(feedback, Util.getMillis());
                Component message = view.awardedPoints() > 0
                        ? Component.translatable(
                                "message.taczweaponblueprints.research_points.awarded",
                                view.awardedPoints())
                        : Component.translatable(
                                "message.taczweaponblueprints.research_points.cap_claimed");
                if (view.namedAwards().size() == 1) {
                    message = message.copy().append(" · ")
                            .append(Component.translatable(view.namedAwards().get(0)));
                } else if (view.namedAwards().size() > 1) {
                    message = message.copy().append(" · ").append(Component.translatable(
                            "message.taczweaponblueprints.research_points.rewards",
                            view.namedAwards().size()));
                }
                if (view.awardedPoints() > 0 && view.claimedAtCap()) {
                    message = message.copy().append(" · ").append(Component.translatable(
                            "message.taczweaponblueprints.research_points.cap_claimed"));
                }
                minecraft.player.displayClientMessage(message, true);
            });
        }
    }

    Feedback feedback() {
        return feedback;
    }
}
