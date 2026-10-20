package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import net.minecraft.resources.ResourceLocation;

class AutomaticWeaponAnchorCandidatesTest {
    @Test void lazyTraversalMatchesThePreviousFullScanAndRotationAtEveryPositionBoundary() throws Exception {
        var same = new ArrayList<ResourceLocation>();
        var positions = new HashMap<ResourceLocation, ProgressionPosition>();
        for (int i = 0; i < 60; i++) {
            var id = id("same_" + i); same.add(id); positions.put(id, position(i / 3));
        }
        for (int size : List.of(0, 1, 2, 60)) for (int sibling = 0; sibling <= 21; sibling++) {
            var bucket = List.copyOf(same.subList(0, size));
            var authored = sibling % 2 == 0 ? List.of(id("old_a"), id("old_b")) : List.<ResourceLocation>of();
            var automatic = sibling % 3 == 0 ? List.<ResourceLocation>of() : List.of(id("old_c"), id("old_d"), id("old_e"));
            // Include a real target in the bucket; equal-position peers must also be excluded.
            var target = sibling < 20 ? same.get(sibling * 3) : id("target_" + sibling);
            var actual = AutomaticWeaponPrerequisitePlanner.anchorCandidates(target, position(sibling), bucket, authored, automatic, positions::get);
            int order = sibling;
            var earlier = bucket.stream().filter(id -> !id.equals(target) && positions.get(id).siblingOrder() < order).toList();
            var expected = new ArrayList<ResourceLocation>();
            for (var group : List.of(earlier, authored, automatic)) expected.addAll(oldRotation(target, group));
            assertIterableEquals(expected, actual);
            assertEquals(expected, new ArrayList<>(actual));
            assertThrows(UnsupportedOperationException.class, () -> actual.add(target));
            assertThrows(IndexOutOfBoundsException.class, () -> actual.get(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> actual.get(actual.size()));
        }
    }

    @Test void denseBucketPreparationUsesLogarithmicReadsAndDoesNotCopyUnusedCandidates() {
        var reads = new AtomicInteger();
        var ids = java.util.stream.IntStream.range(0, 4096).mapToObj(i -> id("same_" + i)).toList();
        var positions = new HashMap<ResourceLocation, ProgressionPosition>();
        for (int i = 0; i < ids.size(); i++) positions.put(ids.get(i), position(i));
        var counted = new AbstractList<ResourceLocation>() {
            public int size() { return ids.size(); }
            public ResourceLocation get(int i) { reads.incrementAndGet(); return ids.get(i); }
        };
        for (int target = 0; target < ids.size(); target++) {
            reads.set(0);
            var candidates = AutomaticWeaponPrerequisitePlanner.anchorCandidates(ids.get(target), position(target), counted,
                    List.of(), List.of(), positions::get);
            assertEquals(target, candidates.size());
            assertTrue(reads.get() <= 13, "preparation rescanned a dense bucket");
            int preparedReads = reads.get();
            if (target > 0) candidates.iterator().next();
            assertEquals(preparedReads + (target > 0 ? 1 : 0), reads.get());
        }
    }

    private static List<ResourceLocation> oldRotation(ResourceLocation target, List<ResourceLocation> candidates) throws Exception {
        var result = new ArrayList<>(candidates);
        if (result.size() > 1) {
            long hash = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(target.toString().getBytes(StandardCharsets.UTF_8))).getLong();
            Collections.rotate(result, -(int) Math.floorMod(hash, result.size()));
        }
        return result;
    }
    private static ProgressionPosition position(long order) { return new ProgressionPosition(Tier.STARTER, 0, order); }
    private static ResourceLocation id(String path) { return ResourceLocation.parse("test:" + path); }
}
