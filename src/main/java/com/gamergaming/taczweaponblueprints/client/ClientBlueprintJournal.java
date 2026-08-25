package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;

/** Atomic client-side publication consumed by the future Journal screen. */
public final class ClientBlueprintJournal {
    private ClientBlueprintJournal() {
    }

    public static BlueprintJournalSnapshot snapshot() {
        return ClientResearchState.publication().journal();
    }

    public static void publish(BlueprintJournalSnapshot completed) {
        if (completed == null) {
            throw new IllegalArgumentException("completed Journal snapshot cannot be null");
        }
        ClientResearchState.publishJournalOnly(completed);
    }

    public static void clear() {
        ClientResearchState.clear();
    }
}
