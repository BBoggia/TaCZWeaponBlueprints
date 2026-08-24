package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;

/** Atomic client-side publication consumed by the future Journal screen. */
public final class ClientBlueprintJournal {
    private static volatile BlueprintJournalSnapshot snapshot = BlueprintJournalSnapshot.EMPTY;

    private ClientBlueprintJournal() {
    }

    public static BlueprintJournalSnapshot snapshot() {
        return snapshot;
    }

    public static void publish(BlueprintJournalSnapshot completed) {
        if (completed == null) {
            throw new IllegalArgumentException("completed Journal snapshot cannot be null");
        }
        snapshot = completed;
    }

    public static void clear() {
        snapshot = BlueprintJournalSnapshot.EMPTY;
    }
}
