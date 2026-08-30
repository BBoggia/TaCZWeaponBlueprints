package com.gamergaming.taczweaponblueprints.capabilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Bounded server-side history used to enforce RP claim, cooldown, and rolling
 * window policies. It stores historical facts only; live policy settings remain
 * in the current immutable datapack snapshot.
 */
public final class ResearchPointAwardLedger {
    public static final int LEDGER_DATA_VERSION = 1;

    private static final String VERSION_TAG = "LedgerVersion";
    private static final String CLAIMS_TAG = "Claims";
    private static final String COOLDOWNS_TAG = "Cooldowns";
    private static final String WINDOWS_TAG = "Windows";
    private static final String BUDGETS_TAG = "Budgets";
    private static final String ID_TAG = "Id";
    private static final String TARGET_TAG = "Target";
    private static final String LAST_AWARD_TIME_TAG = "LastAwardGameTime";
    private static final String ENTRIES_TAG = "Entries";
    private static final String GAME_TIME_TAG = "GameTime";
    private static final String POINTS_TAG = "Points";

    private static final Comparator<ClaimKey> CLAIM_ORDER = Comparator
            .comparing((ClaimKey key) -> key.claimId().toString())
            .thenComparing(key -> key.targetId().map(ResourceLocation::toString).orElse(""));
    private static final Comparator<ScopeKey> SCOPE_ORDER = Comparator
            .comparing((ScopeKey key) -> key.id().toString())
            .thenComparing(key -> key.targetId().map(ResourceLocation::toString).orElse(""));
    private static final Comparator<ResourceLocation> RESOURCE_ORDER =
            Comparator.comparing(ResourceLocation::toString);
    private static final Comparator<WindowEntry> ENTRY_ORDER = Comparator
            .comparingLong(WindowEntry::gameTime)
            .thenComparingInt(WindowEntry::points);

    private final NavigableSet<ClaimKey> claims = new TreeSet<>(CLAIM_ORDER);
    private final NavigableMap<ScopeKey, Long> cooldowns = new TreeMap<>(SCOPE_ORDER);
    private final NavigableMap<ScopeKey, List<WindowEntry>> windows = new TreeMap<>(SCOPE_ORDER);
    private final NavigableMap<ResourceLocation, List<WindowEntry>> budgets =
            new TreeMap<>(RESOURCE_ORDER);
    private int windowEntryCount;

    public boolean hasClaim(ClaimKey claim) {
        return claim != null && claims.contains(claim);
    }

    public OptionalLong lastAwardGameTime(ScopeKey scope) {
        if (scope == null) {
            return OptionalLong.empty();
        }
        Long value = cooldowns.get(scope);
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    /** Returns current local-window usage after removing entries before the cutoff. */
    public Usage windowUsage(ScopeKey scope, long minimumGameTimeInclusive) {
        if (scope == null || minimumGameTimeInclusive < 0L) {
            return Usage.EMPTY;
        }
        return usageAfterPrune(windows, scope, minimumGameTimeInclusive);
    }

    /** Returns current shared-budget usage after removing entries before the cutoff. */
    public Usage budgetUsage(ResourceLocation budgetId, long minimumGameTimeInclusive) {
        if (!validId(budgetId) || minimumGameTimeInclusive < 0L) {
            return Usage.EMPTY;
        }
        return usageAfterPrune(budgets, budgetId, minimumGameTimeInclusive);
    }

    public int claimCount() {
        return claims.size();
    }

    public int rateStateCount() {
        return cooldowns.size() + windows.size() + budgets.size();
    }

    public int windowEntryCount() {
        return windowEntryCount;
    }

    public boolean isEmpty() {
        return claims.isEmpty() && cooldowns.isEmpty() && windows.isEmpty() && budgets.isEmpty();
    }

    public Set<ClaimKey> claims() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(claims));
    }

    void replaceWith(ResearchPointAwardLedger source) {
        clear();
        if (source == null) {
            return;
        }
        claims.addAll(source.claims);
        cooldowns.putAll(source.cooldowns);
        source.windows.forEach((key, value) -> windows.put(key, new ArrayList<>(value)));
        source.budgets.forEach((key, value) -> budgets.put(key, new ArrayList<>(value)));
        windowEntryCount = source.windowEntryCount;
    }

    public Map<ScopeKey, Long> cooldowns() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(cooldowns));
    }

    public Map<ScopeKey, List<WindowEntry>> windows() {
        return immutableWindowSnapshot(windows);
    }

    public Map<ResourceLocation, List<WindowEntry>> budgets() {
        return immutableWindowSnapshot(budgets);
    }

    /**
     * Applies every operation or none. This method performs all capacity and
     * monotonic-time checks before mutating any collection.
     */
    public boolean apply(Mutation mutation) {
        if (mutation == null) {
            return false;
        }

        ClaimKey claim = mutation.claim().orElse(null);
        CooldownUpdate cooldown = mutation.cooldown().orElse(null);
        WindowUpdate window = mutation.window().orElse(null);
        BudgetUpdate budget = mutation.budget().orElse(null);

        if (claim != null && claims.contains(claim)) {
            return false;
        }
        if (claim != null
                && claims.size() >= PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_CLAIMS) {
            return false;
        }
        if (cooldown != null) {
            Long existing = cooldowns.get(cooldown.scope());
            if (existing != null && cooldown.lastAwardGameTime() < existing) {
                return false;
            }
        }

        int additionalRateStates = 0;
        if (cooldown != null && !cooldowns.containsKey(cooldown.scope())) {
            additionalRateStates++;
        }
        if (window != null && !windows.containsKey(window.scope())) {
            additionalRateStates++;
        }
        if (budget != null && !budgets.containsKey(budget.budgetId())) {
            additionalRateStates++;
        }
        if (rateStateCount() > PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_RATE_STATES
                - additionalRateStates) {
            return false;
        }

        int additionalEntries = (window == null ? 0 : 1) + (budget == null ? 0 : 1);
        if (windowEntryCount
                > PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_WINDOW_ENTRIES
                - additionalEntries) {
            return false;
        }

        if (claim != null) {
            claims.add(claim);
        }
        if (cooldown != null) {
            cooldowns.put(cooldown.scope(), cooldown.lastAwardGameTime());
        }
        if (window != null) {
            windows.computeIfAbsent(window.scope(), ignored -> new ArrayList<>())
                    .add(window.entry());
            windowEntryCount++;
        }
        if (budget != null) {
            budgets.computeIfAbsent(budget.budgetId(), ignored -> new ArrayList<>())
                    .add(budget.entry());
            windowEntryCount++;
        }
        return true;
    }

    public void clear() {
        claims.clear();
        cooldowns.clear();
        windows.clear();
        budgets.clear();
        windowEntryCount = 0;
    }

    public CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag();
        root.putInt(VERSION_TAG, LEDGER_DATA_VERSION);
        root.put(CLAIMS_TAG, writeClaims());
        root.put(COOLDOWNS_TAG, writeCooldowns());
        root.put(WINDOWS_TAG, writeWindows());
        root.put(BUDGETS_TAG, writeBudgets());
        return root;
    }

    public void deserializeNBT(CompoundTag root) {
        clear();
        if (root == null || !root.contains(VERSION_TAG, Tag.TAG_ANY_NUMERIC)
                || root.getInt(VERSION_TAG) < 1) {
            return;
        }
        readClaims(root);
        readCooldowns(root);
        readWindows(root);
        readBudgets(root);
    }

    private ListTag writeClaims() {
        ListTag values = new ListTag();
        for (ClaimKey claim : claims) {
            CompoundTag value = new CompoundTag();
            writeIdentity(value, claim.claimId(), claim.targetId());
            values.add(value);
        }
        return values;
    }

    private ListTag writeCooldowns() {
        ListTag values = new ListTag();
        for (Map.Entry<ScopeKey, Long> cooldown : cooldowns.entrySet()) {
            CompoundTag value = new CompoundTag();
            writeIdentity(value, cooldown.getKey().id(), cooldown.getKey().targetId());
            value.putLong(LAST_AWARD_TIME_TAG, cooldown.getValue());
            values.add(value);
        }
        return values;
    }

    private ListTag writeWindows() {
        ListTag values = new ListTag();
        for (Map.Entry<ScopeKey, List<WindowEntry>> window : windows.entrySet()) {
            CompoundTag value = new CompoundTag();
            writeIdentity(value, window.getKey().id(), window.getKey().targetId());
            value.put(ENTRIES_TAG, writeEntries(window.getValue()));
            values.add(value);
        }
        return values;
    }

    private ListTag writeBudgets() {
        ListTag values = new ListTag();
        for (Map.Entry<ResourceLocation, List<WindowEntry>> budget : budgets.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putString(ID_TAG, budget.getKey().toString());
            value.put(ENTRIES_TAG, writeEntries(budget.getValue()));
            values.add(value);
        }
        return values;
    }

    private static ListTag writeEntries(List<WindowEntry> source) {
        ListTag entries = new ListTag();
        source.stream().sorted(ENTRY_ORDER).forEach(entry -> {
            CompoundTag value = new CompoundTag();
            value.putLong(GAME_TIME_TAG, entry.gameTime());
            value.putInt(POINTS_TAG, entry.points());
            entries.add(value);
        });
        return entries;
    }

    private void readClaims(CompoundTag root) {
        ListTag values = root.getList(CLAIMS_TAG, Tag.TAG_COMPOUND);
        int inspected = Math.min(
                values.size(), PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_CLAIMS);
        for (int index = 0; index < inspected; index++) {
            readClaim(values.getCompound(index)).ifPresent(claims::add);
        }
    }

    private void readCooldowns(CompoundTag root) {
        ListTag values = root.getList(COOLDOWNS_TAG, Tag.TAG_COMPOUND);
        int inspected = Math.min(
                values.size(), PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_RATE_STATES);
        for (int index = 0; index < inspected; index++) {
            CompoundTag value = values.getCompound(index);
            Optional<ScopeKey> scope = readScope(value);
            if (scope.isEmpty() || !value.contains(LAST_AWARD_TIME_TAG, Tag.TAG_ANY_NUMERIC)) {
                continue;
            }
            long gameTime = value.getLong(LAST_AWARD_TIME_TAG);
            if (gameTime < 0L) {
                continue;
            }
            if (!cooldowns.containsKey(scope.orElseThrow())
                    && rateStateCount()
                    >= PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_RATE_STATES) {
                break;
            }
            cooldowns.merge(scope.orElseThrow(), gameTime, Math::max);
        }
    }

    private void readWindows(CompoundTag root) {
        ListTag values = root.getList(WINDOWS_TAG, Tag.TAG_COMPOUND);
        int inspected = Math.min(
                values.size(), PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_RATE_STATES);
        for (int index = 0; index < inspected
                && windowEntryCount
                < PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_WINDOW_ENTRIES; index++) {
            CompoundTag value = values.getCompound(index);
            Optional<ScopeKey> scope = readScope(value);
            if (scope.isEmpty()) {
                continue;
            }
            ScopeKey key = scope.orElseThrow();
            if (!windows.containsKey(key)
                    && rateStateCount()
                    >= PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_RATE_STATES) {
                break;
            }
            List<WindowEntry> entries = readEntries(value);
            if (!entries.isEmpty()) {
                windows.computeIfAbsent(key, ignored -> new ArrayList<>()).addAll(entries);
                windowEntryCount += entries.size();
            }
        }
    }

    private void readBudgets(CompoundTag root) {
        ListTag values = root.getList(BUDGETS_TAG, Tag.TAG_COMPOUND);
        int inspected = Math.min(
                values.size(), PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_RATE_STATES);
        for (int index = 0; index < inspected
                && windowEntryCount
                < PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_WINDOW_ENTRIES; index++) {
            CompoundTag value = values.getCompound(index);
            ResourceLocation id = readId(value, ID_TAG).orElse(null);
            if (id == null) {
                continue;
            }
            if (!budgets.containsKey(id)
                    && rateStateCount()
                    >= PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_RATE_STATES) {
                break;
            }
            List<WindowEntry> entries = readEntries(value);
            if (!entries.isEmpty()) {
                budgets.computeIfAbsent(id, ignored -> new ArrayList<>()).addAll(entries);
                windowEntryCount += entries.size();
            }
        }
    }

    private List<WindowEntry> readEntries(CompoundTag state) {
        int remaining = PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_WINDOW_ENTRIES
                - windowEntryCount;
        if (remaining <= 0) {
            return List.of();
        }
        ListTag values = state.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
        int inspected = Math.min(values.size(), remaining);
        List<WindowEntry> entries = new ArrayList<>(inspected);
        for (int index = 0; index < inspected; index++) {
            CompoundTag value = values.getCompound(index);
            if (!value.contains(GAME_TIME_TAG, Tag.TAG_ANY_NUMERIC)
                    || !value.contains(POINTS_TAG, Tag.TAG_ANY_NUMERIC)) {
                continue;
            }
            long gameTime = value.getLong(GAME_TIME_TAG);
            long points = value.getLong(POINTS_TAG);
            if (gameTime >= 0L && points > 0L
                    && points <= PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
                entries.add(new WindowEntry(gameTime, (int) points));
            }
        }
        entries.sort(ENTRY_ORDER);
        return entries;
    }

    private static Optional<ClaimKey> readClaim(CompoundTag value) {
        ResourceLocation id = readId(value, ID_TAG).orElse(null);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.of(new ClaimKey(id, readOptionalTarget(value)));
    }

    private static Optional<ScopeKey> readScope(CompoundTag value) {
        ResourceLocation id = readId(value, ID_TAG).orElse(null);
        return id == null
                ? Optional.empty()
                : Optional.of(new ScopeKey(id, readOptionalTarget(value)));
    }

    private static Optional<ResourceLocation> readOptionalTarget(CompoundTag value) {
        return value.contains(TARGET_TAG, Tag.TAG_STRING)
                ? readId(value, TARGET_TAG)
                : Optional.empty();
    }

    private static Optional<ResourceLocation> readId(CompoundTag value, String key) {
        if (!value.contains(key, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        String raw = value.getString(key);
        if (raw.isEmpty() || raw.length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            return Optional.empty();
        }
        return Optional.ofNullable(ResourceLocation.tryParse(raw));
    }

    private static void writeIdentity(
            CompoundTag value,
            ResourceLocation id,
            Optional<ResourceLocation> targetId) {
        value.putString(ID_TAG, id.toString());
        targetId.ifPresent(target -> value.putString(TARGET_TAG, target.toString()));
    }

    private <K> Usage usageAfterPrune(
            NavigableMap<K, List<WindowEntry>> states,
            K key,
            long minimumGameTimeInclusive) {
        List<WindowEntry> entries = states.get(key);
        if (entries == null) {
            return Usage.EMPTY;
        }
        int before = entries.size();
        entries.removeIf(entry -> entry.gameTime() < minimumGameTimeInclusive);
        windowEntryCount -= before - entries.size();
        if (entries.isEmpty()) {
            states.remove(key);
            return Usage.EMPTY;
        }
        long points = 0L;
        for (WindowEntry entry : entries) {
            points += entry.points();
        }
        return new Usage(entries.size(), points);
    }

    private static <K> Map<K, List<WindowEntry>> immutableWindowSnapshot(
            NavigableMap<K, List<WindowEntry>> source) {
        Map<K, List<WindowEntry>> snapshot = new LinkedHashMap<>();
        source.forEach((key, entries) -> snapshot.put(key, List.copyOf(entries)));
        return Collections.unmodifiableMap(snapshot);
    }

    private static boolean validId(ResourceLocation id) {
        return id != null
                && id.toString().length() <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH;
    }

    public record ClaimKey(ResourceLocation claimId, Optional<ResourceLocation> targetId) {
        public ClaimKey {
            targetId = targetId == null ? Optional.empty() : targetId;
            if (!validId(claimId) || targetId.filter(id -> !validId(id)).isPresent()) {
                throw new IllegalArgumentException("invalid Research Point claim key");
            }
        }

        public static ClaimKey once(ResourceLocation claimId) {
            return new ClaimKey(claimId, Optional.empty());
        }

        public static ClaimKey targeted(ResourceLocation claimId, ResourceLocation targetId) {
            return new ClaimKey(claimId, Optional.of(Objects.requireNonNull(targetId)));
        }
    }

    public record ScopeKey(ResourceLocation id, Optional<ResourceLocation> targetId) {
        public ScopeKey {
            targetId = targetId == null ? Optional.empty() : targetId;
            if (!validId(id) || targetId.filter(target -> !validId(target)).isPresent()) {
                throw new IllegalArgumentException("invalid Research Point rate scope");
            }
        }

        public static ScopeKey global(ResourceLocation id) {
            return new ScopeKey(id, Optional.empty());
        }

        public static ScopeKey targeted(ResourceLocation id, ResourceLocation targetId) {
            return new ScopeKey(id, Optional.of(Objects.requireNonNull(targetId)));
        }
    }

    public record WindowEntry(long gameTime, int points) {
        public WindowEntry {
            if (gameTime < 0L || points <= 0
                    || points > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
                throw new IllegalArgumentException("invalid Research Point rolling-window entry");
            }
        }
    }

    public record Usage(int awards, long points) {
        private static final Usage EMPTY = new Usage(0, 0L);

        public Usage {
            if (awards < 0 || points < 0L) {
                throw new IllegalArgumentException("invalid Research Point rolling-window usage");
            }
        }
    }

    public record CooldownUpdate(ScopeKey scope, long lastAwardGameTime) {
        public CooldownUpdate {
            if (scope == null || lastAwardGameTime < 0L) {
                throw new IllegalArgumentException("invalid Research Point cooldown update");
            }
        }
    }

    public record WindowUpdate(ScopeKey scope, WindowEntry entry) {
        public WindowUpdate {
            if (scope == null || entry == null) {
                throw new IllegalArgumentException("invalid Research Point window update");
            }
        }
    }

    public record BudgetUpdate(ResourceLocation budgetId, WindowEntry entry) {
        public BudgetUpdate {
            if (!validId(budgetId) || entry == null) {
                throw new IllegalArgumentException("invalid Research Point budget update");
            }
        }
    }

    public record Mutation(
            Optional<ClaimKey> claim,
            Optional<CooldownUpdate> cooldown,
            Optional<WindowUpdate> window,
            Optional<BudgetUpdate> budget) {
        private static final Mutation EMPTY = new Mutation(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        public Mutation {
            claim = claim == null ? Optional.empty() : claim;
            cooldown = cooldown == null ? Optional.empty() : cooldown;
            window = window == null ? Optional.empty() : window;
            budget = budget == null ? Optional.empty() : budget;
        }

        public static Mutation empty() {
            return EMPTY;
        }

        public static Mutation claim(ClaimKey claim) {
            return new Mutation(Optional.of(Objects.requireNonNull(claim)), Optional.empty(),
                    Optional.empty(), Optional.empty());
        }

        public boolean isEmpty() {
            return claim.isEmpty() && cooldown.isEmpty() && window.isEmpty() && budget.isEmpty();
        }
    }
}
