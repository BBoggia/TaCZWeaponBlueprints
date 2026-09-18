package com.gamergaming.taczweaponblueprints.mixin;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import com.gamergaming.taczweaponblueprints.villager.stock.StockPublicationCoordinator;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(MinecraftServer.class)
public abstract class VillagerServerReloadMixin {
    // reloadResources can enter nested managedBlock calls on the server thread.
    @Unique private final ThreadLocal<ArrayDeque<Long>> taczweaponblueprints$reloads=ThreadLocal.withInitial(ArrayDeque::new);
    @Inject(method="reloadResources",at=@At("HEAD"))
    private void begin(Collection<String> packs,CallbackInfoReturnable<CompletableFuture<Void>> ci){
        taczweaponblueprints$reloads.get().push(StockPublicationCoordinator.beginReload());
    }
    @Inject(method="reloadResources",at=@At("RETURN"))
    private void complete(Collection<String> packs,CallbackInfoReturnable<CompletableFuture<Void>> ci){
        long attempt=taczweaponblueprints$reloads.get().pop();
        if(taczweaponblueprints$reloads.get().isEmpty())taczweaponblueprints$reloads.remove();
        var server=(MinecraftServer)(Object)this;
        ci.getReturnValue().whenComplete((unused,error)->server.execute(()->StockPublicationCoordinator.finished(server,attempt,error)));
    }
}
