package com.gamergaming.taczweaponblueprints.villager;

import java.util.List;
import com.gamergaming.taczweaponblueprints.init.ModVillagers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;

/** A quote belongs to one open native menu; stock usage itself is not a price revision. */
public final class VillagerMerchantSession {
    private static final int MAX_OFFERS = com.gamergaming.taczweaponblueprints.villager.trade.TradeLimits.OWNED_OFFERS;
    private final Merchant merchant;
    private final List<Quote> quotes;
    private final net.minecraft.world.entity.npc.VillagerProfession profession;
    private static final java.util.concurrent.atomic.AtomicLong NEXT_SESSION=new java.util.concurrent.atomic.AtomicLong();
    private final long session=NEXT_SESSION.incrementAndGet();
    private long statusRevision;
    private Object lastStatusPublication;
    private List<VillagerTradeStatus.Row> lastStatus;
    private record Quote(MerchantOffer offer, VillagerOfferTerms terms, ItemStack primary, ItemStack secondary, ItemStack result, List<Object> dependencies) {
        Quote(MerchantOffer offer) {
            this(offer, ((OwnedVillagerOffer) offer).taczweaponblueprints$terms(),
                    offer.getCostA().copy(), offer.getCostB().copy(), offer.getResult().copy(),VillagerMerchantSession.dependencies(offer));
        }
        boolean current(MerchantOffer candidate) {
            return candidate == offer && java.util.Objects.equals(terms, ((OwnedVillagerOffer) candidate).taczweaponblueprints$terms())
                    && ItemStack.matches(primary, candidate.getCostA()) && ItemStack.matches(secondary, candidate.getCostB())
                    && ItemStack.matches(result, candidate.getResult()) && dependencies.equals(VillagerMerchantSession.dependencies(candidate));
        }
    }
    private static List<Object> dependencies(MerchantOffer offer){
        var binding=((OwnedVillagerOffer)offer).taczweaponblueprints$binding();
        return binding==null?List.of():com.gamergaming.taczweaponblueprints.villager.stock.StockCatalog.current().merchandiseTerms(binding.entry());
    }
    private VillagerMerchantSession(Merchant merchant) {
        this.merchant = merchant;
        profession=((Villager)merchant).getVillagerData().getProfession();
        var offers = merchant.getOffers();
        quotes = offers.size() > MAX_OFFERS ? List.of() : offers.stream().map(Quote::new).toList();
    }
    public static VillagerMerchantSession capture(Merchant merchant) {
        if (!(merchant instanceof Villager villager) || merchant.isClientSide() || !ModVillagers.owns(villager.getVillagerData().getProfession())) return null;
        return new VillagerMerchantSession(merchant);
    }
    public boolean validate(Player player, MerchantMenu menu) {
        if (merchant.isClientSide()) return true;
        if(com.gamergaming.taczweaponblueprints.villager.stock.StockPublicationCoordinator.pending())return false;
        var before=com.gamergaming.taczweaponblueprints.villager.stock.StockCatalog.current();
        if(!before.coherent())return false;
        var offers = merchant.getOffers();
        boolean valid = player.containerMenu == menu && merchant.getTradingPlayer() == player
                && player.isAlive() && merchant instanceof Villager v && v.isAlive() && !v.isRemoved() && v.level()==player.level()
                && v.level().hasChunkAt(v.blockPosition()) && ((net.minecraft.server.level.ServerLevel)v.level()).getEntity(v.getUUID())==v
                && v.getVillagerData().getProfession()==profession && v.distanceToSqr(player) <= 64
                && ModVillagers.owns(v.getVillagerData().getProfession()) && offers.size() <= MAX_OFFERS && offers.size() == quotes.size();
        for (int i = 0; valid && i < quotes.size(); i++) valid = quotes.get(i).current(offers.get(i));
        valid &= before==com.gamergaming.taczweaponblueprints.villager.stock.StockCatalog.current();
        if(valid && player instanceof ServerPlayer serverPlayer && lastStatusPublication!=before){
            var status=VillagerTradeStatus.capture(offers,player.registryAccess());lastStatusPublication=before;
            if(!status.equals(lastStatus)){
                lastStatus=status;
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer, new com.gamergaming.taczweaponblueprints.network.SyncVillagerTradeStatusPacket(menu.containerId,session,++statusRevision,status));
            }
        }
        if (!valid && player instanceof ServerPlayer serverPlayer && player.containerMenu == menu) {
            serverPlayer.closeContainer();
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable("merchant.taczweaponblueprints.changed"),true);
        }
        return valid;
    }
}
