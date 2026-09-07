package com.gamergaming.taczweaponblueprints.progression.analyzer;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Four disjoint counts for one target. Ordinary fragments never enter this value. */
public record AnalysisFragmentCounts(int protectedPrerequisites, int protectedBypass,
        int recyclablePrerequisites, int recyclableBypass) {
    public static final AnalysisFragmentCounts EMPTY = new AnalysisFragmentCounts(0, 0, 0, 0);
    public AnalysisFragmentCounts {
        if (protectedPrerequisites < 0 || protectedBypass < 0 || recyclablePrerequisites < 0 || recyclableBypass < 0
                || (long) protectedPrerequisites + protectedBypass + recyclablePrerequisites + recyclableBypass
                        > PlayerProgressionLimits.MAX_PROGRESS_VALUE) throw new IllegalArgumentException("analysis fragment counts exceed their shared bound");
    }
    public int total() { return protectedPrerequisites + protectedBypass + recyclablePrerequisites + recyclableBypass; }
    public int get(AnalysisRewardClass reward) {
        return switch (reward) {
            case PROTECTED_PREREQUISITES -> protectedPrerequisites;
            case PROTECTED_BYPASS -> protectedBypass;
            case RECYCLABLE_PREREQUISITES -> recyclablePrerequisites;
            case RECYCLABLE_BYPASS -> recyclableBypass;
        };
    }
    public AnalysisFragmentCounts with(AnalysisRewardClass reward, int value) {
        java.util.Objects.requireNonNull(reward);
        return new AnalysisFragmentCounts(reward == AnalysisRewardClass.PROTECTED_PREREQUISITES ? value : protectedPrerequisites,
                reward == AnalysisRewardClass.PROTECTED_BYPASS ? value : protectedBypass,
                reward == AnalysisRewardClass.RECYCLABLE_PREREQUISITES ? value : recyclablePrerequisites,
                reward == AnalysisRewardClass.RECYCLABLE_BYPASS ? value : recyclableBypass);
    }
    public CompoundTag toTag() {
        var result = new CompoundTag();
        for (var reward : AnalysisRewardClass.values()) if (get(reward) > 0) result.putInt(reward.serializedName(), get(reward));
        return result;
    }
    public static AnalysisFragmentCounts fromTag(CompoundTag tag) {
        if (tag == null || tag.size() > 4) throw new IllegalArgumentException("invalid analysis counts");
        var result = EMPTY;
        for (String key : tag.getAllKeys()) {
            var reward = AnalysisRewardClass.parse(key).orElseThrow(() -> new IllegalArgumentException("unknown analysis reward class"));
            if (!tag.contains(key, Tag.TAG_INT) || tag.getInt(key) <= 0) throw new IllegalArgumentException("invalid analysis count");
            result = result.with(reward, tag.getInt(key));
        }
        return result;
    }
    public static Map<String, AnalysisFragmentCounts> snapshot(Map<String, AnalysisFragmentCounts> values) {
        if (values == null || values.size() > PlayerProgressionLimits.MAX_FRAGMENT_TARGETS) throw new IllegalArgumentException("invalid analysis archive");
        var copy = new TreeMap<String, AnalysisFragmentCounts>();
        values.forEach((id, counts) -> {
            if (!validTarget(id) || counts == null || counts.total() == 0) throw new IllegalArgumentException("invalid analysis archive entry");
            copy.put(id, counts);
        });
        return Collections.unmodifiableMap(copy);
    }
    private static boolean validTarget(String id) {
        if (id == null || id.length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) return false;
        var parsed = ResourceLocation.tryParse(id);
        return parsed != null && parsed.toString().equals(id);
    }
    public static boolean fitsTogether(Map<String, Integer> ordinary, Map<String, AnalysisFragmentCounts> analysis) {
        var targets = new TreeSet<>(ordinary.keySet()); targets.addAll(analysis.keySet());
        if (targets.size() > PlayerProgressionLimits.MAX_FRAGMENT_TARGETS) return false;
        for (var entry : analysis.entrySet()) {
            if ((long) ordinary.getOrDefault(entry.getKey(), 0) + entry.getValue().total() > PlayerProgressionLimits.MAX_PROGRESS_VALUE) return false;
        }
        return true;
    }
}
