package com.gamergaming.taczweaponblueprints.api;

import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Currency-aware integration awards; source IDs and claim history are shared with the legacy API. */
public final class ResearchCurrencyAwards {
    private ResearchCurrencyAwards() { }
    public static boolean registerSource(ResourceLocation id) { return ResearchPointAwards.registerSource(id); }
    public static boolean isRegistered(ResourceLocation id) { return ResearchPointAwards.isRegistered(id); }
    public static java.util.List<ResourceLocation> registeredSources() { return ResearchPointAwards.registeredSources(); }
    public static Result trigger(ServerPlayer player, ResourceLocation id) {
        return triggerInternal(player, id, isRegistered(id));
    }
    public static Result triggerFromCommand(CommandSourceStack source, ServerPlayer player, ResourceLocation id) {
        return triggerInternal(player, id, source != null && source.hasPermission(2) && player != null && source.getServer() == player.server);
    }
    private static Result triggerInternal(ServerPlayer player, ResourceLocation id, boolean trusted) {
        var currency = ModConfigs.BLUEPRINT.currencyPolicy();
        var result = ResearchPointAwards.triggerInternal(player, id, trusted);
        return new Result(result.status(), currency, result.awardedPoints(), result.committedAwards());
    }
    public record Result(ResearchPointAwards.Status status, ResearchCurrencyPolicy currency, int awardedUnits, int committedAwards) {
        public Result {
            if (status == null || currency == null || awardedUnits < 0 || awardedUnits > 1_000_000_000
                    || committedAwards < 0 || committedAwards > com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_GROUPS_PER_EVENT)
                throw new IllegalArgumentException("invalid currency award result");
        }
        public boolean successful() { return status == ResearchPointAwards.Status.TRIGGERED; }
    }
}
