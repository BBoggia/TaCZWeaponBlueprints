package com.gamergaming.taczweaponblueprints.villager.trade;

import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Structural data only. Missing installed content is resolved separately. */
public record TradeDefinition(ResourceLocation id, Profession profession, int level,
        ResourceLocation slot, Role role, boolean enabled, Output output, Input primary,
        Optional<Input> secondary, int uses, int xp, float demandMultiplier,
        Optional<Integer> minimumBasisPoints, List<String> requiredMods, List<ResourceLocation> fallbacks) {
    public enum Profession { GUNSMITH, BALLISTICS_RESEARCHER }
    public enum Role { CORE, SUPPORT, FALLBACK }
    public enum Kind { CURRENCY, RESEARCH_DATA, GUN, AMMO, ATTACHMENT, MAGAZINE, ANALYSIS_FRAGMENTS, BLUEPRINT }
    /** Pair/study identity interpreted by stock planning, never by customer knowledge. */
    public enum Selection { NONE, STARTER, EXPERT, STOCKED, STUDY_T1, STUDY_T2 }
    public record Choice(ResourceLocation id, double weight) {
        public Choice {
            checkId(id);
            if (!Double.isFinite(weight) || weight <= 0 || weight > 1_000_000)
                throw new IllegalArgumentException("candidate weight must be finite and in (0, 1000000]");
        }
    }
    public record Output(Kind kind, int count, int tier, Selection selection, List<Choice> candidates, boolean allowLegacyExchange) {
        public Output(Kind kind,int count,int tier,Selection selection,List<Choice> candidates) {
            this(kind,count,tier,selection,candidates,false);
        }
        public Output {
            Objects.requireNonNull(kind); Objects.requireNonNull(selection);
            candidates = List.copyOf(candidates);
            if(allowLegacyExchange && kind!=Kind.RESEARCH_DATA)throw new IllegalArgumentException("legacy exchange applies only to Research Data");
            range(count, 1, 64, "output count"); range(tier, 0, 3, "tier");
            if (candidates.size() > TradeLimits.CHOICES || candidates.stream().map(Choice::id).distinct().count() != candidates.size())
                throw new IllegalArgumentException("too many or duplicate candidates");
            if ((kind == Kind.CURRENCY) != candidates.isEmpty() && !(kind == Kind.MAGAZINE && candidates.isEmpty()))
                throw new IllegalArgumentException("outputs require explicit candidates; currency has none");
            if (kind == Kind.GUN && count != 1) throw new IllegalArgumentException("gun output count must be one");
            if ((kind == Kind.CURRENCY || kind == Kind.RESEARCH_DATA) && (tier != 0 || selection != Selection.NONE))
                throw new IllegalArgumentException("currency and Data items cannot have a target tier or pairing selection");
            boolean study = selection == Selection.STUDY_T1 || selection == Selection.STUDY_T2;
            boolean knowledge = kind == Kind.ANALYSIS_FRAGMENTS || kind == Kind.BLUEPRINT;
            if (study && (!knowledge || tier != (selection == Selection.STUDY_T1 ? 1 : 2))
                    || knowledge && selection != Selection.NONE && !study)
                throw new IllegalArgumentException("study selections require a knowledge output with the matching target tier");
        }
    }
    /** Empty item means the effective physical merchant currency. */
    public record Input(Optional<ResourceLocation> item, int count) {
        public Input { item = Objects.requireNonNull(item); item.ifPresent(TradeDefinition::checkId); range(count, 1, 64, "input count"); }
    }
    public TradeDefinition {
        checkId(id); checkId(slot); Objects.requireNonNull(profession); Objects.requireNonNull(role);
        Objects.requireNonNull(output); Objects.requireNonNull(primary); secondary = Objects.requireNonNull(secondary);
        minimumBasisPoints = Objects.requireNonNull(minimumBasisPoints);
        requiredMods = List.copyOf(requiredMods); fallbacks = List.copyOf(fallbacks);
        range(level, 1, 5, "level"); range(uses, 1, TradeLimits.USES, "uses"); range(xp, 0, TradeLimits.XP, "XP");
        if (!Float.isFinite(demandMultiplier) || demandMultiplier < 0 || demandMultiplier > 1)
            throw new IllegalArgumentException("demand multiplier must be finite and in [0,1]");
        minimumBasisPoints.ifPresent(v -> range(v, 0, 10000, "minimum basis points"));
        if (requiredMods.size() > 16 || requiredMods.stream().anyMatch(m -> !m.matches("[a-z][a-z0-9_]{1,63}"))
                || new HashSet<>(requiredMods).size() != requiredMods.size()) throw new IllegalArgumentException("invalid required mods");
        if (fallbacks.size() > TradeLimits.FALLBACK_DEPTH || new HashSet<>(fallbacks).size() != fallbacks.size())
            throw new IllegalArgumentException("invalid fallbacks");
        fallbacks.forEach(TradeDefinition::checkId);
        if (role == Role.SUPPORT && output.kind() != Kind.MAGAZINE)
            throw new IllegalArgumentException("support slots must supply magazines");
        if (output.kind() == Kind.CURRENCY && (primary.item().isEmpty() || secondary.isPresent()))
            throw new IllegalArgumentException("material purchases need one explicit material input");
    }
    public static ResourceLocation parseId(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null || !id.toString().equals(value)) throw new IllegalArgumentException("expected canonical namespaced ID: " + value);
        checkId(id); return id;
    }
    private static void checkId(ResourceLocation id) {
        if (id == null || id.getPath().isBlank() || id.toString().length() > TradeLimits.ID_LENGTH)
            throw new IllegalArgumentException("invalid or oversized ID");
    }
    static void range(int value, int min, int max, String name) {
        if (value < min || value > max) throw new IllegalArgumentException(name + " must be in [" + min + "," + max + "]");
    }
}
