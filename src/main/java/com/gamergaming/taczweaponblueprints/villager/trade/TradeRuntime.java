package com.gamergaming.taczweaponblueprints.villager.trade;

import java.util.*;
import com.gamergaming.taczweaponblueprints.compat.magazines.*;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.minecraft.core.registries.BuiltInRegistries;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.*;

/** Server-thread inspection against current publications; no spawning, sampling, or cached permissions. */
public final class TradeRuntime {
    private TradeRuntime() {}
    public record Inspection(long definitionsRevision, long catalogRevision, long researchRevision,
            String policyIdentity, long magazineRevision, String magazineStatus, TradeSettings settings,
            Optional<String> reloadFailure, TradeResolver.Resolution resolution) {}
    public record View(Inspection inspection, Map<ResourceLocation,List<TradeResolver.Row>> alternatives) {}
    public static Inspection inspect() { return view().inspection(); }
    public static Map<ResourceLocation,List<TradeResolver.Row>> alternatives() { return view().alternatives(); }
    public static View view() {
        var definitions=TradeDataManager.INSTANCE.publication();
        var content=new CurrentContent(definitions.snapshot());
        var config=ModConfigs.BLUEPRINT;var settings=config.villagerSettings();var currency=settings.currency(config.currencyPolicy());
        var inspection=new Inspection(definitions.revision(),BlueprintDataManager.SERVER.catalogRevision(),
                BlueprintResearchDataManager.INSTANCE.revision(),content.context.map(c->c.revisionIdentity().toString()).orElse("pending"),
                content.magazines.revision(),content.magazines.catalog().status().name(),settings,definitions.failure(),
                TradeResolver.resolve(definitions.snapshot(),settings,currency,content));
        return new View(inspection,TradeResolver.alternatives(definitions.snapshot(),settings,currency,content));
    }
    private static final class CurrentContent implements TradeResolver.Content {
        private final Optional<ProgressionPolicyAccessService.Context> context =
                ProgressionPolicyAccessService.acquire(ProgressionPolicyAccessService.Mode.CURRENT_ONLY);
        private final MagazineCatalog.Publication magazines = MagazineCatalog.INSTANCE.publication();
        private final TradeSettings settings = ModConfigs.BLUEPRINT.villagerSettings();
        private final TradeSnapshot definitions;
        private CurrentContent(TradeSnapshot definitions) { this.definitions = definitions; }
        private final Map<ResourceLocation, String> dataReasons = new HashMap<>();
        private final Map<Selection, Set<ResourceLocation>> magazineGuns = new EnumMap<>(Selection.class);
        @Override public boolean ready() { return context.isPresent() && !com.gamergaming.taczweaponblueprints.villager.stock.StockPublicationCoordinator.pending(); }
        @Override public boolean pending(Output output) {
            return !ready() || output.kind() == Kind.MAGAZINE
                    && magazines.catalog().status() == TaCZMagazinesAdapter.Status.WAITING_FOR_RECIPES;
        }
        @Override public boolean modLoaded(String id) { return ModList.get().isLoaded(id); }
        @Override public int stackLimit(ResourceLocation id) {
            if (!BuiltInRegistries.ITEM.containsKey(id)) return 0;
            var item = BuiltInRegistries.ITEM.get(id);
            if (item == null || item == Items.AIR) return 0;
            int limit = new ItemStack(item).getMaxStackSize();
            return limit > 0 && limit <= 64 ? limit : 0;
        }
        @Override public String unavailable(Output output,ResourceLocation id,TradeResolver.Price price) {
            if(output.kind()==Kind.RESEARCH_DATA && ResearchDataAnalysisService.legacyAllowed(id)) {
                if(stackLimit(id)<output.count())return "missing_or_oversized_data_item";
                return LegacyDataTradeSafety.unavailable(output,id,price,new ItemStack(BuiltInRegistries.ITEM.get(id)));
            }
            return unavailable(output,id);
        }
        @Override public String unavailable(Output output, ResourceLocation id) {
            if (output.kind() == Kind.RESEARCH_DATA) {
                if (stackLimit(id) < output.count()) return "missing_or_oversized_data_item";
                return dataReasons.computeIfAbsent(id, this::dataReason);
            }
            if (output.kind() == Kind.MAGAZINE) {
                if (magazines.catalog().status() != TaCZMagazinesAdapter.Status.READY) return "magazines_" + magazines.catalog().status().name().toLowerCase(Locale.ROOT);
                var family = magazines.catalog().families().get(id);
                if (family == null) return "missing_magazine_family";
                if (family.extensionLevel() > 0 && !settings.extendedMagazines()) return "extended_magazines_disabled";
                if (output.count() > family.output().getMaxStackSize()) return "oversized_magazine_output";
                return family.compatibleGuns().stream().anyMatch(gun -> equipmentReason(gun, BlueprintKind.GUN, output.tier(), true).isEmpty())
                        ? "" : "no_allowed_compatible_gun";
            }
            boolean knowledge = output.kind() == Kind.ANALYSIS_FRAGMENTS || output.kind() == Kind.BLUEPRINT;
            BlueprintKind kind = switch (output.kind()) {
                case GUN, ANALYSIS_FRAGMENTS, BLUEPRINT -> BlueprintKind.GUN;
                case AMMO -> BlueprintKind.AMMO;
                case ATTACHMENT -> BlueprintKind.ATTACHMENT;
                default -> throw new IllegalArgumentException("unsupported catalog output");
            };
            String reason = equipmentReason(id, kind, knowledge ? 0 : output.tier(), !knowledge);
            if (!reason.isEmpty()) return reason;
            var c = context.orElseThrow(); var data = c.catalog().blueprints().get(id);
            if (!knowledge && output.count() > data.getRecipe().getOutput().getMaxStackSize()) return "oversized_output";
            if (!knowledge) return "";
            if (!ModConfigs.BLUEPRINT.progressionSnapshot().blueprintsEnabled()) return "blueprints_disabled";
            if (ModConfigs.BLUEPRINT.accessSnapshot().isProgressionExempt(id, data)) return "progression_exempt_target";
            if (c.policyFor(id).isEmpty()) return "research_target_excluded";
            var source = BlueprintWeaponAnalysisService.context();
            var quote = BlueprintAnalyzerPolicyResolver.resolve(source, c.profileId(), id, AnalyzerTier.TIER_3,
                    BlueprintAnalyzerPolicy.Origin.UNKNOWN, FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY);
            int tier = quote.weaponTier().map(AnalyzerTier::level).orElseGet(() -> c.policyFor(id).orElseThrow().researchWorkbenchTier().level());
            if (output.tier() > 0 && tier != output.tier()) return "study_tier_mismatch";
            var reconstruction = AnalysisReconstructionResolver.resolve(source, c.profileId(), id, AnalyzerTier.TIER_3);
            return reconstruction.available() && reconstruction.policy().learning().learningPermitted()
                    ? "" : "reconstruction_disabled";
        }
        private String equipmentReason(ResourceLocation id, BlueprintKind kind, int requiredTier, boolean applyMarketCap) {
            if (context.isEmpty()) return "catalog_pending";
            var c = context.orElseThrow(); var data = c.catalog().blueprints().get(id);
            if (data == null || data.getRecipe() == null || data.getRecipe().getOutput().isEmpty()) return "missing_recipe";
            if (data.getKind() != kind) return "wrong_content_kind";
            if (ModConfigs.BLUEPRINT.isItemBlacklisted(id.toString())) return "content_blacklisted";
            var crafting = c.craftingPolicyFor(id);
            if (crafting.isEmpty()) return "crafting_policy_unavailable";
            if (crafting.orElseThrow().disposition() == BlueprintCraftingDisposition.DISABLED) return "crafting_disabled";
            int researchTier = c.policyFor(id).map(p -> p.researchWorkbenchTier().level()).orElse(1);
            int craftingTier = crafting.orElseThrow().requiredWorkbenchTier().map(t -> t.level()).orElse(1);
            int tier = Math.max(researchTier, craftingTier);
            if (applyMarketCap && kind == BlueprintKind.GUN && tier > settings.maximumGunTier()) return "maximum_gun_tier";
            return requiredTier > 0 && tier != requiredTier ? "market_tier_mismatch" : "";
        }
        private String dataReason(ResourceLocation item) {
            var config = ModConfigs.BLUEPRINT;
            if (!config.progressionSnapshot().blueprintsEnabled()) return "blueprints_disabled";
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(item));
            var owner = ResearchDataAnalysisService.owner(stack);
            if (owner.isEmpty()) return "missing_data_policy";
            var poolContext = ResearchDataAnalysisService.context();
            var policy = poolContext.source().research().researchData().policies().get(owner.orElseThrow());
            if (config.dataSettings().behaviorFor(Optional.ofNullable(policy)) != ResearchDataPolicy.Behavior.ANALYSIS)
                return "data_analysis_disabled_or_legacy";
            if (policy.rewardClass() != AnalysisRewardClass.PROTECTED_PREREQUISITES) return "unprotected_data_rewards";
            var c = context.orElseThrow();
            // This is global usefulness, independent of the first customer's archive/discovery.
            var player = new ResearchDataPoolResolver.PlayerView(Set.of(), c.catalog().blueprints().keySet(), Map.of());
            var pool = ResearchDataPoolResolver.evaluate(poolContext, owner.orElseThrow(), c.profileId(), AnalyzerTier.TIER_3,
                    config.dataSettings().defaults(), player, id -> equipmentReason(id, BlueprintKind.GUN, 0, false).isEmpty()
                            && !config.accessSnapshot().isProgressionExempt(id, c.catalog().blueprints().get(id)));
            return pool.ready() ? "" : "data_pool_" + pool.status().name().toLowerCase(Locale.ROOT);
        }
        @Override public List<Choice> magazineCandidates(Output output) {
            if (!settings.categories().contains(Kind.GUN)) return List.of();
            var guns=magazineGuns.computeIfAbsent(output.selection(),selection->TradeResolver.magazineGuns(
                    definitions,settings,settings.currency(ModConfigs.BLUEPRINT.currencyPolicy()),this,selection));
            return magazines.catalog().families().values().stream()
                    .filter(f -> f.extensionLevel() == 0 || settings.extendedMagazines())
                    .filter(f -> f.compatibleGuns().stream().anyMatch(guns::contains))
                    .map(f -> f.familyKey()).filter(id -> id.toString().length() <= TradeLimits.ID_LENGTH)
                    .sorted().map(id -> new Choice(id, 1)).toList();
        }
    }
}
