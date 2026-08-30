package com.gamergaming.taczweaponblueprints.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenuBridge;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerPreview;
import com.gamergaming.taczweaponblueprints.progression.BlueprintRecyclingService;
import com.gamergaming.taczweaponblueprints.progression.BlueprintReverseEngineeringService;
import com.gamergaming.taczweaponblueprints.progression.ResearchDataRedemptionService;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/** Server-authored state for the active two-slot Blueprint Analyzer. */
public final class SyncBlueprintRecyclerPreviewPacket {
    private final int containerId;
    private final BlueprintRecyclerPreview preview;

    public SyncBlueprintRecyclerPreviewPacket(int containerId, BlueprintRecyclerPreview preview) {
        if (containerId < 0 || preview == null) {
            throw new IllegalArgumentException("invalid Blueprint Recycler preview packet");
        }
        this.containerId = containerId;
        this.preview = preview;
    }

    public SyncBlueprintRecyclerPreviewPacket(FriendlyByteBuf buffer) {
        this.containerId = readContainerId(buffer);
        this.preview = readPreview(buffer);
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(preview.inputKind().ordinal());
        writeOptionalId(buffer, preview.inputId());
        buffer.writeVarInt(preview.inputCount());
        buffer.writeVarInt(preview.pointValue());
        buffer.writeVarInt(preview.pointBalance());
        buffer.writeVarInt(preview.pointCap());
        writeOptionalEnum(buffer, preview.recyclingStatus());
        writeOptionalEnum(buffer, preview.researchDataStatus());
        buffer.writeVarLong(preview.stateToken());
        writeOptionalId(buffer, preview.outputBlueprintId());
        buffer.writeVarInt(preview.requiredInputCount());
        buffer.writeVarInt(preview.pointCost());
        buffer.writeBoolean(preview.ingredientsSatisfied());
        buffer.writeBoolean(preview.outputAvailable());
        buffer.writeBoolean(preview.customizationWillBeLost());
        buffer.writeBoolean(preview.alreadyKnown());
        writeOptionalEnum(buffer, preview.reverseEngineeringStatus());
        buffer.writeVarInt(preview.ingredients().size());
        for (BlueprintRecyclerPreview.IngredientPreview ingredient : preview.ingredients()) {
            buffer.writeVarInt(ingredient.items().size());
            ingredient.items().forEach(id -> buffer.writeUtf(
                    id.toString(), PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH));
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
                    && minecraft.player.containerMenu instanceof BlueprintRecyclerMenuBridge menu) {
                menu.acceptRecyclerPreview(preview);
            }
        });
        context.setPacketHandled(true);
    }

    int containerId() {
        return containerId;
    }

    BlueprintRecyclerPreview preview() {
        return preview;
    }

    private static BlueprintRecyclerPreview readPreview(FriendlyByteBuf buffer) {
        BlueprintRecyclerPreview.InputKind inputKind = readEnum(
                buffer, BlueprintRecyclerPreview.InputKind.values(), "input kind");
        Optional<ResourceLocation> inputId = readOptionalId(buffer);
        int inputCount = buffer.readVarInt();
        int pointValue = buffer.readVarInt();
        int pointBalance = buffer.readVarInt();
        int pointCap = buffer.readVarInt();
        Optional<BlueprintRecyclingService.Status> recyclingStatus = readOptionalEnum(
                buffer, BlueprintRecyclingService.Status.values(), "recycling status");
        Optional<ResearchDataRedemptionService.Status> researchDataStatus = readOptionalEnum(
                buffer, ResearchDataRedemptionService.Status.values(), "Research Data status");
        long stateToken = buffer.readVarLong();
        Optional<ResourceLocation> outputBlueprintId = readOptionalId(buffer);
        int requiredInputCount = buffer.readVarInt();
        int pointCost = buffer.readVarInt();
        boolean ingredientsSatisfied = buffer.readBoolean();
        boolean outputAvailable = buffer.readBoolean();
        boolean customizationWillBeLost = buffer.readBoolean();
        boolean alreadyKnown = buffer.readBoolean();
        Optional<BlueprintReverseEngineeringService.Status> reverseStatus = readOptionalEnum(
                buffer,
                BlueprintReverseEngineeringService.Status.values(),
                "reverse-engineering status");
        int ingredientCount = buffer.readVarInt();
        if (ingredientCount < 0 || ingredientCount > BlueprintResearchCost.MAX_INGREDIENT_TYPES) {
            throw new IllegalArgumentException(
                    "invalid Blueprint Analyzer ingredient count");
        }
        List<BlueprintRecyclerPreview.IngredientPreview> ingredients =
                new ArrayList<>(ingredientCount);
        for (int index = 0; index < ingredientCount; index++) {
            int itemCount = buffer.readVarInt();
            if (itemCount < 0 || itemCount > BlueprintResearchIngredient.MAX_ITEMS) {
                throw new IllegalArgumentException(
                        "invalid Blueprint Analyzer ingredient alternative count");
            }
            List<ResourceLocation> items = new ArrayList<>(itemCount);
            for (int itemIndex = 0; itemIndex < itemCount; itemIndex++) {
                items.add(readId(buffer));
            }
            ingredients.add(new BlueprintRecyclerPreview.IngredientPreview(
                    items,
                    readOptionalId(buffer),
                    buffer.readVarInt(),
                    buffer.readVarInt()));
        }
        return new BlueprintRecyclerPreview(
                inputKind,
                inputId,
                inputCount,
                pointValue,
                pointBalance,
                pointCap,
                recyclingStatus,
                researchDataStatus,
                stateToken,
                outputBlueprintId,
                requiredInputCount,
                pointCost,
                ingredientsSatisfied,
                outputAvailable,
                customizationWillBeLost,
                alreadyKnown,
                reverseStatus,
                ingredients);
    }

    private static int readContainerId(FriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        if (containerId < 0) {
            throw new IllegalArgumentException("invalid Blueprint Recycler container ID");
        }
        return containerId;
    }

    private static Optional<ResourceLocation> readOptionalId(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return Optional.empty();
        }
        String raw = buffer.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            throw new IllegalArgumentException("invalid Blueprint Recycler input ID");
        }
        return Optional.of(id);
    }

    private static ResourceLocation readId(FriendlyByteBuf buffer) {
        String raw = buffer.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            throw new IllegalArgumentException("invalid Blueprint Analyzer resource ID");
        }
        return id;
    }

    private static void writeOptionalId(
            FriendlyByteBuf buffer,
            Optional<ResourceLocation> inputId) {
        buffer.writeBoolean(inputId.isPresent());
        inputId.ifPresent(id -> buffer.writeUtf(
                id.toString(), PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH));
    }

    private static <T extends Enum<T>> Optional<T> readOptionalEnum(
            FriendlyByteBuf buffer,
            T[] values,
            String description) {
        return buffer.readBoolean()
                ? Optional.of(readEnum(buffer, values, description))
                : Optional.empty();
    }

    private static <T extends Enum<T>> T readEnum(
            FriendlyByteBuf buffer,
            T[] values,
            String description) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("invalid Blueprint Recycler " + description);
        }
        return values[ordinal];
    }

    private static <T extends Enum<T>> void writeOptionalEnum(
            FriendlyByteBuf buffer,
            Optional<T> value) {
        buffer.writeBoolean(value.isPresent());
        value.ifPresent(status -> buffer.writeVarInt(status.ordinal()));
    }
}
