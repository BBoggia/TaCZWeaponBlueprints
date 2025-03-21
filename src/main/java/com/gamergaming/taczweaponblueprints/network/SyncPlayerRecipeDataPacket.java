package com.gamergaming.taczweaponblueprints.network;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.network.NetworkEvent;

import java.util.Set;
import java.util.HashSet;
import java.util.function.Supplier;

public class SyncPlayerRecipeDataPacket {
    private final Set<String> learnedRecipes;

    public SyncPlayerRecipeDataPacket(Set<String> learnedRecipes) {
        this.learnedRecipes = learnedRecipes;
    }

    public SyncPlayerRecipeDataPacket(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.learnedRecipes = new HashSet<>();
        for (int i = 0; i < size; i++) {
            this.learnedRecipes.add(buf.readUtf(32767));
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(learnedRecipes.size());
        for (String recipeId : learnedRecipes) {
            buf.writeUtf(recipeId);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                Minecraft.getInstance().player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(cap -> {
                    cap.getLearnedRecipes().clear();
                    cap.getLearnedRecipes().addAll(learnedRecipes);
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}