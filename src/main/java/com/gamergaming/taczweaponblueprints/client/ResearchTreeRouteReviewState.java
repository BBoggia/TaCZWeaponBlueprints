package com.gamergaming.taczweaponblueprints.client;

import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.network.ResearchRouteReviewRequestPacket;
import com.gamergaming.taczweaponblueprints.network.ResearchRouteReviewResultPacket;
import com.gamergaming.taczweaponblueprints.progression.ResearchGuidanceSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import net.minecraft.resources.ResourceLocation;

/** Screen-owned review; all resource and quote changes withdraw the current-purchase label. */
final class ResearchTreeRouteReviewState {
    enum Status { WAITING, LOADING, CURRENT, UNAVAILABLE }
    record Binding(int menu, long generation, ResourceLocation target, ResearchSelectionPreview preview, Object inventory) { }
    private static final AtomicInteger IDS = new AtomicInteger(1);
    private Binding binding;
    private ResearchRouteReviewRequestPacket pending;
    private ResearchGuidanceSnapshot snapshot;
    private int attempts;
    private long sentAt, nextAttempt;
    private Status status = Status.WAITING;
    private long revision;
    void clear() { binding = null; pending = null; snapshot = null; attempts = 0; status = Status.WAITING; revision++; }
    void bind(Binding next) {
        if (!Objects.equals(binding,next)) { clear(); binding = next; }
    }
    long revision() { return revision; }
    Status status() { return status; }
    Optional<ResearchGuidanceSnapshot> snapshot() { return Optional.ofNullable(snapshot); }
    Optional<ResearchRouteReviewRequestPacket> request(long now) {
        if (pending != null && now - sentAt >= 3000) { pending = null; status = Status.WAITING; revision++; }
        if (binding == null || binding.preview().routeFingerprint().isEmpty() || status == Status.CURRENT
                || status == Status.UNAVAILABLE || pending != null || now < nextAttempt) return Optional.empty();
        if (attempts >= 2) { status = Status.UNAVAILABLE; revision++; return Optional.empty(); }
        pending = new ResearchRouteReviewRequestPacket(binding.menu(), IDS.getAndUpdate(i -> i == Integer.MAX_VALUE ? 1 : i+1),
                binding.generation(), binding.target(), binding.preview().routeFingerprint().orElseThrow());
        attempts++; sentAt = now; nextAttempt = now + 500; status = Status.LOADING; revision++;
        return Optional.of(pending);
    }
    boolean accept(ResearchRouteReviewResultPacket packet, ResearchTreeGraph graph, long now) {
        if (binding == null || pending == null || !pending.equals(packet.request())) return false;
        pending = null; revision++;
        if (packet.result().code() == ResearchBenchMenu.GuidanceResultCode.THROTTLED) {
            status = Status.WAITING; nextAttempt = now + 500; return true;
        }
        var value = packet.result().snapshot().orElse(null);
        var preview = binding.preview();
        if (value == null || !validRoute(value, graph) || !value.routeAvailable()
                || value.pointCost() != (preview.costMode().pointsEnabled() ? preview.pointCost() : 0)
                || value.pointBalance() != (preview.costMode().pointsEnabled() ? preview.pointBalance() : 0)
                || value.purchaseIds().size() != preview.unlockCount() || value.costMode() != preview.costMode()
                || value.costBypassed() != preview.creativeBypass() || value.totalMaterialTypes() != (preview.costMode().itemsEnabled() ? preview.ingredientTypeCount() : 0) || !allocationsMatch(value, preview)) {
            status = Status.UNAVAILABLE; snapshot = null; return true;
        }
        snapshot = value; status = Status.CURRENT; return true;
    }
    private static boolean allocationsMatch(ResearchGuidanceSnapshot snapshot, ResearchSelectionPreview preview) {
        if (snapshot.transactionCapacityAvailable() != preview.transactionCapacityAvailable()
                || !preview.creativeBypass() && preview.ingredientsSatisfied()
                        != (snapshot.missingMaterialTypes() == 0)) return false;
        if (snapshot.materials().size() != Math.min(snapshot.totalMaterialTypes(),
                ResearchGuidanceSnapshot.MAX_MATERIAL_PROGRESS)) return false;
        // Guidance carries only a bounded prefix; a material page may start
        // inside that prefix, straddle it, or lie entirely beyond it.
        int offset = preview.ingredientOffset();
        int common = Math.min(Math.max(0, snapshot.materials().size() - offset), preview.ingredients().size());
        for (int i = 0; i < common; i++) {
            var route = snapshot.materials().get(offset + i); var quote = preview.ingredients().get(i);
            // The preview expands tags into example items for icons; guidance retains the tag itself.
            if (!route.tag().equals(quote.tag()) || route.tag().isEmpty() && !route.items().equals(quote.items())
                    || route.required() != quote.required() || route.allocated() != quote.inventoryAvailable()) return false;
        }
        return true;
    }
    private static boolean validRoute(ResearchGuidanceSnapshot snapshot, ResearchTreeGraph graph) {
        if (!ClientResearchGuidanceState.validAgainst(snapshot, graph)) return false;
        for (var id : snapshot.purchaseIds()) for (var group : graph.requirementGroupsOf(id)) {
            if (snapshot.selectedRequirements().stream().noneMatch(s -> s.dependentId().equals(id)
                    && s.groupOrdinal() == group.ordinal())) return false;
        }
        var reachable = new java.util.HashSet<ResourceLocation>();
        reachable.add(snapshot.targetId());
        boolean changed;
        do {
            changed = false;
            for (var selected : snapshot.selectedRequirements()) if (reachable.contains(selected.dependentId()))
                changed |= reachable.add(selected.prerequisiteId());
        } while (changed);
        return reachable.containsAll(snapshot.supportIds());
    }

}
