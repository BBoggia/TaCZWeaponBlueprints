package com.gamergaming.taczweaponblueprints.network;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class SyncBlueprintDataPacket {
    private final Map<ResourceLocation, BlueprintData> blueprintDataMap;

    public SyncBlueprintDataPacket(Map<ResourceLocation, BlueprintData> blueprintDataMap) {
        this.blueprintDataMap = blueprintDataMap;
    }

    public SyncBlueprintDataPacket(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        blueprintDataMap = new HashMap<>();
        for (int i = 0; i < size; i++) {
            ResourceLocation bpId = buf.readResourceLocation();
            String nameKey = buf.readUtf();
            String tooltipKey = buf.readUtf();
            ResourceLocation recipeId = buf.readResourceLocation();
            String itemType = buf.readUtf();
            ResourceLocation displaySlotKey = buf.readResourceLocation();

            BlueprintData data = new BlueprintData(bpId.toString(), nameKey, tooltipKey, recipeId, null, itemType, displaySlotKey);
            blueprintDataMap.put(bpId, data);
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(blueprintDataMap.size());
        for (Map.Entry<ResourceLocation, BlueprintData> entry : blueprintDataMap.entrySet()) {
            BlueprintData data = entry.getValue();
            buf.writeResourceLocation(new ResourceLocation(data.getBpId()));
            buf.writeUtf(data.getNameKey());
            buf.writeUtf(data.getTooltipKey());
            buf.writeResourceLocation(data.getRecipeId());
            buf.writeUtf(data.getItemType());
            buf.writeResourceLocation(data.getDisplaySlotKey());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Update the BlueprintDataManager on client side
            BlueprintDataManager.INSTANCE.setBlueprintDataMap(blueprintDataMap);
        });
        ctx.get().setPacketHandled(true);
    }
}