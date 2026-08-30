package com.gamergaming.taczweaponblueprints.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

public final class SyncResearchBenchPreviewPacket {
    private final int containerId;
    private final ResearchSelectionPreview preview;

    public SyncResearchBenchPreviewPacket(int containerId, ResearchSelectionPreview preview) {
        if (containerId < 0 || preview == null) {
            throw new IllegalArgumentException("invalid Research Bench preview packet");
        }
        this.containerId = containerId;
        this.preview = preview;
    }

    public SyncResearchBenchPreviewPacket(FriendlyByteBuf buffer) {
        this.containerId = buffer.readVarInt();
        if (containerId < 0) {
            throw new IllegalArgumentException("invalid Research Bench container ID");
        }
        Optional<ResourceLocation> blueprintId = readOptionalId(buffer);
        int pointCost = buffer.readVarInt();
        int pointBalance = buffer.readVarInt();
        boolean policyEligible = buffer.readBoolean();
        boolean ingredientsSatisfied = buffer.readBoolean();
        boolean outputSpace = buffer.readBoolean();
        boolean researchable = buffer.readBoolean();
        boolean creativeBypass = buffer.readBoolean();
        int ingredientCount = buffer.readVarInt();
        if (ingredientCount < 0 || ingredientCount > BlueprintResearchCost.MAX_INGREDIENT_TYPES) {
            throw new IllegalArgumentException("invalid Research Bench ingredient count");
        }
        List<ResearchSelectionPreview.IngredientPreview> ingredients =
                new ArrayList<>(ingredientCount);
        for (int index = 0; index < ingredientCount; index++) {
            int itemCount = buffer.readVarInt();
            if (itemCount < 0 || itemCount > BlueprintResearchIngredient.MAX_ITEMS) {
                throw new IllegalArgumentException("invalid Research Bench alternative count");
            }
            List<ResourceLocation> items = new ArrayList<>(itemCount);
            for (int itemIndex = 0; itemIndex < itemCount; itemIndex++) {
                items.add(readId(buffer));
            }
            Optional<ResourceLocation> tag = readOptionalId(buffer);
            ingredients.add(new ResearchSelectionPreview.IngredientPreview(
                    items, tag, buffer.readVarInt(), buffer.readVarInt()));
        }
        this.preview = new ResearchSelectionPreview(
                blueprintId, pointCost, pointBalance,
                policyEligible, ingredientsSatisfied, outputSpace,
                researchable, creativeBypass, ingredients);
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        writeOptionalId(buffer, preview.blueprintId());
        buffer.writeVarInt(preview.pointCost());
        buffer.writeVarInt(preview.pointBalance());
        buffer.writeBoolean(preview.policyEligible());
        buffer.writeBoolean(preview.ingredientsSatisfied());
        buffer.writeBoolean(preview.outputSpace());
        buffer.writeBoolean(preview.researchable());
        buffer.writeBoolean(preview.creativeBypass());
        buffer.writeVarInt(preview.ingredients().size());
        for (ResearchSelectionPreview.IngredientPreview ingredient : preview.ingredients()) {
            buffer.writeVarInt(ingredient.items().size());
            ingredient.items().forEach(id -> writeId(buffer, id));
            writeOptionalId(buffer, ingredient.tag());
            buffer.writeVarInt(ingredient.required());
            buffer.writeVarInt(ingredient.inventoryAvailable());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null
                    && minecraft.player.containerMenu.containerId == containerId
                    && minecraft.player.containerMenu instanceof ResearchBenchMenu menu) {
                menu.acceptPreview(preview);
            }
        });
        context.setPacketHandled(true);
    }

    int containerId() {
        return containerId;
    }

    ResearchSelectionPreview preview() {
        return preview;
    }

    private static Optional<ResourceLocation> readOptionalId(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? Optional.of(readId(buffer)) : Optional.empty();
    }

    private static ResourceLocation readId(FriendlyByteBuf buffer) {
        String raw = buffer.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            throw new IllegalArgumentException("invalid resource ID in Research Bench preview");
        }
        return id;
    }

    private static void writeOptionalId(FriendlyByteBuf buffer, Optional<ResourceLocation> id) {
        buffer.writeBoolean(id.isPresent());
        id.ifPresent(value -> writeId(buffer, value));
    }

    private static void writeId(FriendlyByteBuf buffer, ResourceLocation id) {
        buffer.writeUtf(id.toString(), PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
    }
}
