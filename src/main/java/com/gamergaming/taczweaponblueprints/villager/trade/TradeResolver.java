package com.gamergaming.taczweaponblueprints.villager.trade;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import com.gamergaming.taczweaponblueprints.villager.VillagerTradePricing;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.*;

/** Pure global pool/price resolution. Per-villager pair selection belongs to stock planning. */
public final class TradeResolver {
    private TradeResolver() {}
    public enum Status { READY, UNAVAILABLE, PENDING }
    public interface Content {
        boolean ready();
        default boolean pending(Output output) { return !ready(); }
        boolean modLoaded(String id);
        /** Zero means missing, air, or unsupported stack capacity. */
        int stackLimit(ResourceLocation item);
        /** Empty reason means usable. Implementations must read current content, never retained eligibility. */
        String unavailable(Output output, ResourceLocation candidate);
        List<Choice> magazineCandidates(Output output);
        default String unavailable(Output output,ResourceLocation candidate,Price price){return unavailable(output,candidate);}
    }
    public record Price(ResourceLocation primaryItem, Optional<ResourceLocation> secondaryItem,
            VillagerTradePricing.NormalizedPrice amounts) {}
    public record Row(ResourceLocation slot, ResourceLocation definition, Profession profession, int level, Role role, Output output, float demandMultiplier,
            Status status, String reason, Optional<Price> price, int uses, int xp, List<Choice> candidates,
            Map<ResourceLocation, String> excluded, List<ResourceLocation> fallbackPath) {
        public Row {
            candidates = List.copyOf(candidates); excluded = Collections.unmodifiableMap(new TreeMap<>(excluded));
            fallbackPath = List.copyOf(fallbackPath);
        }
        public boolean ready() { return status == Status.READY; }
    }
    public record Resolution(ResourceLocation currency, Map<ResourceLocation, Row> slots,
            Map<Profession, String> acquisition) {
        public Resolution { slots = Collections.unmodifiableMap(new TreeMap<>(slots)); acquisition = Map.copyOf(acquisition); }
        /** An established offer never adopts a newly chosen fallback or a different target. */
        public boolean retains(ResourceLocation slot, ResourceLocation definition, ResourceLocation target) {
            Row row = slots.get(slot);
            return row != null && row.ready() && row.definition().equals(definition)
                    && row.candidates().stream().anyMatch(c -> c.id().equals(target));
        }
    }
    public static Resolution resolve(TradeSnapshot snapshot, TradeSettings settings, ResourceLocation currency, Content content) {
        Map<ResourceLocation, Row> resolved = new HashMap<>(), slots = new TreeMap<>();
        for (var d : snapshot.definitions().values()) {
            if (d.role() == Role.FALLBACK) continue;
            Row row = resolve(d, snapshot, settings, currency, content, resolved);
            slots.put(d.slot(), row);
        }
        if (slots.values().stream().mapToInt(r -> r.candidates().size()).sum() > TradeLimits.CANDIDATE_IDS) {
            slots.replaceAll((id, r) -> new Row(r.slot(), r.definition(), r.profession(), r.level(), r.role(),
                    r.output(), r.demandMultiplier(), Status.UNAVAILABLE, "aggregate_resolved_candidate_limit",
                    r.price(), r.uses(), r.xp(), List.of(), r.excluded(), r.fallbackPath()));
        }
        Map<Profession, String> acquisition = new EnumMap<>(Profession.class);
        for (var profession : Profession.values()) {
            String reason = settings.enabled(profession) ? "ready" : "profession_disabled";
            // Repeated restocking permits any positive-XP offer at/below each level to progress.
            for (int level = 1; reason.equals("ready") && level < 5; level++) {
                final int at = level;
                boolean reachable = slots.values().stream().anyMatch(r -> r.profession() == profession
                        && r.role() == Role.CORE && r.level() <= at && r.ready() && r.xp() > 0);
                if (!reachable) reason = slots.values().stream().anyMatch(r -> r.profession() == profession
                        && r.level() <= at && r.status() == Status.PENDING) ? "catalog_pending" : "no_positive_xp_at_level_" + level;
            }
            acquisition.put(profession, reason);
        }
        return new Resolution(currency, slots, acquisition);
    }
    /** Ordered direct alternatives for stock planning; does not discard a usable saved fallback. */
    public static Map<ResourceLocation, List<Row>> alternatives(TradeSnapshot snapshot, TradeSettings settings,
            ResourceLocation currency, Content content) {
        Map<ResourceLocation, Row> direct = new HashMap<>();
        snapshot.definitions().values().forEach(d -> direct.put(d.id(), direct(d, settings, currency, content)));
        Map<ResourceLocation, List<Row>> result = new TreeMap<>();
        for (var d : snapshot.definitions().values()) {
            if (d.role() == Role.FALLBACK) continue;
            List<Row> rows = new ArrayList<>();
            collect(d, d, snapshot, direct, rows, new HashSet<>(), d.enabled() && settings.enabled(d.profession()));
            result.put(d.slot(), List.copyOf(rows));
        }
        if (result.values().stream().flatMap(List::stream).mapToInt(r -> r.candidates().size()).sum() > TradeLimits.CANDIDATE_IDS)
            result.replaceAll((slot, rows) -> rows.stream().map(r -> new Row(r.slot(), r.definition(), r.profession(), r.level(), r.role(),
                    r.output(), r.demandMultiplier(), Status.UNAVAILABLE, "aggregate_resolved_candidate_limit", r.price(), r.uses(), r.xp(),
                    List.of(), r.excluded(), r.fallbackPath())).toList());
        return Collections.unmodifiableMap(result);
    }
    /** Reachable gun alternatives for automatic magazine pools, including saved fallbacks.
     * Only gun rows are resolved here, avoiding recursion through magazine resolution. */
    static Set<ResourceLocation> magazineGuns(TradeSnapshot snapshot, TradeSettings settings,
            ResourceLocation currency, Content content, Selection selection) {
        Set<ResourceLocation> guns=new TreeSet<>(),seen=new HashSet<>();
        if (!settings.categories().contains(Kind.GUN)) return Set.of();
        for (var root:snapshot.definitions().values())
            if(root.role()==Role.CORE)
                collectMagazineGuns(root,snapshot,settings,currency,content,selection,seen,guns);
        return Set.copyOf(guns);
    }
    private static void collectMagazineGuns(TradeDefinition d, TradeSnapshot snapshot, TradeSettings settings,
            ResourceLocation currency, Content content, Selection selection,
            Set<ResourceLocation> seen, Set<ResourceLocation> guns) {
        if(!d.enabled()||!settings.enabled(d.profession())||!seen.add(d.id()))return;
        if(d.output().kind()==Kind.GUN && (selection==Selection.NONE||selection==Selection.STOCKED
                ||selection==d.output().selection())) {
            var row=direct(d,settings,currency,content);
            if(row.ready())row.candidates().forEach(c->guns.add(c.id()));
        }
        for(var child:d.fallbacks())
            collectMagazineGuns(snapshot.definitions().get(child),snapshot,settings,currency,content,selection,seen,guns);
    }
    private static void collect(TradeDefinition root, TradeDefinition d, TradeSnapshot snapshot,
            Map<ResourceLocation, Row> direct, List<Row> rows, Set<ResourceLocation> seen, boolean follow) {
        if (!seen.add(d.id())) return;
        Row r = direct.get(d.id());
        rows.add(new Row(root.slot(), r.definition(), root.profession(), root.level(), root.role(), r.output(),
                r.demandMultiplier(), r.status(), root == d ? r.reason() : "fallback:" + r.reason(), r.price(),
                r.uses(), r.xp(), r.candidates(), r.excluded(), root == d ? List.of(root.id()) : List.of(root.id(), d.id())));
        if (follow && d.enabled()) for (var id : d.fallbacks()) collect(root, snapshot.definitions().get(id), snapshot, direct, rows, seen, true);
    }
    private static Row resolve(TradeDefinition d, TradeSnapshot snapshot, TradeSettings settings,
            ResourceLocation currency, Content content, Map<ResourceLocation, Row> cache) {
        Row cached = cache.get(d.id()); if (cached != null) return cached;
        Row row = direct(d, settings, currency, content);
        if (d.enabled() && settings.enabled(d.profession()) && row.status() == Status.UNAVAILABLE) {
            for (var id : d.fallbacks()) {
                Row fallback = resolve(snapshot.definitions().get(id), snapshot, settings, currency, content, cache);
                if (fallback.status() == Status.UNAVAILABLE) continue;
                List<ResourceLocation> path = new ArrayList<>(); path.add(d.id()); path.addAll(fallback.fallbackPath());
                Map<ResourceLocation, String> exclusions = new TreeMap<>(row.excluded());
                exclusions.putAll(fallback.excluded());
                row = new Row(d.slot(), fallback.definition(), d.profession(), d.level(), d.role(), fallback.output(), fallback.demandMultiplier(), fallback.status(),
                        "fallback:" + row.reason(), fallback.price(), fallback.uses(), fallback.xp(), fallback.candidates(),
                        exclusions, path);
                break;
            }
        }
        cache.put(d.id(), row); return row;
    }
    private static Row direct(TradeDefinition d, TradeSettings settings, ResourceLocation currency, Content content) {
        if (!d.enabled()) return unavailable(d, "definition_disabled");
        if (!settings.enabled(d.profession())) return unavailable(d, "profession_disabled");
        if (!settings.categories().contains(d.output().kind())) return unavailable(d, "category_disabled");
        if (d.requiredMods().stream().anyMatch(m -> !content.modLoaded(m))) return unavailable(d, "optional_mod_absent");
        if (d.output().kind() == Kind.CURRENCY && !settings.materialPurchases()) return unavailable(d, "material_purchases_disabled");
        if (d.output().kind() == Kind.CURRENCY && d.primary().item().filter(currency::equals).isPresent())
            return unavailable(d, "material_is_currency");
        ResourceLocation primary = d.primary().item().orElse(currency);
        Optional<ResourceLocation> secondary = d.secondary().map(i -> i.item().orElse(currency));
        Price price; int uses;
        try {
            var amounts = VillagerTradePricing.price(d.primary().count(), d.secondary().map(Input::count).orElse(0),
                    secondary.filter(primary::equals).isPresent(), content.stackLimit(primary),
                    secondary.map(content::stackLimit).orElse(0), settings.priceBasisPoints(),
                    Math.max(settings.minimumBasisPoints(), d.minimumBasisPoints().orElse(0)));
            price = new Price(primary, secondary, amounts);
            uses = VillagerTradePricing.scale(d.uses(), settings.stockBasisPoints(), TradeLimits.USES);
        } catch (IllegalArgumentException e) { return unavailable(d, "unrepresentable_price_or_stock:" + e.getMessage()); }
        if (content.pending(d.output()) && d.output().kind() != Kind.CURRENCY)
            return new Row(d.slot(), d.id(), d.profession(), d.level(), d.role(), d.output(), d.demandMultiplier(), Status.PENDING, "catalog_pending",
                    Optional.of(price), uses, d.xp(), List.of(), Map.of(), List.of(d.id()));
        List<Choice> candidates = switch (d.output().kind()) {
            case CURRENCY -> List.of(new Choice(currency, 1));
            case MAGAZINE -> d.output().candidates().isEmpty() ? content.magazineCandidates(d.output()) : d.output().candidates();
            default -> d.output().candidates();
        };
        if (candidates.size() > TradeLimits.CHOICES) return unavailable(d, "resolved_candidate_limit");
        List<Choice> usable = new ArrayList<>(); Map<ResourceLocation, String> excluded = new TreeMap<>();
        for (var candidate : candidates) {
            String reason = d.output().kind() == Kind.CURRENCY
                    ? (content.stackLimit(currency) < d.output().count() ? "currency_stack_unavailable" : "")
                    : content.unavailable(d.output(), candidate.id(),price);
            if (reason.isEmpty()) usable.add(candidate); else excluded.put(candidate.id(), reason);
        }
        return new Row(d.slot(), d.id(), d.profession(), d.level(), d.role(), d.output(), d.demandMultiplier(), usable.isEmpty() ? Status.UNAVAILABLE : Status.READY,
                usable.isEmpty() ? "no_usable_candidates" : "ready", Optional.of(price), uses, d.xp(), usable, excluded, List.of(d.id()));
    }
    private static Row unavailable(TradeDefinition d, String reason) {
        return new Row(d.slot(), d.id(), d.profession(), d.level(), d.role(), d.output(), d.demandMultiplier(), Status.UNAVAILABLE, reason,
                Optional.empty(), 0, d.xp(), List.of(), Map.of(), List.of(d.id()));
    }
}
