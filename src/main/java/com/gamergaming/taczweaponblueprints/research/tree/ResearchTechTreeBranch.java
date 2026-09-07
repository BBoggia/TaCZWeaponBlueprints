package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;

/** Public containment metadata. Members are direct ownership, never prerequisites. */
public record ResearchTechTreeBranch(ResourceLocation id, Optional<ResourceLocation> parent,
        String title, Optional<String> translationKey, int order,
        Optional<ResourceLocation> anchor, List<ResourceLocation> members) {
    public static final int MAX_BRANCHES = 128;
    public static final int MAX_DEPTH = 16;

    public ResearchTechTreeBranch {
        Objects.requireNonNull(parent); Objects.requireNonNull(translationKey); Objects.requireNonNull(anchor);
        members = List.copyOf(members);
        if (!validId(id) || parent.filter(p -> !validId(p)).isPresent()
                || anchor.filter(p -> !validId(p)).isPresent() || order < 0 || order > 1_000_000
                || title == null || !title.isEmpty() && title.isBlank() || title.length() > 80 || !title.equals(title.trim())
                || title.chars().anyMatch(Character::isISOControl)
                || translationKey.filter(k -> k.isBlank() || k.length() > 256
                        || k.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))).isPresent()
                || title.isEmpty() && translationKey.isPresent()
                || members.size() > ResearchTreeGraph.MAX_NODES
                || members.stream().anyMatch(m -> !validId(m))
                || members.stream().distinct().count() != members.size()) {
            throw new IllegalArgumentException("Invalid Tech Tree branch " + id);
        }
        members = members.stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList();
    }

    public static void validate(List<ResearchTechTreeBranch> branches, Set<ResourceLocation> permitted) {
        if (branches.size() > MAX_BRANCHES) throw new IllegalArgumentException("Tech Tree branch count exceeds " + MAX_BRANCHES);
        Map<ResourceLocation, ResearchTechTreeBranch> byId = new HashMap<>();
        Set<ResourceLocation> owned = new HashSet<>();
        for (var b : branches) {
            if (byId.put(b.id(), b) != null) throw new IllegalArgumentException("Duplicate Tech Tree branch " + b.id());
            for (var id : b.members()) {
                if (!permitted.contains(id) || !owned.add(id)) throw new IllegalArgumentException("Branch " + b.id() + " has missing or duplicate member " + id);
            }
        }
        if (owned.size() > ResearchTreeGraph.MAX_NODES) throw new IllegalArgumentException("Branch membership limit exceeded");
        Set<ResourceLocation> populated = new HashSet<>();
        for (var b : branches) {
            var cursor = b; Set<ResourceLocation> visited = new HashSet<>();
            while (true) {
                if (!visited.add(cursor.id()) || visited.size() > MAX_DEPTH) throw new IllegalArgumentException("Branch cycle or depth limit at " + b.id());
                if (!b.members().isEmpty()) populated.add(cursor.id());
                if (cursor.parent().isEmpty()) break;
                var parent = byId.get(cursor.parent().orElseThrow());
                if (parent == null) throw new IllegalArgumentException("Missing parent of branch " + cursor.id());
                cursor = parent;
            }
            b.anchor().ifPresent(anchor -> {
                var owner = branches.stream().filter(candidate -> candidate.members().contains(anchor)).findFirst();
                if (owner.isEmpty()) throw new IllegalArgumentException("Branch " + b.id() + " has missing anchor " + anchor);
                var current = owner.orElseThrow();
                for (int depth = 0; depth < MAX_DEPTH; depth++) {
                    if (current.id().equals(b.id())) return;
                    if (current.parent().isEmpty()) break;
                    current = byId.get(current.parent().orElseThrow());
                    if (current == null) break;
                }
                throw new IllegalArgumentException("Branch " + b.id() + " anchor is outside its subtree: " + anchor);
            });
        }
        if (populated.size() != branches.size()) throw new IllegalArgumentException("Tech Tree contains an empty branch subtree");
    }

    private static boolean validId(ResourceLocation id) {
        return id != null && id.toString().length() <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH;
    }
}
