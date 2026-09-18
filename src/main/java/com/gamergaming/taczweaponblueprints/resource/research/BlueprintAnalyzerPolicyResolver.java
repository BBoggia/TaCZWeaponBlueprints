package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.FoundWeaponRecoveryMode;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerTier;
import net.minecraft.resources.ResourceLocation;

/** Pure server-side policy resolution; no inventory access, random draws, or state publication. */
public final class BlueprintAnalyzerPolicyResolver {
    private BlueprintAnalyzerPolicyResolver() { }

    public static final class Context {
        private final com.gamergaming.taczweaponblueprints.progression.StationSetup stations;
        public com.gamergaming.taczweaponblueprints.progression.StationSetup stations() { return stations; }
        private final BlueprintResearchSnapshot research;
        private final Map<ResourceLocation, BlueprintData> catalog;
        private final long researchRevision, catalogRevision;
        private final BlueprintProgressionPolicySnapshot progression;
        private final java.util.concurrent.ConcurrentMap<ResourceLocation,
                java.util.function.BiFunction<ResourceLocation, BlueprintData, BlueprintResearchPolicyResolver.RuleSelection>> selectors
                = new java.util.concurrent.ConcurrentHashMap<>();

        public Context(BlueprintResearchSnapshot research, Map<ResourceLocation, BlueprintData> catalog,
                long researchRevision, long catalogRevision, BlueprintProgressionPolicySnapshot progression) {
            this(research, catalog, researchRevision, catalogRevision, progression,
                    com.gamergaming.taczweaponblueprints.progression.StationSetup.COMPATIBILITY);
        }
        public Context(BlueprintResearchSnapshot research, Map<ResourceLocation, BlueprintData> catalog,
                long researchRevision, long catalogRevision, BlueprintProgressionPolicySnapshot progression,
                com.gamergaming.taczweaponblueprints.progression.StationSetup stations) {
            this.stations = Objects.requireNonNull(stations);
            this.research = Objects.requireNonNull(research); this.catalog = Map.copyOf(catalog);
            this.progression = Objects.requireNonNull(progression);
            if (researchRevision < 0 || catalogRevision < 0) throw new IllegalArgumentException("negative analysis context revision");
            this.researchRevision = researchRevision; this.catalogRevision = catalogRevision;
        }
        public BlueprintResearchSnapshot research() { return research; }
        public Map<ResourceLocation, BlueprintData> catalog() { return catalog; }
        public long researchRevision() { return researchRevision; }
        public long catalogRevision() { return catalogRevision; }
        public BlueprintProgressionPolicySnapshot progression() { return progression; }
        BlueprintResearchPolicyResolver.RuleSelection selection(ResourceLocation profile, ResourceLocation target) {
            // Do not retain caller-supplied unknown IDs in the bounded derived cache.
            if (!research.profiles().containsKey(profile)) return BlueprintResearchPolicyResolver.analyzerRuleSelection(research, profile, target, catalog.get(target));
            return selectors.computeIfAbsent(profile, id -> BlueprintResearchPolicyResolver.analyzerRuleSelector(research, id))
                    .apply(target, catalog.get(target));
        }
    }

    public static AnalyzerPolicyQuote resolve(Context context, ResourceLocation profileId, ResourceLocation target,
            AnalyzerTier station, BlueprintAnalyzerPolicy.Origin origin, FoundWeaponRecoveryMode recovery) {
        var quote = resolveLegacy(context, profileId, target, station, origin, recovery);
        if (context.research().researchData().reconstruction().isEmpty()
                || quote.outcomes().filter(row -> row.fragmentWeight() > 0).isEmpty()) return quote;
        var shared = AnalysisReconstructionResolver.resolve(context, profileId, target, station);
        var status = shared.available() ? quote.status() : AnalyzerPolicyQuote.Status.DISABLED;
        return new AnalyzerPolicyQuote(quote.profileId(), quote.blueprintId(), quote.analyzerTier(), quote.origin(), status,
                quote.weaponTier(), quote.tierSource(), quote.reviewRequired(), quote.selectedRuleId(), quote.specificity(),
                quote.attempt(), quote.outcomes(), shared.policy().threshold(), shared.policy().retainedCap());
    }

    static AnalyzerPolicyQuote resolveLegacy(Context context, ResourceLocation profileId, ResourceLocation target,
            AnalyzerTier station, BlueprintAnalyzerPolicy.Origin origin, FoundWeaponRecoveryMode recovery) {
        Objects.requireNonNull(context); Objects.requireNonNull(profileId); Objects.requireNonNull(target);
        Objects.requireNonNull(station); Objects.requireNonNull(origin); Objects.requireNonNull(recovery);
        var profile = context.research().profiles().get(profileId);
        var data = context.catalog().get(target);
        var selection = context.selection(profileId, target);
        var override = selection.selectedRuleId().flatMap(id -> context.research().rules().get(id).analyzer());
        var policy = profile == null ? BlueprintAnalyzerPolicy.LEGACY
                : profile.analyzer().orElse(BlueprintAnalyzerPolicy.LEGACY).apply(override.orElse(null));
        Optional<AnalyzerTier> weapon = Optional.empty();
        String source = "unavailable";
        boolean review = false;
        Optional<BlueprintReverseEngineeringPolicy> attempt = Optional.empty();
        Optional<AnalyzerOutcomeTable> outcomes = Optional.empty();
        int threshold = 0, cap = 0;
        AnalyzerPolicyQuote.Status status;
        if (profile == null || data == null) {
            status = AnalyzerPolicyQuote.Status.CONTENT_UNAVAILABLE;
        } else if (data.getKind() != BlueprintKind.GUN || policy.mode() == BlueprintAnalyzerPolicy.Mode.LEGACY) {
            status = data.getKind() == BlueprintKind.GUN && origin == BlueprintAnalyzerPolicy.Origin.TRADED && !policy.allowedOrigins().contains(origin)
                    ? AnalyzerPolicyQuote.Status.ORIGIN_DISABLED : AnalyzerPolicyQuote.Status.LEGACY;
            var legacy = BlueprintResearchPolicyResolver.reverseEngineeringPolicyFor(context.research(), context.catalog(), profileId, target);
            attempt = Optional.of(origin == BlueprintAnalyzerPolicy.Origin.TRADED ? legacy.forTradedWeapon() : legacy);
            outcomes = Optional.of(AnalyzerOutcomeTable.GUARANTEED_BLUEPRINT);
            source = "legacy";
        } else if (policy.mode() == BlueprintAnalyzerPolicy.Mode.DISABLED) {
            status = AnalyzerPolicyQuote.Status.DISABLED;
        } else {
            weapon = override.flatMap(BlueprintAnalyzerOverride::weaponTier);
            if (weapon.isPresent()) source = "analyzer_rule";
            boolean stale = weapon.isEmpty() && context.progression() != BlueprintProgressionPolicySnapshot.EMPTY
                    && (context.progression().researchRevision() != context.researchRevision()
                            || context.progression().catalogRevision() != context.catalogRevision());
            if (weapon.isEmpty() && !stale) {
                var progression = context.progression().policy(profileId, target);
                if (progression.isPresent()) {
                    var p = progression.orElseThrow();
                    if (!p.profileId().equals(profileId) || !p.blueprintId().equals(target)) throw new IllegalArgumentException("mismatched analysis progression target");
                    weapon = Optional.of(AnalyzerTier.fromLevel(p.researchWorkbenchTier().level()));
                    source = "progression:" + p.tierSource().name().toLowerCase(java.util.Locale.ROOT);
                    review = p.reviewRequired();
                } else if (policy.fallbackWeaponTier().isPresent()) {
                    weapon = policy.fallbackWeaponTier(); source = "analyzer_fallback"; review = true;
                }
            }
            if (stale) status = AnalyzerPolicyQuote.Status.STALE_PROGRESSION;
            else if (weapon.isEmpty()) status = AnalyzerPolicyQuote.Status.TIER_UNAVAILABLE;
            else {
                var row = policy.tiers().get(context.stations().weaponRewardTier(station, weapon.orElseThrow())).get(weapon.orElseThrow());
                var legacy = BlueprintResearchPolicyResolver.reverseEngineeringPolicyFor(context.research(), context.catalog(), profileId, target);
                var effective = policy.effectiveAttempt(legacy, row);
                if (origin == BlueprintAnalyzerPolicy.Origin.FOUND) {
                    effective = new BlueprintReverseEngineeringPolicy(effective.enabled(), effective.inputCount(), effective.cost(),
                            effective.allowKnown(), effective.allowModified(), effective.physicalBlueprintLearningMode(), recovery.foundBlueprintRecyclable(), effective.expertAllowEconomyLoop());
                }
                if (origin == BlueprintAnalyzerPolicy.Origin.TRADED) effective = effective.forTradedWeapon();
                attempt = Optional.of(effective); outcomes = Optional.of(row.outcomes());
                if (policy.reconstruction().isPresent()) {
                    var reconstruction = policy.reconstruction().orElseThrow();
                    threshold = reconstruction.thresholds().get(weapon.orElseThrow()); cap = reconstruction.retainedProgressCap();
                }
                if (!row.enabled() || !effective.enabled() || (!effective.physicalBlueprintLearningMode().learningPermitted() && !effective.outputRecyclable())) status = AnalyzerPolicyQuote.Status.DISABLED;
                else if (context.stations().analyzer() != com.gamergaming.taczweaponblueprints.progression.StationSetup.AnalyzerMode.SINGLE && station.level() < policy.minimumAnalyzerTier().level()) status = AnalyzerPolicyQuote.Status.STATION_TIER_REQUIRED;
                else if (!policy.allowedOrigins().contains(origin)) status = AnalyzerPolicyQuote.Status.ORIGIN_DISABLED;
                else if (origin == BlueprintAnalyzerPolicy.Origin.FOUND && !recovery.blueprintExtractionEnabled()) status = AnalyzerPolicyQuote.Status.DIRECT_RECOVERY_ONLY;
                else status = AnalyzerPolicyQuote.Status.CONFIGURED;
            }
        }
        return new AnalyzerPolicyQuote(profileId, target, station, origin, status, weapon, source, review,
                selection.selectedRuleId(), selection.specificity(), attempt, outcomes, threshold, cap);
    }
}
