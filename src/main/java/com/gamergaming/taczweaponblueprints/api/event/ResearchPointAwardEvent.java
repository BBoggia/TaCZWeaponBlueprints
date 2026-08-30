package com.gamergaming.taczweaponblueprints.api.event;

import com.gamergaming.taczweaponblueprints.progression.ResearchPointAwardService;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/** Server-only observation boundary around one already-resolved RP award. */
public abstract class ResearchPointAwardEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation definitionId;
    private final ResearchPointAwardContext context;
    private final int requestedPoints;

    protected ResearchPointAwardEvent(
            ServerPlayer player,
            ResourceLocation definitionId,
            ResearchPointAwardContext context,
            int requestedPoints) {
        if (player == null || definitionId == null || context == null || requestedPoints <= 0) {
            throw new IllegalArgumentException("invalid Research Point award event");
        }
        this.player = player;
        this.definitionId = definitionId;
        this.context = context;
        this.requestedPoints = requestedPoints;
    }

    public final ServerPlayer getPlayer() {
        return player;
    }

    public final ResourceLocation getDefinitionId() {
        return definitionId;
    }

    public final ResearchPointAwardContext getAwardContext() {
        return context;
    }

    /** Immutable datapack-authored amount; listeners cannot replace it. */
    public final int getRequestedPoints() {
        return requestedPoints;
    }

    /** Fired after eligibility but before any balance or ledger mutation. */
    @Cancelable
    public static final class Pre extends ResearchPointAwardEvent {
        public Pre(
                ServerPlayer player,
                ResourceLocation definitionId,
                ResearchPointAwardContext context,
                int requestedPoints) {
            super(player, definitionId, context, requestedPoints);
        }
    }

    /** Fired only after an atomic committed point/ledger result. */
    public static final class Post extends ResearchPointAwardEvent {
        private final ResearchPointAwardService.Status status;
        private final int awardedPoints;

        public Post(
                ServerPlayer player,
                ResourceLocation definitionId,
                ResearchPointAwardContext context,
                int requestedPoints,
                ResearchPointAwardService.Status status,
                int awardedPoints) {
            super(player, definitionId, context, requestedPoints);
            if (status == null || !status.committed() || awardedPoints < 0
                    || awardedPoints > requestedPoints) {
                throw new IllegalArgumentException("post-award event requires a committed result");
            }
            this.status = status;
            this.awardedPoints = awardedPoints;
        }

        public ResearchPointAwardService.Status getStatus() {
            return status;
        }

        public int getAwardedPoints() {
            return awardedPoints;
        }
    }
}
