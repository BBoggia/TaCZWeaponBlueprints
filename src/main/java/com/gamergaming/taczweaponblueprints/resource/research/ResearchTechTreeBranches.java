package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.*;
import com.gamergaming.taczweaponblueprints.research.tree.*;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import net.minecraft.resources.ResourceLocation;

/** Compiles and sanitizes presentation ownership without consulting progression generation. */
public final class ResearchTechTreeBranches {
    private ResearchTechTreeBranches() { }

    public static void validateDefinitions(List<ResearchTechTreeBranchDefinition> branches,
            List<ResearchTechTreeDefinition.DomainDefinition> domains) {
        if (branches.size() > ResearchTechTreeBranch.MAX_BRANCHES) throw new IllegalArgumentException("Too many authored Tech Tree branches");
        Map<ResourceLocation,ResearchTechTreeBranchDefinition> byId = new HashMap<>();
        for (var b : branches) {
            if (byId.put(b.id(),b) != null) throw new IllegalArgumentException("Duplicate branch " + b.id());
            if (domains.stream().noneMatch(d -> d.domain() == b.domain())) throw new IllegalArgumentException("Branch " + b.id() + " references absent domain " + b.domain());
        }
        for (var b : branches) {
            Set<ResourceLocation> seen = new HashSet<>(); var cursor = b;
            while (true) {
                if (!seen.add(cursor.id()) || seen.size() > ResearchTechTreeBranch.MAX_DEPTH) throw new IllegalArgumentException("Branch cycle/depth limit at " + b.id());
                if (cursor.parent().isEmpty()) break;
                var parent = byId.get(cursor.parent().orElseThrow());
                if (parent == null || parent.domain() != b.domain()) throw new IllegalArgumentException("Branch " + cursor.id() + " has missing or cross-domain parent " + cursor.parent().orElseThrow());
                cursor = parent;
            }
        }
    }

    public static List<ResearchTechTreeBranch> compile(ResourceLocation treeId, ResearchTechTreeDefinition tree,
            Map<ResourceLocation,ResearchTechTreeEntryBundle> bundles) {
        Map<ResourceLocation,List<ResourceLocation>> members = new HashMap<>();
        Set<ResourceLocation> owners = new HashSet<>();
        for (var entry : bundles.entrySet()) {
            if (!entry.getValue().tree().equals(treeId)) continue;
            for (var membership : entry.getValue().branchMemberships()) {
                if (tree.branches().stream().noneMatch(b -> b.id().equals(membership.branch())))
                    throw new IllegalArgumentException("Entry bundle " + entry.getKey() + " references missing branch " + membership.branch() + " in tree " + treeId);
                for (var id : membership.members()) {
                    if (owners.size() >= 4096) throw new IllegalArgumentException("Tech Tree " + treeId + " exceeds branch membership budget");
                    if (!owners.add(id)) throw new IllegalArgumentException("Entry bundle " + entry.getKey() + " duplicates branch ownership for " + id);
                    members.computeIfAbsent(membership.branch(),ignored -> new ArrayList<>()).add(id);
                }
            }
        }
        var result = tree.branches().stream().map(b -> new ResearchTechTreeBranch(b.id(),b.parent(),b.title(),b.translationKey(),b.order(),b.anchor(),members.getOrDefault(b.id(),List.of())))
                .sorted(Comparator.comparing(b -> b.id().toString())).toList();
        try { ResearchTechTreeBranch.validate(result, owners); }
        catch (IllegalArgumentException failure) { throw new IllegalArgumentException("Tech Tree " + treeId + ": " + failure.getMessage(),failure); }
        return result;
    }

    public static List<ResearchTechTreeBranch> publish(BlueprintResearchSnapshot snapshot, ResourceLocation treeId,
            Domain domain, Set<ResourceLocation> permitted, Map<ResourceLocation,ResourceLocation> publicIds,
            Set<ResourceLocation> revealedIds) {
        var definition = snapshot.techTrees().get(treeId);
        Map<ResourceLocation,ResearchTechTreeBranchDefinition> definitions = new HashMap<>();
        definition.branches().stream().filter(b -> b.domain() == domain).forEach(b -> definitions.put(b.id(),b));
        Map<ResourceLocation,List<ResourceLocation>> members = new HashMap<>();
        Set<ResourceLocation> retained = new HashSet<>();
        for (var b : snapshot.authoredBranches(treeId)) {
            if (!definitions.containsKey(b.id())) continue;
            var visible = b.members().stream().map(publicIds::get).filter(Objects::nonNull).filter(permitted::contains).toList();
            members.put(b.id(),visible);
            if (!visible.isEmpty()) {
                var cursor = definitions.get(b.id());
                while (retained.add(cursor.id()) && cursor.parent().isPresent()) cursor = definitions.get(cursor.parent().orElseThrow());
            }
        }
        return definitions.values().stream().filter(b -> retained.contains(b.id())).map(b -> {
            var anchor = b.anchor().map(publicIds::get).filter(permitted::contains).filter(revealedIds::contains);
            boolean label = b.labelVisibility() == ResearchTechTreeBranchDefinition.LabelVisibility.PUBLIC_WHEN_POPULATED || anchor.isPresent();
            return new ResearchTechTreeBranch(b.id(),b.parent(),label ? b.title() : "",label ? b.translationKey() : Optional.empty(),b.order(),anchor,members.getOrDefault(b.id(),List.of()));
        }).sorted(Comparator.comparing(b -> b.id().toString())).toList();
    }
}
