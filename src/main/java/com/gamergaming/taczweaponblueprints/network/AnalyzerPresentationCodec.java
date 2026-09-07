package com.gamergaming.taczweaponblueprints.network;

import java.util.ArrayList;
import java.util.Optional;
import com.gamergaming.taczweaponblueprints.menu.AnalyzerPresentation;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerPreview;
import com.gamergaming.taczweaponblueprints.progression.BlueprintReverseEngineeringService;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisRewardClass;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable;
import com.gamergaming.taczweaponblueprints.resource.research.AnalyzerPolicyQuote;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Protocol 59 extension. Validate counts before allocation and records before publication. */
final class AnalyzerPresentationCodec {
    private AnalyzerPresentationCodec() { }
    static void write(FriendlyByteBuf b, AnalyzerPresentation p) {
        b.writeVarInt(p.stationTier());
        b.writeBoolean(p.weapon().isPresent());
        p.weapon().ifPresent(w -> {
            b.writeVarInt(w.tier()); b.writeVarInt(w.status().ordinal());
            b.writeBoolean(w.outcomes().isPresent());
            w.outcomes().ifPresent(o -> { b.writeVarInt(o.blueprintWeight()); b.writeVarInt(o.fragmentWeight());
                b.writeVarInt(o.noResultWeight()); b.writeVarInt(o.minimumFragments()); b.writeVarInt(o.maximumFragments()); });
            reward(b, w.reward()); b.writeVarInt(w.saved()); b.writeVarInt(w.totalSaved()); b.writeVarInt(w.threshold()); b.writeVarInt(w.cap());
        });
        b.writeBoolean(p.fragments().isPresent());
        p.fragments().ifPresent(f -> { b.writeVarInt(f.reward().ordinal()); reward(b, f.outputReward());
            b.writeVarInt(f.totalSaved()); b.writeVarInt(f.cap()); });
        b.writeBoolean(p.recoveryCost().isPresent());
        p.recoveryCost().ifPresent(c -> {
            b.writeVarInt(c.inputCount()); b.writeVarInt(c.points()); b.writeVarInt(c.status().ordinal());
            b.writeVarInt(c.ingredients().size());
            c.ingredients().forEach(i -> {
                b.writeVarInt(i.items().size()); i.items().forEach(id -> id(b, id));
                b.writeBoolean(i.tag().isPresent()); i.tag().ifPresent(id -> id(b, id));
                b.writeVarInt(i.required()); b.writeVarInt(i.inventoryAvailable());
            });
        });
        // Omit an absent tail to preserve frozen pre-Data payloads exactly.
        p.data().ifPresent(d -> {
            b.writeByte(1); b.writeVarInt(d.minimumStation()); b.writeVarInt(d.configuredTiers());
            b.writeBoolean(d.allowLearned()); b.writeBoolean(d.allowUndiscovered()); b.writeBoolean(d.partialProgressBias());
            b.writeVarInt(d.reward().ordinal());
            for (var row : d.stations()) {
                b.writeVarInt(row.eligibleTiers()); b.writeDouble(row.blueprintProbability());
                b.writeBoolean(row.blueprintsPossible()); b.writeBoolean(row.fragmentsPossible());
                b.writeVarInt(row.minimumFragments()); b.writeVarInt(row.maximumFragments());
            }
        });
    }
    static AnalyzerPresentation read(FriendlyByteBuf b) {
        int tier = b.readVarInt();
        Optional<AnalyzerPresentation.Weapon> weapon = Optional.empty();
        if (b.readBoolean()) {
            int wt = b.readVarInt(); var status = value(b, AnalyzerPolicyQuote.Status.values());
            var outcomes = b.readBoolean() ? Optional.of(new AnalyzerOutcomeTable(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()))
                    : Optional.<AnalyzerOutcomeTable>empty();
            weapon = Optional.of(new AnalyzerPresentation.Weapon(wt, status, outcomes, reward(b), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()));
        }
        var fragments = b.readBoolean() ? Optional.of(new AnalyzerPresentation.Fragments(value(b, AnalysisRewardClass.values()), reward(b), b.readVarInt(), b.readVarInt()))
                : Optional.<AnalyzerPresentation.Fragments>empty();
        Optional<AnalyzerPresentation.RecoveryCost> cost = Optional.empty();
        if (b.readBoolean()) {
            int input = b.readVarInt(), points = b.readVarInt(); var status = value(b, BlueprintReverseEngineeringService.Status.values());
            int size = count(b, BlueprintResearchCost.MAX_INGREDIENT_TYPES);
            var ingredients = new ArrayList<BlueprintRecyclerPreview.IngredientPreview>(size);
            for (int j = 0; j < size; j++) {
                int alternatives = count(b, BlueprintResearchIngredient.MAX_ITEMS);
                var items = new ArrayList<ResourceLocation>(alternatives);
                for (int i = 0; i < alternatives; i++) items.add(id(b));
                ingredients.add(new BlueprintRecyclerPreview.IngredientPreview(items, b.readBoolean() ? Optional.of(id(b)) : Optional.empty(), b.readVarInt(), b.readVarInt()));
            }
            cost = Optional.of(new AnalyzerPresentation.RecoveryCost(input, points, ingredients, status));
        }
        var result = new AnalyzerPresentation(tier, weapon, fragments, cost);
        if (b.isReadable()) {
            if (b.readUnsignedByte()!=1) throw new IllegalArgumentException("unsupported Data presentation");
            int minimum=b.readVarInt(), tiers=b.readVarInt(); boolean learned=b.readBoolean(), undiscovered=b.readBoolean(), bias=b.readBoolean();
            var reward=value(b, AnalysisRewardClass.values());
            var rows=new ArrayList<com.gamergaming.taczweaponblueprints.menu.ResearchDataPresentation.Station>(3);
            for (int i=1;i<=3;i++) rows.add(new com.gamergaming.taczweaponblueprints.menu.ResearchDataPresentation.Station(i,b.readVarInt(),b.readDouble(),b.readBoolean(),b.readBoolean(),b.readVarInt(),b.readVarInt()));
            result=result.withData(new com.gamergaming.taczweaponblueprints.menu.ResearchDataPresentation(minimum,tiers,learned,undiscovered,bias,reward,rows));
            if (b.isReadable()) throw new IllegalArgumentException("unexpected Data presentation trailing bytes");
        }
        return result;
    }
    static void outcome(FriendlyByteBuf b, Optional<AnalyzerOutcomeTable.Result> result) {
        b.writeBoolean(result.isPresent()); result.ifPresent(r -> { b.writeVarInt(r.kind().ordinal()); b.writeVarInt(r.count()); });
    }
    static Optional<AnalyzerOutcomeTable.Result> outcome(FriendlyByteBuf b) {
        return b.readBoolean() ? Optional.of(new AnalyzerOutcomeTable.Result(value(b, AnalyzerOutcomeTable.Kind.values()), b.readVarInt())) : Optional.empty();
    }
    static void fragments(FriendlyByteBuf b, Optional<com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract.FragmentResult> f) {
        b.writeBoolean(f.isPresent()); f.ifPresent(r -> { b.writeVarInt(r.archived()); b.writeVarInt(r.retained()); b.writeVarInt(r.saved()); b.writeBoolean(r.blueprintCreated()); });
    }
    static Optional<com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract.FragmentResult> fragments(FriendlyByteBuf b) {
        return b.readBoolean() ? Optional.of(new com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract.FragmentResult(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean())) : Optional.empty();
    }
    private static void reward(FriendlyByteBuf b, Optional<AnalysisRewardClass> reward) {
        b.writeBoolean(reward.isPresent()); reward.ifPresent(r -> b.writeVarInt(r.ordinal()));
    }
    private static Optional<AnalysisRewardClass> reward(FriendlyByteBuf b) {
        return b.readBoolean() ? Optional.of(value(b, AnalysisRewardClass.values())) : Optional.empty();
    }
    private static <T> T value(FriendlyByteBuf b, T[] values) {
        int index = b.readVarInt();
        if (index < 0 || index >= values.length) throw new IllegalArgumentException("invalid Analyzer enum");
        return values[index];
    }
    private static int count(FriendlyByteBuf b, int maximum) {
        int count = b.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("invalid Analyzer list length");
        return count;
    }
    private static void id(FriendlyByteBuf b, ResourceLocation id) { b.writeUtf(id.toString(), PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH); }
    private static ResourceLocation id(FriendlyByteBuf b) {
        var id = ResourceLocation.tryParse(b.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH));
        if (id == null) throw new IllegalArgumentException("invalid Analyzer resource ID");
        return id;
    }
}
