package com.gamergaming.taczweaponblueprints.villager.trade;

import java.util.*;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy;
import com.gamergaming.taczweaponblueprints.resource.award.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Compares a conservative whole-bundle redemption bound only in the same physical denomination. */
public final class LegacyDataTradeSafety {
    private LegacyDataTradeSafety() {}
    public static String compare(boolean optedIn, ResearchCurrencyPolicy redemption, ResourceLocation payment,
            int minimumPaid, int outputCount, long maximumPerItem) {
        if (!optedIn) return "legacy_data_exchange_not_enabled";
        if (redemption.kind()!=ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM || !redemption.item().filter(payment::equals).isPresent())
            return "legacy_data_denomination_mismatch";
        if (maximumPerItem<=0 || outputCount<1 || outputCount>64 || maximumPerItem>Long.MAX_VALUE/outputCount)
            return "legacy_data_reward_unavailable";
        return minimumPaid>maximumPerItem*outputCount ? "" : "legacy_data_redemption_reaches_minimum_price";
    }
    public static String unavailable(TradeDefinition.Output output, ResourceLocation item,
            TradeResolver.Price price, ItemStack stack) {
        var config=ModConfigs.BLUEPRINT.awardSnapshot();
        if(!config.awardsEnabled())return "legacy_data_rewards_disabled";
        var tags=stack.getTags().map(net.minecraft.tags.TagKey::location).collect(java.util.stream.Collectors.toSet());
        var context=new ResearchPointAwardContext(ResearchPointAwardTrigger.Type.INVENTORY_TURN_IN,config.activeProfileId(),
                ResearchPointAwardContext.DispatchMode.LIVE,Optional.of(item),tags,Optional.empty(),Optional.empty(),Optional.empty(),0,0,Optional.empty());
        var resolution=ResearchPointAwardResolver.resolve(ResearchPointAwardDataManager.INSTANCE.snapshot(),context);
        if(!resolution.successful())return "legacy_data_reward_unavailable";
        // Ignore player exhaustion/cooldowns: those can only reduce the authored reward, never make a cheap sale safe.
        long maximum=resolution.awards().stream().mapToLong(a->a.binding().definition().reward().points()).sum();
        return compare(output.allowLegacyExchange(),config.currencyPolicy(),price.primaryItem(),
                price.amounts().charged().minimumPrimary(),output.count(),maximum);
    }
}
