package com.gamergaming.taczweaponblueprints.villager.trade;

import java.util.*;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;

/** Last valid definitions are retained, never a last-valid permission to sell removed content. */
public final class TradeDataManager extends SimplePreparableReloadListener<TradeDataManager.Prepared> {
    public static final TradeDataManager INSTANCE = new TradeDataManager();
    public static final String DIRECTORY = "taczweaponblueprints/villager_trades";
    public record Publication(TradeSnapshot snapshot, long revision, Optional<String> failure) {}
    public record Prepared(Optional<TradeSnapshot> snapshot, Optional<String> failure) {}
    private volatile Publication publication = new Publication(TradeSnapshot.EMPTY, 0, Optional.empty());
    public Publication publication() { return publication; }
    /** Restore committed data without retaining a partially applied failed reload. */
    public void restoreAfterAbortedReload(Publication committed) {
        publication = new Publication(committed.snapshot(), Math.incrementExact(publication.revision()), committed.failure());
    }
    public void clear() { publication = new Publication(TradeSnapshot.EMPTY, 0, Optional.empty()); }
    @Override protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
        try {
            Map<ResourceLocation, TradeDefinition> definitions = new TreeMap<>();
            var resources = manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json"));
            if (resources.size() > TradeLimits.DEFINITIONS) throw new IllegalArgumentException("too many trade documents");
            int characters = 0;
            for (var entry : new TreeMap<>(resources).entrySet()) {
                var resource = entry.getValue();
                ResourceLocation id = TradeDefinition.parseId(entry.getKey().getNamespace() + ":"
                        + entry.getKey().getPath().substring(DIRECTORY.length() + 1, entry.getKey().getPath().length() - 5));
                try (var reader = resource.openAsReader()) {
                    var parsed = TradeParser.read(id, reader);
                    characters += parsed.characters();
                    if (characters > TradeLimits.TOTAL_JSON_CHARACTERS) throw new IllegalArgumentException("aggregate trade JSON limit exceeded");
                    definitions.put(id, parsed.definition());
                } catch (Exception e) {
                    throw new IllegalArgumentException(id + " from " + resource.sourcePackId() + ": " + e.getMessage(), e);
                }
            }
            return new Prepared(Optional.of(new TradeSnapshot(definitions)), Optional.empty());
        } catch (RuntimeException e) { return new Prepared(Optional.empty(), Optional.of(bounded(e.getMessage()))); }
    }
    @Override protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) { publish(prepared); }
    void publish(Prepared prepared) {
        if (prepared.snapshot().isPresent() == prepared.failure().isPresent()) throw new IllegalArgumentException("invalid trade preparation");
        var previous = publication;
        if (prepared.failure().isPresent()) {
            publication = new Publication(previous.snapshot(), previous.revision(), prepared.failure());
            TaCZWeaponBlueprints.LOGGER.error("Rejected villager trades; retaining definitions {}: {}", previous.revision(), prepared.failure().orElseThrow());
        } else {
            publication = new Publication(prepared.snapshot().orElseThrow(), Math.incrementExact(previous.revision()), Optional.empty());
            TaCZWeaponBlueprints.LOGGER.info("Published villager trade definitions {} ({} documents)", publication.revision(), publication.snapshot().definitions().size());
        }
    }
    private static String bounded(String value) { return value == null ? "invalid trade data" : value.substring(0, Math.min(1024, value.length())); }
}
