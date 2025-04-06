//package com.gamergaming.taczweaponblueprints.network;
//
//import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
//import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
//import net.minecraft.network.FriendlyByteBuf;
//import net.minecraft.resources.ResourceLocation;
//import net.minecraftforge.network.NetworkEvent;
//import net.minecraft.client.Minecraft;
//import java.util.HashSet;
//import java.util.Set;
//import java.util.function.Supplier;
//
//public class PlayerRecipeDataSyncPacket {
//    private final Set<String> learnedRecipes;
//
//    public PlayerRecipeDataSyncPacket(Set<String> learnedRecipes) {
//        this.learnedRecipes = learnedRecipes;
//    }
//
//    public PlayerRecipeDataSyncPacket(FriendlyByteBuf buf) {
//        int size = buf.readVarInt();
//        this.learnedRecipes = new HashSet<>();
//        for (int i = 0; i < size; i++) {
//            this.learnedRecipes.add(buf.readUtf(32767));
//        }
//    }
//
//    public void toBytes(FriendlyByteBuf buf) {
//        buf.writeVarInt(learnedRecipes.size());
//        for (String recipeId : learnedRecipes) {
//            buf.writeUtf(recipeId);
//        }
//    }
//
////    public static void handle(PlayerRecipeDataSyncPacket message, Supplier<NetworkEvent.Context> ctx) {
////        ctx.get().enqueueWork(() -> {
////            net.minecraft.client.Minecraft.getInstance().player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(cap -> {
////                cap.getLearnedRecipes().clear();
////                cap.getLearnedRecipes().addAll(message.learnedRecipes);
////            });
////        });
////        ctx.get().setPacketHandled(true);
////    }
//
//    public static void handle(PlayerRecipeDataSyncPacket message, Supplier<NetworkEvent.Context> ctx) {
//        ctx.get().enqueueWork(() -> {
//            // Client-side handling
//            Minecraft mc = Minecraft.getInstance();
//            if (mc.player != null) {
//                mc.player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(cap -> {
//                    cap.getLearnedRecipes().clear();
//                    cap.getLearnedRecipes().addAll(message.learnedRecipes);
//                    // Log the synchronization
//                    TaCZWeaponBlueprints.LOGGER.debug("Synchronized learned recipes: " + message.learnedRecipes);
//                });
//            } else {
//                TaCZWeaponBlueprints.LOGGER.warn("Player entity is null during recipe data synchronization.");
//            }
//        });
//        ctx.get().setPacketHandled(true);
//    }
//}