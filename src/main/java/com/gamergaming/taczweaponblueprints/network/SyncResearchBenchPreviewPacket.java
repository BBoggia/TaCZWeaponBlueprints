package com.gamergaming.taczweaponblueprints.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionProgressionPreview;
import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.progression.ResearchRouteFingerprint;
import com.gamergaming.taczweaponblueprints.progression.ProgressionIds;
import com.gamergaming.taczweaponblueprints.progression.DisclosedCraftingAccess;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchAccessSummary;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintCraftingDisposition;

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
        int unlockCount = buffer.readVarInt();
        int totalIngredientTypes = buffer.readVarInt();
        int pathPlanningStateOrdinal = buffer.readVarInt();
        ResearchSelectionPreview.PathPlanningState[] pathPlanningStates =
                ResearchSelectionPreview.PathPlanningState.values();
        if (pathPlanningStateOrdinal < 0
                || pathPlanningStateOrdinal >= pathPlanningStates.length) {
            throw new IllegalArgumentException("invalid Research Bench path-planning state");
        }
        ResearchSelectionPreview.PathPlanningState pathPlanningState =
                pathPlanningStates[pathPlanningStateOrdinal];
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
        int costModeOrdinal = buffer.readVarInt();
        ResearchCostMode[] costModes = ResearchCostMode.values();
        if (costModeOrdinal < 0 || costModeOrdinal >= costModes.length) {
            throw new IllegalArgumentException("invalid Research Bench cost mode");
        }
        Optional<ResearchRouteFingerprint> routeFingerprint = buffer.readBoolean()
                ? Optional.of(new ResearchRouteFingerprint(
                        buffer.readLong(), buffer.readLong()))
                : Optional.empty();
        ResearchAccessSummary accessSummary = readAccessSummary(buffer);
        ResearchSelectionProgressionPreview progression = readProgression(buffer);
        this.preview = new ResearchSelectionPreview(
                blueprintId, pointCost, pointBalance,
                policyEligible, ingredientsSatisfied, outputSpace,
                researchable, creativeBypass, ingredients,
                unlockCount, totalIngredientTypes, pathPlanningState,
                costModes[costModeOrdinal], routeFingerprint, accessSummary, progression);
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
        buffer.writeVarInt(preview.unlockCount());
        buffer.writeVarInt(preview.ingredientTypeCount());
        buffer.writeVarInt(preview.pathPlanningState().ordinal());
        buffer.writeVarInt(preview.ingredients().size());
        for (ResearchSelectionPreview.IngredientPreview ingredient : preview.ingredients()) {
            buffer.writeVarInt(ingredient.items().size());
            ingredient.items().forEach(id -> writeId(buffer, id));
            writeOptionalId(buffer, ingredient.tag());
            buffer.writeVarInt(ingredient.required());
            buffer.writeVarInt(ingredient.inventoryAvailable());
        }
        buffer.writeVarInt(preview.costMode().ordinal());
        buffer.writeBoolean(preview.routeFingerprint().isPresent());
        preview.routeFingerprint().ifPresent(fingerprint -> {
            buffer.writeLong(fingerprint.high());
            buffer.writeLong(fingerprint.low());
        });
        writeAccessSummary(buffer, preview.accessSummary());
        writeProgression(buffer, preview.progression());
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

    private static ResearchAccessSummary readAccessSummary(FriendlyByteBuf buffer) {
        int ordinal = buffer.readVarInt();
        ResearchAccessSummary.Kind[] kinds = ResearchAccessSummary.Kind.values();
        if (ordinal < 0 || ordinal >= kinds.length) {
            throw new IllegalArgumentException("invalid research access-summary kind");
        }
        Optional<ResearchWorkbenchTier> current = readOptionalTier(buffer);
        Optional<ResearchWorkbenchTier> required = readOptionalTier(buffer);
        Optional<String> messageKey = buffer.readBoolean()
                ? Optional.of(buffer.readUtf(ProgressionIds.MAX_MESSAGE_KEY_LENGTH))
                : Optional.empty();
        return new ResearchAccessSummary(kinds[ordinal], current, required, messageKey);
    }

    private static void writeAccessSummary(
            FriendlyByteBuf buffer,
            ResearchAccessSummary summary) {
        buffer.writeVarInt(summary.kind().ordinal());
        writeOptionalTier(buffer, summary.currentTier());
        writeOptionalTier(buffer, summary.requiredTier());
        buffer.writeBoolean(summary.messageKey().isPresent());
        summary.messageKey().ifPresent(key ->
                buffer.writeUtf(key, ProgressionIds.MAX_MESSAGE_KEY_LENGTH));
    }

    private static ResearchSelectionProgressionPreview readProgression(
            FriendlyByteBuf buffer) {
        Optional<ResearchWorkbenchTier> current = readOptionalTier(buffer);
        Optional<ResearchWorkbenchTier> required = readOptionalTier(buffer);
        Optional<ResearchSelectionProgressionPreview.FragmentProgress> fragments =
                Optional.empty();
        if (buffer.readBoolean()) {
            int modeOrdinal = buffer.readVarInt();
            BlueprintFragmentPolicy.CompletionMode[] modes =
                    BlueprintFragmentPolicy.CompletionMode.values();
            if (modeOrdinal < 0 || modeOrdinal >= modes.length) {
                throw new IllegalArgumentException(
                        "invalid Blueprint Fragment completion mode");
            }
            fragments = Optional.of(new ResearchSelectionProgressionPreview.FragmentProgress(
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    modes[modeOrdinal],
                    buffer.readBoolean()));
        }
        return new ResearchSelectionProgressionPreview(
                current,
                required,
                fragments,
                readCraftingAccess(buffer));
    }

    private static void writeProgression(
            FriendlyByteBuf buffer,
            ResearchSelectionProgressionPreview progression) {
        writeOptionalTier(buffer, progression.currentTier());
        writeOptionalTier(buffer, progression.requiredTier());
        buffer.writeBoolean(progression.fragments().isPresent());
        progression.fragments().ifPresent(fragments -> {
            buffer.writeVarInt(fragments.completionMode().ordinal());
            buffer.writeVarInt(fragments.archived());
            buffer.writeVarInt(fragments.threshold());
            buffer.writeBoolean(fragments.discountApplied());
        });
        writeCraftingAccess(buffer, progression.craftingAccess());
    }

    private static Optional<DisclosedCraftingAccess> readCraftingAccess(
            FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return Optional.empty();
        }
        int ordinal = buffer.readVarInt();
        BlueprintCraftingDisposition[] dispositions = BlueprintCraftingDisposition.values();
        if (ordinal < 0 || ordinal >= dispositions.length) {
            throw new IllegalArgumentException("invalid disclosed crafting disposition");
        }
        return Optional.of(new DisclosedCraftingAccess(
                dispositions[ordinal], readOptionalTier(buffer)));
    }

    private static void writeCraftingAccess(
            FriendlyByteBuf buffer,
            Optional<DisclosedCraftingAccess> access) {
        buffer.writeBoolean(access.isPresent());
        access.ifPresent(value -> {
            buffer.writeVarInt(value.disposition().ordinal());
            writeOptionalTier(buffer, value.requiredWorkbenchTier());
        });
    }

    private static Optional<ResearchWorkbenchTier> readOptionalTier(FriendlyByteBuf buffer) {
        return buffer.readBoolean()
                ? Optional.of(ResearchWorkbenchTier.fromLevel(buffer.readVarInt()))
                : Optional.empty();
    }

    private static void writeOptionalTier(
            FriendlyByteBuf buffer,
            Optional<ResearchWorkbenchTier> tier) {
        buffer.writeBoolean(tier.isPresent());
        tier.ifPresent(value -> buffer.writeVarInt(value.level()));
    }
}
