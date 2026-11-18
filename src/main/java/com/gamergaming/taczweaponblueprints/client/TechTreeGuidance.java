package com.gamergaming.taczweaponblueprints.client;

import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.client.ui.UiSheet;

import net.minecraft.resources.ResourceLocation;

/**
 * Works out the one thing worth telling a player who opens the Tech Tree: where to start, what is
 * ready, or what is in the way. Pure logic so the wording rules can be tested without a screen.
 */
final class TechTreeGuidance {
    private TechTreeGuidance() { }

    enum Kind {
        /** Nothing learned yet and something can be researched: point at the cheapest one. */
        START,
        /** One or more blueprints can be researched now. */
        READY,
        /** Prerequisites are met for something, but the player can't pay for any of it. */
        SHORT,
        /** Everything within reach needs a better Research Bench. */
        BENCH,
        /** Nothing is open for research: the rest waits on discovery or server rules. */
        WAITING,
        /** Every blueprint in this tree is learned. */
        COMPLETE,
        CHECKING,
        BLOCKED
    }

    /** One blueprint as the sheet shows it, in reading order. */
    record Entry(ResourceLocation id, UiSheet.TileState state, boolean benchTooLow, int cost, int benchTier,
            ResearchTreeSelectedNodePresenter.Message reason) {
        Entry(ResourceLocation id, UiSheet.TileState state, boolean benchTooLow, int cost, int benchTier) {
            this(id, state, benchTooLow, cost, benchTier, benchTooLow
                    ? ResearchTreeSelectedNodePresenter.Message.WORKBENCH_TIER_REQUIRED
                    : state == UiSheet.TileState.OPEN ? ResearchTreeSelectedNodePresenter.Message.POINTS_REQUIRED
                    : ResearchTreeSelectedNodePresenter.Message.LOCKED);
        }
    }

    /**
     * @param target the blueprint the advice points at, when it points at one
     * @param readyCount how many blueprints can be researched now
     * @param cost the target's own currency price
     * @param benchTier the lowest bench tier that would open something, for {@link Kind#BENCH}
     */
    record Advice(Kind kind, Optional<ResourceLocation> target, int readyCount, int cost, int benchTier) { }

    static Advice advise(List<Entry> entries) {
        List<Entry> ready = entries.stream().filter(entry -> entry.state() == UiSheet.TileState.READY).toList();
        boolean anyLearned = entries.stream().anyMatch(entry -> entry.state() == UiSheet.TileState.LEARNED);
        if (!ready.isEmpty()) {
            if (!anyLearned) {
                Entry cheapest = cheapest(ready);
                return new Advice(Kind.START, Optional.of(cheapest.id()), ready.size(), cheapest.cost(), 0);
            }
            return new Advice(Kind.READY, Optional.of(ready.get(0).id()), ready.size(), ready.get(0).cost(), 0);
        }
        if (!entries.isEmpty() && entries.stream().allMatch(entry -> entry.state() == UiSheet.TileState.LEARNED)) {
            return new Advice(Kind.COMPLETE, Optional.empty(), 0, 0, 0);
        }
        List<Entry> open = entries.stream().filter(entry -> entry.state() == UiSheet.TileState.OPEN).toList();
        List<Entry> unaffordable = open.stream().filter(entry -> entry.reason() == ResearchTreeSelectedNodePresenter.Message.POINTS_REQUIRED
                || entry.reason() == ResearchTreeSelectedNodePresenter.Message.POINTS_AND_MATERIALS_REQUIRED).toList();
        if (!unaffordable.isEmpty()) {
            Entry cheapest = cheapest(unaffordable);
            return new Advice(Kind.SHORT, Optional.of(cheapest.id()), 0, cheapest.cost(), 0);
        }
        List<Entry> checking = open.stream().filter(entry -> entry.reason()
                == ResearchTreeSelectedNodePresenter.Message.CHECKING_REQUIREMENTS).toList();
        if (!checking.isEmpty()) return new Advice(Kind.CHECKING, Optional.of(checking.get(0).id()), 0, -1, 0);
        List<Entry> other = open.stream().filter(entry -> !entry.benchTooLow()).toList();
        if (!other.isEmpty()) return new Advice(Kind.BLOCKED, Optional.of(other.get(0).id()), 0, -1, 0);
        if (!open.isEmpty()) {
            Entry lowest = open.get(0);
            for (Entry entry : open) {
                if (entry.benchTier() < lowest.benchTier()) {
                    lowest = entry;
                }
            }
            return new Advice(Kind.BENCH, Optional.of(lowest.id()), 0, lowest.cost(), lowest.benchTier());
        }
        return new Advice(Kind.WAITING, Optional.empty(), 0, 0, 0);
    }

    /**
     * The blueprint the Next control moves to: the ready blueprint after the current one, wrapping
     * round. With nothing ready it is whatever the advice points at.
     */
    static Optional<ResourceLocation> next(List<Entry> entries, Optional<ResourceLocation> current) {
        List<ResourceLocation> ready = entries.stream().filter(entry -> entry.state() == UiSheet.TileState.READY)
                .map(Entry::id).toList();
        if (ready.isEmpty()) {
            return advise(entries).target();
        }
        int index = current.map(ready::indexOf).orElse(-1);
        return Optional.of(ready.get((index + 1) % ready.size()));
    }

    /** The lowest price, keeping reading order between equals. */
    private static Entry cheapest(List<Entry> entries) {
        Entry best = entries.get(0);
        for (Entry entry : entries) {
            if (entry.cost() >= 0 && (best.cost() < 0 || entry.cost() < best.cost())) {
                best = entry;
            }
        }
        return best;
    }
}
