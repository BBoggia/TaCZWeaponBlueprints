package com.gamergaming.taczweaponblueprints.api.event;

import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.Event;

/** Physical currency event boundary. Banked mode retains ResearchPointAwardEvent. */
public abstract class ResearchCurrencyAwardEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation definitionId;
    private final ResearchPointAwardContext context;
    private final ResearchCurrencyPolicy currency;
    private final int units;
    protected ResearchCurrencyAwardEvent(ServerPlayer player, ResourceLocation definitionId,
            ResearchPointAwardContext context, ResearchCurrencyPolicy currency, int units) {
        this.player = java.util.Objects.requireNonNull(player); this.definitionId = java.util.Objects.requireNonNull(definitionId);
        this.context = java.util.Objects.requireNonNull(context); this.currency = java.util.Objects.requireNonNull(currency);
        if (units < 1) throw new IllegalArgumentException("currency award must be positive");
        this.units = units;
    }
    public ServerPlayer getPlayer() { return player; }
    public ResourceLocation getDefinitionId() { return definitionId; }
    public ResearchPointAwardContext getAwardContext() { return context; }
    public ResearchCurrencyPolicy getCurrency() { return currency; }
    public int getUnits() { return units; }
    public static final class Pre extends ResearchCurrencyAwardEvent implements ICancellableEvent {
        public Pre(ServerPlayer player, ResourceLocation id, ResearchPointAwardContext context, ResearchCurrencyPolicy currency, int units) {
            super(player, id, context, currency, units);
        }
    }
    public static final class Post extends ResearchCurrencyAwardEvent {
        public Post(ServerPlayer player, ResourceLocation id, ResearchPointAwardContext context, ResearchCurrencyPolicy currency, int deliveredUnits) {
            super(player, id, context, currency, deliveredUnits);
        }
    }
}
