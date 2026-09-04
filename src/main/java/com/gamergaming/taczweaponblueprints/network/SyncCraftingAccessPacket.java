package com.gamergaming.taczweaponblueprints.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.client.ClientBlueprintCatalog;
import com.gamergaming.taczweaponblueprints.client.ClientCraftingAccessState;
import com.gamergaming.taczweaponblueprints.progression.CraftingEligibilityService;
import com.gamergaming.taczweaponblueprints.progression.CraftingEligibilityService.AccessIdentity;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.tacz.guns.inventory.GunSmithTableMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/** Bounded, session-scoped recipe access for one exact native crafting menu. */
public final class SyncCraftingAccessPacket {
    private static final ClientAccumulator CLIENT_ACCUMULATOR = new ClientAccumulator();

    private final int containerId;
    private final long requestId;
    private final long snapshotId;
    private final Optional<AccessIdentity> accessIdentity;
    private final int chunkIndex;
    private final int chunkCount;
    private final CraftingEligibilityService.Status status;
    private final Set<String> allowedRecipeIds;

    private SyncCraftingAccessPacket(
            int containerId,
            long requestId,
            long snapshotId,
            Optional<AccessIdentity> accessIdentity,
            int chunkIndex,
            int chunkCount,
            CraftingEligibilityService.Status status,
            Set<String> allowedRecipeIds) {
        validateMetadata(containerId, requestId, snapshotId, chunkIndex, chunkCount);
        accessIdentity = accessIdentity == null ? Optional.empty() : accessIdentity;
        if (status == null || allowedRecipeIds == null
                || status != CraftingEligibilityService.Status.ALLOWED
                        && !allowedRecipeIds.isEmpty()
                || requiresIdentity(status) && accessIdentity.isEmpty()) {
            throw new IllegalArgumentException("invalid crafting access snapshot");
        }
        this.containerId = containerId;
        this.requestId = requestId;
        this.snapshotId = snapshotId;
        this.accessIdentity = accessIdentity;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
        this.status = status;
        this.allowedRecipeIds = normalized(allowedRecipeIds);
        if (estimatedPayloadBytes() > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("crafting access chunk exceeds byte budget");
        }
    }

    public SyncCraftingAccessPacket(FriendlyByteBuf buffer) {
        int start = buffer.readerIndex();
        containerId = buffer.readVarInt();
        requestId = buffer.readLong();
        snapshotId = buffer.readLong();
        accessIdentity = readIdentity(buffer);
        chunkIndex = buffer.readVarInt();
        chunkCount = buffer.readVarInt();
        validateMetadata(containerId, requestId, snapshotId, chunkIndex, chunkCount);
        status = buffer.readEnum(CraftingEligibilityService.Status.class);
        int size = buffer.readVarInt();
        if (size < 0 || size > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
            throw new IllegalArgumentException("invalid crafting access payload");
        }
        TreeSet<String> decoded = new TreeSet<>();
        for (int index = 0; index < size; index++) {
            String id = buffer.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
            if (net.minecraft.resources.ResourceLocation.tryParse(id) == null
                    || !decoded.add(id)) {
                throw new IllegalArgumentException("invalid crafting recipe ID");
            }
        }
        if (requiresIdentity(status) && accessIdentity.isEmpty()
                || status != CraftingEligibilityService.Status.ALLOWED && !decoded.isEmpty()
                || buffer.readerIndex() - start > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("invalid crafting access payload shape");
        }
        allowedRecipeIds = Collections.unmodifiableSet(decoded);
    }

    public static List<SyncCraftingAccessPacket> split(
            int containerId,
            long requestId,
            long snapshotId,
            CraftingEligibilityService.Snapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("crafting access snapshot cannot be null");
        }
        Set<String> normalizedIds = normalized(snapshot.allowedRecipeIds());
        if (snapshot.status() != CraftingEligibilityService.Status.ALLOWED
                && !normalizedIds.isEmpty()) {
            throw new IllegalArgumentException("unavailable crafting access cannot disclose recipes");
        }

        List<Set<String>> chunks = new ArrayList<>();
        TreeSet<String> current = new TreeSet<>();
        int estimatedBytes = BlueprintSyncLimits.CHUNK_HEADER_RESERVE
                + estimatedIdentityBytes(snapshot.accessIdentity());
        for (String id : normalizedIds) {
            int entryBytes = BlueprintSyncLimits.encodedUtfBytes(id);
            if (entryBytes + BlueprintSyncLimits.CHUNK_HEADER_RESERVE
                    + estimatedIdentityBytes(snapshot.accessIdentity())
                    > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
                throw new IllegalArgumentException("crafting recipe ID cannot fit in one chunk");
            }
            if (!current.isEmpty()
                    && estimatedBytes + entryBytes > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
                chunks.add(Collections.unmodifiableSet(current));
                current = new TreeSet<>();
                estimatedBytes = BlueprintSyncLimits.CHUNK_HEADER_RESERVE
                        + estimatedIdentityBytes(snapshot.accessIdentity());
            }
            current.add(id);
            estimatedBytes += entryBytes;
        }
        if (!current.isEmpty() || chunks.isEmpty()) {
            chunks.add(Collections.unmodifiableSet(current));
        }
        if (chunks.size() > BlueprintSyncLimits.MAX_CHUNKS_PER_SNAPSHOT) {
            throw new IllegalArgumentException("crafting access requires too many chunks");
        }

        List<SyncCraftingAccessPacket> packets = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            packets.add(new SyncCraftingAccessPacket(
                    containerId,
                    requestId,
                    snapshotId,
                    snapshot.accessIdentity(),
                    index,
                    chunks.size(),
                    snapshot.status(),
                    chunks.get(index)));
        }
        return List.copyOf(packets);
    }

    public void toBytes(FriendlyByteBuf buffer) {
        int start = buffer.writerIndex();
        buffer.writeVarInt(containerId);
        buffer.writeLong(requestId);
        buffer.writeLong(snapshotId);
        writeIdentity(buffer, accessIdentity);
        buffer.writeVarInt(chunkIndex);
        buffer.writeVarInt(chunkCount);
        buffer.writeEnum(status);
        buffer.writeVarInt(allowedRecipeIds.size());
        allowedRecipeIds.forEach(id -> buffer.writeUtf(
                id, PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH));
        if (buffer.writerIndex() - start > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("crafting access payload exceeds byte budget");
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ClientAccumulator.Acceptance acceptance = CLIENT_ACCUMULATOR.accept(this);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null
                    || minecraft.player.containerMenu.containerId != containerId
                    || !(minecraft.player.containerMenu instanceof GunSmithTableMenu)) {
                return;
            }
            boolean changed = acceptance.startedSnapshot()
                    && ClientCraftingAccessState.beginSnapshot(
                            containerId, requestId, snapshotId, accessIdentity);
            if (acceptance.completedSnapshot().isPresent()) {
                Snapshot snapshot = acceptance.completedSnapshot().orElseThrow();
                boolean accepted = ClientCraftingAccessState.accept(
                        snapshot.containerId(),
                        snapshot.requestId(),
                        snapshot.snapshotId(),
                        snapshot.accessIdentity(),
                        snapshot.status(),
                        snapshot.allowedRecipeIds());
                changed |= accepted;
                TaCZWeaponBlueprints.LOGGER.info(
                        "Workbench recipe diagnostics [packet]: container={}, request={}, "
                                + "snapshot={}, chunks={}, status={}, receivedRecipes={}, "
                                + "acceptedByActiveMenu={}, recipeSample={}",
                        snapshot.containerId(),
                        snapshot.requestId(),
                        snapshot.snapshotId(),
                        chunkCount,
                        snapshot.status(),
                        snapshot.allowedRecipeIds().size(),
                        accepted,
                        snapshot.allowedRecipeIds().stream().limit(8).toList());
            }
            if (changed) {
                ClientBlueprintCatalog.refreshOpenGunSmithScreen();
            }
        });
        context.setPacketHandled(true);
    }

    public static void clearClientState() {
        CLIENT_ACCUMULATOR.clear();
    }

    private int estimatedPayloadBytes() {
        int size = BlueprintSyncLimits.CHUNK_HEADER_RESERVE
                + estimatedIdentityBytes(accessIdentity);
        for (String id : allowedRecipeIds) {
            size += BlueprintSyncLimits.encodedUtfBytes(id);
        }
        return size;
    }

    private static void writeIdentity(
            FriendlyByteBuf buffer,
            Optional<AccessIdentity> identity) {
        buffer.writeBoolean(identity.isPresent());
        if (identity.isEmpty()) {
            return;
        }
        AccessIdentity value = identity.orElseThrow();
        buffer.writeLong(value.catalogRevision());
        buffer.writeLong(value.researchRevision());
        buffer.writeLong(value.automaticRevision());
        buffer.writeLong(value.evidenceRevision());
        buffer.writeLong(value.ammoAssociationRevision());
        buffer.writeLong(value.policyPublicationRevision());
        buffer.writeUtf(value.profileId().toString(), BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH);
        buffer.writeUtf(
                value.workstationId().toString(), BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH);
        buffer.writeEnum(value.workstationTier());
        buffer.writeBoolean(value.unrestrictedWorkbench());
        buffer.writeBoolean(value.enforceCraftingTiers());
        buffer.writeBoolean(value.bypassTier());
        buffer.writeBoolean(value.bypassGates());
        buffer.writeBoolean(value.blueprintsEnabled());
    }

    private static Optional<AccessIdentity> readIdentity(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return Optional.empty();
        }
        long catalogRevision = buffer.readLong();
        long researchRevision = buffer.readLong();
        long automaticRevision = buffer.readLong();
        long evidenceRevision = buffer.readLong();
        long associationRevision = buffer.readLong();
        long policyRevision = buffer.readLong();
        ResourceLocation profileId = parseId(buffer.readUtf(
                BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH));
        ResourceLocation workstationId = parseId(buffer.readUtf(
                BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH));
        ResearchWorkbenchTier workstationTier = buffer.readEnum(ResearchWorkbenchTier.class);
        return Optional.of(new AccessIdentity(
                catalogRevision,
                researchRevision,
                automaticRevision,
                evidenceRevision,
                associationRevision,
                policyRevision,
                profileId,
                workstationId,
                workstationTier,
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean()));
    }

    private static ResourceLocation parseId(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            throw new IllegalArgumentException("invalid crafting access identity resource ID");
        }
        return id;
    }

    private static int estimatedIdentityBytes(Optional<AccessIdentity> identity) {
        if (identity.isEmpty()) {
            return 1;
        }
        AccessIdentity value = identity.orElseThrow();
        return 1 + Long.BYTES * 6
                + BlueprintSyncLimits.encodedUtfBytes(value.profileId().toString())
                + BlueprintSyncLimits.encodedUtfBytes(value.workstationId().toString())
                + 6;
    }

    private static boolean requiresIdentity(CraftingEligibilityService.Status status) {
        return status != CraftingEligibilityService.Status.INVALID_REQUEST
                && status != CraftingEligibilityService.Status.INVALID_WORKSTATION
                && status != CraftingEligibilityService.Status.POLICY_UNAVAILABLE;
    }

    private static Set<String> normalized(Set<String> ids) {
        if (ids == null || ids.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
            throw new IllegalArgumentException("invalid crafting recipe ID collection");
        }
        TreeSet<String> result = new TreeSet<>();
        for (String id : ids) {
            if (id == null || id.length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH
                    || net.minecraft.resources.ResourceLocation.tryParse(id) == null
                    || !result.add(id)) {
                throw new IllegalArgumentException("invalid crafting recipe ID");
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static void validateMetadata(
            int containerId,
            long requestId,
            long snapshotId,
            int chunkIndex,
            int chunkCount) {
        if (containerId < 0 || requestId < 1L || snapshotId < 1L
                || chunkCount < 1 || chunkCount > BlueprintSyncLimits.MAX_CHUNKS_PER_SNAPSHOT
                || chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException("invalid crafting access chunk metadata");
        }
    }

    int containerId() {
        return containerId;
    }

    long requestId() {
        return requestId;
    }

    long snapshotId() {
        return snapshotId;
    }

    Optional<AccessIdentity> accessIdentity() {
        return accessIdentity;
    }

    int chunkIndex() {
        return chunkIndex;
    }

    int chunkCount() {
        return chunkCount;
    }

    CraftingEligibilityService.Status status() {
        return status;
    }

    Set<String> entries() {
        return allowedRecipeIds;
    }

    record Snapshot(
            int containerId,
            long requestId,
            long snapshotId,
            Optional<AccessIdentity> accessIdentity,
            CraftingEligibilityService.Status status,
            Set<String> allowedRecipeIds) {
        Snapshot {
            accessIdentity = accessIdentity == null ? Optional.empty() : accessIdentity;
            allowedRecipeIds = Set.copyOf(allowedRecipeIds);
        }
    }

    static final class ClientAccumulator {
        private boolean initialized;
        private boolean completed;
        private int containerId;
        private long requestId;
        private long snapshotId;
        private Optional<AccessIdentity> accessIdentity = Optional.empty();
        private int expectedChunks;
        private CraftingEligibilityService.Status status;
        private final Map<Integer, SyncCraftingAccessPacket> chunks = new TreeMap<>();

        synchronized Acceptance accept(SyncCraftingAccessPacket packet) {
            if (packet == null) {
                throw new IllegalArgumentException("crafting access packet cannot be null");
            }
            if (initialized && (packet.requestId < requestId
                    || packet.requestId == requestId && packet.snapshotId < snapshotId)) {
                return Acceptance.ignored();
            }
            boolean startedSnapshot = false;
            if (!initialized || packet.requestId != requestId
                    || packet.snapshotId != snapshotId) {
                initialized = true;
                completed = false;
                startedSnapshot = true;
                containerId = packet.containerId;
                requestId = packet.requestId;
                snapshotId = packet.snapshotId;
                accessIdentity = packet.accessIdentity;
                expectedChunks = packet.chunkCount;
                status = packet.status;
                chunks.clear();
            }
            if (containerId != packet.containerId
                    || expectedChunks != packet.chunkCount
                    || status != packet.status
                    || !accessIdentity.equals(packet.accessIdentity)) {
                clear();
                throw new IllegalArgumentException("inconsistent crafting access chunks");
            }
            if (completed) {
                SyncCraftingAccessPacket existing = chunks.get(packet.chunkIndex);
                if (existing == null
                        || !existing.allowedRecipeIds.equals(packet.allowedRecipeIds)) {
                    clear();
                    throw new IllegalArgumentException("conflicting completed crafting snapshot");
                }
                return Acceptance.ignored();
            }
            SyncCraftingAccessPacket existing = chunks.putIfAbsent(packet.chunkIndex, packet);
            if (existing != null && !existing.allowedRecipeIds.equals(packet.allowedRecipeIds)) {
                clear();
                throw new IllegalArgumentException("conflicting crafting access chunk");
            }
            long entryCount = chunks.values().stream()
                    .mapToLong(value -> value.allowedRecipeIds.size()).sum();
            if (entryCount > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
                clear();
                throw new IllegalArgumentException("crafting access exceeds entry limit");
            }
            if (chunks.size() != expectedChunks) {
                return new Acceptance(startedSnapshot, Optional.empty());
            }
            TreeSet<String> merged = new TreeSet<>();
            for (SyncCraftingAccessPacket value : chunks.values()) {
                for (String id : value.allowedRecipeIds) {
                    if (!merged.add(id)) {
                        clear();
                        throw new IllegalArgumentException("duplicate crafting recipe across chunks");
                    }
                }
            }
            completed = true;
            return new Acceptance(
                    startedSnapshot,
                    Optional.of(new Snapshot(
                            containerId,
                            requestId,
                            snapshotId,
                            accessIdentity,
                            status,
                            Collections.unmodifiableSet(merged))));
        }

        record Acceptance(
                boolean startedSnapshot,
                Optional<Snapshot> completedSnapshot) {
            Acceptance {
                completedSnapshot = completedSnapshot == null
                        ? Optional.empty()
                        : completedSnapshot;
            }

            static Acceptance ignored() {
                return new Acceptance(false, Optional.empty());
            }
        }

        synchronized void clear() {
            initialized = false;
            completed = false;
            containerId = -1;
            requestId = 0L;
            snapshotId = 0L;
            accessIdentity = Optional.empty();
            expectedChunks = 0;
            status = null;
            chunks.clear();
        }
    }
}
