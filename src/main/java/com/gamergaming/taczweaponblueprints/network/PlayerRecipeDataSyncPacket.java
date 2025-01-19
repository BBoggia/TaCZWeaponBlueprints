package com.gamergaming.taczweaponblueprints.network;

import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class PlayerRecipeDataSyncPacket {
    private final Set<String> learnedRecipes;

    public PlayerRecipeDataSyncPacket(Set<String> learnedRecipes) {
        this.learnedRecipes = learnedRecipes;
    }

    public PlayerRecipeDataSyncPacket(FriendlyByteBuf buf) {
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

    public static void handle(PlayerRecipeDataSyncPacket message, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // This is client-side
            net.minecraft.client.Minecraft.getInstance().player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(cap -> {
                cap.getLearnedRecipes().clear();
                cap.getLearnedRecipes().addAll(message.learnedRecipes);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}